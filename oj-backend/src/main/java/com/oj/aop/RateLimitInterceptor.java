package com.oj.aop;

import com.oj.annotation.RateLimit;
import com.oj.common.ErrorCode;
import com.oj.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.UUID;

/**
 * 接口限流拦截器
 * 基于 Redis ZSET + Lua 脚本实现滑动窗口限流
 *
 * 原理：
 *   每个请求以时间戳为 score、UUID 为 member 存入 ZSET
 *   Lua 脚本原子执行：移除窗口外记录 → 判断是否超限 → 添加当前请求
 *   相比固定窗口计数器，消除了窗口边界突刺问题
 */
@Slf4j
@Aspect
@Component
public class RateLimitInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<Long> rateLimitScript;

    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/rate_limit.lua")));
        rateLimitScript.setResultType(Long.class);
    }

    @Around("@annotation(rateLimit)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // 构建限流 key
        String methodKey = rateLimit.key().isEmpty()
                ? joinPoint.getSignature().toShortString()
                : rateLimit.key();
        String identifier = getIdentifier(request);
        String redisKey = "rate_limit:" + methodKey + ":" + identifier;

        // 当前时间戳（毫秒）
        long now = System.currentTimeMillis();
        // 窗口大小（毫秒）
        long windowMs = rateLimit.windowSeconds() * 1000L;
        // 限流上限
        int limit = rateLimit.count();
        // 唯一标识（避免同一毫秒内重复 member 被 ZSET 去重）
        String member = UUID.randomUUID().toString();

        try {
            // 执行 Lua 脚本：原子操作
            Long result = stringRedisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    member
            );

            if (result == null) {
                // Redis 异常，降级放行
                log.warn("限流 Lua 脚本执行异常，降级放行: key={}", redisKey);
                return joinPoint.proceed();
            }

            if (result == -1) {
                // 超限
                log.warn("接口限流: key={}, identifier={}, limit={}", methodKey, identifier, limit);
                throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                        "请求过于频繁，请 " + rateLimit.windowSeconds() + " 秒后再试");
            }

            // result 为当前窗口请求数，放行
            return joinPoint.proceed();

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // Redis 连接异常等，降级放行
            log.warn("限流组件异常，降级放行: key={}, error={}", redisKey, e.getMessage());
            return joinPoint.proceed();
        }
    }

    /**
     * 获取请求标识（优先用 userId，其次用 IP）
     */
    private String getIdentifier(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        if (userId != null) {
            return "u:" + userId;
        }
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
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
