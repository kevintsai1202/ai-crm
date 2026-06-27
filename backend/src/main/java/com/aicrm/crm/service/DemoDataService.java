package com.aicrm.crm.service;

import com.aicrm.crm.config.CacheConfig;
import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.CloseReason;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Interaction;
import com.aicrm.crm.domain.InteractionType;
import com.aicrm.crm.domain.LeadSource;
import com.aicrm.crm.domain.Opportunity;
import com.aicrm.crm.domain.OpportunityStage;
import com.aicrm.crm.domain.OpportunityType;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.ChatMessageRepository;
import com.aicrm.crm.repository.ContactRepository;
import com.aicrm.crm.repository.CustomerRepository;
import com.aicrm.crm.repository.InteractionInsightRepository;
import com.aicrm.crm.repository.InteractionRepository;
import com.aicrm.crm.repository.OpportunityRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 示範資料生成器。
 *
 * <p>以固定 seed 大量灌入客戶 / 互動 / 商機，讓情緒意圖雷達（分布、趨勢、排序）有足夠樣本可視覺化。
 * 互動內容取自「每個 Intent 一組中文範本」，並刻意包含 Task A deterministic 分類器認得的關鍵字
 * （客訴 / 續約 / 競品 / 報價 / 加購 / 取消…），使情緒與意圖分布多元。</p>
 *
 * <p>效能與正確性紀律：</p>
 * <ul>
 *   <li>直接以 repository / entity 批次建資料，不逐筆走 {@code CustomerService.addInteraction}，
 *       避免每筆觸發 LLM 分析又慢。</li>
 *   <li>生成後統一呼叫 {@code sentimentIntentService.analyzeMissing(false)} 做 deterministic 批次分析。</li>
 *   <li>固定 seed 確保可重現；生成為「附加」，不清除既有資料。</li>
 * </ul>
 */
@Service
@Transactional
public class DemoDataService {

