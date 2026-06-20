package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.repository.AppUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 登入服務，負責帳密驗證與 JWT 發行。
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    /** 使用者資料存取介面。 */
    private final AppUserRepository users;

    /** 密碼比對工具。 */
    private final PasswordEncoder passwordEncoder;

    /** JWT 發行工具。 */
    private final JwtService jwtService;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * 驗證帳密並回傳 token。
     *
     * @param request 登入請求
     * @return 登入結果
     */
    public Dtos.LoginResponse login(Dtos.LoginRequest request) {
        var user = users.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("帳號或密碼錯誤"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("帳號或密碼錯誤");
        }
        // 停用帳號禁止登入
        if (!user.isEnabled()) {
            throw new BadCredentialsException("帳號已停用，請洽系統管理員");
        }
        var response = new Dtos.UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
        return new Dtos.LoginResponse(jwtService.issue(user), response);
    }
}

