# Redis 缓存穿透、击穿、雪崩 — 结合 OJ 项目

## 面试怎么答

> "本项目用 Redis 缓存用户信息（`user:id:{userId}`，TTL 30 分钟）。针对缓存问题，我做了以下防护：用空值防穿透、用互斥锁防击穿、用随机 TTL 防雪崩。"

---

## 本项目的缓存设计

```java
// UserServiceImpl.java
private static final String USER_CACHE_KEY = "user:id:";
private static final long USER_CACHE_EXPIRE_MINUTES = 30;

@Override
public User getLoginUser(HttpServletRequest request) {
    Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);

    // 1. 先查 Redis 缓存
    String cacheKey = USER_CACHE_KEY + userId;
    User currentUser = redisCacheUtils.get(cacheKey, User.class);
    if (currentUser != null) {
        return currentUser;
    }

    // 2. 缓存未命中，查数据库
    currentUser = this.getById(userId);
    if (currentUser == null) {
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
    }

    // 3. 写入缓存，TTL 30 分钟
    redisCacheUtils.set(cacheKey, currentUser, USER_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
    return currentUser;
}
```

---

## 1. 缓存穿透

### 什么是穿透？

```
请求一个根本不存在的数据：
  查询 userId = -1（不存在）
  → Redis 没有 → 查 MySQL → MySQL 也没有 → 返回空
  → 下次请求又来 → 又查 MySQL → 又没有...
  
每次请求都打到 MySQL，缓存形同虚设
```

### 本项目有没有这个问题？

**有**。如果恶意用户构造大量不存在的 userId 请求，每次都穿透到数据库。

### 解决方案

```java
@Override
public User getLoginUser(HttpServletRequest request) {
    Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);

    String cacheKey = USER_CACHE_KEY + userId;
    User currentUser = redisCacheUtils.get(cacheKey, User.class);

    // 命中缓存
    if (currentUser != null) {
        // 如果是空值占位符，说明用户不存在
        if (currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    // 查数据库
    currentUser = this.getById(userId);

    if (currentUser == null) {
        // ⭐ 缓存空值，TTL 设短一点（2 分钟），防止大量不存在的 key 占内存
        User emptyUser = new User();
        redisCacheUtils.set(cacheKey, emptyUser, 2, TimeUnit.MINUTES);
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
    }

    // 正常缓存
    redisCacheUtils.set(cacheKey, currentUser, USER_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
    return currentUser;
}
```

**原理**：第一次查不到就缓存一个空值，后续请求直接从 Redis 返回空，不再查 MySQL。

---

## 2. 缓存击穿

### 什么是击穿？

```
某个热点 key 过期的瞬间，大量请求同时涌入：
  user:id:1（热点用户）TTL 到期
  → 100 个请求同时查 Redis → 都没命中
  → 100 个请求同时查 MySQL → 数据库压力暴增
```

### 本项目有没有这个问题？

**有**。管理员账号被高频访问时，TTL 到期瞬间可能有并发请求。

### 解决方案：互斥锁

```java
@Override
public User getLoginUser(HttpServletRequest request) {
    Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
    String cacheKey = USER_CACHE_KEY + userId;

    // 1. 先查缓存
    User currentUser = redisCacheUtils.get(cacheKey, User.class);
    if (currentUser != null) {
        return currentUser;
    }

    // 2. 缓存未命中，尝试获取锁
    String lockKey = "lock:user:" + userId;
    boolean locked = redisCacheUtils.setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);

    if (locked) {
        try {
            // 获取到锁，再次检查缓存（可能其他线程已经写入了）
            currentUser = redisCacheUtils.get(cacheKey, User.class);
            if (currentUser != null) {
                return currentUser;
            }

            // 查数据库并写入缓存
            currentUser = this.getById(userId);
            if (currentUser != null) {
                redisCacheUtils.set(cacheKey, currentUser, USER_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            }
            return currentUser;
        } finally {
            redisCacheUtils.delete(lockKey);  // 释放锁
        }
    } else {
        // 没获取到锁，短暂等待后重试
        Thread.sleep(50);
        return getLoginUser(request);  // 递归重试
    }
}
```

**原理**：只有一个请求能拿到锁去查数据库，其他请求等待。查完后写入缓存，后续请求直接命中缓存。

---

## 3. 缓存雪崩

### 什么是雪崩？

```
大量 key 在同一时间过期：
  1000 个用户的缓存都是 30 分钟前写入的
  → 同一时刻全部过期
  → 1000 个请求同时打到 MySQL
  → 数据库可能崩溃
```

### 本项目有没有这个问题？

**有**。如果大量用户同时登录（比如系统刚启动），30 分钟后会同时过期。

### 解决方案：随机 TTL

```java
// 基础 TTL 30 分钟 + 随机 0-5 分钟
long baseTtl = USER_CACHE_EXPIRE_MINUTES;
long randomExtra = ThreadLocalRandom.current().nextLong(0, 5);
long ttl = baseTtl + randomExtra;

redisCacheUtils.set(cacheKey, currentUser, ttl, TimeUnit.MINUTES);
```

**原理**：每个 key 的过期时间加一个随机偏移，避免同时过期。

---

## 4. 三者对比

| 问题 | 原因 | 后果 | 解决方案 |
|------|------|------|----------|
| **穿透** | 查询不存在的数据 | 每次都打到 DB | 缓存空值 |
| **击穿** | 热点 key 过期 | 并发打到 DB | 互斥锁 |
| **雪崩** | 大量 key 同时过期 | DB 压力暴增 | 随机 TTL |

---

## 5. 面试常见追问

### Q: 缓存穿透用布隆过滤器不是更好？

> 布隆过滤器适合**数据量大、查询相对固定**的场景（比如商品 ID）。本项目的 userId 是连续递增的，用缓存空值就够了，布隆过滤器引入反而增加复杂度。

### Q: 互斥锁用 Redis 的 SETNX 实现，如果锁没释放怎么办？

> 设置了过期时间（10 秒），即使持有锁的线程崩溃，锁也会自动释放。更严谨的方案可以用 Redisson 的分布式锁，支持看门狗自动续期。

### Q: 你们项目实际遇到过缓存问题吗？

> 目前用户量不大，还没遇到。但代码已经做了防护（缓存空值 + 随机 TTL），如果后续用户量上来，再加互斥锁。
