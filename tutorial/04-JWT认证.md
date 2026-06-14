# JWT Token 认证 — 无状态登录

## 面试怎么答

> "使用 JWT（JSON Web Token）实现无状态认证。用户登录后，后端生成一个包含用户 ID 和角色的 Token，前端存入 localStorage，每次请求在 Header 中携带。后端通过拦截器解析 Token，获取用户信息，实现鉴权。"

---

## 1. 传统 Session vs JWT

### 传统 Session 模式

```
用户登录
    │
    ▼
后端创建 Session，存入内存/Redis
    │  SessionId = abc123
    ▼
返回 Cookie: SessionId=abc123
    │
    ▼
后续请求携带 Cookie
    │
    ▼
后端根据 SessionId 查找用户信息
```

**问题**：
- 服务端需要存储 Session（内存/Redis）
- 分布式部署需要 Session 共享
- Cookie 有跨域限制

### JWT 模式（本项目）

```
用户登录
    │
    ▼
后端生成 JWT Token（包含用户信息 + 签名）
    │  eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.xxx
    ▼
返回 Token（存在响应体中）
    │
    ▼
前端存入 localStorage
    │
    ▼
后续请求 Header: Authorization: Bearer <Token>
    │
    ▼
后端解析 Token，直接获取用户信息（无需查库）
```

**优势**：
- 无状态：服务端不存储任何会话信息
- 无跨域问题：Token 放在 Header 中，不依赖 Cookie
- 易扩展：多服务器部署无需 Session 共享

---

## 2. JWT 结构

```
eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.4tkHdFz2hU7E_cH2HgLaKQGZKCP5T-9dPCmGRrmY0kU

由三部分组成，用 . 分隔：
    │
    ├── Header（头部）：算法和类型
    │   {"alg": "HS256", "typ": "JWT"}
    │   Base64 编码 → eyJhbGciOiJIUzI1NiJ9
    │
    ├── Payload（载荷）：用户信息
    │   {"userId": 1, "userRole": "admin", "exp": 1234567890}
    │   Base64 编码 → eyJ1c2VySWQiOjF9
    │
    └── Signature（签名）：防篡改
        HMACSHA256(base64(header) + "." + base64(payload), secret)
        → 4tkHdFz2hU7E_cH2HgLaKQGZKCP5T-9dPCmGRrmY0kU
```

**注意**：Header 和 Payload 只是 Base64 编码，**不是加密**，任何人都能解码看到内容。安全性靠签名保证。

---

## 3. 生成 Token

```java
@Component
public class JwtUtils {

    @Value("${jwt.secret}")
    private String secret;  // 签名密钥

    @Value("${jwt.expiration}")
    private long expiration;  // 过期时间（毫秒）

    /**
     * 生成 JWT Token
     * @param userId 用户 ID
     * @param userRole 用户角色
     * @return Token 字符串
     */
    public String generateToken(Long userId, String userRole) {
        return Jwts.builder()
            .claim("userId", userId)           // 自定义字段：用户 ID
            .claim("userRole", userRole)       // 自定义字段：用户角色
            .setExpiration(new Date(System.currentTimeMillis() + expiration))  // 过期时间
            .signWith(SignatureAlgorithm.HS256, secret)  // 签名
            .compact();
    }
}
```

**生成的 Payload 内容**：
```json
{
  "userId": 123,
  "userRole": "admin",
  "exp": 1718000000000
}
```

---

## 4. 解析 Token（拦截器）

```java
@Component
public class JwtInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "userId";

    @Value("${jwt.secret}")
    private String secret;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        // 1. 获取 Token
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            return true;  // 没有 Token，放行（由后续权限校验处理）
        }
        token = token.substring(7);  // 去掉 "Bearer " 前缀

        // 2. 解析 Token
        Claims claims;
        try {
            claims = Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
        } catch (ExpiredJwtException e) {
            response.setStatus(401);
            response.getWriter().write("Token 已过期");
            return false;
        } catch (Exception e) {
            response.setStatus(401);
            response.getWriter().write("Token 无效");
            return false;
        }

        // 3. 提取用户信息，存入 Request
        Long userId = claims.get("userId", Long.class);
        request.setAttribute(ATTR_USER_ID, userId);

        return true;  // 放行
    }
}
```

---

## 5. 权限校验（AOP 注解）

### @AuthCheck 注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {
    String mustRole() default "";  // 必须的角色
}
```

### AOP 切面

```java
@Aspect
@Component
public class AuthInterceptor {

    @Around("@annotation(authCheck)")
    public Object around(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 1. 从 Request 中获取用户 ID
        HttpServletRequest request = ((ServletRequestAttributes)
            RequestContextHolder.getRequestAttributes()).getRequest();
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);

