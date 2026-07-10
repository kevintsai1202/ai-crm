package com.aicrm.crm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * BASE_URL 空白正規化單元測試。
 */
class BaseUrlEnvironmentPostProcessorTest {

    private final BaseUrlEnvironmentPostProcessor processor = new BaseUrlEnvironmentPostProcessor();

    @Test
    void blankBaseUrlBecomesDefault() {
        var env = new StandardEnvironment();
        Map<String, Object> props = new HashMap<>();
        props.put("BASE_URL", "   ");
        props.put("spring.ai.openai.base-url", "");
        env.getPropertySources().addFirst(new MapPropertySource("test", props));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("BASE_URL"))
                .isEqualTo(BaseUrlEnvironmentPostProcessor.DEFAULT_OPENAI_BASE_URL);
        assertThat(env.getProperty("spring.ai.openai.base-url"))
                .isEqualTo(BaseUrlEnvironmentPostProcessor.DEFAULT_OPENAI_BASE_URL);
    }

    @Test
    void nonBlankBaseUrlUnchanged() {
        var env = new StandardEnvironment();
        Map<String, Object> props = new HashMap<>();
        props.put("BASE_URL", "https://hnd1.aihub.zeabur.ai/v1");
        props.put("spring.ai.openai.base-url", "https://hnd1.aihub.zeabur.ai/v1");
        env.getPropertySources().addFirst(new MapPropertySource("test", props));

        processor.postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("BASE_URL")).isEqualTo("https://hnd1.aihub.zeabur.ai/v1");
        assertThat(env.getProperty("spring.ai.openai.base-url"))
                .isEqualTo("https://hnd1.aihub.zeabur.ai/v1");
    }

    @Test
    void isBlankHelper() {
        assertThat(BaseUrlEnvironmentPostProcessor.isBlank(null)).isTrue();
        assertThat(BaseUrlEnvironmentPostProcessor.isBlank("")).isTrue();
        assertThat(BaseUrlEnvironmentPostProcessor.isBlank("  ")).isTrue();
        assertThat(BaseUrlEnvironmentPostProcessor.isBlank("x")).isFalse();
    }
}
