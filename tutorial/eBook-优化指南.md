# OJ 系统架构与性能优化实战指南

> 一本面向面试的实战电子书

---

## 前言

这是一个基于 Spring Boot 2.7 的 Online Judge 系统。
核心能力包括：

- **用户系统**（注册/登录/JWT认证）
- **题目系统**（题目管理/查询/提交）
- **代码沙箱**（Docker隔离执行）
- **判题系统**（异步判题/RabbitMQ）
- **性能优化**（缓存/索引/BCrypt）

---

## 第一章：架构总览

```
        ┌──────────────────────────────────────────────┐
        │           Vue3 前端                      │
        │  (页面/交互/防抖/自定义指令)         │
        └───────────────────┬───────────────────┘
                            │ HTTP 请求
                            ▼
        ┌──────────────────────────────────────┐
        │   Spring Boot 后端                │
        │                                      │
        │  ┌─────────┐   ┌─────────┐         │
        │  │Auth │   │ Monitor │         │
        │  │Check│   │ AOP    │         │
        │  └───┬───┘   └────┬─────┘         │
        │      │                    │               │
        │      ▼                    ▼               │
        │  ┌──────────────────────────────┐   │
        │  │    Controller 层            │   │
        │  │ (题目/用户/提交/监控)       │   │
        │  └──────┬──────────────┬───────┘   │
        │         │              │             │
        │         ▼              ▼             │
        │   ┌──────────┐  ┌──────────┐        │
        │   │ Service │  │ RabbitMQ │        │
        │   │ (缓存)   │  │ (异步)   │        │
        │   └──┬─────┘  └────┬─────┘        │
        │      │                │                │
        │      ▼                ▼                │
        │   ┌──────┐      ┌──────────┐      │
        │   │ Redis│      │ Judge    │      │
        │   │ (缓存) │      │ Consumer│      │
        │   └──────┘      └────┬─────┘      │
        │                    ▼               │
        │   ┌──────────────┐       │
        │   │   MySQL      │       │
        │   │  (组合索引) │       │
        │   └──────┬───────┘       │
        │          │                │
        │          ▼                │
        │   ┌──────────────┐       │
        │   │   Docker  │       │
        │   │ (DooD  │       │
        │   │  -v 挂载│       │
        │   └──────────────┘       │
        └──────────────────────────────────────┘
```

---

## 第二章：核心技术选型

| 层级 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 前端 | Vue3 + Element Plus | Vue Router | 题目列表/详情/提交 |
| 后端 | Spring Boot | 2.7.x | 核心框架 |
| API | RESTful API | - | 统一 BaseResponse 格式 |
| 数据库 | MySQL | 8.x | 数据存储，组合索引优化 |
| 消息队列 | RabbitMQ | 3.x | 异步判题队列 |
| 缓存 | Redis | 7.x | 缓存/限流/黑名单 |
| 代码沙箱 | Docker | latest | 隔离环境执行用户代码 |

---

## 第三章：性能优化实战

### 3.1 代码沙箱：从每次 build 到 -v 挂载

**问题**：每次判题都要 `docker build` 新镜像，耗时 5-15 秒。

**优化**：预构建基础镜像（含编译环境），执行时用 `-v host_path:/code:ro` 挂载用户代码目录。

```
之前：用户代码 → docker build → docker run   (5-15s)
之后：基础镜像已构建一次
      用户代码 → docker run -v 挂载 (1-3s)
```

| 阶段 | 耗时 |
|------|------|
| **首次** | 10-15s |
| **重复** | 1-3s |
| **提升** | 5-10 倍 |

### 3.2 Redis 三级缓存策略

| 缓存对象 | Key | TTL | 场景 |
|---------|-----|-----|------|
| 用户信息 | `user:id:{id}` | 30min | 登录态用户查询 |
| 题目列表 | `question:page:{params}` | 5min | 列表分页查询 |
| 题目详情 | `question:detail:{id}` | 10min | 题目详情页 |
| 相同代码 | `submit:cache:{qid}:{lang}:{MD5(code)}` | 5min | 重复提交相同代码 |

### 3.3 数据库组合索引

```sql
-- question 表
ALTER TABLE question ADD INDEX idx_difficulty_isDelete(difficulty, isDelete);
ALTER TABLE question ADD INDEX idx_userId_createTime(userId, createTime);

-- question_submit 表
ALTER TABLE question_submit ADD INDEX idx_userId_status(userId, status);
ALTER TABLE question_submit ADD INDEX idx_questionId_status(questionId, status);
ALTER TABLE question_submit ADD INDEX idx_userId_createTime(userId, createTime);
```

**列顺序原则**：等值查询在前，范围查询在后。

### 3.4 密码安全：MD5 → BCrypt

- 固定 salt → 自动加盐（每个用户独立 salt）
- 新用户：直接用 BCrypt
- 旧用户：登录时自动升级
- 完全兼容旧数据

### 3.5 JWT 优化：先快速判断过期

- 先解析 exp 字段 → 已过期直接拒绝（无需查 Redis）
- 减少黑名单查询 → 提升认证速度

---

## 第四章：安全保障

### 4.1 Docker 沙箱 6 层安全

