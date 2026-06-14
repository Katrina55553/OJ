package com.oj.aop;

import com.oj.utils.JwtUtils;
import com.oj.utils.RedisCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * JWT 认证拦截器
 * 从 Authorization 头提取 Token，解析后将 userId 和 userRole 存入 Request Attribute
 * 支持 Token 黑名单校验
 */
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "jwt_userId";
    public static final String ATTR_USER_ROLE = "jwt_userRole";

    /** Redis 黑名单 key 前缀 */
    private static final String TOKEN_BLACKLIST_PREFIX = "blacklist:token:";

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private RedisCacheUtils redisCacheUtils;

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

            // 检查 Token 是否在黑名单中
            String tokenId = jwtUtils.getTokenId(token);
            if (tokenId != null && redisCacheUtils.hasKey(TOKEN_BLACKLIST_PREFIX + tokenId)) {
                log.info("Token 已失效（已加入黑名单）: tokenId={}", tokenId);
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":40100,\"message\":\"Token 已失效，请重新登录\"}");
                return false;
            }

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
