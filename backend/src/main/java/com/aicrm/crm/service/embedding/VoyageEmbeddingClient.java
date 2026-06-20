package com.aicrm.crm.service.embedding;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Voyage 向量嵌入用戶端：有 VOYAGE_API_KEY 時呼叫 Voyage REST；無金鑰或失敗時走 deterministic fallback。
 */
@Component
@Primary
public class VoyageEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(VoyageEmbeddingClient.class);

    private final String apiKey;
    private final String model;
    private final int dimension;
    private final boolean enabled;
    private final RestClient restClient;

    public VoyageEmbeddingClient(
            @Value("${app.voyage.api-key:}") String apiKey,
            @Value("${app.voyage.model:voyage-4-lite}") String model,
            @Value("${app.voyage.url:https://api.voyageai.com/v1/embeddings}") String url,
            @Value("${app.voyage.dimension:1024}") int dimension) {
        this.apiKey = apiKey;
        this.model = model;
        this.dimension = dimension;
        this.enabled = apiKey != null && !apiKey.isBlank();
        this.restClient = RestClient.builder().baseUrl(url).build();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public List<float[]> embed(List<String> texts, InputType type) {
        if (!enabled) {
            return fallback(texts);
        }
        try {
            var body = Map.of(
                    "input", texts,
                    "model", model,
                    "input_type", type == InputType.QUERY ? "query" : "document");
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            var result = parse(response);
            // 維度防呆：任一向量長度不符即 fallback
            if (result.stream().anyMatch(v -> v.length != dimension)) {
                log.warn("Voyage 回傳維度與設定不符（預期 {}），改用 fallback", dimension);
                return fallback(texts);
            }
            return result;
        } catch (Exception e) {
            log.warn("Voyage 嵌入失敗，改用 deterministic fallback：{}", e.getMessage());
            return fallback(texts);
        }
    }

    /** 解析 Voyage 回應 {data:[{embedding:[...]}]} 為 float[] 清單。 */
    @SuppressWarnings("unchecked")
    private List<float[]> parse(Map<String, Object> response) {
        var data = (List<Map<String, Object>>) response.get("data");
        return data.stream().map(item -> {
            var emb = (List<Number>) item.get("embedding");
            var arr = new float[emb.size()];
            for (int i = 0; i < emb.size(); i++) arr[i] = emb.get(i).floatValue();
            return arr;
        }).toList();
    }

    /** 無金鑰 / 失敗時的確定性後備。 */
    private List<float[]> fallback(List<String> texts) {
        return texts.stream().map(t -> DeterministicEmbedding.embed(t, dimension)).toList();
    }
}