    /** 記錄生成統計等事件。 */
    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);

    /** 固定亂數種子，確保每次生成結果可重現。 */
    private static final long SEED = 20260619L;

    /** 客戶名稱前綴常數池（產業 / 公司型態用語）。 */
    private static final String[] COMPANY_PREFIXES = {
            "宏達", "台積", "聯發", "鴻海", "中華", "遠東", "統一", "富邦", "國泰", "台塑",
            "華碩", "廣達", "緯創", "仁寶", "光寶", "研華", "上銀", "大立", "和泰", "裕隆"
    };

    /** 客戶名稱後綴常數池（公司型態）。 */
    private static final String[] COMPANY_SUFFIXES = {
            "科技", "資訊", "電子", "生技", "製造", "貿易", "實業", "工業", "系統", "雲端"
    };

    /** 產業常數池。 */
    private static final String[] INDUSTRIES = {
            "半導體", "金融", "醫療", "零售", "製造", "電信", "教育", "物流", "餐飲", "電商"
    };

    /**
     * 負責業務常數池（13 位）。含預設登入測試帳號「業務代表」(sales@aurora.local)，
     * 使其名下也有示範客戶、能出現在業務分析；其餘為擬真姓名讓績效分布分散。
     */
    private static final String[] OWNERS = {
            "業務代表",
            "王小明", "李美華", "張志豪", "陳怡君", "林家豪", "黃淑芬",
            "吳俊賢", "劉雅婷", "蔡承翰", "鄭雅文", "許志明", "周庭瑋"
    };

    /**
     * 商機階段的「漏斗權重」：越前段越多，使快照計數天然呈現上寬下窄的漏斗。
     * 順序對應 {@link OpportunityStage}：資格評估 / 提案 / 議價 / 已成交 / 已流失。
     */
    private static final int[] STAGE_WEIGHTS = {35, 27, 18, 13, 7};

    /**
     * 每個 Intent 一組中文互動範本。
     * 範本內含 deterministic 分類器（Task A）認得的關鍵字，讓情緒 / 意圖分布多元。
     */
    private static final String[] TPL_COMPLAINT = {
            "客戶來電客訴系統當機影響營運，情緒激動要求補償。",
            "客戶對近期服務品質非常不滿，已正式提出投訴。",
            "客戶抱怨交付延遲，要求退費並追究責任。",
            "客戶投訴客服回應太慢，揚言公開抱怨。"
    };
    private static final String[] TPL_CHURN = {
            "客戶表示正在評估取消合約，傾向不續約。",
            "客戶提到可能解約並轉單給其他廠商。",
            "客戶暗示有流失風險，考慮換供應商。",
            "客戶明確表達合約到期後不續，準備轉單。"
    };
    private static final String[] TPL_COMPARE = {
            "客戶拿我們與競品比較，質疑我們的優勢。",
            "客戶說別家報的條件更好，正在比較他牌方案。",
            "客戶提到對手提供更多功能，要求我們說明差異。",
            "客戶正評估競品，希望我們提出對應說明。"
    };
    private static final String[] TPL_PRICING = {
            "客戶詢問最新報價與折扣方案，想了解費用。",
            "客戶希望提供正式報價單，確認價格與付款條件。",
            "客戶詢問大量採購的價錢與優惠空間。",
            "客戶想知道升級方案的費用差異與報價。"
    };
    private static final String[] TPL_RENEWAL = {
            "客戶主動表達續約意願，希望延長合約一年。",
            "客戶滿意目前服務，確認將續訂下年度方案。",
            "客戶詢問續簽流程，準備辦理續約手續。",
            "客戶表示合約到期會續約，並考慮加長期程。"
    };
    private static final String[] TPL_UPSELL = {
            "客戶有意加購進階模組，詢問升級流程。",
            "客戶希望擴充使用席次，評估加值服務。",
            "客戶想增購額外授權，了解升級方案內容。",
            "客戶詢問加購進階分析功能的可行性。"
    };
    private static final String[] TPL_OTHER = {
            "客戶來電確認近期會議時間與議程。",
            "業務拜訪客戶進行例行關係維護。",
            "客戶回報使用狀況良好，無特別需求。",
            "與客戶寒暄並更新聯絡窗口資訊。"
    };

    /** 所有意圖範本集合（隨機挑選一組再挑一句，使分布有變化）。 */
    private static final String[][] TEMPLATE_GROUPS = {
            TPL_COMPLAINT, TPL_CHURN, TPL_COMPARE, TPL_PRICING, TPL_RENEWAL, TPL_UPSELL, TPL_OTHER
    };

    /** 互動類型全集（隨機取用）。 */
    private static final InteractionType[] INTERACTION_TYPES = InteractionType.values();

    /** 商機階段全集（隨機取用）。 */
    private static final OpportunityStage[] OPPORTUNITY_STAGES = OpportunityStage.values();

    /** 商機類型全集（隨機取用）。 */
    private static final OpportunityType[] OPPORTUNITY_TYPES = OpportunityType.values();

    /** 客戶資料存取。 */
    private final CustomerRepository customerRepository;

    /** 商機資料存取。 */
    private final OpportunityRepository opportunityRepository;

    /** 互動資料存取：清除重建時刪除既有互動。 */
    private final InteractionRepository interactionRepository;

    /** 互動分析（衍生資料）存取：清除重建時先刪除，避免 FK 殘留。 */
    private final InteractionInsightRepository interactionInsightRepository;

    /** 聯絡人資料存取：清除重建時刪除既有客戶聯絡人（FK 參照 customers）。 */
    private final ContactRepository contactRepository;

    /** 對話記憶存取：清除重建時刪除綁定既有客戶的對話（FK 參照 customers，not null）。 */
    private final ChatMessageRepository chatMessageRepository;

    /** 情緒意圖分類服務：生成後做 deterministic 批次分析。 */
    private final SentimentIntentService sentimentIntentService;

    /** 使用者存取：生成示範資料時確保業務帳號存在並指派。 */
    private final AppUserRepository userRepository;

    /** 密碼雜湊：自動建立業務帳號的預設密碼。 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 清除重建開關：預設 false（安全側）。正式環境保持 false 以防誤刪真實業務資料；
     * 開發 / 示範環境以 {@code DEMO_RESET_ENABLED=true} 啟用。
     */
    private final boolean resetEnabled;

    public DemoDataService(CustomerRepository customerRepository,
                           OpportunityRepository opportunityRepository,
                           InteractionRepository interactionRepository,
                           InteractionInsightRepository interactionInsightRepository,
                           ContactRepository contactRepository,
                           ChatMessageRepository chatMessageRepository,
                           SentimentIntentService sentimentIntentService,
                           AppUserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @org.springframework.beans.factory.annotation.Value("${app.demo.reset-enabled:false}") boolean resetEnabled) {
        this.customerRepository = customerRepository;
        this.opportunityRepository = opportunityRepository;
        this.interactionRepository = interactionRepository;
        this.interactionInsightRepository = interactionInsightRepository;
        this.contactRepository = contactRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.sentimentIntentService = sentimentIntentService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.resetEnabled = resetEnabled;
    }

    /**
     * 確保示範業務名單（OWNERS）皆有對應的 SALES 登入帳號，回傳這些帳號供指派。
     * 以 displayName 比對：缺少者建立（預設密碼 password123），已存在者沿用。
     *
     * @return 示範業務帳號清單
     */
    private List<AppUser> ensureOwnerAccounts() {
        Map<String, AppUser> byDisplayName = new HashMap<>();
        for (AppUser u : userRepository.findAll()) {
            byDisplayName.putIfAbsent(u.getDisplayName(), u);
        }
        var result = new ArrayList<AppUser>(OWNERS.length);
        for (String ownerName : OWNERS) {
            var account = byDisplayName.get(ownerName);
            if (account == null) {
                var username = ownerName + "@aurora.local";
                int suffix = 1;
                while (userRepository.existsByUsername(username)) {
                    username = ownerName + (suffix++) + "@aurora.local";
                }
                account = userRepository.save(new AppUser(username, passwordEncoder.encode("password123"), ownerName, Role.SALES));
                byDisplayName.put(ownerName, account);
            }
            result.add(account);
        }
        return result;
    }

    /**
     * 生成統計結果。
     *
     * @param customers 本次新增客戶數
     * @param interactions 本次新增互動數
     * @param insights 本次新增（補算）的分析筆數
     */
    public record DemoStats(int customers, int interactions, int insights) {}

    /**
     * 生成示範資料（附加，不清既有資料）。
     *
     * <p>每客戶 5–25 則互動：type 隨機、{@code occurredAt} = now 減隨機 0–365 天、content 隨機挑 intent
     * 範本再挑一句；部分客戶建 1–3 商機。先批次存客戶（含互動，靠 cascade）與商機，
     * 再呼叫 {@code analyzeMissing(false)} 做 deterministic 批次情緒意圖分析。</p>
     *
     * @param customers 欲生成的客戶數
     * @return 生成統計（客戶數 / 互動數 / 分析筆數）
     */
    @Caching(evict = {
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_SUMMARY),
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_REPORTS),
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_RFM),
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_SENTIMENT)
    })
    public DemoStats generate(int customers) {
        return generate(customers, false);
    }

    /**
     * 生成示範資料，可選擇先清除既有業務資料再重建。
     *
     * <p>{@code reset=true}（清除重建）僅在 {@code app.demo.reset-enabled=true} 時允許；
     * 清除範圍限「業務資料」：互動分析 → 互動 → 商機 → 客戶（依 FK 順序），
     * 不動帳號（app_users）、系統設定、AI 呼叫歷程與對話記憶。</p>
     *
     * @param customers 欲生成的客戶數
     * @param reset 是否先清除既有業務資料再重建
     * @return 生成統計（客戶數 / 互動數 / 分析筆數）
     * @throws IllegalStateException reset=true 但未啟用 {@code app.demo.reset-enabled}
     */
    @Caching(evict = {
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_SUMMARY),
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_REPORTS),
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_RFM),
        @CacheEvict(CacheConfig.CACHE_DASHBOARD_SENTIMENT)
    })
    public DemoStats generate(int customers, boolean reset) {
        if (reset) {
            if (!resetEnabled) {
                throw new IllegalStateException(
                        "示範資料清除重建未啟用：請於開發 / 示範環境設定 DEMO_RESET_ENABLED=true，"
                        + "正式環境請維持關閉以保護真實業務資料。");
            }
            clearBusinessData();
        }
        return doGenerate(customers);
    }

    /**
     * 依 FK 順序清除「業務資料」：互動分析 → 互動 → 商機 → 客戶。
     * 帳號、系統設定、AI 歷程、對話記憶不在清除範圍。
     */
    private void clearBusinessData() {
        interactionInsightRepository.deleteAllInBatch();
        interactionRepository.deleteAllInBatch();
        contactRepository.deleteAllInBatch();
        opportunityRepository.deleteAllInBatch();
        chatMessageRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        log.info("示範資料清除完成（互動分析 / 互動 / 聯絡人 / 商機 / 對話記憶 / 客戶）；帳號、設定與 AI 稽核歷程保留。");
    }

    /**
     * 實際生成邏輯（不含清除）。
     *
     * @param customers 欲生成的客戶數
     * @return 生成統計
     */
    private DemoStats doGenerate(int customers) {
        var random = new Random(SEED);
        // 確保示範業務皆有對應 SALES 帳號，客戶以正規關聯指派給帳號
        var owners = ensureOwnerAccounts();
        var newCustomers = new ArrayList<Customer>(Math.max(customers, 0));
        var newOpportunities = new ArrayList<Opportunity>();
        int interactionCount = 0;

        for (int i = 0; i < customers; i++) {
            var customer = buildCustomer(random, i, owners);

            // 每客戶 5–25 則互動
            int interactionsForCustomer = 5 + random.nextInt(21);
            for (int j = 0; j < interactionsForCustomer; j++) {
                customer.addInteraction(buildInteraction(random));
                interactionCount++;
            }
            newCustomers.add(customer);

            // 部分客戶（約 40%）建 1–3 商機
            if (random.nextInt(10) < 4) {
                int opportunityCount = 1 + random.nextInt(3);
                for (int k = 0; k < opportunityCount; k++) {
                    newOpportunities.add(buildOpportunity(random, customer));
                }
            }
        }

        // 批次存客戶（互動經 cascade 一併寫入），再存商機
        customerRepository.saveAll(newCustomers);
        opportunityRepository.saveAll(newOpportunities);
        customerRepository.flush();

        // 生成後統一做 deterministic 批次分析（不逐筆觸發 LLM）
        int insights = sentimentIntentService.analyzeMissing(false);

        log.info("示範資料生成完成：customers={}, interactions={}, insights={}",
                newCustomers.size(), interactionCount, insights);
        return new DemoStats(newCustomers.size(), interactionCount, insights);
    }

    /**
     * 以常數池組合一筆客戶基本資料。
     *
     * @param random 固定種子亂數源
     * @param index 序號（用於 email / 統編去重）
     * @return 客戶實體
     */
    private Customer buildCustomer(Random random, int index, List<AppUser> owners) {
        var name = pick(random, COMPANY_PREFIXES) + pick(random, COMPANY_SUFFIXES);
        // email / 統編帶序號避免重複；phone 為合法台灣手機格式
        var email = "demo" + index + "@example.com";
        var phone = "09" + String.format("%08d", random.nextInt(100_000_000));
        var taxId = String.format("%08d", 10_000_000 + index);
        var industry = pick(random, INDUSTRIES);
        // 隨機指派一個業務帳號（正規關聯；assignOwner 同步 owner_name）
        var owner = owners.get(random.nextInt(owners.size()));
        var customer = new Customer(name, email, phone, taxId, industry, owner.getDisplayName());
        customer.assignOwner(owner);

        // 合約日期：起始日為過去 0–2 年內，到期日為起始日後一年，續約日為到期日前一個月
        var start = LocalDate.now().minusDays(random.nextInt(730));
        var end = start.plusYears(1);
        customer.updateContractDates(start, end, end.minusMonths(1));
        return customer;
    }

    /**
     * 隨機產生一筆互動：類型隨機、發生時間為近一年內、內容取自隨機 intent 範本。
     *
     * @param random 固定種子亂數源
     * @return 互動實體
     */
    private Interaction buildInteraction(Random random) {
        var type = pick(random, INTERACTION_TYPES);
        var occurredAt = LocalDateTime.now().minusDays(random.nextInt(366));
        var group = TEMPLATE_GROUPS[random.nextInt(TEMPLATE_GROUPS.length)];
        var content = group[random.nextInt(group.length)];
        return new Interaction(type, occurredAt, content);
    }

    /**
     * 隨機產生一筆商機：金額 / 階段 / 類型隨機，預計成交日為未來半年內。
     *
     * @param random 固定種子亂數源
     * @param customer 所屬客戶
     * @return 商機實體
     */
    private Opportunity buildOpportunity(Random random, Customer customer) {
        var stage = pickStage(random);
        var type = pick(random, OPPORTUNITY_TYPES);
        // 金額 10 萬 – 510 萬，整數萬元
        var amount = BigDecimal.valueOf((10 + random.nextInt(500)) * 10_000L);
        var expectedClose = LocalDate.now().plusDays(random.nextInt(180));
        var name = customer.getName() + (type == OpportunityType.RENEWAL ? "續約案" : "新案");
        // 來源隨機；機率依階段預設（與 V18 回填一致）
        var leadSource = pick(random, LeadSource.values());
        int probability = switch (stage) {
            case QUALIFICATION -> 20;
            case PROPOSAL -> 50;
            case NEGOTIATION -> 75;
            case CLOSED_WON -> 100;
            case CLOSED_LOST -> 0;
        };
        var opp = new Opportunity(customer, name, stage, amount, expectedClose, type, leadSource, probability);
        opp.assignOwner(customer.getOwner());
        // 結案案子帶輸贏原因與實際成交日（示範資料）
        if (stage == OpportunityStage.CLOSED_WON) {
            opp.closeWith(stage, CloseReason.WON_PRICE, "示範：價格優勢", expectedClose);
        } else if (stage == OpportunityStage.CLOSED_LOST) {
            opp.closeWith(stage, CloseReason.LOST_COMPETITOR, "示範：輸給競品", expectedClose);
        }
        return opp;
    }

    /**
     * 從陣列隨機取一個元素。
     *
     * @param random 固定種子亂數源
     * @param pool 候選陣列
     * @param <T> 元素型別
     * @return 隨機元素
     */
    private <T> T pick(Random random, T[] pool) {
        return pool[random.nextInt(pool.length)];
    }

    /**
     * 依 {@link #STAGE_WEIGHTS} 加權挑選商機階段：越前段權重越高，
     * 使大量生成後的階段快照天然呈現上寬下窄的漏斗。
     *
     * @param random 固定種子亂數源
     * @return 加權後的商機階段
     */
    private OpportunityStage pickStage(Random random) {
        int totalWeight = 0;
        for (int w : STAGE_WEIGHTS) {
            totalWeight += w;
        }
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (int i = 0; i < OPPORTUNITY_STAGES.length; i++) {
            cumulative += STAGE_WEIGHTS[i];
            if (roll < cumulative) {
                return OPPORTUNITY_STAGES[i];
            }
        }
        // 理論上不會到這（roll < totalWeight），保險回最後一個階段
        return OPPORTUNITY_STAGES[OPPORTUNITY_STAGES.length - 1];
    }
}
