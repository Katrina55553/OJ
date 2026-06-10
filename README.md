# OJ 在线判题系统

面向编程学习者的在线判题平台，支持多语言代码提交与自动判题，采用前后端分离架构，Docker 容器化部署。

## 功能特性

- **多语言判题** — 支持 C++、Java、Python、Go、JavaScript 五种语言
- **Docker 代码沙箱** — 用户代码在独立容器中执行，安全隔离（内存/CPU/网络/文件系统限制），每种语言独立镜像
- **在线运行代码** — 即时执行代码并返回输出，与异步判题独立，方便调试
- **题库管理** — 题目的增删改查，支持 Markdown + KaTeX 数学公式编辑
- **异步判题** — RabbitMQ 消息队列解耦，支持高并发提交
- **比赛系统** — 比赛列表、详情、进度跟踪、时间状态标识（进行中/未开始/已结束）
- **AI 聊天助手** — 基于 Ollama 的 AI 对话，SSE 流式输出，Markdown 渲染，聊天记录持久化
- **暗色主题** — GitHub Dark 风格暗色模式，支持跟随系统偏好自动切换，Pinia 状态管理 + localStorage 持久化
- **用户系统** — JWT 无状态认证，三级角色权限（user/admin/ban）
- **数据统计** — 语言分布饼图、用户提交热力图（GitHub 风格，含连续打卡统计）
- **讨论区** — 帖子发布、点赞、收藏，支持 Elasticsearch 全文搜索
- **反馈系统** — 用户反馈提交，支持分类（Bug / 内容错误 / 建议）和图片上传
- **Redis 缓存** — 用户信息缓存 + 接口限流
- **可选组件** — 微信公众号集成、腾讯云 COS 文件上传、Elasticsearch 帖子搜索、JDoodle 备用沙箱

## 技术栈

| 层 | 技术 |
|---|------|
| 前端 | Vue 3、TypeScript、Arco Design Vue、Monaco Editor、ByteMD、ECharts |
| 后端 | Spring Boot 2.7、MyBatis-Plus、JWT |
| 数据库 | MySQL 8.0、Redis 7 |
| 消息队列 | RabbitMQ |
| AI | Ollama（默认 deepseek-r1:7b）、SSE 流式输出 |
| 代码沙箱 | Docker 容器隔离（每语言独立 Dockerfile） |
| 部署 | Docker Compose、Nginx |

## 项目结构

```
OJ/
├── oj-frontend/                # 前端项目
│   ├── src/
│   │   ├── components/         # 公共组件（CodeEditor、MdEditor、GlobalHeader）
│   │   ├── views/              # 页面（题目、比赛、提交记录、用户）
│   │   ├── router/             # 路由配置
│   │   ├── store/              # 状态管理（Vuex + Pinia）
│   │   ├── access/             # 权限控制
│   │   ├── utils/              # 工具函数
│   │   └── constants/          # 常量定义
│   ├── generated/              # OpenAPI 自动生成的 API 客户端
│   ├── Dockerfile              # 前端 Docker 镜像
│   └── nginx.conf              # Nginx 配置
│
├── oj-backend/oj-backend-master/  # 后端项目
│   ├── src/main/java/com/oj/
│   │   ├── controller/         # REST 接口
│   │   ├── service/            # 业务逻辑
│   │   ├── judge/              # 判题系统（CodeSandbox + JudgeStrategy）
│   │   ├── mq/                 # RabbitMQ 消息队列
│   │   ├── config/             # 配置类（Redis、RabbitMQ、JWT、CORS）
│   │   ├── aop/                # 拦截器（JWT、权限、日志、限流）
│   │   └── model/              # 实体、DTO、VO、枚举
│   ├── sandbox/                # 代码沙箱 Dockerfile（每语言一个）
│   │   ├── Dockerfile.cpp
│   │   ├── Dockerfile.java
│   │   ├── Dockerfile.python
│   │   ├── Dockerfile.go
│   │   └── Dockerfile.node
│   ├── sql/                    # 数据库初始化脚本
│   └── Dockerfile              # 后端 Docker 镜像
│
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

# 后端
cd oj-backend/oj-backend-master
mvn spring-boot:run

# 前端（新开终端）
cd oj-frontend
npm install
npm run serve
```

访问 `http://localhost:8080`

> **依赖要求**：JDK 17+、Maven 3.6+、Node.js 18+、MySQL 8.0、Redis 7、RabbitMQ。本地开发需自行安装并启动 MySQL/Redis/RabbitMQ，或使用 Docker 单独启动这些服务。

### Docker 部署（推荐）

```bash
# 克隆项目
git clone https://github.com/Katrina55553/OJ.git
cd OJ

# 配置环境变量（可选，有默认值）
cp .env.example .env
# 编辑 .env 填入实际配置

# 一键启动全部服务
docker compose up -d

# 查看状态
docker compose ps
```

访问 `http://服务器IP:3000`

