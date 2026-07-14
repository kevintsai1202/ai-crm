package com.aicrm.crm.service.vision;

import com.aicrm.crm.api.Dtos.RecognizedBusinessCard;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Base64;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** OpenAI-compatible Vision adapter，使用 Provider base URL 與憑證。 */
@Component
public class OpenAiBusinessCardRecognitionClient implements BusinessCardRecognitionClient {
    /** 有界 timeout 的 HTTP client。 */
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    /** Jackson 3 parser。 */
    private final ObjectMapper mapper;

    /** 建立 Vision adapter。 */
    public OpenAiBusinessCardRecognitionClient(ObjectMapper mapper) { this.mapper = mapper; }

    /** 以 data URL 傳送圖片並要求模型只輸出 JSON。 */
    @Override public RecognizedBusinessCard recognize(byte[] image, String mimeType, AiModelAssignment assignment) {
        try {
            String endpoint = normalizeBase(assignment.baseUrl()) + "/v1/chat/completions";
            String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);
            var payload = mapper.createObjectNode(); payload.put("model", assignment.model()); payload.put("temperature", 0);
            var messages = payload.putArray("messages"); var message = messages.addObject(); message.put("role", "user");
            var content = message.putArray("content"); content.addObject().put("type", "text").put("text",
                    "Read this business card. Return JSON only with personName,title,email,phone,companyName,website,confidence,warnings.");
            content.addObject().put("type", "image_url").putObject("image_url").put("url", dataUrl);
            payload.putObject("response_format").put("type", "json_object");
            var request = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + assignment.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new VisionServiceException("Vision provider HTTP 錯誤");
            var root = mapper.readTree(response.body());
            String json = root.path("choices").path(0).path("message").path("content").asText();
            if (json == null || json.isBlank()) throw new VisionServiceException("Vision provider 回應缺少內容");
            return mapper.readValue(json, RecognizedBusinessCard.class);
        } catch (VisionServiceException exception) { throw exception; }
        catch (Exception exception) { throw new VisionServiceException("Vision provider 回應無法處理", exception); }
    }

    /** 正規化 Provider base URL，避免重複斜線。 */
    private String normalizeBase(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? "https://api.openai.com" : baseUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value.endsWith("/v1") ? value.substring(0, value.length() - 3) : value;
    }
}
