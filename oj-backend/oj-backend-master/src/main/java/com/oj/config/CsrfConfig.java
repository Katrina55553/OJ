package com.oj.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * CSRF 防护配置
 * 使用 Double Submit Cookie 模式：
 * 1. 服务端在响应中设置一个 CSRF Token Cookie
 * 2. 前端在 POST 请求的 Header 中带上该 Token
 * 3. 服务端校验 Header 与 Cookie 中的 Token 是否一致
 *
 * 注意：GET/HEAD/OPTIONS 等安全方法不做校验
 */
@Configuration
public class CsrfConfig {

    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    /**
     * CSRF 过滤器 Bean（通过 FilterRegistrationBean 注册，见下方）
     */
    @Bean
    public OncePerRequestFilter csrfFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {

                // 1. 确保响应中始终携带 CSRF Token Cookie
                ensureCsrfCookie(request, response);

                // 2. 对非安全方法（POST/PUT/DELETE 等）校验 Token
                String method = request.getMethod().toUpperCase();
                if (!"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method)) {
                    String headerToken = request.getHeader(CSRF_HEADER_NAME);
                    String cookieToken = getCookieValue(request, CSRF_COOKIE_NAME);

                    if (headerToken == null || cookieToken == null || !headerToken.equals(cookieToken)) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":403,\"message\":\"CSRF token 不匹配\"}");
                        return;
                    }
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    /**
     * 如果请求中没有 CSRF Cookie，则生成一个新的
     */
    private void ensureCsrfCookie(HttpServletRequest request, HttpServletResponse response) {
        String existingToken = getCookieValue(request, CSRF_COOKIE_NAME);
        if (existingToken == null || existingToken.isEmpty()) {
            String token = UUID.randomUUID().toString();
            Cookie cookie = new Cookie(CSRF_COOKIE_NAME, token);
            cookie.setPath("/");
            cookie.setHttpOnly(false); // 前端 JS 需要读取
            cookie.setMaxAge(2592000); // 30 天
            response.addCookie(cookie);
        }
    }

    /**
     * 注册 CSRF 过滤器，优先级高于业务 Filter
     */
    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> csrfFilterRegistration(OncePerRequestFilter csrfFilter) {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(csrfFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10); // CORS 之后执行
        return registration;
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
