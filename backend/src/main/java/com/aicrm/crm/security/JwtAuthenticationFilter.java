package com.aicrm.crm.security;

import com.aicrm.crm.api.AuthController;
import com.aicrm.crm.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 認證過濾器，優先從 httpOnly cookie 讀取 token，
 * 退而讀取 Authorization Bearer header（相容舊版與本機開發）。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** JWT 驗證服務。 */
    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * 對每個 HTTP request 執行一次 JWT 驗證。
     *
     * @param request HTTP request
     * @param response HTTP response
     * @param filterChain 後續 filter chain
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        var token = extractToken(request);
        if (token != null) {
            try {
                var principal = jwtService.parse(token);
                var auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 從 request 取出 JWT：先找 httpOnly cookie，找不到再看 Authorization Bearer header。
     *
     * @param request HTTP request
     * @return JWT 字串，或 null
     */
    private String extractToken(HttpServletRequest request) {
        // 1. 優先讀取 httpOnly cookie
        if (request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if (AuthController.COOKIE_NAME.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        // 2. 退而讀取 Authorization Bearer header（相容本機 curl / Swagger UI）
        var header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
