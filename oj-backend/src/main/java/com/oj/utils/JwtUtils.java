package com.oj.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtils {

    @Value("${jwt.secret:oj-default-secret-key-must-be-at-least-256-bits-long-for-hs256}")
    private String secret;

    @Value("${jwt.expiration:604800000}")
    private long expiration; // 默认 7 天（毫秒）

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token
     *
     * @param userId   用户 ID
     * @param userRole 用户角色
     * @return JWT 字符串
     */
    public String generateToken(Long userId, String userRole) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setId(UUID.randomUUID().toString())  // jti，唯一标识 Token
                .claim("userId", userId)
                .claim("userRole", userRole)
                .setIssuedAt(now)
                .setExpiration(expireDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从 Token 中获取 jti（JWT ID）
     */
    public String getTokenId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.getId() : null;
    }

    /**
     * 从 Token 中获取剩余有效期（毫秒）
     * 如果已过期返回 0
     */
    public long getTokenRemainingMs(String token) {
        Claims claims = parseToken(token);
        if (claims == null || claims.getExpiration() == null) return 0;
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        return Math.max(remaining, 0);
    }

    /**
     * 解析 JWT Token
     *
     * @param token JWT 字符串
     * @return Claims（包含 userId, userRole），解析失败返回 null
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Token 中获取用户 ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        if (claims == null) return null;
        Object userId = claims.get("userId");
        return userId instanceof Integer ? ((Integer) userId).longValue() : (Long) userId;
    }

    /**
     * 从 Token 中获取用户角色
     */
    public String getUserRole(String token) {
        Claims claims = parseToken(token);
        return claims != null ? (String) claims.get("userRole") : null;
    }

    /**
     * 判断 Token 是否已过期
     * 先从 token 结构中快速判断 exp 字段，避免不必要的解析开销
     */
    public boolean isTokenExpired(String token) {
        try {
            // 快速定位 exp 位置（避免完整解析 JWT）
            String[] parts = token.split("\\.");
            if (parts.length != 3) return true;

            // Base64 解码 payload
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            // 查找 exp 字段
            int expIndex = payload.indexOf("\"exp\"");
            if (expIndex == -1) return true;

            // 定位 exp 值
            int colonIndex = payload.indexOf(":", expIndex);
            int commaIndex = payload.indexOf(",", expIndex);
            String expStr = payload.substring(colonIndex + 1, commaIndex > 0 ? commaIndex : payload.indexOf("}", expIndex)).trim();
            long expTime = Long.parseLong(expStr);

            return System.currentTimeMillis() > expTime * 1000;
        } catch (Exception e) {
            // 解析失败假定已过期
            return true;
        }
    }
}
