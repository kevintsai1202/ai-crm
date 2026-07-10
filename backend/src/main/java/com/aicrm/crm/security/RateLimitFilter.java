package com.aicrm.crm.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 簡易滑動視窗限流：保護登入與 AI 寫入／串流入口，避免暴力嘗試與 AI 帳單暴衝。
 *
 * <p>以 client IP + 路徑類別為 key；視窗固定 60 秒內計數，超限回 429。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RateLimitFilter extends OncePerRequestFilter {

    /** 是否啟用限流（測試可關）。 */
    private final boolean enabled;

    /** 登入每分鐘上限。 */
    private final int loginPerMinute;

    /** AI 相關每分鐘上限（每 IP）。 */
    private final int aiPerMinute;

    /** key → 視窗桶。 */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.login-per-minute:30}") int loginPerMinute,
            @Value("${app.rate-limit.ai-per-minute:60}") int aiPerMinute) {
        this.enabled = enabled;
        this.loginPerMinute = loginPerMinute;
        this.aiPerMinute = aiPerMinute;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        String bucket = classify(request);
        if (bucket == null) {
            filterChain.doFilter(request, response);
            return;
        }
        int limit = "login".equals(bucket) ? loginPerMinute : aiPerMinute;
        String ip = clientIp(request);
        String key = bucket + ":" + ip;
        if (!tryAcquire(key, limit)) {
            response.setStatus(429);
            response.setContentType("application/json; charset=utf-8");
            response.getWriter().write(
                    "{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"請求過於頻繁，請稍後再試\",\"instance\":\""
                            + request.getRequestURI() + "\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 分類需限流的請求；不需限流回 null。
     */
    private String classify(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        if (HttpMethod.POST.matches(request.getMethod()) && path.endsWith("/api/auth/login")) {
            return "login";
        }
        // AI 與工作檯串流／同步
        if (path.contains("/api/ai/") || path.contains("/api/workspace/")) {
            if (HttpMethod.POST.matches(request.getMethod()) || HttpMethod.GET.matches(request.getMethod())) {
                // 僅限高成本路徑：chat、assessment、portfolio、workspace 推薦/問答、model test
                if (path.contains("/chat") || path.contains("/assessment") || path.contains("/portfolio")
                        || path.contains("/recommendation") || path.contains("/test") || path.contains("/score")
                        || path.contains("/knowledge/reindex")) {
                    return "ai";
                }
            }
        }
        if (path.contains("/api/manager/insights") && HttpMethod.POST.matches(request.getMethod())) {
            return "ai";
        }
        return null;
    }

    private boolean tryAcquire(String key, int limit) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) -> {
            if (old == null || now - old.windowStartMs >= 60_000L) {
                return new Window(now, new AtomicInteger(0));
            }
            return old;
        });
        return w.count.incrementAndGet() <= limit;
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    /** 一分鐘視窗。 */
    private static final class Window {
        final long windowStartMs;
        final AtomicInteger count;

        Window(long windowStartMs, AtomicInteger count) {
            this.windowStartMs = windowStartMs;
            this.count = count;
        }
    }
}
