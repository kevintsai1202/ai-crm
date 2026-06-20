package com.aicrm.crm.bootstrap;

import com.aicrm.crm.service.SentimentIntentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 啟動時補算所有尚無情緒意圖分析（insight）的互動。
 *
 * <p>問題背景：Flyway 種子（V2 / V4）以純 SQL 直接寫入 {@code interactions}，繞過 Java 分析層，
 * 因此這些互動沒有對應的 {@code interaction_insights}。dashboard 的「意圖分布／情緒趨勢／高風險互動／
 * 流失雷達／優先關懷」5 張卡片全部統計 insights，故在僅靠種子灌資料的 demo 環境會全部無資料，
 * 而「近期關鍵互動」直接讀 {@code interactions} 所以正常。</p>
 *
 * <p>本 runner 在每次啟動時以 deterministic 分類器補算缺漏者，讓 demo 環境部署後卡片即有資料。
 * 冪等：{@link SentimentIntentService#analyzeMissing(boolean)} 只撈尚無 insight 的互動，
 * 對種子、真人新增、示範生成的互動皆適用；重複啟動不會重算或覆蓋既有分析。
 * 排在基礎帳號 seed（@Order(1)）與業務帳號正規化（@Order(2)）之後執行。</p>
 */
@Component
@Order(3)
public class InteractionInsightBackfillRunner implements ApplicationRunner {

    /** 記錄補算事件。 */
    private static final Logger log = LoggerFactory.getLogger(InteractionInsightBackfillRunner.class);

    /** 情緒意圖分類服務：提供冪等的缺漏補算。 */
    private final SentimentIntentService sentimentIntentService;

    public InteractionInsightBackfillRunner(SentimentIntentService sentimentIntentService) {
        this.sentimentIntentService = sentimentIntentService;
    }

    /**
     * 啟動補算缺漏的互動分析（批次走 deterministic，不逐筆觸發 LLM）。
     *
     * @param args 應用程式啟動參數
     */
    @Override
    public void run(ApplicationArguments args) {
        int analyzed = sentimentIntentService.analyzeMissing(false);
        if (analyzed > 0) {
            log.info("啟動補算情緒意圖分析完成：{} 筆", analyzed);
        }
    }
}
