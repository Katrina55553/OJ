# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

在线判题系统（Online Judge），前后端分离架构，Docker 容器化部署。仓库包含两个独立子项目：

| 目录 | 技术栈 | 说明 |
|------|--------|------|
| `oj-frontend/` | Vue 3 + TypeScript + Arco Design Vue | 前端 SPA |
| `oj-backend/` | Spring Boot 2.7 + MyBatis-Plus + MySQL | 后端 API 服务 |

前端详细架构参见 `oj-frontend/CLAUDE.md`，改进清单参见 `todo.md`。

## 常用命令

### 后端

```bash
cd oj-backend

# 运行（默认 dev profile，端口 8101，context-path: /api）
mvn spring-boot:run

# 运行指定 profile
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# 构建（跳过测试）
mvn package -DskipTests

# 运行全部测试
mvn test

# Docker 镜像构建
docker build -t oj-backend .
```

### 前端

```bash
cd oj-frontend
npm install
npm run serve    # 开发服务器 → localhost:8080，/api 代理到 localhost:8101
npm run build    # 生产构建
npm run lint     # ESLint + Prettier
```

### Docker 部署

```bash
# 一键启动全部服务（MySQL + Redis + RabbitMQ + 后端 + 前端）
cp .env.example .env    # 可选，编辑填入实际配置
docker compose up -d

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f backend

# 重建单个服务
docker compose build --no-cache backend
docker compose up -d backend

# 停止
docker compose down

# 停止并删除数据卷（慎用）
docker compose down -v
```

## 后端架构

### 分层结构

```
controller/    → REST 接口（context-path: /api）
service/       → 业务逻辑（接口 + impl/ 实现）
mapper/        → MyBatis-Plus Mapper 接口
judge/         → 判题系统（CodeSandbox + JudgeStrategy）
mq/            → RabbitMQ 消息队列（生产者 + 消费者）
config/        → Spring 配置类
aop/           → 拦截器（JWT、权限、日志、限流）
model/
  ├── entity/  → 数据库实体
  ├── dto/     → 请求参数对象
  ├── vo/      → 响应视图对象（脱敏后返回前端）
  └── enums/   → 业务枚举
```

### 核心业务模块

- **题目管理** (`QuestionController`) — 题目的 CRUD、分页查询、提交判题、在线运行代码
- **用户系统** (`UserController`) — 注册、登录、角色管理（user/admin/ban）

### 判题系统（核心）

判题流程采用 **RabbitMQ 异步解耦**：

```
用户提交代码
    ↓
QuestionController.doQuestionSubmit()
    ↓
QuestionSubmitServiceImpl（参数校验 → 入库 → 发送消息）
    ↓
RabbitMQ（oj.judge.queue）
    ↓
JudgeMessageConsumer（消费消息 → 手动 ACK）
    ↓
JudgeServiceImpl.doJudge()
    ↓
DockerCodeSandbox（创建 Docker 容器执行代码，独立镜像/语言）
    ↓
JudgeManager → JudgeStrategy（策略模式比对结果）
    ↓
更新数据库（SUCCEED / FAILED）
```

- **CodeSandbox 接口** — 代码执行抽象层，唯一实现：`DockerCodeSandbox`，通过 `docker build` + `docker run` 为每种语言构建独立镜像并创建容器执行。安全隔离（`--network=none`、`--read-only`、`--pids-limit=50`、`--user=nobody`、内存/CPU 限制），支持 cpp/java/python/go/javascript
- **沙箱 Dockerfile** — `sandbox/` 目录下按语言分离：`Dockerfile.cpp`、`Dockerfile.java`、`Dockerfile.python`、`Dockerfile.go`、`Dockerfile.node`
- **JudgeStrategy 策略模式** — `DefaultJudgeStrategy` 和 `JavaLanguageJudgeStrategy`，根据语言选择判题逻辑，`JudgeManager` 协调执行
- **判题状态流转**：`WAITING` → `RUNNING` → `SUCCEED` / `FAILED`
- **沙箱配置**（`application.yml`）：`codesandbox.type: docker`，超时 10s，内存 256m，CPU 1 核

### 认证与权限（JWT 无状态）

