package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.OpportunityStage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RFM 客戶分群服務（教學版）。
 *
 * <p>對每位客戶計算 R/F/M 三項指標並各評 1–5 分，再依高低組合給出可解釋的分群標籤：
 * <ul>
 *   <li>R（Recency）：距今最後一次互動的天數，越小越好（分數反向）。</li>
 *   <li>F（Frequency）：互動總次數，越多越好。</li>
 *   <li>M（Monetary）：非 CLOSED_LOST 商機的金額合計，越高越好。</li>
 * </ul>
 * 評分採固定門檻（非分位數），資料量小時較穩定且可解釋，適合教學。
 */
@Service
@Transactional(readOnly = true)
public class RfmService {

    // ===== R（Recency）門檻：距今最後互動天數，越小分數越高（反向）=====
    /** ≤7 天 → 5 分。 */
    private static final long R_DAYS_SCORE_5 = 7;
    /** ≤30 天 → 4 分。 */
    private static final long R_DAYS_SCORE_4 = 30;
    /** ≤60 天 → 3 分。 */
    private static final long R_DAYS_SCORE_3 = 60;
    /** ≤90 天 → 2 分；其餘（含無互動）→ 1 分。 */
    private static final long R_DAYS_SCORE_2 = 90;
    /** 無互動時給的極大天數，確保 rScore 落在最低分。 */
    private static final long RECENCY_NONE = 9999;

    // ===== F（Frequency）門檻：互動次數，越多分數越高 =====
    /** ≥10 次 → 5 分。 */
    private static final long F_COUNT_SCORE_5 = 10;
    /** ≥6 次 → 4 分。 */
    private static final long F_COUNT_SCORE_4 = 6;
    /** ≥3 次 → 3 分。 */
    private static final long F_COUNT_SCORE_3 = 3;
    /** ≥1 次 → 2 分；0 次 → 1 分。 */
    private static final long F_COUNT_SCORE_2 = 1;

    // ===== M（Monetary）門檻：有效商機金額合計，越高分數越高 =====
    /** ≥1,000,000 → 5 分。 */
    private static final BigDecimal M_AMOUNT_SCORE_5 = new BigDecimal("1000000");
    /** ≥500,000 → 4 分。 */
    private static final BigDecimal M_AMOUNT_SCORE_4 = new BigDecimal("500000");
    /** ≥100,000 → 3 分。 */
    private static final BigDecimal M_AMOUNT_SCORE_3 = new BigDecimal("100000");
    /** ≥1（>0）→ 2 分；=0 → 1 分。 */
    private static final BigDecimal M_AMOUNT_SCORE_2 = new BigDecimal("1");

    /** 判斷「高分」的門檻：分數 ≥4 視為高。 */
    private static final int HIGH_SCORE = 4;

    /** 客戶服務，提供含互動與商機關聯的完整客戶清單。 */
    private final CustomerService customerService;

    public RfmService(CustomerService customerService) {
        this.customerService = customerService;
    }

    /**
     * 計算所有客戶的 RFM 指標、分數與分群標籤。
     *
     * @return 每位客戶的 RFM 結果清單
     */
    @Transactional(readOnly = true)
    public List<Dtos.RfmResponse> computeRfm() {
        var today = LocalDate.now();
        return customerService.findAllWithDetail().stream()
                .map(customer -> toRfm(customer, today))
                .toList();
    }

