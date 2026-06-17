# BCrypt 密码加密 — MD5 升级实战

## 面试怎么答

> "项目最初用 `MD5 + 固定 salt` 存储密码，但已知 MD5 是摘要算法而非加密算法，已被证明不安全。我升级到 BCrypt，每个用户有独立的随机 salt，计算成本可配置（成本因子 10），同时兼容旧用户（旧用户用 MD5，新用户用 BCrypt，旧用户下次登录自动升级）。"

---

## 1. 为什么 MD5 不安全？

### 什么是 MD5

```java
// 旧代码：MD5 + 固定 salt
private static final String SALT = "Katrina";

String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
```

MD5 是**摘要算法**（单向不可逆），但有致命问题：

1. **固定 salt**：所有用户共享同一个 salt。攻击者只要知道 salt，就可以对所有用户做彩虹表攻击。
2. **计算太快**：现代 GPU 每秒可以计算数十亿次 MD5。
3. **碰撞已被证明**：MD5 已经可以构造碰撞。

### 彩虹表攻击

```
攻击者预先计算 1 亿个常见密码的 MD5：
  "123456"   → e10adc3949ba59abbe56e057f20f883e
  "password" → 5f4dcc3b5aa765d61d8327deb882cf99
  "admin123" → 0192023a7bbd73250516f069df18b500

拿到数据库里的 MD5 值，查表即可得到明文
```

---

## 2. BCrypt 的优势

### 核心特性

| 特性 | 说明 |
|------|------|
| **自动加盐** | 每个用户有独立的随机 salt，存在 hash 结果里 |
| **可调成本** | 成本因子 cost（默认 10），每次翻倍时间 |
| **单向不可逆** | 无法从 hash 反推出明文 |
| **抗暴力破解** | 计算慢（10 次/秒量级），暴力破解不现实 |

### BCrypt hash 格式

```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 \_/ \__/ \______________________/ \________________________/
  |   |             |                        |
  |   |         salt (22 chars)            hash (31 chars)
  |   |
  |   cost factor = 2^10 = 1024 次迭代
  |
  version
```

**关键**：salt 和 cost 直接存进 hash 结果里，不需要额外字段存储 salt。

---

## 3. 升级方案

### 方案一：全量升级（风险较高）

```java
// 对所有用户的 MD5 重新用 BCrypt 加密
// 问题：需要知道原始密码才能升级
// 解决方案：下次用户登录时顺便升级
```

### 方案二：渐进式升级（推荐）

```java
// UserServiceImpl.java
private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

// 注册：直接用 BCrypt
String encryptPassword = PASSWORD_ENCODER.encode(userPassword);

// 登录：先尝试 BCrypt，失败再回退到 MD5
QueryWrapper<User> queryWrapper = new QueryWrapper<>();
queryWrapper.eq("userAccount", userAccount);
User user = this.baseMapper.selectOne(queryWrapper);
if (user == null) {
    throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
}

String storedPassword = user.getUserPassword();
boolean passwordOk = false;
try {
    // 先尝试 BCrypt
    passwordOk = PASSWORD_ENCODER.matches(userPassword, storedPassword);
} catch (Exception e) {
    // BCrypt 解析失败（说明是旧的 MD5）
    passwordOk = false;
}
if (!passwordOk) {
    // 回退到 MD5 校验
    String md5Password = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    if (!storedPassword.equals(md5Password)) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
    }
    // ⭐ 顺便把用户密码升级为 BCrypt
    String upgradedHash = PASSWORD_ENCODER.encode(userPassword);
    user.setUserPassword(upgradedHash);
    this.baseMapper.updateById(user);
}
```

**升级路径**：
- 新用户注册 → 直接 BCrypt
- 旧用户登录时检测 → 是 MD5 → 升级为 BCrypt
- 一段时间后，数据库里几乎全是 BCrypt

---

## 4. 为什么不用其他方案？

| 方案 | 说明 | 适用场景 |
|------|------|---------|
| **MD5 + salt** | 计算快，已不推荐 | 临时/学习项目 |
| **SHA-256** | 比 MD5 强，但还是太快 | 需要和其他系统兼容 |
| **BCrypt** | 自适应，可配置成本 | **推荐，生产环境** |
| **Argon2** | 最新算法，抗 GPU 攻击 | 有密码学专家团队支持 |
| **PBKDF2** | NIST 推荐，但不如 BCrypt 易用 | 政府/金融 |

---

## 5. 面试常见追问

### Q: BCrypt 每次 encode 的结果不一样，怎么比对？

> BCrypt 的 salt 是随机的（存在 hash 结果里），所以每次 encode 的结果都不同。比对时用 `matches(plainText, hashed)`，内部会从 hash 结果里解析出 salt 和 cost，重新计算一遍再比对。

### Q: 成本因子 cost 选多少合适？

> 默认 10（2^10 = 1024 次迭代）比较合理，密码校验约 0.1 秒。
> - 太低（5-6）：计算太快，暴力破解容易
> - 太高（14-15）：计算太慢，用户登录卡顿
> - 建议：本地测试一下，选一个 0.1 秒内完成的成本因子

### Q: BCrypt 密码长度限制 72 字节？

> 是的，BCrypt 内部只使用 72 字节的输入。如果密码超过 72 字节，多出的部分被忽略。
>
> **解决方案**：先对长密码做 SHA-256 摘要，再 feed 给 BCrypt：
>
> ```java
> byte[] sha256 = DigestUtils.sha256(userPassword.getBytes());
> String hash = PASSWORD_ENCODER.encode(new String(sha256, StandardCharsets.ISO_8859_1));
> ```
> 但实际项目中，超过 72 字节的密码很少见，一般不用处理。

### Q: 升级后旧用户还能正常登录吗？

> 可以。代码里加了回退逻辑：先尝试 BCrypt，失败再用 MD5 校验。同时把旧用户的密码升级为 BCrypt，下次登录直接用 BCrypt。

### Q: 数据库字段需要改吗？

> 不需要。BCrypt hash 结果固定 60 字符（`$2a$10$...` 格式），MD5 是 32 字符。VARCHAR(255) 足够存下。
