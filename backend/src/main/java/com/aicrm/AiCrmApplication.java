package com.aicrm;

import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.Role;
import com.aicrm.crm.repository.AppUserRepository;
import java.util.List;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * AI CRM 後端啟動入口，負責載入 Spring Boot 與 JPA Auditing。
 */
@EnableJpaAuditing
@SpringBootApplication
public class AiCrmApplication {

    /**
     * 啟動 Spring Boot 應用程式。
     *
     * @param args 命令列參數
     */
    public static void main(String[] args) {
        SpringApplication.run(AiCrmApplication.class, args);
    }

    /**
     * 初始化教學用帳號，確保登入與角色授權可直接驗收。
     *
     * @param users 使用者資料庫存取介面
     * @param passwordEncoder 密碼雜湊工具
     * @return 啟動後執行的初始化工作
     */
    @Bean
    @Order(1)
    ApplicationRunner seedUsers(AppUserRepository users, PasswordEncoder passwordEncoder) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            var password = passwordEncoder.encode("password123");
            users.saveAll(List.of(
                    new AppUser("sales@aurora.local", password, "業務代表", Role.SALES),
                    new AppUser("manager@aurora.local", password, "銷售經理", Role.MANAGER),
                    new AppUser("admin@aurora.local", password, "系統管理員", Role.ADMIN)
            ));
        };
    }
}

