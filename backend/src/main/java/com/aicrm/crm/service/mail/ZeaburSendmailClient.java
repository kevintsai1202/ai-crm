package com.aicrm.crm.service.mail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 正式環境的 Zeabur Sendmail 郵件寄送 adapter。憑證（token）只從後端環境設定讀取，
 * 絕不回傳前端、寫入 audit 或出現在任何例外訊息中。以 JSON 呼叫設定的 endpoint，
 * 解析回應的 message id；任何失敗一律拋出 {@link MailDeliveryException}（訊息不含憑證）。
 */
@Component
public class ZeaburSendmailClient implements MailDeliveryClient {
    /** Jackson 3 parser。 */
    private final ObjectMapper mapper;
    /** Sendmail HTTP endpoint（例如 Zeabur Sendmail API）。 */
    private final String endpoint;
    /** Sendmail 授權 token（僅後端持有）。 */
    private final String token;
    /** 回應內容上限，避免異常大回應占用記憶體。 */
    private static final int MAX_CONTENT = 100_000;

    /** 由環境設定注入 endpoint 與 token。 */
    public ZeaburSendmailClient(ObjectMapper mapper,
            @Value("${app.mail.zeabur.endpoint:}") String endpoint,
            @Value("${app.mail.zeabur.token:}") String token) {
        this.mapper = mapper; this.endpoint = endpoint; this.token = token;
    }

    /** 以 JSON POST 寄送郵件並解析 message id；不在錯誤訊息中洩漏任何憑證。 */
    @Override
    public DeliveryResult send(ApprovedEmail email) {
        if (endpoint == null || endpoint.isBlank() || token == null || token.isBlank()) {
            throw new MailDeliveryException("尚未設定寄信服務 endpoint 或憑證");
        }
        try {
            URI uri = resolveEndpoint(endpoint);
            byte[] payload = mapper.writeValueAsBytes(java.util.Map.of(
                    "from", nullToEmpty(email.from()),
                    "replyTo", nullToEmpty(email.replyTo()),
                    "to", nullToEmpty(email.recipient()),
                    "subject", nullToEmpty(email.subject()),
                    "body", nullToEmpty(email.body())));
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MailDeliveryException("寄信服務回應 HTTP " + response.statusCode());
            }
            String raw = response.body();
            if (raw == null || raw.isBlank() || raw.length() > MAX_CONTENT) {
                throw new MailDeliveryException("寄信服務回應無效");
            }
            JsonNode node = mapper.readTree(raw);
            // 相容常見欄位命名，抽取 message id。
            String messageId = firstNonBlank(node.path("messageId").asText(""),
                    node.path("id").asText(""), node.path("message_id").asText(""));
            if (messageId.isBlank()) throw new MailDeliveryException("寄信服務回應缺少 message id");
            return new DeliveryResult(messageId);
        } catch (MailDeliveryException exception) {
            throw exception;
        } catch (Exception exception) {
            // 只轉述固定訊息，避免堆疊或底層例外文字外洩憑證。
            throw new MailDeliveryException("寄信服務呼叫失敗");
        }
    }

    /** 僅接受 http/https endpoint。 */
    private URI resolveEndpoint(String value) {
        URI uri;
        try { uri = URI.create(value.trim()); } catch (RuntimeException e) { throw new MailDeliveryException("寄信服務 endpoint 不合法"); }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getAuthority() == null) {
            throw new MailDeliveryException("寄信服務 endpoint 不合法");
        }
        return uri;
    }

    /** null 轉空字串。 */
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    /** 取第一個非空白字串。 */
    private String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }
}