        // 2. 查询用户信息
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 3. 校验角色
        String mustRole = authCheck.mustRole();
        if (StringUtils.isNotBlank(mustRole)) {
            String userRole = user.getUserRole();
            if (!mustRole.equals(userRole)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }

        // 4. 放行
        return joinPoint.proceed();
    }
}
```

### 使用方式

```java
@PostMapping("/delete")
@AuthCheck(mustRole = "admin")  // 只有管理员能删除
public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest request) {
    // 删除逻辑...
}
```

---

## 6. 完整认证流程图

```
┌──────────────────────────────────────────────────────────┐
│                    登录流程                                │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  用户输入账号密码                                          │
│       │                                                  │
│       ▼                                                  │
│  POST /api/user/login                                    │
│       │                                                  │
│       ▼                                                  │
│  后端校验密码                                              │
│       │                                                  │
│       ▼                                                  │
│  生成 JWT Token（userId + userRole + 过期时间）            │
│       │                                                  │
│       ▼                                                  │
│  返回 Token → 前端存入 localStorage                       │
│                                                          │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│                    请求鉴权流程                            │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  前端发起请求                                              │
│  Header: Authorization: Bearer eyJhbGci...               │
│       │                                                  │
│       ▼                                                  │
│  JwtInterceptor 拦截                                     │
│       │                                                  │
│       ├── Token 为空？→ 放行（后续权限校验处理）            │
│       │                                                  │
│       ├── 解析 jti，检查 Redis 黑名单                      │
│       │   └── 在黑名单中？→ 返回 401                       │
│       │                                                  │
│       ├── Token 过期？→ 返回 401                          │
│       │                                                  │
│       ├── Token 无效？→ 返回 401                          │
│       │                                                  │
│       ▼                                                  │
│  解析成功，提取 userId 存入 Request                        │
│       │                                                  │
│       ▼                                                  │
│  AuthInterceptor 拦截（如果有 @AuthCheck 注解）            │
│       │                                                  │
│       ├── 查询用户信息                                     │
│       ├── 校验角色                                        │
│       └── 权限不足？→ 返回 403                            │
│       │                                                  │
│       ▼                                                  │
│  放行，执行 Controller 方法                                │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

## 7. 面试常见追问

### Q: JWT 的密钥怎么管理？

> 密钥通过环境变量 `JWT_SECRET` 注入，不硬编码在代码中。开发环境用默认值，生产环境必须修改。密钥长度至少 256 位（HS256 要求）。

### Q: Token 过期了怎么办？

> 前端收到 401 响应后，清除 localStorage 中的 Token，跳转到登录页。更高级的方案是用双 Token（AccessToken + RefreshToken），AccessToken 短期（2 小时），RefreshToken 长期（7 天），AccessToken 过期时用 RefreshToken 静默刷新。

### Q: JWT 的缺点是什么？

> 1. **无法主动失效**：Token 一旦签发，在过期前都有效。用户修改密码后，旧 Token 仍然有效。解决方案：维护一个 Token 黑名单（Redis）
> 2. **Payload 不宜过大**：Token 每次请求都要传输，Payload 太大会影响性能
> 3. **不能存储敏感信息**：Payload 只是 Base64 编码，不是加密

### Q: Token 黑名单怎么实现的？

> **Redis + jti（JWT ID）方案**：
>
> 1. **生成 Token 时**，添加 `jti`（UUID）作为唯一标识
> 2. **退出登录时**，将 `blacklist:token:{jti}` 写入 Redis，TTL = Token 剩余有效期
> 3. **每次请求时**，拦截器检查 Token 的 jti 是否在 Redis 黑名单中，如果在则返回 401
>
> **Redis Key 设计**：
> ```
> Key:   blacklist:token:{jti}
> Value: "1"（只需 key 存在）
> TTL:   Token 剩余有效期（毫秒）
> ```
>
> **为什么用 jti 而不是存整个 Token？**
> - jti 是固定的 UUID 字符串（36 字符），Token 是变长的（可能几百字符）
> - jti 更省 Redis 存储空间
> - 同一个用户多次登录会生成不同 jti，可以精确失效某一次登录的 Token

### Q: 为什么不把 Token 存在 Cookie 里？

> Cookie 有跨域限制（同源策略），前后端分离部署时前端和后端端口不同（8080 vs 8101），Cookie 无法跨域传递。放在 Header 中没有这个问题。

### Q: 前端怎么自动携带 Token？

> 通过 Axios 请求拦截器：
> ```javascript
> axios.interceptors.request.use(config => {
>   const token = localStorage.getItem('token');
>   if (token) {
>     config.headers.Authorization = `Bearer ${token}`;
>   }
>   return config;
> });
> ```
