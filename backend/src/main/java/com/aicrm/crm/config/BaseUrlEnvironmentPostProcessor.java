package com.aicrm.crm.config;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 正規化空白的 AI base-url，避免 Zeabur 等平台「建了 BASE_URL 卻留空」時
 * Spring 綁定成空字串、蓋掉 YAML 預設，導致 SDK 打錯 host。
 *
 * <p>僅處理「未設定／空字串／純空白」；有值但不含 /v1 時不自動改寫（避免誤傷自架路徑）。</p>
 */
@Order(Ordered.LOWEST_PRECEDENCE)
public class BaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    /** 與 application.yml 中 spring.ai.openai.base-url 預設一致。 */
    public static final String DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1";

    /** 環境變數名（使用者常設）。 */
    public static final String ENV_BASE_URL = "BASE_URL";

    /** Spring AI 正式屬性名。 */
    public static final String PROP_SPRING_AI_BASE_URL = "spring.ai.openai.base-url";

    /**
     * 若 BASE_URL 或 spring.ai.openai.base-url 為空白，覆寫為 OpenAI 預設（含 /v1）。
     *
     * @param environment 可配置環境
     * @param application 啟動應用（未使用）
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> overrides = new HashMap<>();

        String envBase = environment.getProperty(ENV_BASE_URL);
        if (isBlank(envBase)) {
            overrides.put(ENV_BASE_URL, DEFAULT_OPENAI_BASE_URL);
        }

        // 讀取時若 BASE_URL 已覆寫，spring 屬性可能仍是佔位解析後的空字串，一併正規化
        String springBase = environment.getProperty(PROP_SPRING_AI_BASE_URL);
        if (isBlank(springBase)) {
            overrides.put(PROP_SPRING_AI_BASE_URL, DEFAULT_OPENAI_BASE_URL);
        } else if (overrides.containsKey(ENV_BASE_URL) && isBlank(springBase)) {
            overrides.put(PROP_SPRING_AI_BASE_URL, DEFAULT_OPENAI_BASE_URL);
        }

        // 若僅 ENV 被設成空白、spring 屬性尚未解析，強制兩個鍵都有合法值
        if (overrides.containsKey(ENV_BASE_URL) && !overrides.containsKey(PROP_SPRING_AI_BASE_URL)) {
            String currentSpring = environment.getProperty(PROP_SPRING_AI_BASE_URL);
            if (isBlank(currentSpring)) {
                overrides.put(PROP_SPRING_AI_BASE_URL, DEFAULT_OPENAI_BASE_URL);
            }
        }

        if (!overrides.isEmpty()) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("baseUrlNormalization", overrides));
        }
    }

    /**
     * 判斷字串是否為 null、空或僅空白。
     *
     * @param value 待測字串
     * @return true 表示應視為未設定
     */
    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
