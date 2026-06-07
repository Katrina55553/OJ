package com.oj.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存工具类
 */
@Slf4j
@Component
public class RedisCacheUtils {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== String 操作 ====================

    /**
     * 获取缓存
     */
    public String get(String key) {
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取缓存并反序列化为指定类型
     */
    public <T> T get(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) return null;
        try {
            return objectMapper.convertValue(value, clazz);
        } catch (Exception e) {
            log.warn("Redis 反序列化失败, key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 设置缓存（带过期时间）
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.warn("Redis 设置失败, key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 设置缓存（不过期）
     */
    public void set(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.warn("Redis 设置失败, key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 删除缓存
     */
    public boolean delete(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.warn("Redis 删除失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            log.warn("Redis 设置过期失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    /**
     * 判断 key 是否存在
     */
    public boolean hasKey(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Redis 判断 key 失败, key={}, error={}", key, e.getMessage());
            return false;
        }
    }

    // ==================== 计数器（限流用） ====================

    /**
     * 原子递增
     */
    public Long increment(String key, long delta) {
        try {
            return redisTemplate.opsForValue().increment(key, delta);
        } catch (Exception e) {
            log.warn("Redis 递增失败, key={}, error={}", key, e.getMessage());
            return null;
        }
    }
}
