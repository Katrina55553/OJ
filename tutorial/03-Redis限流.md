# Redis + Lua 滑动窗口限流 — AOP 无侵入接入

## 面试怎么答

> "基于 Redis + Lua 脚本实现了滑动窗口限流算法，通过自定义注解 + AOP 切面无侵入式接入，只需要在 Controller 方法上加一个 `@RateLimit` 注解就能实现限流。"

---

## 1. 为什么需要限流？

```
没有限流：
  恶意用户 1 秒发 1000 次请求 → 服务器被打垮 → 所有用户受影响

有限流：
  恶意用户 1 秒发 1000 次请求 → 第 6 次就被拒绝 → 服务器正常运行
```

**场景**：
- 防止暴力破解密码（登录接口）
- 防止恶意注册（注册接口）
- 防止刷接口消耗资源

---

## 2. 限流算法对比

| 算法 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| **固定窗口** | 每 N 秒一个窗口，窗口内计数 | 简单 | 窗口边界突发（两个窗口交界处可能通过 2 倍请求） |
| **滑动窗口** | 窗口随时间滑动，精确计数 | 精确 | 需要更多存储 |
| **令牌桶** | 固定速率往桶里放令牌，请求消耗令牌 | 允许一定突发 | 实现复杂 |
| **漏桶** | 请求进入桶，固定速率流出 | 平滑 | 不允许突发 |

**本项目使用滑动窗口**，因为：
1. 实现简单（Redis + Lua 几行代码）
2. 精确（不存在固定窗口的边界问题）
3. 性能好（Redis 单线程，原子操作）

---

## 3. 滑动窗口原理

### 图解

```
时间轴（60 秒窗口，限制 5 次请求）：

    ←───── 60 秒窗口 ─────→
    |                       |
    ▼                       ▼
────┬───┬───┬───┬───┬───┬───┬───→ 时间
    t1  t2  t3  t4  t5  t6  t7

    t1: 请求 1 → 计数 1 ✓
    t2: 请求 2 → 计数 2 ✓
    t3: 请求 3 → 计数 3 ✓
    t4: 请求 4 → 计数 4 ✓
    t5: 请求 5 → 计数 5 ✓
    t6: 请求 6 → 计数 6 ✗（超过限制，拒绝）

    当 t7 时，窗口滑动，t1 过期，计数变为 5
```

### Redis 实现

```
Key: rate_limit:192.168.1.1:/api/user/login
Value: Sorted Set
  - 成员：请求的唯一 ID（UUID 或时间戳）
  - 分数：请求的时间戳（毫秒）
```

**每次请求**：
1. 删除窗口外的旧记录（`ZREMRANGEBYSCORE`）
2. 统计窗口内的记录数（`ZCARD`）
3. 如果超过限制，拒绝
4. 否则，添加当前请求记录（`ZADD`）

---

## 4. Lua 脚本（核心）

```lua
-- rate_limit.lua
-- KEYS[1]：限流 key（如 rate_limit:ip:uri）
-- ARGV[1]：窗口大小（毫秒）
-- ARGV[2]：最大请求数
-- ARGV[3]：当前时间戳（毫秒）
-- ARGV[4]：请求唯一标识

-- 1. 删除窗口外的旧记录
redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[3] - ARGV[1])

-- 2. 统计窗口内的请求数
local current = redis.call('ZCARD', KEYS[1])

-- 3. 判断是否超过限制
if current < tonumber(ARGV[2]) then
    -- 未超过，添加当前请求
    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4])
    -- 设置 key 过期时间（窗口大小 + 1 秒，避免残留）
    redis.call('PEXPIRE', KEYS[1], ARGV[1])
    return 1  -- 允许
else
    return 0  -- 拒绝
end
```

### 为什么用 Lua 脚本？

**不用 Lua**（有并发问题）：
```
线程 A: 读取计数 = 4
线程 B: 读取计数 = 4
线程 A: 计数 4 < 5，允许，写入计数 = 5
线程 B: 计数 4 < 5，允许，写入计数 = 5
结果：窗口内通过了 6 个请求！
```

**用 Lua**（原子操作）：
```
Lua 脚本在 Redis 中是原子执行的
线程 A 和线程 B 的整个"删除-判断-写入"过程不会被打断
```

---

## 5. Java 接入 — 自定义注解 + AOP

### 自定义注解

```java
@Target(ElementType.METHOD)  // 只能加在方法上
@Retention(RetentionPolicy.RUNTIME)  // 运行时保留
public @interface RateLimit {
    int count() default 10;           // 窗口内最大请求数
    int windowSeconds() default 60;   // 窗口大小（秒）
    String key() default "";          // 限流 key 前缀
}
```

### AOP 切面

```java
@Aspect
@Component
public class RateLimitInterceptor {

    @Resource
    private StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        // 1. 构造限流 key
        HttpServletRequest request = ((ServletRequestAttributes)
            RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = request.getRemoteAddr();
        String uri = request.getRequestURI();
        String key = "rate_limit:" + rateLimit.key() + ":" + ip + ":" + uri;

        // 2. 执行 Lua 脚本
        Long result = redisTemplate.execute(
            rateLimitScript,                          // Lua 脚本
            Collections.singletonList(key),           // KEYS
            String.valueOf(rateLimit.windowSeconds() * 1000),  // 窗口大小（毫秒）
            String.valueOf(rateLimit.count()),         // 最大请求数
            String.valueOf(System.currentTimeMillis()), // 当前时间
            UUID.randomUUID().toString()               // 请求唯一标识
        );

        // 3. 判断结果
        if (result == null || result == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "请求过于频繁");
        }

        // 4. 放行
        return joinPoint.proceed();
    }
}
```

### 使用方式

```java
@PostMapping("/login")
@RateLimit(count = 5, windowSeconds = 60, key = "user_login")
public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest request) {
    // 登录逻辑...
}
```

**一行注解**就能实现：60 秒内最多 5 次登录请求。

---

## 6. 完整流程图

```
用户发起请求
    │
    ▼
AOP 拦截 @RateLimit 注解
    │
    ▼
构造 key = rate_limit:user_login:192.168.1.1:/api/user/login
    │
    ▼
执行 Lua 脚本
    ├── 删除 60 秒前的旧记录
    ├── 统计当前窗口内的请求数
    ├── 数量 < 5？→ 添加记录，返回 1（允许）
    └── 数量 >= 5？→ 返回 0（拒绝）
    │
    ▼
返回 0？→ 抛出异常"请求过于频繁"
返回 1？→ 放行，执行 Controller 方法
```

---

## 7. 面试常见追问

### Q: 为什么用 Lua 脚本，不用 Java 代码？

> Redis 的"删除-判断-写入"是三步操作。如果用 Java 代码，多线程并发时会出现竞态条件（两个线程同时读到计数 4，都认为可以放行）。Lua 脚本在 Redis 中原子执行，不会被打断。

### Q: 限流 key 是怎么设计的？

> `rate_limit:{业务}:{IP}:{URI}`，三个维度：
> - 业务：区分不同接口（登录、注册、提交）
> - IP：区分不同用户
> - URI：区分不同接口

### Q: 如果用户换了 IP 怎么办？

> IP 限流不能防所有场景。更严格的方案：
> 1. 登录后用 userId 限流
> 2. 验证码（图形验证码、滑块验证码）
> 3. 设备指纹

### Q: AOP 的执行顺序？

> `@Around` → `@Before` → 方法执行 → `@After` → `@AfterReturning`/`@AfterThrowing`
> 限流用 `@Around`，因为在方法执行前就需要判断是否放行。
