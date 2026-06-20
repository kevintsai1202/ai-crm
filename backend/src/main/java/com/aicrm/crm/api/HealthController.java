package com.aicrm.crm.api;

import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康檢查 API，供前端呼吸燈判斷後端連線狀態。
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    /** 是否設定 OpenAI api-key，作為 AI 模式判斷依據。 */
    private final boolean aiEnabled;

    public HealthController(@Value("${spring.ai.openai.api-key:}") String openAiApiKey) {
        this.aiEnabled = openAiApiKey != null && !openAiApiKey.isBlank();
    }

    /**
     * 回傳目前服務狀態與啟用功能；AI 模式依是否設定 OpenAI api-key 動態回報。
     *
     * @return 健康檢查 DTO
     */
    @GetMapping
    public Dtos.HealthResponse health() {
        // 設定了 api-key 才走真實 LLM，否則為 deterministic 教學流程
        var aiMode = aiEnabled ? "Spring AI + OpenAI" : "Deterministic Teaching Flow";
        return new Dtos.HealthResponse("UP", Instant.now(), Map.of(
                "database", "JPA",
                "security", "JWT",
                "ai", aiMode
        ));
    }
}
