# OJ 在线判题系统

> 面向编程学习者的在线判题平台，支持多语言代码提交与自动判题，采用前后端分离架构，Docker 容器化部署，配套完整 eBook 和面试教程。

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3-4FC08D)](https://vuejs.org/)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

## 功能特性

### 核心判题
- **多语言支持** — C++ / Java / Python / Go / JavaScript 五种语言
- **Docker 沙箱** — 用户代码在独立容器中执行，6 层安全隔离（内存 / CPU / 网络 / 文件系统 / 进程数 / 用户）
- **镜像复用** — 预构建基础镜像 + `-v` 卷挂载，避免每次提交都重建镜像，判题耗时下降 90%
- **在线运行** — 即时执行并返回输出，与异步判题独立，方便调试
- **异步判题** — RabbitMQ 消息队列解耦 + 手动 ACK + 死信队列，支持高并发

### 题库与编辑
- **题库管理** — 增删改查、Markdown + KaTeX 数学公式编辑
- **判题配置** — 灵活的时间 / 内存 / 测试用例配置
- **策略模式** — 默认 / Java 语言策略可扩展

### 用户与权限
- **JWT 无状态认证** — HS256 签名，Redis 黑名单支持主动登出
- **三级角色** — user / admin / ban
- **BCrypt 密码加密** — 替代 MD5，兼容历史数据
- **路由级权限** — 路由守卫 + 访问枚举 ACCESS_ENUM
- **未登录保护** — 评测记录代码查看、提交记录跳转登录态校验

### 性能与监控
- **Redis 多级缓存** — 用户信息 / 题目列表 / 题目详情 / 相同代码提交结果
- **接口限流** — `@RateLimit` 注解 + Redis Lua 滑动窗口
- **MySQL 组合索引** — `idx_difficulty_isDelete`、`idx_userId_createTime`、`idx_questionId_status` 等
- **JWT 快速校验** — 先本地解析 token 过期时间，命中黑名单前零 Redis 访问
- **监控 AOP** — Controller / Service 接口耗时采集，定时汇总
- **Docker 清理** — 沙箱容器 / 镜像定期清理（`@Scheduled`）
- **前端防抖** — 搜索表单 300ms 防抖

### 体验
- **暗色主题** — GitHub Dark 风格，跟随系统自动切换，Pinia 状态 + localStorage 持久化
- **OpenAPI 自动生成** — 前后端 API 客户端同步，前端零手写请求代码
- **Knife4j 文档** — 增强版 Swagger UI，访问 `/api/doc.html`

## 技术栈

| 层 | 技术 |
|---|------|
| 前端 | Vue 3、TypeScript、Arco Design Vue、Monaco Editor、ByteMD、Vuex 4 |
| 后端 | Spring Boot 2.7、MyBatis-Plus 3.5、JWT、Spring AOP |
| 数据库 | MySQL 8.0、Redis 7 |
| 消息队列 | RabbitMQ 3.x |
| 代码沙箱 | Docker 容器（每语言独立基础镜像） |
| 部署 | Docker Compose、Nginx |
| 文档 | Knife4j、Markdown 教程（12 篇 + eBook 12 章） |

## 项目结构

```
OJ/
├── oj-frontend/                # 前端项目
│   ├── src/
│   │   ├── components/         # 公共组件（CodeEditor、MdEditor、GlobalHeader、MdPreview、ThemeSwitcher）
│   │   ├── views/              # 页面（题目、提交记录、用户、管理）
│   │   ├── router/             # 路由配置 + 路由守卫
│   │   ├── store/              # 状态管理（Vuex：user/theme）
│   │   ├── access/             # 权限控制（ACCESS_ENUM + checkAccess）
│   │   ├── utils/              # 工具函数（debounce、question）
│   │   └── layouts/            # 布局组件（BasicLayout、UserLayout）
│   ├── generated/              # OpenAPI 自动生成的 TypeScript 客户端
│   ├── Dockerfile              # 前端 Docker 镜像
│   └── nginx.conf              # Nginx 配置（含 /api 反代）
│
├── oj-backend/                 # 后端项目
│   ├── src/main/java/com/oj/
│   │   ├── controller/         # REST 接口（Question、User、CodeExecute、Monitor）
│   │   ├── service/impl/       # 业务实现（User、Question、QuestionSubmit、Monitor）
│   │   ├── judge/              # 判题系统
│   │   │   ├── codesandbox/    # Docker 沙箱实现
│   │   │   ├── strategy/       # 判题策略（默认 / Java）
│   │   │   ├── JudgeManager    # 策略路由
│   │   │   ├── JudgeService    # 判题主流程
│   │   │   └── DockerCleanupTask  # 沙箱清理定时任务
│   │   ├── mq/                 # RabbitMQ 生产者 / 消费者 / 死信
│   │   ├── aop/                # 拦截器（JWT、Auth、RateLimit、Log、Monitor）
│   │   ├── annotation/         # 自定义注解（@AuthCheck、@RateLimit）
│   │   ├── config/             # 配置类（Redis、RabbitMQ、CORS、MyBatisPlus、Knife4j）
│   │   ├── exception/          # 全局异常处理
│   │   ├── model/              # Entity / DTO / VO / 枚举
│   │   └── utils/              # JWT、RedisCacheUtils、SqlUtils
│   ├── sandbox/                # 代码沙箱 Dockerfile（每语言一个）
│   ├── sql/                    # 数据库初始化 + 索引
│   └── Dockerfile              # 后端 Docker 镜像
│
├── ebook/                      # 架构 eBook（12 章）
├── tutorial/                   # 面试教程（12 篇 + 优化指南）
├── docker-compose.yml          # Docker 编排配置
├── .env.example                # 环境变量模板
├── CLAUDE.md                   # 项目架构文档（AI 辅助开发用）
└── todo.md                     # 改进清单
```

## 快速开始

### 本地开发

```bash
# 克隆项目
git clone https://github.com/Katrina55553/OJ.git
cd OJ

# 后端（需 JDK 17+ / Maven 3.6+）
cd oj-backend
mvn spring-boot:run

# 前端（新开终端，需 Node.js 18+）
cd oj-frontend
npm install
npm run serve
```

访问 `http://localhost:8080`

> 依赖要求：JDK 17+、Maven 3.6+、Node.js 18+、MySQL 8.0、Redis 7、RabbitMQ 3.x。
> 本地开发需自行安装中间件，或使用 Docker 单独启动。

### Docker 部署（推荐）

```bash
git clone https://github.com/Katrina55553/OJ.git
cd OJ

cp .env.example .env
# 编辑 .env 填入实际配置

# 一键启动全部服务（MySQL / Redis / RabbitMQ / 后端 / 前端）
docker compose up -d

docker compose ps
```

访问 `http://服务器IP:3000`

> 首次启动需拉取 / 构建镜像，约 3-5 分钟。

## 端口说明

| 服务 | 端口 | 环境 | 说明 |
|------|------|------|------|
| 前端 dev server | 8080 | 本地开发 | Vue CLI 热更新 |
| 前端（生产） | 3000 | Docker | Nginx 静态服务 + `/api` 反代 |
| 后端 API | 8101 | 全部 | Spring Boot（context-path: `/api`） |
| MySQL | 3306 | Docker | 数据库 |
| Redis | 6379 | Docker | 缓存 |
| RabbitMQ | 5672 | Docker | 消息队列 |
| RabbitMQ 管理 | 15672 | Docker | guest / guest |

本地开发时，前端 `/api` 请求通过 `vue.config.js` 的 devServer proxy 转发到 `localhost:8101`。

## 核心架构

### 判题流程

```
用户提交代码
    ↓
QuestionController.doQuestionSubmit()        // @RateLimit 限流
    ↓
QuestionSubmitServiceImpl                     // 验参 → 入库（status=WAITING）→ 发送 MQ
    ↓
RabbitMQ（oj.judge.queue）                    // 手动 ACK
    ↓
JudgeMessageConsumer                          // 消费 → 调判题
    ↓
JudgeServiceImpl.doJudge()                   // 状态校验 → 调沙箱 → 判题策略
    ↓
DockerCodeSandbox                             // 复用基础镜像 + -v 挂载用户代码目录
    ↓
JudgeManager → JudgeStrategy（Default / Java） // 比对输出结果
    ↓
更新数据库（SUCCEED / FAILED）+ Redis 缓存
    ↓
RabbitMQ ACK
```

### 沙箱安全隔离（6 层）

| 参数 | 值 | 防护目标 |
|------|-----|----------|
| `--memory=256m` | 内存 | OOM 攻击 |
| `--cpus=1` | CPU | 算力耗尽 |
| `--network=none` | 网络 | 外网通信 / 攻击内网 |
| `--read-only` | 文件系统 | 篡改沙箱 / 持久化 |
| `--pids-limit=50` | 进程数 | fork 炸弹 |
| `--user=nobody` | 用户 | 提权 |

镜像复用：每种语言构建一个 `oj-base-{lang}` 基础镜像（包含编译器和运行时），用户代码通过 `-v` 挂载到容器的 `/code` 目录，**免去每次提交都 `docker build` 的开销**。

### 认证流程

```
登录 → 后端生成 JWT（HS256，7 天）→ 前端存 localStorage
请求 → Authorization: Bearer <token>
     → JwtInterceptor 先本地校验过期
     → 命中后再查 Redis 黑名单
     → 注入 userId 到 Request 属性
路由守卫 → 路由级 access 校验 → 未登录跳转 /user/login
```

### 缓存设计

| 缓存项 | key 格式 | TTL | 清除时机 |
|--------|----------|-----|----------|
| 用户信息 | `user:id:{userId}` | 30 min | 更新时 |
| 题目列表 | `question:list:{hash}` | 5 min | 增删改时 SCAN 清空 |
| 题目详情 | `question:detail:{id}` | 10 min | 同上 |
| 提交结果 | `submit:cache:{qid}:{lang}:{md5(code)}` | 5 min | 无 |
| 限流计数 | `rate_limit:{key}:{userId/ip}` | 窗口大小 | 滑动窗口 |

### 限流

`@RateLimit(count=N, windowSeconds=M, key="...")` 注解 → `RateLimitInterceptor` AOP → Redis Lua 滑动窗口脚本。  
已应用接口：题目提交、代码运行。

## 常用命令

### 本地开发

```bash
# 后端测试
cd oj-backend
mvn test                          # 全部测试
mvn compile                       # 编译

# 前端
cd oj-frontend
npm run lint                      # ESLint + Prettier
npm run build                     # 生产构建
```

### Docker 运维

```bash
# 查看日志
docker compose logs -f backend

# 重建单个服务
docker compose build --no-cache backend
docker compose up -d backend

# 进入容器
docker exec -it oj-backend sh
docker exec -it oj-mysql mysql -uroot -p123456 yuoj

# 沙箱清理（自动 + 手动）
# 自动：后端 @Scheduled 定时任务（DockerCleanupTask）
# 手动：
docker image prune -f --filter "label=oj-sandbox"
docker container prune -f

# 停止 / 清理
docker compose down               # 停止
docker compose down -v            # 停止 + 删除数据
```

## 环境变量

完整列表见 [.env.example](.env.example)。核心变量：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_URL` | MySQL 连接地址 | `jdbc:mysql://localhost:3306/yuoj` |
| `DB_USERNAME` | MySQL 用户名 | `root` |
| `DB_PASSWORD` | MySQL 密码 | `123456` |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `RABBITMQ_HOST` | RabbitMQ 地址 | `localhost` |
| `RABBITMQ_USERNAME` | RabbitMQ 用户 | `guest` |
| `RABBITMQ_PASSWORD` | RabbitMQ 密码 | `guest` |
| `JWT_SECRET` | JWT 签名密钥（≥32 字符） | 内置默认值（仅开发用） |

## API 文档

启动后端后访问：`http://localhost:8101/api/doc.html`（Knife4j / Swagger 增强版）

主要接口：

| 模块 | 接口 | 方法 | 鉴权 |
|------|------|------|------|
| 用户 | `/api/user/register` | POST | 否 |
| 用户 | `/api/user/login` | POST | 否 |
| 用户 | `/api/user/get/login` | GET | 是 |
| 用户 | `/api/user/logout` | POST | 是 |
| 题目 | `/api/question/list/page` | POST | 否 |
| 题目 | `/api/question/get` | GET | 否 |
| 题目 | `/api/question/add` | POST | admin |
| 题目 | `/api/question/delete` | POST | admin |
| 提交 | `/api/question/submit/do` | POST | user |
| 提交 | `/api/question/submit/list/page` | POST | 否 |
| 运行 | `/api/code/execute` | POST | 否（限流 30/min） |
| 监控 | `/api/monitor/**` | GET | admin |

## 文档资源

| 路径 | 内容 |
|------|------|
| [CLAUDE.md](CLAUDE.md) | 项目架构总览（AI 辅助开发用） |
| [oj-frontend/CLAUDE.md](oj-frontend/CLAUDE.md) | 前端组件 / 路由 / 状态详细指南 |
| [ebook/](ebook/) | 12 章架构 eBook（基础架构 → 进阶扩展） |
| [tutorial/](tutorial/) | 12 篇面试教程（核心架构 / 数据库优化 / 压测方案） |
| [todo.md](todo.md) | 改进清单 |

## 核心亮点（面试话术）

1. **Docker 沙箱镜像复用**：从"每次 docker build"改为"基础镜像 + 卷挂载"，单次判题耗时从 ~10s 降到 ~0.5s
2. **多级 Redis 缓存**：用户 / 题目列表 / 题目详情 / 相同代码提交结果，覆盖主要读路径
3. **滑动窗口限流**：Redis Lua 脚本保证原子性，相比固定窗口消除突刺
4. **策略模式判题**：DefaultJudgeStrategy + JavaLanguageJudgeStrategy 应对不同语言判题逻辑
5. **BCrypt 密码升级**：增量迁移方案，新用户 BCrypt + 旧用户 MD5 兜底
6. **组合索引实战**：分析慢查询 → 设计组合索引 → EXPLAIN 验证

## License

MIT
