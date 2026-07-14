package com.aicrm.crm.security;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import jakarta.servlet.DispatcherType;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 設定，符合教學提示詞的 JWT 與角色保護需求。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** JWT 認證過濾器。 */
    private final JwtAuthenticationFilter jwtFilter;

    /**
     * 允許的 CORS 來源樣式（逗號分隔）。
     * 預設僅放行本機開發來源；正式部署時以環境變數 APP_CORS_ALLOWED_ORIGINS 覆蓋，
     * 例如 https://ai-crm-frontend.zeabur.app。支援 setAllowedOriginPatterns 的萬用字元（如 *）。
     */
    @Value("${app.cors.allowed-origins:http://127.0.0.1:*,http://localhost:*}")
    private List<String> corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    /**
     * 設定 SecurityFilterChain。
     *
     * @param http Spring Security HTTP 設定
     * @return Security filter chain
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ASYNC dispatch 為 SSE 串流完成時的回呼，不重新做授權（security context 已清空）
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/health", "/api/auth/login", "/api/auth/logout", "/h2-console/**", "/v3/api-docs/**", "/swagger-ui/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/customers/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/ai/knowledge/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/ai/usage").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/manager/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers("/api/dev/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/customers/**", "/api/opportunities/**", "/api/contacts/**", "/api/interactions/**", "/api/tasks/**", "/api/dashboard/**", "/api/ai/**", "/api/agent/**", "/api/me/**", "/api/workspace/**").authenticated()
                        // 預設拒絕：未明確列為公開（line 58）的端點一律需認證，避免日後新增 Controller 因漏列白名單而裸露。
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(401);
                            response.setContentType("application/json; charset=utf-8");
                            response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"未攜帶有效的認證 Token\",\"instance\":\"" + request.getRequestURI() + "\"}");
                        })
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 建立 BCrypt 密碼編碼器。
     *
     * @return 密碼編碼器
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 設定前端開發伺服器允許的 CORS 來源。
     *
     * @return CORS 設定來源
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        // 來源樣式由設定注入（app.cors.allowed-origins / 環境變數 APP_CORS_ALLOWED_ORIGINS）
        config.setAllowedOriginPatterns(corsAllowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Caddy 代理後前端同域請求仍帶 Origin，需 allowCredentials 讓 cookie 隨請求回傳
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
