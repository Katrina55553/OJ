package com.oj.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 窗口内最大请求数
     */
    int count() default 10;

    /**
     * 窗口时间（秒）
     */
    int windowSeconds() default 60;

    /**
     * 限流 key 前缀（默认使用方法名）
     */
    String key() default "";
}
