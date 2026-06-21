package com.aicrm.crm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aicrm.crm.domain.AppUser;
import com.aicrm.crm.domain.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * JwtService 單元測試：簽發/驗證 round-trip、竄改、過期、格式錯誤。
 */
class JwtServiceTest {

    /** 以固定密鑰與 Jackson 3 ObjectMapper 建立受測服務。 */
    private JwtService newService(long ttlSeconds) {
        return new JwtService("test-secret-please-change-0123456789", ttlSeconds, new ObjectMapper());
    }

    /** 建立帶 id 的測試使用者（id 以反射設定，避免 issue() 的 Map.of NPE）。 */
    private AppUser newUser(Role role) {
        var user = new AppUser("sales@aurora.local", "hash", "業務小明", role);
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    @Test
    void issueThenParse_roundTrips() {
        var service = newService(3600);
        var token = service.issue(newUser(Role.SALES));
        var principal = service.parse(token);
        assertThat(principal.username()).isEqualTo("sales@aurora.local");
        assertThat(principal.displayName()).isEqualTo("業務小明");
        assertThat(principal.role()).isEqualTo(Role.SALES);
    }

    @Test
    void tamperedSignature_throws() {
        var service = newService(3600);
        var token = service.issue(newUser(Role.ADMIN));
        var tampered = token.substring(0, token.length() - 2) + "xx";
        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredToken_throws() {
        var service = newService(-10); // 立即過期
        var token = service.issue(newUser(Role.MANAGER));
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void malformedToken_throws() {
        var service = newService(3600);
        assertThatThrownBy(() -> service.parse("not-a-jwt")).isInstanceOf(IllegalArgumentException.class);
    }
}
