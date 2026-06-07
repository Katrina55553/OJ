package com.oj.aop;

import com.oj.annotation.RateLimit;
import com.oj.common.ErrorCode;
import com.oj.exception.BusinessException;
import com.oj.utils.RedisCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;

/**
 * 接口限流拦截器
 * 使用 Redis 固定窗口计数器实现
 */
@Slf4j
@Aspect
@Component
public class RateLimitInterceptor {

    @Resource
    private RedisCacheUtils redisCacheUtils;

    @Around("@annotation(rateLimit)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 获取请求信息
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 构建限流 key：rate_limit:{key}:{identifier}
        String methodKey = rateLimit.key().isEmpty()
                ? joinPoint.getSignature().toShortString()
                : rateLimit.key();
        String identifier = getIdentifier(request);
        String redisKey = "rate_limit:" + methodKey + ":" + identifier;

        // 获取当前窗口的请求次数
        Long count = redisCacheUtils.increment(redisKey, 1);
        if (count == null) {
            // Redis 异常，放行
            return joinPoint.proceed();
        }

        // 第一次请求时设置过期时间
        if (count == 1) {
            redisCacheUtils.expire(redisKey, rateLimit.windowSeconds(), TimeUnit.SECONDS);
        }

        // 超过限制
        if (count > rateLimit.count()) {
            log.warn("接口限流: key={}, identifier={}, count={}", methodKey, identifier, count);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "请求过于频繁，请 " + rateLimit.windowSeconds() + " 秒后再试");
        }

        return joinPoint.proceed();
    }

    /**
     * 获取请求标识（优先用 userId，其次用 IP）
     */
    private String getIdentifier(HttpServletRequest request) {
        // 尝试从 JWT 获取 userId
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        if (userId != null) {
            return "u:" + userId;
        }
        // 未登录则用 IP
        return "ip:" + getClientIp(request);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
