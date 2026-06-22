package com.aicrm.crm.service;

import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 串流輔助工具：集中管理 content/tail 的 SseEmitter 送出邏輯。
 * 函式級註解：InsightService 與 ManagerInsightService 共用此工具，
 * 避免兩個 Service 互相注入而造成循環依賴。設計為靜態方法，無需注入。
 */
public final class SseHelper {

    private SseHelper() {}

    /**
     * 推送一段內容 delta。
     *
     * @param emitter SSE 發送器
     * @param delta 內容片段
     */
    public static void sendContent(SseEmitter emitter, String delta) {
        try {
            emitter.send(SseEmitter.event().data(Map.of("type", "content", "delta", delta)));
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    /**
     * 串流尾段（無 citations/risk 版本）：只送 callId 與 [DONE]。
     * 用於 Portfolio / Team / Owner 等無 RAG 引用的串流。
     *
     * @param emitter SSE 發送器
     * @param callId AI 呼叫紀錄 id（可為 null）
     */
    public static void sendSimpleTailAndComplete(SseEmitter emitter, Long callId) {
        try {
            if (callId != null) {
                emitter.send(SseEmitter.event().data(Map.of("type", "callId", "callId", callId)));
            }
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }
}
