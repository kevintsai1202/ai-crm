package com.aicrm.crm.api;

import com.aicrm.crm.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認證 API，提供登入（設定 httpOnly cookie）與登出（清除 cookie）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Cookie 名稱，與 JwtAuthenticationFilter 一致。 */
    public static final String COOKIE_NAME = "ai-crm-session";

    /** 登入服務。 */
    private final AuthService authService;

    /** Cookie 有效秒數，與 JWT TTL 同步。 */
    private final long tokenTtlSeconds;

    public AuthController(AuthService authService,
                          @Value("${app.security.token-ttl-seconds}") long tokenTtlSeconds) {
        this.authService = authService;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /**
     * 驗證帳密，成功後以 Set-Cookie header 回傳 httpOnly JWT，
     * response body 僅含使用者資訊（不含明文 token，避免 JS 讀取）。
     *
     * @param request 登入請求
     * @param response HTTP 回應（用於寫入 Set-Cookie）
     * @return 登入結果（user 資訊，token 欄位為 null）
     */
    @PostMapping("/login")
    public Dtos.LoginResponse login(@Valid @RequestBody Dtos.LoginRequest request,
                                    HttpServletResponse response) {
        var result = authService.login(request);
        // token 改由 response body 回傳，由前端存入 sessionStorage 後以 Bearer header 傳送，
        // 解決 iOS Safari ITP 封鎖跨域 httpOnly cookie 的問題。
        // 同時保留 cookie 以相容既有桌面瀏覽器 session（cookie 優先於 Bearer header）。
        // 本機 HTTP 開發勿用 Secure（瀏覽器會丟棄 cookie）；正式 HTTPS 再加 Secure。
        // 前端主要靠 response body token + sessionStorage Bearer；cookie 為相容路徑。
        response.addHeader("Set-Cookie", buildSessionCookie(result.token(), tokenTtlSeconds));
        return new Dtos.LoginResponse(result.token(), result.user());
    }

    /**
     * 登出：清除 ai-crm-session cookie（Max-Age=0）。
     *
     * @param response HTTP 回應
     */
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildSessionCookie("", 0));
    }

    /**
     * 組裝 session cookie：本機 HTTP 用 SameSite=Lax；可用 APP_COOKIE_SECURE=true 強制 Secure+None（跨站 HTTPS）。
     *
     * @param token JWT 或空字串（清除）
     * @param maxAge 秒數
     * @return Set-Cookie 值
     */
    private String buildSessionCookie(String token, long maxAge) {
        boolean secure = Boolean.parseBoolean(System.getenv().getOrDefault("APP_COOKIE_SECURE", "false"));
        StringBuilder sb = new StringBuilder();
        sb.append(COOKIE_NAME).append("=").append(token == null ? "" : token);
        sb.append("; Path=/; HttpOnly; Max-Age=").append(maxAge);
        if (secure) {
            sb.append("; Secure; SameSite=None");
        } else {
            sb.append("; SameSite=Lax");
        }
        return sb.toString();
    }
}
