package com.aicrm.crm.service;

import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.Role;
import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 輕量 JWT 服務，使用 HMAC-SHA256 簽章避免額外依賴。
 */
@Service
public class JwtService {

    /** JWT 簽章密鑰。 */
    private final String secret;

    /** Token 有效秒數。 */
    private final long ttlSeconds;

    /** JSON 序列化工具（Spring Boot 4 自動配置的 Jackson 3 ObjectMapper）。 */
    private final ObjectMapper objectMapper;

    /** 歷史公開預設密鑰，曾外洩於版控，嚴禁再用於簽章。 */
    private static final String LEAKED_DEFAULT_SECRET = "ai-crm-teaching-secret-change-me";

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.token-ttl-seconds}") long ttlSeconds,
            ObjectMapper objectMapper
    ) {
        // 啟動 fail-fast：密鑰必須由環境變數提供、長度足夠、且不得為已外洩的公開預設值，
        // 否則拒絕啟動，避免任何人用已知密鑰偽造 JWT 造成認證繞過。
        if (secret == null || secret.isBlank() || secret.length() < 32
                || LEAKED_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "app.security.jwt-secret 未設定或不安全：請以環境變數 APP_SECURITY_JWT_SECRET 提供至少 32 字元的高強度隨機值。");
        }
        this.secret = secret;
        this.ttlSeconds = ttlSeconds;
        this.objectMapper = objectMapper;
    }

    /**
     * 為登入成功的使用者產生 JWT。
     *
     * @param user 使用者資料
     * @return JWT 字串
     */
    public String issue(AppUser user) {
        try {
            var header = Map.of("alg", "HS256", "typ", "JWT");
            var now = Instant.now();
            var payload = Map.of(
                    "sub", user.getUsername(),
                    "uid", user.getId(),
                    "name", user.getDisplayName(),
                    "role", user.getRole().name(),
                    "iat", now.getEpochSecond(),
                    "exp", now.plusSeconds(ttlSeconds).getEpochSecond()
            );
            var encodedHeader = encodeJson(header);
            var encodedPayload = encodeJson(payload);
            var signature = sign(encodedHeader + "." + encodedPayload);
            return encodedHeader + "." + encodedPayload + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException("JWT 產生失敗", ex);
        }
    }

    /**
     * 驗證並解析 JWT。
     *
     * @param token JWT 字串
     * @return 解析後的認證資料
     */
    public AuthPrincipal parse(String token) {
        try {
            var parts = token.split("\\.");
            // 以常數時間比對簽章，避免時間旁路攻擊逐位元猜測簽章。
            var expected = sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            var actual = parts.length == 3 ? parts[2].getBytes(StandardCharsets.UTF_8) : new byte[0];
            if (parts.length != 3 || !java.security.MessageDigest.isEqual(expected, actual)) {
                throw new IllegalArgumentException("JWT 簽章不正確");
            }
            @SuppressWarnings("unchecked")
            var payload = objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            var exp = Long.parseLong(payload.get("exp").toString());
            if (Instant.now().getEpochSecond() > exp) {
                throw new IllegalArgumentException("JWT 已過期");
            }
            return new AuthPrincipal(payload.get("sub").toString(), payload.get("name").toString(), Role.valueOf(payload.get("role").toString()));
        } catch (Exception ex) {
            throw new IllegalArgumentException("JWT 驗證失敗", ex);
        }
    }

    /**
     * 將物件轉為 Base64Url JSON。
     *
     * @param value 欲編碼物件
     * @return Base64Url 字串
     */
    private String encodeJson(Object value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(value));
    }

    /**
     * 使用 HMAC-SHA256 簽章。
     *
     * @param content 待簽章內容
     * @return Base64Url 簽章
     */
    private String sign(String content) throws Exception {
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * JWT 解析後的登入主體。
     *
     * @param username 帳號
     * @param displayName 顯示名稱
     * @param role 角色
     */
    public record AuthPrincipal(String username, String displayName, Role role) {}
}

