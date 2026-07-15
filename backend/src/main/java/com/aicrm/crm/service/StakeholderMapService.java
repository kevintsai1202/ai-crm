package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Contact;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.StakeholderInfluence;
import com.aicrm.crm.domain.StakeholderRelation;
import com.aicrm.crm.domain.StakeholderRelationType;
import com.aicrm.crm.domain.StakeholderRole;
import com.aicrm.crm.domain.StakeholderRoleType;
import com.aicrm.crm.domain.StakeholderSource;
import com.aicrm.crm.domain.StakeholderStance;
import com.aicrm.crm.domain.StakeholderSuggestionStatus;
import com.aicrm.crm.repository.ContactRepository;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.StakeholderRelationRepository;
import com.aicrm.crm.repository.StakeholderRoleRepository;
import com.aicrm.crm.security.OwnershipGuard;
import com.aicrm.crm.service.JwtService.AuthPrincipal;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stakeholder 決策鏈應用服務（V27）：由現有 Contacts deterministic 產生角色 / 關係「建議」，
 * 提供確認 / 拒絕，並回傳可明確區分「已確認事實」與「待確認建議」的決策鏈圖。
 *
 * <p>核心規則：</p>
 * <ul>
 *   <li>建議狀態為 SUGGESTED，<b>不</b>出現在已確認圖；只有 confirm 後（CONFIRMED）才視為事實。</li>
 *   <li>reject 後（REJECTED）保留稽核但不顯示為事實，也不出現在待確認清單。</li>
 *   <li>關係兩端 Contact 必須屬同一 customer，跨 customer 一律拒絕（400）。</li>
 *   <li>suggest 為 deterministic 且冪等：重複呼叫不重複產生相同建議（以既有紀錄去重）。</li>
 *   <li>owner scope：SALES 僅能操作自己負責客戶，越權由 {@link OwnershipGuard} 丟 403。</li>
 * </ul>
 */
@Service
public class StakeholderMapService {

    /** 角色建議 suggestion id 前綴（供 confirm / reject 路徑辨識類型）。 */
    private static final String ROLE_PREFIX = "role-";

    /** 關係建議 suggestion id 前綴。 */
    private static final String RELATION_PREFIX = "relation-";

    private final CustomerRepository customers;
    private final ContactRepository contacts;
    private final StakeholderRoleRepository roles;
    private final StakeholderRelationRepository relations;
    private final OwnershipGuard ownershipGuard;

