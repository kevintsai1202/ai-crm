package com.aicrm.crm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

/**
 * RateLimitFilter 單元測試：同一 IP 超限回 429。
 */
class RateLimitFilterTest {

    @Test
    void loginExceedsLimitReturns429() throws Exception {
        var filter = new RateLimitFilter(true, 2, 60);
        var chain = mock(FilterChain.class);
        for (int i = 0; i < 2; i++) {
            var req = mockLogin();
            var res = mock(HttpServletResponse.class);
            filter.doFilter(req, res, chain);
        }
        var req = mockLogin();
        var res = mock(HttpServletResponse.class);
        var sw = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(sw));
        filter.doFilter(req, res, chain);
        // 第三次應 429
        org.mockito.Mockito.verify(res).setStatus(429);
        assertThat(sw.toString()).contains("429");
    }

    private HttpServletRequest mockLogin() {
        var req = mock(HttpServletRequest.class);
        when(req.getRequestURI()).thenReturn("/api/auth/login");
        when(req.getMethod()).thenReturn("POST");
        when(req.getRemoteAddr()).thenReturn("10.0.0.9");
        return req;
    }
}