| 层级 | 配置 | 作用 |
|------|------|------|
| 1 | `--memory=256m` | 限制内存 |
| 2 | `--cpus=1` | 限制 CPU |
| 3 | `--network=none` | 断网 |
| 4 | `--pids-limit=50` | 限制进程数，防 Fork 炸弹 |
| 5 | `--read-only` | 只读文件系统 |
| 6 | `--user=nobody` | 非 root 用户 |

### 4.2 Redis 限流 + JWT 认证

```
请求 → AOP 限流（滑动窗口）→ JWT 校验 → 黑名单检查 → 业务逻辑
```

限流：Lua 脚本保证原子性（时间窗口 + 令牌数

### 4.3 定期清理

- **每小时**清理已停止的容器
- **每 6 小时**清理悬空镜像
- **每天**清理临时构建镜像

---

## 第五章：监控与观测

### 5.1 接口耗时监控

通过 MonitorInterceptor（AOP）：
- 记录所有 Controller 方法的调用次数、平均耗时、最大耗时
- 存储在 ConcurrentHashMap（轻量级，适合单机部署
- 通过 `GET /api/monitor/metrics` 查看全部指标
- 通过 `GET /api/monitor/reset` 清空统计

**指标示例**：

| 接口 | 次数 | 平均 | 最大 |
|------|------|------|
| QuestionController.getQuestionVOPage | 1523 | 480ms | 2.1s |
| QuestionSubmitController.doQuestionSubmit | 328 | 12.5s | 35.2s |
| UserController.userLogin | 1205 | 85ms | 250ms |

---

## 第六章：面试高频题解答

Q1：**你们的项目中遇到的最大挑战是什么？如何解决的？

> **判题性能优化。最初每次判题要 docker build 新镜像，耗时 5-15 秒。

> **解决方案**：

> ① 预构建基础镜像（gcc/jdk/python/go），执行时用 -v 挂载用户代码目录。
> ② 对编译型语言做"先编译再执行"分离。
> ③ 相同代码提交缓存 5 分钟内的缓存结果。
> ④ 判题速度从 15 秒降到 1-3 秒。

Q2：**如何保障用户密码安全？

> 用 BCrypt 算法，计算成本可配置（cost=10，每次加密约 0.1 秒。每个用户的密码有独立随机 salt，不存在彩虹表攻击。旧用户用 MD5，登录时自动升级到 BCrypt，保证平滑升级。

Q3：**你们项目的缓存策略？

> 三级缓存：① Redis 缓存热点数据（用户、题目、题目详情）、③ 相同代码提交结果缓存。key 设计包含分页参数和查询条件，避免互相污染。穿透/击穿/雪崩：分别用缓存空值、互斥锁、随机 TTL 来预防。

Q4：**索引优化怎么做的？

> 从单列索引改成组合索引。比如查我的提交列表时，查询条件是 (userId, status)，加了组合索引 `idx_userId_status(userId, status)，定位直接定位 2-5 倍。组合索引的原则是：等值查询在前、范围查询在后。

Q5：**如何保证沙箱安全？

> ① Docker-outside-of-Docker（DooD）架构。
> ② 6 层安全限制（内存、CPU、网络、PID、只读、非root）。
> ③ -v 挂载是只读模式，无法写宿主文件。

---

## 第七章：优化总结

| 优化项 | 之前 | 之后 | 提升 |
|--------|------|------|------|
| 代码沙箱 | 每次 build 5-15s | 预构建基础镜像 -v 挂载 1-3s | 5-10x |
| 题目列表 | 每次查库 | Redis 缓存 5 分钟 | 列表查询 3-5x |
| 题目详情 | 每次查库 | Redis 缓存 10 分钟 | 详情查询 2-3x |
| 相同代码提交 | 15+ 秒判题 | Redis 缓存 5 分钟 | 缓存命中 <100ms |
| 数据库查询 | 单列索引 | 组合索引 | 2-5x |
| JWT 认证 | 每次查 Redis 黑名单 | 先判断过期 |减少大量无效查询 |
| 密码存储 | MD5 固定 salt | BCrypt 自动加盐 | 安全性大幅提升 |

---

## 附录

### A. 核心配置文件

```properties
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/oj
  data:
    redis:
      host: localhost
      port: 6379
  rabbitmq:
    host: localhost
    port: 5672
```

### B. 关键文件结构

```
src/main/java/com/oj
├── aop
│   ├── AuthInterceptor.java         (权限校验
│   ├── JwtInterceptor.java (JWT 认证
│   ├── MonitorInterceptor.java (监控AOP)
│   └── RateLimiterInterceptor.java (Redis限流
├── judge
│   ├── JudgeServiceImpl.java    (判题服务
│   ├── JudgeMessageConsumer.java (消息消费者
│   ├── codesandbox/impl/DockerCodeSandbox.java (Docker 沙箱
│   └── DockerCleanupTask.java (容器清理任务
├── service/impl
│   ├── QuestionServiceImpl.java (题目服务
│   ├── QuestionSubmitServiceImpl.java (提交服务
│   ├── UserServiceImpl.java (用户服务)
│   └── MonitorServiceImpl.java (监控服务)
├── controller
│   ├── QuestionController.java
│   ├── QuestionSubmitController.java
│   ├── UserController.java
│   └── MonitorController.java
├── utils
│   ├── JwtUtils.java
│   └── RedisCacheUtils.java
└── sql/create_table.sql (建表 SQL + 索引
```
