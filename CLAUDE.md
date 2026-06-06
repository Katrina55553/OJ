# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

在线判题系统（Online Judge），前后端分离架构。仓库包含两个独立子项目：

| 目录 | 技术栈 | 说明 |
|------|--------|------|
| `oj-frontend/` | Vue 3 + TypeScript + Arco Design Vue | 前端 SPA |
| `oj-backend/oj-backend-master/` | Spring Boot 2.7 + MyBatis-Plus + MySQL | 后端 API 服务 |

前端详细架构参见 `oj-frontend/CLAUDE.md`。

## 常用命令

### 后端

```bash
# 构建（跳过测试）
cd oj-backend/oj-backend-master
mvn package -DskipTests

# 运行（默认 dev profile，端口 8101）
mvn spring-boot:run

# 运行测试
mvn test

# Docker 构建
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

## 后端架构

### 分层结构

```
controller/    → REST 接口（路径前缀 /api）
service/       → 业务逻辑（接口 + impl/ 实现）
manager/       → 可复用的业务能力封装
mapper/        → MyBatis-Plus Mapper 接口
model/
  ├── entity/  → 数据库实体（对应表结构）
  ├── dto/     → 请求参数对象
  ├── vo/      → 响应视图对象（脱敏后返回前端）
  └── enums/   → 业务枚举
```

### 核心业务模块

- **题目管理** (`QuestionController`) — 题目的 CRUD、分页查询、提交判题
- **用户系统** (`UserController`) — 注册、登录（Session）、角色管理（user/admin/ban）
- **帖子系统** (`PostController`) — 帖子的 CRUD、点赞、收藏
- **文件上传** (`FileController`) — 基于腾讯云 COS
- **微信公众号** (`WxMpController`) — 微信登录集成

### 判题系统（核心）

判题流程：`QuestionSubmit` → `JudgeServiceImpl.doJudge()` → `JudgeManager` → `JudgeStrategy`

- **CodeSandbox 接口** — 代码执行抽象层，三种实现：
  - `ExampleCodeSandbox` — 示例（未实际实现）
  - `RemoteCodeSandbox` — 远程沙箱（localhost:8090）
  - `ThirdPartyCodeSandbox` — 第三方服务
  - `JdoodleApiClient` — **当前实际使用**，调用 JDoodle API 执行代码（支持 cpp/java/python/go/javascript）
- **JudgeStrategy 策略模式** — `DefaultJudgeStrategy` 和 `JavaLanguageJudgeStrategy`，根据语言选择判题逻辑
- **判题状态流转**：`WAITING` → `RUNNING` → `SUCCEED` / `FAILED`

### 权限控制

- **@AuthCheck 注解** — 自定义注解 + AOP 拦截器 (`AuthInterceptor`)，声明式角色校验
- **三种角色**：`user`、`admin`、`ban`（定义在 `UserRoleEnum`）
- Session 存储用户信息，前端通过 `Authorization: Bearer` 头传递 Session ID

### 数据库

- MySQL，数据库名 `yuoj`
- MyBatis-Plus 配置：驼峰映射关闭、逻辑删除字段 `isDelete`、自增主键
- 核心表：`user`、`question`、`question_submit`、`post`、`post_thumb`、`post_favour`
- SQL 初始化脚本：`oj-backend/oj-backend-master/sql/create_table.sql`

### 外部依赖

- **Redis** — 当前配置为 `store-type: none`（已关闭），如需开启需修改 `MainApplication` 的 exclude 配置
- **Elasticsearch** — 帖子搜索同步（`IncSyncPostToEs`、`FullSyncPostToEs`）
- **腾讯云 COS** — 文件存储（`CosClientConfig`）
- **微信开放平台** — 微信登录（配置项为占位符，需替换）

### API 文档

集成了 Knife4j（Swagger 增强），启动后访问 `/api/doc.html` 查看接口文档。

## 前端架构要点

- **路由守卫** — `src/access/index.ts` 基于 `ACCESS_ENUM` 自动校验权限
- **API 生成** — `src/generated/` 由 OpenAPI 规范自动生成 TypeScript 客户端
- **代码编辑器** — Monaco Editor（`CodeEditor.vue`），支持多语言
- **Markdown 编辑器** — ByteMD + KaTeX，用于题目内容编辑
- **数据可视化** — ECharts（语言统计、热力图）

## 端口与代理

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 dev server | 8080 | Vue CLI |
| 后端 API | 8101 | Spring Boot（context-path: `/api`） |
| 远程代码沙箱 | 8090 | `RemoteCodeSandbox` 目标地址 |

前端开发时，`/api` 前缀的请求通过 `vue.config.js` 代理到后端 8101 端口。

## 配置文件

| 文件 | 用途 |
|------|------|
| `oj-backend/.../application.yml` | 后端主配置（数据库、Session、微信、COS、JDoodle） |
| `oj-backend/.../application-prod.yml` | 生产环境覆盖配置 |
| `oj-backend/.../application-test.yml` | 测试环境覆盖配置 |
| `oj-frontend/vue.config.js` | Webpack 配置、Monaco 插件、API 代理 |
| `oj-frontend/tsconfig.json` | TypeScript 编译选项 |
