package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Contact;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.StakeholderRelationType;
import com.aicrm.crm.domain.StakeholderSuggestionStatus;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.StakeholderRelationRepository;
import com.aicrm.crm.repository.StakeholderRoleRepository;
import com.aicrm.crm.support.PostgresTestBase;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * StakeholderMapService 服務層測試（V27）：驗證 AI 建議 / 已確認事實分離、確認、拒絕保留 audit、
 * 跨 customer relation 被拒與 suggest deterministic 去重。服務層測試不帶登入主體，owner scope 由整合測試覆蓋。
 */
class StakeholderMapServiceTest extends PostgresTestBase {

    @Autowired StakeholderMapService service;
    @Autowired CustomerRepository customers;
    @Autowired StakeholderRoleRepository roles;
    @Autowired StakeholderRelationRepository relations;

    /** 建立一個含三位不同職稱聯絡人的客戶，回傳已保存的客戶（其 contacts 為初始化的記憶體集合，可安全讀取）。 */
    private Customer seedCustomerWithContacts() {
        var customer = new Customer("決策鏈測試客戶", "chain@buyer.example", "0912345678", "22345678", "科技", "x");
        // A：總經理 → 決策者（HIGH）；B：工程師 → 技術買者（MEDIUM）；C：採購專員 → 經濟買者（HIGH）
        customer.getContacts().add(new Contact(customer, "王大明", "總經理", "boss@buyer.example"));
        customer.getContacts().add(new Contact(customer, "李小華", "資訊部工程師", "eng@buyer.example"));
        customer.getContacts().add(new Contact(customer, "張美麗", "採購專員", "buyer@buyer.example"));
        return customers.save(customer);
    }

    /** suggest 產生的角色/關係狀態皆為 SUGGESTED，且不出現在已確認圖。 */
    @Test
    void suggest_producesSuggestedAndNotInConfirmedGraph() {
        Long customerId = seedCustomerWithContacts().getId();

        List<Dtos.StakeholderSuggestionDto> suggestions = service.suggest(customerId, null);

        // 三位聯絡人各一角色建議 + 兩條「回報給決策者」關係建議。
        Assertions.assertThat(suggestions).hasSize(5);
        Assertions.assertThat(suggestions).allMatch(s -> s.status() == StakeholderSuggestionStatus.SUGGESTED);

        Dtos.StakeholderMapResponse map = service.get(customerId, null);
        // 已確認圖此時為空；建議全數落在 suggestions。
        Assertions.assertThat(map.confirmedRoles()).isEmpty();
        Assertions.assertThat(map.confirmedRelations()).isEmpty();
        Assertions.assertThat(map.suggestions()).hasSize(5);
    }

    /** confirm 後建議成為已確認事實，進入 confirmed 圖並自待確認清單移除。 */
    @Test
    void confirm_movesSuggestionIntoConfirmedGraph() {
        Long customerId = seedCustomerWithContacts().getId();
        List<Dtos.StakeholderSuggestionDto> suggestions = service.suggest(customerId, null);
        // 取一個角色建議來確認。
        Dtos.StakeholderSuggestionDto roleSuggestion = suggestions.stream()
                .filter(s -> "ROLE".equals(s.kind())).findFirst().orElseThrow();

        Dtos.StakeholderSuggestionDto confirmed = service.confirm(roleSuggestion.suggestionId(), null);
        Assertions.assertThat(confirmed.status()).isEqualTo(StakeholderSuggestionStatus.CONFIRMED);

        Dtos.StakeholderMapResponse map = service.get(customerId, null);
        Assertions.assertThat(map.confirmedRoles()).hasSize(1);
        Assertions.assertThat(map.confirmedRoles().get(0).status()).isEqualTo(StakeholderSuggestionStatus.CONFIRMED);
        // 已確認者不再出現在待確認清單。
        Assertions.assertThat(map.suggestions()).noneMatch(s -> s.suggestionId().equals(roleSuggestion.suggestionId()));
        Assertions.assertThat(map.suggestions()).hasSize(4);
    }

    /** reject 後保留 audit（狀態 REJECTED），但不顯示為事實，也不出現在待確認清單。 */
    @Test
    void reject_keepsAuditButHiddenFromFactsAndPending() {
        Long customerId = seedCustomerWithContacts().getId();
        List<Dtos.StakeholderSuggestionDto> suggestions = service.suggest(customerId, null);
        Dtos.StakeholderSuggestionDto relationSuggestion = suggestions.stream()
                .filter(s -> "RELATION".equals(s.kind())).findFirst().orElseThrow();

        Dtos.StakeholderSuggestionDto rejected = service.reject(relationSuggestion.suggestionId(), null);
        Assertions.assertThat(rejected.status()).isEqualTo(StakeholderSuggestionStatus.REJECTED);

        Dtos.StakeholderMapResponse map = service.get(customerId, null);
        Assertions.assertThat(map.confirmedRelations()).isEmpty();
        Assertions.assertThat(map.suggestions()).noneMatch(s -> s.suggestionId().equals(relationSuggestion.suggestionId()));

        // audit 保留：以 REJECTED 狀態仍存在於資料庫。
        long rejectedCount = relations.findByFromContact_Customer_Id(customerId).stream()
                .filter(r -> r.getStatus() == StakeholderSuggestionStatus.REJECTED).count();
        Assertions.assertThat(rejectedCount).isEqualTo(1);
    }

    /** 跨 customer 的關係被拒絕：兩位 Contact 屬不同客戶時丟出驗證例外。 */
    @Test
    void manualRelation_crossCustomerIsRejected() {
        Customer customerA = seedCustomerWithContacts();
        Customer customerB = seedCustomerWithContacts();
        // 由已保存實例的記憶體 contacts 取 id，避免對 detached entity 觸發 LazyInitialization。
        Long contactA = customerA.getContacts().get(0).getId();
        Long contactB = customerB.getContacts().get(0).getId();

        Assertions.assertThatThrownBy(() ->
                        service.addManualRelation(customerA.getId(), contactA, contactB, StakeholderRelationType.PEER, null))
                .isInstanceOf(StakeholderValidationException.class);
    }

    /** suggest 為 deterministic：重複呼叫不重複產生相同建議。 */
    @Test
    void suggest_isIdempotent() {
        Long customerId = seedCustomerWithContacts().getId();
        List<Dtos.StakeholderSuggestionDto> first = service.suggest(customerId, null);
        List<Dtos.StakeholderSuggestionDto> second = service.suggest(customerId, null);

        Assertions.assertThat(second).hasSameSizeAs(first);
        // 資料庫不因重複 suggest 而增生。
        Assertions.assertThat(roles.findByContact_Customer_Id(customerId)).hasSize(3);
        Assertions.assertThat(relations.findByFromContact_Customer_Id(customerId)).hasSize(2);
    }
}
