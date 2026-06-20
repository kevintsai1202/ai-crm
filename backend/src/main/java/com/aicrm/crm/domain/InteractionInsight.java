package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 互動情緒意圖分析結果。
 * 函式級註解：採簡化稽核欄位，自行管理 analyzed_at（與 V8 migration 一致），不繼承 AuditableEntity。
 * 每則互動最多對應一筆（interaction_id unique），重新分析時更新既有筆。
 */
@Entity
@Table(name = "interaction_insights")
public class InteractionInsight {

    /** 主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 關聯互動 ID（唯一）。 */
    @Column(name = "interaction_id", nullable = false, unique = true)
    private Long interactionId;

    /** 冗餘客戶 ID，供聚合查詢避免 join interactions。 */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** 情緒分類。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Sentiment sentiment;

    /** 情緒分數（-100..100，負值越大越負面）。 */
    @Column(name = "sentiment_score", nullable = false)
    private int sentimentScore;

    /** 意圖分類。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Intent intent;

    /** 分析時間（自行管理）。 */
    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    protected InteractionInsight() {
    }

    /**
     * 建立互動情緒意圖分析結果。
     *
     * @param interactionId 互動 ID
     * @param customerId 客戶 ID
     * @param sentiment 情緒
     * @param sentimentScore 情緒分數
     * @param intent 意圖
     */
    public InteractionInsight(Long interactionId, Long customerId, Sentiment sentiment, int sentimentScore, Intent intent) {
        this.interactionId = interactionId;
        this.customerId = customerId;
        this.sentiment = sentiment;
        this.sentimentScore = sentimentScore;
        this.intent = intent;
        this.analyzedAt = Instant.now();
    }

    /**
     * 以新分類結果更新本筆（重新分析時使用），並更新分析時間。
     *
     * @param sentiment 情緒
     * @param sentimentScore 情緒分數
     * @param intent 意圖
     */
    public void update(Sentiment sentiment, int sentimentScore, Intent intent) {
        this.sentiment = sentiment;
        this.sentimentScore = sentimentScore;
        this.intent = intent;
        this.analyzedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getInteractionId() { return interactionId; }
    public Long getCustomerId() { return customerId; }
    public Sentiment getSentiment() { return sentiment; }
    public int getSentimentScore() { return sentimentScore; }
    public Intent getIntent() { return intent; }
    public Instant getAnalyzedAt() { return analyzedAt; }
}
