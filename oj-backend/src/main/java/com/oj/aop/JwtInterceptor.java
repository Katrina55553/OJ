package com.oj.aop;

import com.oj.utils.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 认证拦截器
 * 从 Authorization 头提取 Token，解析后将 userId 和 userRole 存入 Request Attribute
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "jwt_userId";
    public static final String ATTR_USER_ROLE = "jwt_userRole";

    @Resource
    private JwtUtils jwtUtils;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // OPTIONS 请求直接放行（CORS 预检）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 尝试解析 Token，有则设置用户信息，无则跳过（不拦截）
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Long userId = jwtUtils.getUserId(token);
            String userRole = jwtUtils.getUserRole(token);

            if (userId != null) {
                request.setAttribute(ATTR_USER_ID, userId);
                request.setAttribute(ATTR_USER_ROLE, userRole);
            }
        }

        // 放行所有请求，具体的登录校验由 Service 层的 getLoginUser 处理
        return true;
    }
}
