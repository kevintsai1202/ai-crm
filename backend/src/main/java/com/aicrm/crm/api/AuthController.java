package com.aicrm.crm.api;

import com.aicrm.crm.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 認證 API，提供登入與 JWT 發行。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** 登入服務。 */
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 驗證帳密並回傳 JWT。
     *
     * @param request 登入請求
     * @return 登入結果
     */
    @PostMapping("/login")
    public Dtos.LoginResponse login(@Valid @RequestBody Dtos.LoginRequest request) {
        return authService.login(request);
    }
}

