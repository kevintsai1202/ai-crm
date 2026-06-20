package com.aicrm.crm.api;

import com.aicrm.crm.service.AdminUserService;
import com.aicrm.crm.service.JwtService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理員帳號維護 REST API（全部限 ADMIN，由 SecurityConfig 以 /api/admin/** 控管）。
 * 提供列出、新增、編輯（顯示名稱/角色）、重設密碼、啟用/停用。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    /** 帳號維護服務。 */
    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 列出所有帳號。
     *
     * @return 帳號清單
     */
    @GetMapping
    public List<Dtos.AdminUserResponse> list() {
        return adminUserService.list();
    }

    /**
     * 新增帳號。
     *
     * @param request 新增請求
     * @return 建立後帳號
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.AdminUserResponse create(@Valid @RequestBody Dtos.CreateUserRequest request) {
        return adminUserService.create(request);
    }

    /**
     * 編輯帳號顯示名稱與角色。
     *
     * @param id 目標帳號 id
     * @param request 編輯請求
     * @param authentication 操作者認證（自我保護用）
     * @return 更新後帳號
     */
    @PutMapping("/{id}")
    public Dtos.AdminUserResponse update(@PathVariable Long id, @Valid @RequestBody Dtos.UpdateUserRequest request, Authentication authentication) {
        return adminUserService.update(id, request, resolveUsername(authentication));
    }

    /**
     * 重設帳號密碼。
     *
     * @param id 目標帳號 id
     * @param request 新密碼
     * @return 更新後帳號
     */
    @PutMapping("/{id}/password")
    public Dtos.AdminUserResponse resetPassword(@PathVariable Long id, @Valid @RequestBody Dtos.ResetPasswordRequest request) {
        return adminUserService.resetPassword(id, request);
    }

    /**
     * 啟用/停用帳號。
     *
     * @param id 目標帳號 id
     * @param request 啟用旗標
     * @param authentication 操作者認證（自我保護用）
     * @return 更新後帳號
     */
    @PutMapping("/{id}/enabled")
    public Dtos.AdminUserResponse setEnabled(@PathVariable Long id, @Valid @RequestBody Dtos.SetEnabledRequest request, Authentication authentication) {
        return adminUserService.setEnabled(id, request, resolveUsername(authentication));
    }

    /** 從認證主體解析操作者登入帳號。 */
    private String resolveUsername(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof JwtService.AuthPrincipal principal) {
            return principal.username();
        }
        throw new IllegalStateException("未取得登入身分");
    }
}
