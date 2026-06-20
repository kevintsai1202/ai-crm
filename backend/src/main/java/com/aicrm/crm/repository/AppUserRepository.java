package com.aicrm.crm.repository;

import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 使用者資料存取介面，提供登入流程查詢帳號。
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * 依登入帳號查詢使用者。
     *
     * @param username 登入帳號
     * @return 使用者資料
     */
    Optional<AppUser> findByUsername(String username);

    /**
     * 帳號是否已存在（新增帳號時檢查唯一性）。
     *
     * @param username 登入帳號
     * @return 是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 依角色查詢帳號（供新增客戶的負責業務下拉、業務指派使用）。
     *
     * @param role 角色
     * @return 該角色的帳號清單
     */
    List<AppUser> findByRole(Role role);
}

