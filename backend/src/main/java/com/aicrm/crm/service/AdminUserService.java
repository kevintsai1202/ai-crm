package com.aicrm.crm.service;

import com.aicrm.crm.api.Dtos;
import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.AppUserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理員帳號維護服務：列出、新增、編輯（顯示名稱/角色）、重設密碼、啟用/停用帳號。
 * 含自我保護：管理員不可停用或調降自己的帳號，避免自我鎖死。
 */
@Service
@Transactional
public class AdminUserService {

    /** 使用者資料存取介面。 */
    private final AppUserRepository users;

    /** 密碼雜湊工具。 */
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 列出所有帳號（依 id 升冪）。
     *
     * @return 帳號清單
     */
    @Transactional(readOnly = true)
    public List<Dtos.AdminUserResponse> list() {
        return users.findAll().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(this::toResponse)
                .toList();
    }

    /**
     * 新增帳號。
     *
     * @param request 新增請求（帳號、顯示名稱、角色、初始密碼）
     * @return 建立後的帳號
     */
    public Dtos.AdminUserResponse create(Dtos.CreateUserRequest request) {
        if (users.existsByUsername(request.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "帳號已存在：" + request.username());
        }
        var user = new AppUser(request.username(), passwordEncoder.encode(request.password()), request.displayName(), request.role());
        return toResponse(users.save(user));
    }

    /**
     * 編輯帳號的顯示名稱與角色。
     *
     * @param id 目標帳號 id
     * @param request 編輯請求
     * @param actorUsername 操作者帳號（用於自我保護）
     * @return 更新後的帳號
     */
    public Dtos.AdminUserResponse update(Long id, Dtos.UpdateUserRequest request, String actorUsername) {
        var user = require(id);
        // 自我保護：不可把自己從 ADMIN 調降，避免失去管理權限而鎖死
        if (user.getUsername().equals(actorUsername) && request.role() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不可調降自己的管理員角色");
        }
        user.setDisplayName(request.displayName());
        user.setRole(request.role());
        return toResponse(user);
    }

    /**
     * 重設帳號密碼。
     *
     * @param id 目標帳號 id
     * @param request 新密碼
     * @return 更新後的帳號
     */
    public Dtos.AdminUserResponse resetPassword(Long id, Dtos.ResetPasswordRequest request) {
        var user = require(id);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return toResponse(user);
    }

    /**
     * 啟用或停用帳號。
     *
     * @param id 目標帳號 id
     * @param request 啟用旗標
     * @param actorUsername 操作者帳號（用於自我保護）
     * @return 更新後的帳號
     */
    public Dtos.AdminUserResponse setEnabled(Long id, Dtos.SetEnabledRequest request, String actorUsername) {
        var user = require(id);
        // 自我保護：不可停用自己，避免登出後無法再登入
        if (user.getUsername().equals(actorUsername) && !request.enabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不可停用自己的帳號");
        }
        user.setEnabled(request.enabled());
        return toResponse(user);
    }

    /** 依 id 取得帳號，不存在則 404。 */
    private AppUser require(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "帳號不存在：" + id));
    }

    /** Entity → DTO。 */
    private Dtos.AdminUserResponse toResponse(AppUser user) {
        return new Dtos.AdminUserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
