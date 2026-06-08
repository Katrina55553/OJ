# OJ 在线判题系统

面向编程学习者的在线判题平台，支持多语言代码提交与自动判题，采用前后端分离架构，Docker 容器化部署。

## 功能特性

- **多语言判题** — 支持 C++、Java、Python、Go、JavaScript 五种语言
- **Docker 代码沙箱** — 用户代码在独立容器中执行，安全隔离（内存/CPU/网络/文件系统限制）
- **题库管理** — 题目的增删改查，支持 Markdown + 数学公式编辑
- **异步判题** — RabbitMQ 消息队列解耦，支持高并发提交
- **用户系统** — JWT 无状态认证，三级角色权限（user/admin/ban）
- **数据统计** — 语言分布饼图、提交热力图
- **讨论区** — 帖子发布、点赞、收藏
- **Redis 缓存** — 用户信息缓存 + 接口限流

## 技术栈

| 层 | 技术 |
|---|------|
| 前端 | Vue 3、TypeScript、Arco Design Vue、Monaco Editor、ByteMD |
| 后端 | Spring Boot 2.7、MyBatis-Plus、JWT |
| 数据库 | MySQL 8.0、Redis 7 |
| 消息队列 | RabbitMQ |
| 代码沙箱 | Docker 容器隔离 |
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
│   ├── sandbox/                # 代码沙箱 Dockerfile
│   ├── sql/                    # 数据库初始化脚本
│   └── Dockerfile              # 后端 Docker 镜像
│
├── docker-compose.yml          # Docker 编排配置
├── .env.example                # 环境变量模板
├── CLAUDE.md                   # 项目文档
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

# 前端
cd oj-frontend
npm install
npm run serve
```

访问 `http://localhost:8080`

### Docker 部署

```bash
# 克隆项目
git clone https://github.com/Katrina55553/OJ.git
cd OJ

# 配置环境变量（可选，有默认值）
cp .env.example .env
# 编辑 .env 填入实际配置

# 一键启动
docker compose up -d

# 查看状态
docker compose ps
```

访问 `http://服务器IP:3000`

## 端口说明

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 3000 | Nginx 静态服务 + API 代理 |
| 后端 | 8101 | Spring Boot API |
| MySQL | 3306 | 数据库 |
| Redis | 6379 | 缓存 |
| RabbitMQ | 5672 | 消息队列 |
| RabbitMQ 管理 | 15672 | 管理界面（guest/guest） |

## 核心架构

### 判题流程

```
用户提交代码
    ↓
QuestionController.doQuestionSubmit()
    ↓
QuestionSubmitServiceImpl（参数校验 → 入库 → 发送消息）
    ↓
RabbitMQ（oj.judge.queue）
    ↓
JudgeMessageConsumer（消费消息）
    ↓
JudgeServiceImpl.doJudge()
    ↓
DockerCodeSandbox（创建容器执行代码）
    ↓
JudgeManager → JudgeStrategy（比对结果）
    ↓
更新数据库（SUCCEED / FAILED）
```

### 认证流程

```
登录 → 后端生成 JWT → 返回 Token → 前端存入 localStorage
请求 → Authorization: Bearer <token> → JwtInterceptor 解析 → 放行
退出 → 前端清除 Token
```

### 缓存策略

```
用户信息：Redis 缓存（user:id:{userId}，TTL 30 分钟）
接口限流：Redis 滑动窗口计数器（@RateLimit 注解）
```

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_PASSWORD` | MySQL 密码 | 123456 |
| `REDIS_PASSWORD` | Redis 密码 | 空 |
| `JWT_SECRET` | JWT 密钥 | 内置默认值 |
| `JDOODLE_CLIENT_ID` | JDoodle API ID（备用） | 空 |
| `JDOODLE_CLIENT_SECRET` | JDoodle API Secret（备用） | 空 |

## API 文档

启动后端后访问：`http://localhost:8101/api/doc.html`（Knife4j / Swagger）

## 常用命令

```bash
# 查看日志
docker compose logs -f backend

# 重建单个服务
docker compose build --no-cache backend
docker compose up -d backend

# 进入容器调试
docker exec -it oj-backend sh
docker exec -it oj-mysql mysql -uroot -p123456 yuoj

# 停止所有服务
docker compose down

# 停止并删除数据（慎用）
docker compose down -v
```

## License

MIT
