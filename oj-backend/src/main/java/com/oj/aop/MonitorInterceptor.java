package com.oj.aop;

import com.oj.service.MonitorService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 请求耗时监控切面
 * 拦截所有 Controller 方法，记录接口调用耗时
 */
@Aspect
@Component
@Slf4j
public class MonitorInterceptor {

    @Resource
    private MonitorService monitorService;

    @Around("execution(* com.oj.controller.*.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String metricName = className + "." + methodName;

        Object result;
        try {
            result = joinPoint.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            monitorService.recordCost(metricName, cost);
        }
        return result;
    }
}
