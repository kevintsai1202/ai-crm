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
        // 以 response header 直接寫入 SameSite=Strict，避免 Jakarta Cookie API 版本差異
        // SameSite=None 允許跨域攜帶 cookie（前端 ai-crm.springai.world → 後端 zeabur.app）
        response.addHeader("Set-Cookie",
                COOKIE_NAME + "=" + result.token()
                + "; Path=/; HttpOnly; Secure; SameSite=None; Max-Age=" + tokenTtlSeconds);
        // body 只回傳 user 資訊，不帶明文 token
        return new Dtos.LoginResponse(null, result.user());
    }

    /**
     * 登出：清除 ai-crm-session cookie（Max-Age=0）。
     *
     * @param response HTTP 回應
     */
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
                COOKIE_NAME + "=; Path=/; HttpOnly; Secure; SameSite=None; Max-Age=0");
    }
}