> **注意**：Docker Compose 会启动 MySQL、Redis、RabbitMQ、Ollama（AI）、后端、前端共 6 个容器，自动创建网络和数据卷。首次启动需拉取/构建镜像，约 3-5 分钟。Ollama 服务占用资源较多（4GB 内存），如不需要 AI 功能可注释掉 `docker-compose.yml` 中的 `ollama` 服务。

## 端口说明

| 服务 | 端口 | 环境 | 说明 |
|------|------|------|------|
| 前端 dev server | 8080 | 本地开发 | Vue CLI 热更新 |
| 前端（生产） | 3000 | Docker | Nginx 静态服务 + API 代理 |
| 后端 API | 8101 | 全部 | Spring Boot（context-path: `/api`） |
| Ollama | 11434 | Docker | AI 模型服务（deepseek-r1:7b） |
| MySQL | 3306 | Docker | 数据库 |
| Redis | 6379 | Docker | 缓存 |
| RabbitMQ | 5672 | Docker | 消息队列 |
| RabbitMQ 管理 | 15672 | Docker | 管理界面（guest/guest） |

本地开发时，前端 `/api` 请求通过 `vue.config.js` 代理到后端 `localhost:8101`。

## 核心架构

### 判题流程

```
用户提交代码
    ↓
QuestionController.doQuestionSubmit()
    ↓
QuestionSubmitServiceImpl（参数校验 → 入库 → 发送消息到 oj.judge.queue）
    ↓
RabbitMQ 消息队列
    ↓
JudgeMessageConsumer（消费消息 → 手动 ACK）
    ↓
JudgeServiceImpl.doJudge()
    ↓
DockerCodeSandbox（构建镜像 → 创建容器 → 执行代码 → 返回输出）
    ↓
JudgeManager → JudgeStrategy（比对输出结果）
    ↓
更新数据库（SUCCEED / FAILED）
```

### 沙箱安全隔离

每种编程语言使用独立的 Dockerfile 构建执行镜像，运行时的安全限制：

| 参数 | 值 | 说明 |
|------|-----|------|
| `--memory` | 256m | 内存限制 |
| `--cpus` | 1 | CPU 限制 |
| `--network` | none | 禁止网络访问 |
| `--read-only` | — | 只读文件系统 |
| `--pids-limit` | 50 | 进程数限制 |
| `--user` | nobody | 非 root 用户 |

配置位于 `application.yml` 的 `codesandbox.docker` 段。

### 认证流程

```
登录 → 后端生成 JWT（HS256，7 天过期）
     → 前端存入 localStorage
请求 → Authorization: Bearer <token>
     → JwtInterceptor 解析校验
     → 注入用户上下文到 Request
退出 → 前端清除 Token
```

### 缓存与限流

```
用户信息：Redis 缓存（user:id:{userId}，TTL 30 分钟）
接口限流：@RateLimit 注解 → RateLimitInterceptor → Redis 滑动窗口计数器
```

## 常用命令

### 本地开发

```bash
# 后端测试
cd oj-backend/oj-backend-master
mvn test                          # 运行全部测试
mvn test -Dtest=CodeSandboxTest   # 运行单个测试类

# 前端检查
cd oj-frontend
npm run lint                      # ESLint + Prettier
```

### Docker 运维

```bash
# 查看日志
docker compose logs -f backend

# 重建单个服务
docker compose build --no-cache backend
docker compose up -d backend

# 进入容器调试
docker exec -it oj-backend sh
docker exec -it oj-mysql mysql -uroot -p123456 yuoj

# 清理沙箱残留镜像（定期执行，防止磁盘占满）
docker image prune -f --filter "label=oj-sandbox"

# 停止所有服务
docker compose down

# 停止并删除数据（慎用）
docker compose down -v
```

## 环境变量

所有变量在 `application.yml` 中有默认值，生产环境建议通过 `.env` 文件或环境变量覆盖：

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
| `JWT_SECRET` | JWT 签名密钥（≥32 字符） | 内置默认值（仅开发用） |
| `JDOODLE_CLIENT_ID` | JDoodle API ID（备用沙箱） | 空 |
| `JDOODLE_CLIENT_SECRET` | JDoodle API Secret（备用沙箱） | 空 |
| `OLLAMA_BASE_URL` | Ollama 服务地址 | `http://ollama:11434` |
| `OLLAMA_MODEL` | AI 模型名称 | `deepseek-r1:7b` |
| `ES_URL` | Elasticsearch 地址（可选） | `http://localhost:9200` |

> 完整变量列表见 `.env.example`，包含腾讯云 COS、微信开放平台、Elasticsearch 等可选组件的配置。

## API 文档

启动后端后访问：`http://localhost:8101/api/doc.html`（Knife4j / Swagger 增强）

## 参考文档

- `CLAUDE.md` — 项目架构文档（AI 辅助开发用）
- `oj-frontend/CLAUDE.md` — 前端详细架构与组件指南
- `todo.md` — 改进清单（安全修复、代码质量、架构优化、功能缺失）

## License

MIT
