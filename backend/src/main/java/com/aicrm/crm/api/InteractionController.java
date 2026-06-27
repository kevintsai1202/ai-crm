package com.aicrm.crm.api;

import com.aicrm.crm.repository.InteractionInsightRepository;
import com.aicrm.crm.repository.InteractionRepository;
import com.aicrm.crm.service.SentimentIntentService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 互動 REST API，提供編輯（含重新情緒意圖分析）與刪除。
 */
@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    /** 記錄重新分析失敗等事件。 */
    private static final Logger log = LoggerFactory.getLogger(InteractionController.class);

    /** 互動資料存取介面。 */
    private final InteractionRepository interactionRepository;

    /** 互動情緒意圖分析結果存取：刪除前先清掉對應分析列（外鍵無 ON DELETE CASCADE）。 */
    private final InteractionInsightRepository interactionInsightRepository;

    /** 情緒意圖分類服務：編輯互動後重新分析落庫。 */
    private final SentimentIntentService sentimentIntentService;

    /** 擁有權守衛：強制 SALES 僅能編輯 / 刪除自己負責客戶的互動紀錄。 */
    private final com.aicrm.crm.security.OwnershipGuard ownershipGuard;

    public InteractionController(InteractionRepository interactionRepository,
                                 InteractionInsightRepository interactionInsightRepository,
                                 SentimentIntentService sentimentIntentService,
                                 com.aicrm.crm.security.OwnershipGuard ownershipGuard) {
        this.interactionRepository = interactionRepository;
        this.interactionInsightRepository = interactionInsightRepository;
        this.sentimentIntentService = sentimentIntentService;
        this.ownershipGuard = ownershipGuard;
    }

    /**
     * 編輯互動並重新分析情緒意圖。
     *
     * @param id 互動 ID
     * @param request 編輯請求
     * @return 更新後的互動 DTO（含更新後的情緒 / 意圖）
     */
    @PutMapping("/{id}")
    @Transactional
    public Dtos.InteractionResponse update(@PathVariable Long id, @Valid @RequestBody Dtos.UpdateInteractionRequest request) {
        var interaction = interactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("查無此互動：" + id));
        ownershipGuard.assertCanAccessOwner(interaction.getCustomer().getOwnerName());
        interaction.updateInfo(request.type(), request.occurredAt(), request.content());
        interactionRepository.saveAndFlush(interaction);
        // 內容已變更，重新分析情緒意圖（deterministic，與編輯流程一致）。
        // 單筆分析失敗不可讓編輯整個失敗：僅記錄日誌。
        try {
            sentimentIntentService.analyzeAndSave(interaction.getId(), interaction.getCustomer().getId(), interaction.getContent(), false);
        } catch (Exception e) {
            log.warn("編輯互動的情緒意圖重新分析失敗，interactionId={}：{}", interaction.getId(), e.getMessage());
        }
        // 取回剛分析的情緒 / 意圖（單筆分析失敗時為 null）。
        var insight = interactionInsightRepository.findByInteractionId(interaction.getId()).orElse(null);
        var sentiment = insight == null ? null : insight.getSentiment().name();
        var intent = insight == null ? null : insight.getIntent().name();
        return new Dtos.InteractionResponse(interaction.getId(), interaction.getType(), interaction.getOccurredAt(), interaction.getContent(), sentiment, intent);
    }

    /**
     * 刪除互動。
     *
     * <p>因 interaction_insights.interaction_id 外鍵無 ON DELETE CASCADE，須先刪掉對應分析列再刪互動。</p>
     *
     * @param id 互動 ID
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        // 先載入並驗證擁有權，避免 SALES 刪除他人客戶的互動紀錄
        var interaction = interactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("查無此互動：" + id));
        ownershipGuard.assertCanAccessOwner(interaction.getCustomer().getOwnerName());
        interactionInsightRepository.deleteByInteractionId(id);
        interactionRepository.delete(interaction);
    }
}
