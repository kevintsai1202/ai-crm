package com.aicrm.crm.bootstrap;

import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.Customer;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.AppUserRepository;
import com.aicrm.crm.repository.CustomerRepository;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 啟動時把客戶的負責業務（owner_name 字串）正規化為帳號關聯：
 * ① 為每個尚無對應帳號（以 displayName 比對）的業務名稱建立 SALES 登入帳號（預設密碼 password123）；
 * ② 回填所有 owner_id 為 null 的客戶，依 owner_name → 帳號 displayName 對應設定 owner。
 * 冪等：帳號以 displayName 比對只建一次；只回填 owner 為 null 的客戶。在基礎帳號 seed（@Order(1)）之後執行。
 */
@Component
@Order(2)
public class SalesTeamInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SalesTeamInitializer.class);

    /** 自動建立的業務帳號預設密碼。 */
    private static final String DEFAULT_PASSWORD = "password123";

    private final CustomerRepository customers;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public SalesTeamInitializer(CustomerRepository customers, AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.customers = customers;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 建立 displayName → 帳號 的對應表（現有帳號）
        Map<String, AppUser> byDisplayName = new HashMap<>();
        for (AppUser u : users.findAll()) {
            byDisplayName.putIfAbsent(u.getDisplayName(), u);
        }

        // ① 為每個尚無帳號的業務名稱建立 SALES 帳號
        int createdAccounts = 0;
        for (String ownerName : customers.findDistinctOwners()) {
            if (ownerName == null || ownerName.isBlank() || byDisplayName.containsKey(ownerName)) {
                continue;
            }
            String username = toUsername(ownerName, byDisplayName);
            AppUser created = users.save(new AppUser(username, passwordEncoder.encode(DEFAULT_PASSWORD), ownerName, Role.SALES));
            byDisplayName.put(ownerName, created);
            createdAccounts++;
        }

        // ② 回填 owner 為 null 的客戶
        int linked = 0;
        for (Customer c : customers.findAll()) {
            if (c.getOwner() != null) {
                continue;
            }
            AppUser owner = byDisplayName.get(c.getOwnerName());
            if (owner != null) {
                c.assignOwner(owner);
                linked++;
            }
        }
        if (createdAccounts > 0 || linked > 0) {
            log.info("業務帳號正規化：新建 {} 個 SALES 帳號、回填 {} 筆客戶 owner_id", createdAccounts, linked);
        }
    }

    /**
     * 由業務名稱產生唯一登入帳號（email 形式）。名稱可能含中文，故以 ASCII 安全的後綴確保唯一。
     */
    private String toUsername(String ownerName, Map<String, AppUser> existing) {
        // 以名稱為基礎；若 username 已被佔用則加序號，確保唯一
        String base = ownerName.replaceAll("\\s+", "");
        String candidate = base + "@aurora.local";
        int suffix = 1;
        while (users.existsByUsername(candidate)) {
            candidate = base + suffix + "@aurora.local";
            suffix++;
        }
        return candidate;
    }
}
