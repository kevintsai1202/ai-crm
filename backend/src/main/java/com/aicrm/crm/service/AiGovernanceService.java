package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiCallLog;
import com.aicrm.crm.domain.AiCallType;
import com.aicrm.crm.domain.AiFeedback;
import com.aicrm.crm.domain.FeedbackDecision;
import com.aicrm.crm.repository.AiCallLogRepository;
import com.aicrm.crm.repository.AiFeedbackRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 治理服務：記錄每次 LLM 呼叫的用量與答案、處理人工採納/拒絕回饋、彙總用量統計。
 */
@Service
@Transactional
public class AiGovernanceService {

    /** AI 呼叫紀錄資料存取。 */
    private final AiCallLogRepository callLogRepository;

    /** AI 回饋紀錄資料存取。 */
    private final AiFeedbackRepository feedbackRepository;

    public AiGovernanceService(AiCallLogRepository callLogRepository, AiFeedbackRepository feedbackRepository) {
        this.callLogRepository = callLogRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * 記錄一次 AI 呼叫（含 fallback）。
     *
     * @param type 呼叫類型
     * @param customerId 客戶 ID（Portfolio 可為 null）
     * @param model 模型名稱（fallback 為 null）
     * @param promptTokens 提示 token 數（取不到記 0）
     * @param completionTokens 完成 token 數（取不到記 0）
     * @param totalTokens 總 token 數（取不到記 0）
     * @param aiEnabled 是否真實呼叫 LLM
     * @param masked grounding context 是否已遮罩 PII
     * @param answer 回答內容
     * @return 已儲存的呼叫紀錄實體（含 id）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiCallLog record(AiCallType type, Long customerId, String model,
                            Integer promptTokens, Integer completionTokens, Integer totalTokens,
                            boolean aiEnabled, boolean masked, String answer) {
        var log = new AiCallLog(
                customerId, type, model,
                promptTokens == null ? 0 : promptTokens,
                completionTokens == null ? 0 : completionTokens,
                totalTokens == null ? 0 : totalTokens,
                aiEnabled, masked, answer);
        return callLogRepository.save(log);
    }

    /**
     * 記錄一次人工回饋（採納/拒絕）。
     *
     * @param callLogId 對應的 AI 呼叫紀錄 ID
     * @param decision 採納或拒絕
     * @param note 備註（可為 null）
     * @param user 回饋者帳號
     */
    public void feedback(Long callLogId, FeedbackDecision decision, String note, String user) {
        if (!callLogRepository.existsById(callLogId)) {
            throw new IllegalArgumentException("找不到對應的 AI 呼叫紀錄：" + callLogId);
        }
        feedbackRepository.save(new AiFeedback(callLogId, decision, note, user));
    }

    /**
     * 彙總 AI 用量統計：總呼叫數、總 token、真實/fallback 次數、採納/拒絕次數。
     *
     * @return 用量彙總回應
     */
    @Transactional(readOnly = true)
    public Dtos.UsageSummaryResponse usage() {
        return new Dtos.UsageSummaryResponse(
                callLogRepository.count(),
                callLogRepository.sumTotalTokens(),
                callLogRepository.countByAiEnabledTrue(),
                callLogRepository.countByAiEnabledFalse(),
                feedbackRepository.countByDecision(FeedbackDecision.ADOPTED),
                feedbackRepository.countByDecision(FeedbackDecision.REJECTED));
    }
}