    public StakeholderMapService(CustomerRepository customers, ContactRepository contacts,
                                 StakeholderRoleRepository roles, StakeholderRelationRepository relations,
                                 OwnershipGuard ownershipGuard) {
        this.customers = customers;
        this.contacts = contacts;
        this.roles = roles;
        this.relations = relations;
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * 取得某客戶的決策鏈圖：已確認的角色 / 關係（事實）加上待確認（SUGGESTED）建議，兩者可明確區分。
     *
     * @param customerId 客戶 id
     * @param principal 登入主體（owner scope 由 OwnershipGuard 依 SecurityContext 驗證）
     * @return 決策鏈回應（confirmedRoles / confirmedRelations / suggestions 分開欄位）
     */
    @Transactional(readOnly = true)
    public Dtos.StakeholderMapResponse get(Long customerId, AuthPrincipal principal) {
        loadVisibleCustomer(customerId);
        return buildMap(customerId);
    }

    /**
     * 由現有 Contacts deterministic 產生角色與關係建議（狀態 SUGGESTED），並回傳該客戶目前所有待確認建議。
     * 重複呼叫不會重複產生相同建議（以既有紀錄去重），確保可重現。
     *
     * @param customerId 客戶 id
     * @param principal 登入主體
     * @return 待確認建議清單
     */
    @Transactional
    public List<Dtos.StakeholderSuggestionDto> suggest(Long customerId, AuthPrincipal principal) {
        Customer customer = loadVisibleCustomer(customerId);
        // 以聯絡人 id 升冪固定順序，確保 deterministic。
        List<Contact> contacts = customer.getContacts().stream()
                .sorted(Comparator.comparing(Contact::getId))
                .toList();

        generateRoleSuggestions(contacts);
        generateRelationSuggestions(contacts);

        return buildMap(customerId).suggestions();
    }

    /**
     * 將建議（角色或關係）確認為事實。
     *
     * @param suggestionId 建議 id（role-{id} 或 relation-{id}）
     * @param principal 登入主體
     * @return 更新後的建議 DTO（status = CONFIRMED）
     */
    @Transactional
    public Dtos.StakeholderSuggestionDto confirm(String suggestionId, AuthPrincipal principal) {
        return transition(suggestionId, true);
    }

    /**
     * 拒絕建議（保留稽核，狀態轉為 REJECTED）。
     *
     * @param suggestionId 建議 id（role-{id} 或 relation-{id}）
     * @param principal 登入主體
     * @return 更新後的建議 DTO（status = REJECTED）
     */
    @Transactional
    public Dtos.StakeholderSuggestionDto reject(String suggestionId, AuthPrincipal principal) {
        return transition(suggestionId, false);
    }

    /**
     * 手動新增一筆已確認關係（來源 MANUAL、狀態 CONFIRMED）。兩位 Contact 必須屬同一（且為路徑指定）客戶，
     * 否則拒絕（400）。
     *
     * @param customerId 路徑客戶 id
     * @param fromContactId 起點聯絡人 id
     * @param toContactId 終點聯絡人 id
     * @param relationType 關係類型
     * @param principal 登入主體
     * @return 新增的關係 DTO
     */
    @Transactional
    public Dtos.StakeholderRelationDto addManualRelation(Long customerId, Long fromContactId, Long toContactId,
                                                         StakeholderRelationType relationType, AuthPrincipal principal) {
        loadVisibleCustomer(customerId);
        if (Objects.equals(fromContactId, toContactId)) {
            throw new StakeholderValidationException("關係兩端不可為同一位聯絡人");
        }
        Contact from = loadContact(fromContactId);
        Contact to = loadContact(toContactId);
        // 跨 customer 一律拒絕：兩端 Contact 必須同屬路徑指定客戶。
        if (!Objects.equals(from.getCustomer().getId(), customerId)
                || !Objects.equals(to.getCustomer().getId(), customerId)) {
            throw new StakeholderValidationException("關係兩端聯絡人必須屬於同一客戶");
        }
        StakeholderRelation saved = relations.save(new StakeholderRelation(from, to, relationType,
                StakeholderSource.MANUAL, StakeholderSuggestionStatus.CONFIRMED, 100));
        return toRelationDto(saved);
    }

    /**
     * 依聯絡人職稱 deterministic 產生角色建議；每位聯絡人僅產生一次（已存在任何角色紀錄則略過，達成冪等）。
     *
     * @param contacts 客戶聯絡人（已排序）
     */
    private void generateRoleSuggestions(List<Contact> contacts) {
        for (Contact contact : contacts) {
            if (roles.existsByContact_Id(contact.getId())) {
                continue;
            }
            RoleInference inference = inferRole(contact.getTitle());
            roles.save(new StakeholderRole(contact, inference.roleType(), inference.influence(),
                    StakeholderStance.NEUTRAL, inference.confidence(), StakeholderSource.AI,
                    StakeholderSuggestionStatus.SUGGESTED));
        }
    }

    /**
     * deterministic 產生關係建議：找出影響力最高（同分取聯絡人 id 最小）的決策者，
     * 其餘聯絡人各建立一條「REPORTS_TO 決策者」的關係建議。已存在相同三元組（起點/終點/類型）則略過。
     *
     * @param contacts 客戶聯絡人（已排序）
     */
    private void generateRelationSuggestions(List<Contact> contacts) {
        if (contacts.size() < 2) {
            return;
        }
        // 決策者：影響力等級最高、同分取 id 最小（deterministic）。
        Contact decisionMaker = contacts.stream()
                .max(Comparator.<Contact>comparingInt(c -> influenceRank(inferRole(c.getTitle()).influence()))
                        .thenComparing(Comparator.comparing(Contact::getId).reversed()))
                .orElseThrow();
        for (Contact contact : contacts) {
            if (Objects.equals(contact.getId(), decisionMaker.getId())) {
                continue;
            }
            if (relations.existsByFromContact_IdAndToContact_IdAndRelationType(
                    contact.getId(), decisionMaker.getId(), StakeholderRelationType.REPORTS_TO)) {
                continue;
            }
            relations.save(new StakeholderRelation(contact, decisionMaker, StakeholderRelationType.REPORTS_TO,
                    StakeholderSource.AI, StakeholderSuggestionStatus.SUGGESTED, 60));
        }
    }

    /**
     * confirm / reject 共用狀態轉換：僅 SUGGESTED 可轉換，否則丟 409。
     *
     * @param suggestionId 建議 id
     * @param confirm true=確認、false=拒絕
     * @return 更新後的建議 DTO
     */
    private Dtos.StakeholderSuggestionDto transition(String suggestionId, boolean confirm) {
        if (suggestionId != null && suggestionId.startsWith(ROLE_PREFIX)) {
            StakeholderRole role = roles.findById(parseId(suggestionId, ROLE_PREFIX))
                    .orElseThrow(() -> new EntityNotFoundException("查無此角色建議：" + suggestionId));
            ownershipGuard.assertCanAccessOwner(role.getContact().getCustomer().getOwnerName());
            assertPending(role.getStatus());
            if (confirm) {
                role.confirm();
            } else {
                role.reject();
            }
            return toSuggestionDto(roles.save(role));
        }
        if (suggestionId != null && suggestionId.startsWith(RELATION_PREFIX)) {
            StakeholderRelation relation = relations.findById(parseId(suggestionId, RELATION_PREFIX))
                    .orElseThrow(() -> new EntityNotFoundException("查無此關係建議：" + suggestionId));
            ownershipGuard.assertCanAccessOwner(relation.getFromContact().getCustomer().getOwnerName());
            assertPending(relation.getStatus());
            if (confirm) {
                relation.confirm();
            } else {
                relation.reject();
            }
            return toSuggestionDto(relations.save(relation));
        }
        throw new EntityNotFoundException("無法辨識的建議 id：" + suggestionId);
    }

    /** 僅允許對 SUGGESTED 建議做確認 / 拒絕；其餘狀態視為衝突（409）。 */
    private void assertPending(StakeholderSuggestionStatus status) {
        if (status != StakeholderSuggestionStatus.SUGGESTED) {
            throw new StakeholderConflictException("此建議已被確認或拒絕，無法重複處理");
        }
    }

    /** 解析 suggestion id 的數字部分。 */
    private Long parseId(String suggestionId, String prefix) {
        try {
            return Long.parseLong(suggestionId.substring(prefix.length()));
        } catch (NumberFormatException ex) {
            throw new EntityNotFoundException("無法辨識的建議 id：" + suggestionId);
        }
    }

    /**
     * 載入客戶並套用 owner scope（沿用 OpportunityIntelligence 慣例）。
     *
     * @param customerId 客戶 id
     * @return 可見的客戶 entity
     */
    private Customer loadVisibleCustomer(Long customerId) {
        Customer customer = customers.findDetailById(customerId)
                .orElseThrow(() -> new EntityNotFoundException("查無此客戶：" + customerId));
        ownershipGuard.assertCanAccessOwner(customer.getOwnerName());
        return customer;
    }

    /** 載入聯絡人。 */
    private Contact loadContact(Long contactId) {
        return contacts.findById(contactId)
                .orElseThrow(() -> new EntityNotFoundException("查無此聯絡人：" + contactId));
    }

    /**
     * 組裝決策鏈圖：confirmedRoles / confirmedRelations 為事實，suggestions 為待確認建議（REJECTED 不出現在任一）。
     *
     * @param customerId 客戶 id
     * @return 決策鏈回應
     */
    private Dtos.StakeholderMapResponse buildMap(Long customerId) {
        List<Dtos.StakeholderRoleDto> confirmedRoles = roles
                .findByContact_Customer_IdAndStatus(customerId, StakeholderSuggestionStatus.CONFIRMED).stream()
                .sorted(Comparator.comparing(StakeholderRole::getId))
                .map(this::toRoleDto)
                .toList();
        List<Dtos.StakeholderRelationDto> confirmedRelations = relations
                .findByFromContact_Customer_IdAndStatus(customerId, StakeholderSuggestionStatus.CONFIRMED).stream()
                .sorted(Comparator.comparing(StakeholderRelation::getId))
                .map(this::toRelationDto)
                .toList();

        List<Dtos.StakeholderSuggestionDto> suggestions = new ArrayList<>();
        roles.findByContact_Customer_IdAndStatus(customerId, StakeholderSuggestionStatus.SUGGESTED).stream()
                .sorted(Comparator.comparing(StakeholderRole::getId))
                .forEach(r -> suggestions.add(toSuggestionDto(r)));
        relations.findByFromContact_Customer_IdAndStatus(customerId, StakeholderSuggestionStatus.SUGGESTED).stream()
                .sorted(Comparator.comparing(StakeholderRelation::getId))
                .forEach(r -> suggestions.add(toSuggestionDto(r)));

        return new Dtos.StakeholderMapResponse(customerId, confirmedRoles, confirmedRelations, suggestions);
    }

    /** 將角色 entity 轉為 DTO。 */
    private Dtos.StakeholderRoleDto toRoleDto(StakeholderRole role) {
        Contact contact = role.getContact();
        return new Dtos.StakeholderRoleDto(role.getId(), contact.getId(), contact.getName(), contact.getTitle(),
                role.getRoleType(), role.getInfluence(), role.getStance(), role.getConfidence(),
                role.getSource(), role.getStatus());
    }

    /** 將關係 entity 轉為 DTO。 */
    private Dtos.StakeholderRelationDto toRelationDto(StakeholderRelation relation) {
        Contact from = relation.getFromContact();
        Contact to = relation.getToContact();
        return new Dtos.StakeholderRelationDto(relation.getId(), from.getId(), from.getName(), to.getId(), to.getName(),
                relation.getRelationType(), relation.getSource(), relation.getStatus());
    }

    /** 將角色包裝為建議 DTO（suggestionId = role-{id}）。 */
    private Dtos.StakeholderSuggestionDto toSuggestionDto(StakeholderRole role) {
        return new Dtos.StakeholderSuggestionDto(ROLE_PREFIX + role.getId(), "ROLE", role.getStatus(),
                toRoleDto(role), null);
    }

    /** 將關係包裝為建議 DTO（suggestionId = relation-{id}）。 */
    private Dtos.StakeholderSuggestionDto toSuggestionDto(StakeholderRelation relation) {
        return new Dtos.StakeholderSuggestionDto(RELATION_PREFIX + relation.getId(), "RELATION", relation.getStatus(),
                null, toRelationDto(relation));
    }

    /** 影響力等級數值化（供排序找決策者）。 */
    private int influenceRank(StakeholderInfluence influence) {
        return switch (influence) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    /**
     * 依職稱關鍵字 deterministic 推論角色、影響力與信心。純函式（同輸入必得同輸出），確保 suggest 可重現。
     *
     * @param title 聯絡人職稱
     * @return 角色推論結果
     */
    private RoleInference inferRole(String title) {
        String t = title == null ? "" : title;
        if (containsAny(t, "董事長", "總經理", "執行長", "總裁", "CEO")) {
            return new RoleInference(StakeholderRoleType.DECISION_MAKER, StakeholderInfluence.HIGH, 85);
        }
        if (containsAny(t, "採購", "財務", "會計", "CFO")) {
            return new RoleInference(StakeholderRoleType.ECONOMIC_BUYER, StakeholderInfluence.HIGH, 80);
        }
        if (containsAny(t, "技術", "工程", "研發", "資訊", "IT", "CTO")) {
            return new RoleInference(StakeholderRoleType.TECHNICAL_BUYER, StakeholderInfluence.MEDIUM, 75);
        }
        if (containsAny(t, "協理", "副總", "經理", "主管")) {
            return new RoleInference(StakeholderRoleType.INFLUENCER, StakeholderInfluence.MEDIUM, 65);
        }
        if (containsAny(t, "助理", "秘書")) {
            return new RoleInference(StakeholderRoleType.GATEKEEPER, StakeholderInfluence.LOW, 55);
        }
        return new RoleInference(StakeholderRoleType.END_USER, StakeholderInfluence.LOW, 50);
    }

    /** 判斷字串是否含任一關鍵字。 */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 角色推論結果（純資料）。
     *
     * @param roleType 角色類型
     * @param influence 影響力
     * @param confidence 信心分數
     */
    private record RoleInference(StakeholderRoleType roleType, StakeholderInfluence influence, int confidence) {}
}