    /**
     * 將單一客戶轉為 RFM 結果。
     *
     * @param customer 含互動與商機關聯的客戶
     * @param today 計算 Recency 的基準日
     * @return 該客戶的 RFM 結果
     */
    private Dtos.RfmResponse toRfm(Customer customer, LocalDate today) {
        // Recency：距今最後一次互動的天數；無互動給極大值。
        var lastInteractionAt = customer.getInteractions().stream()
                .map(i -> i.getOccurredAt())
                .max(LocalDateTime::compareTo)
                .orElse(null);
        long recencyDays = lastInteractionAt == null
                ? RECENCY_NONE
                : ChronoUnit.DAYS.between(lastInteractionAt.toLocalDate(), today);

        // Frequency：互動總次數。
        long frequency = customer.getInteractions().size();

        // Monetary：非 CLOSED_LOST 商機金額合計。
        var monetary = customer.getOpportunities().stream()
                .filter(o -> o.getStage() != OpportunityStage.CLOSED_LOST)
                .map(o -> o.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int rScore = scoreRecency(recencyDays);
        int fScore = scoreFrequency(frequency);
        int mScore = scoreMonetary(monetary);
        String segment = decideSegment(rScore, fScore, mScore);

        return new Dtos.RfmResponse(customer.getId(), customer.getName(),
                recencyDays, frequency, monetary, rScore, fScore, mScore, segment);
    }

    /**
     * Recency 評分（反向：天數越小分數越高）。
     *
     * @param recencyDays 距今最後互動天數
     * @return 1–5 分
     */
    private int scoreRecency(long recencyDays) {
        if (recencyDays <= R_DAYS_SCORE_5) {
            return 5;
        }
        if (recencyDays <= R_DAYS_SCORE_4) {
            return 4;
        }
        if (recencyDays <= R_DAYS_SCORE_3) {
            return 3;
        }
        if (recencyDays <= R_DAYS_SCORE_2) {
            return 2;
        }
        return 1;
    }

    /**
     * Frequency 評分（次數越多分數越高）。
     *
     * @param frequency 互動次數
     * @return 1–5 分
     */
    private int scoreFrequency(long frequency) {
        if (frequency >= F_COUNT_SCORE_5) {
            return 5;
        }
        if (frequency >= F_COUNT_SCORE_4) {
            return 4;
        }
        if (frequency >= F_COUNT_SCORE_3) {
            return 3;
        }
        if (frequency >= F_COUNT_SCORE_2) {
            return 2;
        }
        return 1;
    }

    /**
     * Monetary 評分（金額越高分數越高）。
     *
     * @param monetary 有效商機金額合計
     * @return 1–5 分
     */
    private int scoreMonetary(BigDecimal monetary) {
        if (monetary.compareTo(M_AMOUNT_SCORE_5) >= 0) {
            return 5;
        }
        if (monetary.compareTo(M_AMOUNT_SCORE_4) >= 0) {
            return 4;
        }
        if (monetary.compareTo(M_AMOUNT_SCORE_3) >= 0) {
            return 3;
        }
        if (monetary.compareTo(M_AMOUNT_SCORE_2) >= 0) {
            return 2;
        }
        return 1;
    }

    /**
     * 依 R/F/M 分數高低組合判定分群標籤（規則可解釋）。
     *
     * <p>判定順序由強到弱：
     * <ol>
     *   <li>R、F、M 皆高 → 冠軍客戶（近期活躍、互動頻繁、貢獻高）。</li>
     *   <li>R 低（久未互動）但 M 高 → 瀕危流失（高價值卻失聯，需優先挽回）。</li>
     *   <li>F、M 皆高（不分 R）→ 忠誠客戶（長期高互動高貢獻）。</li>
     *   <li>R 高（近期活躍）但 M 尚未高 → 具潛力（新近活躍，待培養貢獻）。</li>
     *   <li>其餘 → 需關注。</li>
     * </ol>
     *
     * @param r Recency 分數
     * @param f Frequency 分數
     * @param m Monetary 分數
     * @return 分群中文標籤
     */
    private String decideSegment(int r, int f, int m) {
        boolean rHigh = r >= HIGH_SCORE;
        boolean fHigh = f >= HIGH_SCORE;
        boolean mHigh = m >= HIGH_SCORE;

        if (rHigh && fHigh && mHigh) {
            return "冠軍客戶";
        }
        if (!rHigh && mHigh) {
            return "瀕危流失";
        }
        if (fHigh && mHigh) {
            return "忠誠客戶";
        }
        if (rHigh) {
            return "具潛力";
        }
        return "需關注";
    }
}
