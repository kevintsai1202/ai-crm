package com.aicrm.crm.service.ai;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AiProvider;
import com.aicrm.crm.domain.CapabilitySource;
import com.aicrm.crm.domain.ModelCapability;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * OpenAI-compatible GET /v1/models client；只信任 Provider 明確回傳的 modality metadata。
 */
@Component
public class OpenAiCompatibleModelCatalogClient implements ModelCatalogClient {

    /** Spring 管理的 HTTP client builder；每次探索 clone 以避免跨 Provider 汙染 base URL。 */
    private final RestClient.Builder restClientBuilder;

    public OpenAiCompatibleModelCatalogClient(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    /** 呼叫 Provider 模型目錄，並將可靠 metadata 轉成 CRM 能力。 */
    @Override
    public List<Dtos.ModelOptionItem> discover(AiProvider provider) {
        var client = restClientBuilder.clone().baseUrl(modelsBaseUrl(provider.getBaseUrl())).build();
        var response = client.get()
                .uri("/models")
                .headers(headers -> applyAuthorization(headers, provider.getApiKey()))
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        return parseModels(response, provider.getId());
    }

    /** 將 Provider base URL 正規化為包含 /v1 的模型 API base。 */
    private String modelsBaseUrl(String configuredBaseUrl) {
        var baseUrl = StringUtils.hasText(configuredBaseUrl)
                ? configuredBaseUrl.strip()
                : "https://api.openai.com";
        baseUrl = baseUrl.replaceAll("/+$", "");
        return baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1";
    }

    /** 僅在 Provider 已設定 API key 時送出 Bearer header。 */
    private void applyAuthorization(HttpHeaders headers, String apiKey) {
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey.strip());
        }
    }

    /** 解析 OpenAI-compatible data array；無效項目會被忽略。 */
    private List<Dtos.ModelOptionItem> parseModels(Map<String, Object> response, Long providerId) {
        if (response == null || !(response.get("data") instanceof List<?> rawModels)) {
            return List.of();
        }
        var result = new ArrayList<Dtos.ModelOptionItem>();
        for (var rawModel : rawModels) {
            if (!(rawModel instanceof Map<?, ?> model) || !(model.get("id") instanceof String id)
                    || !StringUtils.hasText(id)) {
                continue;
            }
            var capabilities = extractCapabilities(model.get("input_modalities"));
            var source = capabilities == null ? CapabilitySource.UNKNOWN : CapabilitySource.AUTO;
            result.add(new Dtos.ModelOptionItem(id, providerId, capabilities, source));
        }
        return List.copyOf(result);
    }

    /** 將明確 input modalities 映射成受支援能力；不讀取或推測模型名稱。 */
    private Set<ModelCapability> extractCapabilities(Object rawModalities) {
        if (!(rawModalities instanceof List<?> modalities)) {
            return null;
        }
        var capabilities = new LinkedHashSet<ModelCapability>();
        for (var modality : modalities) {
            if (!(modality instanceof String value) || !StringUtils.hasText(value)) {
                return null;
            }
            switch (value.toLowerCase(Locale.ROOT)) {
                case "image" -> capabilities.add(ModelCapability.VISION);
                case "audio" -> capabilities.add(ModelCapability.AUDIO_TRANSCRIPTION);
                default -> {
                    // text 等未納入 CRM 特殊用途的 modality 不需保存。
                }
            }
        }
        return Set.copyOf(capabilities);
    }
}