- **认证流程**：登录 → 后端生成 JWT（HS256，7 天过期）→ 前端存入 localStorage → 请求带 `Authorization: Bearer <token>` → `JwtInterceptor` 解析校验 → 注入 `LoginUser` 到 Request 属性
- **`JwtInterceptor`** — 解析 Token，设置用户上下文（排除 `/user/login`、`/user/register` 和 Knife4j 文档路径）
- **`@AuthCheck` 注解** — 声明式角色校验，由 `AuthInterceptor`（AOP 切面）拦截执行
- **三种角色**：`user`、`admin`、`ban`（定义在 `UserRoleEnum`）
- **`LogInterceptor`** — 请求日志记录
- **`RateLimitInterceptor`** — 基于 Redis 滑动窗口的接口限流
- JWT 密钥通过 `JWT_SECRET` 环境变量注入，默认值仅用于本地开发

### 数据库

- MySQL 8.0，数据库名 `yuoj`
- MyBatis-Plus 配置：驼峰映射关闭、逻辑删除字段 `isDelete`、自增主键
- 核心表：`user`、`question`、`question_submit`
- SQL 初始化脚本：`oj-backend/sql/create_table.sql`（Docker 部署时自动挂载执行）

### 外部依赖

- **Redis 7** — 用户信息缓存（`user:id:{userId}`，TTL 30 分钟）+ 接口限流计数器（`RateLimitInterceptor`）。注意：`MainApplication` 排除了 `SessionAutoConfiguration`（因为使用 JWT 无状态认证），但 Redis 本身正常运行
- **RabbitMQ** — 判题消息队列（`JudgeMessageProducer` 发送，`JudgeMessageConsumer` 消费）。配置：手动 ACK（`acknowledge-mode: manual`），prefetch=1，重试 3 次间隔 3 秒

### API 文档

集成 Knife4j（Swagger 增强），启动后访问 `http://localhost:8101/api/doc.html`。

## 前端架构要点

前端详细架构、路由设计、状态管理、组件模式见 `oj-frontend/CLAUDE.md`。此处仅列关键差异点：

- **路由守卫** — `src/access/index.ts` 基于 `ACCESS_ENUM` 自动校验权限（NOT_LOGIN / USER / ADMIN）
- **API 生成** — `generated/` 由 `openapi-typescript-codegen` 自动生成 TypeScript 客户端
- **状态管理** — Vuex 4 为主，Pinia 也已安装（渐进迁移中）
- **HTTP 客户端** — Axios，JWT Token 通过拦截器自动附加到 `Authorization: Bearer` 头

## 端口与代理

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 dev server | 8080 | Vue CLI，`/api` 代理到 localhost:8101 |
| 后端 API | 8101 | Spring Boot（context-path: `/api`） |
| MySQL | 3306 | 数据库（Docker 部署时暴露） |
| Redis | 6379 | 缓存（Docker 部署时暴露） |
| RabbitMQ | 5672 | 消息队列 |
| RabbitMQ 管理 | 15672 | 管理界面（guest/guest） |
| 前端（生产） | 3000 | Nginx 静态服务 + API 代理 |

## 配置文件

| 文件 | 用途 |
|------|------|
| `oj-backend/.../application.yml` | 后端主配置（数据库、Redis、RabbitMQ、JWT、沙箱） |
| `oj-backend/.../application-prod.yml` | 生产环境覆盖配置（数据源、Redis 来自环境变量） |
| `oj-backend/.../application-test.yml` | 测试环境覆盖配置 |
| `oj-frontend/vue.config.js` | Webpack 配置、Monaco 插件、API 代理 |
| `oj-frontend/tsconfig.json` | TypeScript 编译选项 |
| `docker-compose.yml` | Docker 服务编排（MySQL + Redis + RabbitMQ + 后端 + 前端） |
| `.env.example` | 环境变量模板（DB_PASSWORD、REDIS_PASSWORD、JWT_SECRET 等） |
| `oj-backend/.../sandbox/` | 各语言代码沙箱 Dockerfile（Dockerfile.cpp/java/python/go/node） |

## 环境变量

后端通过环境变量注入敏感配置，所有变量在 `application.yml` 中有默认值：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_URL` | MySQL 连接地址 | `jdbc:mysql://localhost:3306/yuoj` |
| `DB_USERNAME` | MySQL 用户名 | `root` |
| `DB_PASSWORD` | MySQL 密码 | `123456` |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `RABBITMQ_HOST` | RabbitMQ 地址 | `localhost` |
| `RABBITMQ_USERNAME` | RabbitMQ 用户名 | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | `guest` |
| `JWT_SECRET` | JWT 签名密钥 | 内置默认值（仅开发用） |
