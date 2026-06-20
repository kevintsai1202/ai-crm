package com.aicrm.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 系統登入帳號，提供 JWT 認證與角色授權的資料來源。
 */
@Entity
@Table(name = "app_users")
public class AppUser extends AuditableEntity {

    /** 使用者主鍵。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登入帳號，教學版使用 email。 */
    @Column(nullable = false, unique = true)
    private String username;

    /** BCrypt 密碼雜湊。 */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** 使用者顯示名稱。 */
    @Column(name = "display_name", nullable = false)
    private String displayName;

    /** 使用者角色。 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** 帳號是否啟用；停用者無法登入（預設啟用）。 */
    @Column(nullable = false)
    private boolean enabled = true;

    protected AppUser() {
    }

    /**
     * 建立教學用使用者。
     *
     * @param username 登入帳號
     * @param passwordHash BCrypt 密碼雜湊
     * @param displayName 顯示名稱
     * @param role 權限角色
     */
    public AppUser(String username, String passwordHash, String displayName, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.role = role;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getDisplayName() { return displayName; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }

    /** 更新顯示名稱（管理員編輯用）。 */
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /** 更新角色（管理員編輯用）。 */
    public void setRole(Role role) { this.role = role; }

    /** 更新密碼雜湊（管理員重設密碼用）。 */
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    /** 設定啟用/停用（管理員用）。 */
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}

