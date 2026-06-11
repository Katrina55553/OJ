# 第 10 章 Docker 容器化部署

## 本章目标

- 掌握 Docker Compose 多服务编排
- 理解多阶段构建的原理和优势
- 学会 Nginx 反向代理配置
- 了解服务依赖和健康检查机制

---

## 10.1 Docker Compose 编排

### 服务总览

本项目使用 Docker Compose 编排 **5 个服务**、**1 个网络**、**3 个数据卷**：

```
┌─────────────────────────────────────────────────────────────┐
│                    Docker Compose                            │
│                                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │  MySQL   │  │  Redis   │  │ RabbitMQ │                  │
│  │  :3306   │  │  :6379   │  │ :5672    │                  │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘                  │
│       │             │             │                         │
│       └─────────────┼─────────────┘                         │
│                     │ healthy                               │
│                     ▼                                       │
│              ┌──────────────┐                               │
│              │   Backend    │                               │
│              │   :8101      │                               │
│              └──────┬───────┘                               │
│                     │ started                               │
│                     ▼                                       │
│              ┌──────────────┐                               │
│              │   Frontend   │                               │
│              │   :3000      │                               │
│              └──────────────┘                               │
│                                                             │
│  网络：oj-network (bridge)                                  │
│  卷：mysql-data, redis-data, rabbitmq-data                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 10.2 基础设施服务

### MySQL

```yaml
mysql:
  image: mysql:8.0
  container_name: oj-mysql
  restart: unless-stopped
  ports:
    - "3306:3306"
  environment:
    MYSQL_ROOT_PASSWORD: ${DB_PASSWORD:-123456}
    MYSQL_DATABASE: yuoj
    MYSQL_CHARSET: utf8mb4
    TZ: Asia/Shanghai
  volumes:
    - mysql-data:/var/lib/mysql                    # 数据持久化
    - ./oj-backend/oj-backend-master/sql/create_table.sql:/docker-entrypoint-initdb.d/init.sql:ro  # 自动初始化
  command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci --default-authentication-plugin=mysql_native_password
  healthcheck:
    test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${DB_PASSWORD:-123456}"]
    interval: 10s
    timeout: 5s
    retries: 5
  deploy:
    resources:
      limits:
        cpus: "1.0"
        memory: 512M
  networks:
    - oj-network
```

**关键点**：
- `create_table.sql` 挂载到 `/docker-entrypoint-initdb.d/`，首次启动自动执行
- 健康检查通过 `mysqladmin ping` 验证数据库就绪
- 资源限制：1 CPU / 512M 内存

### Redis

```yaml
redis:
  image: redis:7-alpine
  container_name: oj-redis
  restart: unless-stopped
  ports:
    - "6379:6379"
  command: >
    redis-server
    --appendonly yes                    # AOF 持久化
    --maxmemory 256mb                   # 最大内存
    --maxmemory-policy allkeys-lru      # 内存淘汰策略
    ${REDIS_PASSWORD:+--requirepass ${REDIS_PASSWORD}}  # 条件密码
  volumes:
    - redis-data:/data
  healthcheck:
    test: ["CMD", "redis-cli", "ping"]
    interval: 10s
    timeout: 5s
    retries: 5
  deploy:
    resources:
      limits:
        cpus: "0.5"
        memory: 256M
  networks:
    - oj-network
```

**关键点**：
- `appendonly yes`：AOF 持久化，重启不丢数据
- `allkeys-lru`：内存满时淘汰最近最少使用的 key
- 条件密码：只有设置了 `REDIS_PASSWORD` 环境变量才启用密码

### RabbitMQ

```yaml
rabbitmq:
  image: rabbitmq:3-management-alpine
  container_name: oj-rabbitmq
  restart: unless-stopped
  ports:
    - "5672:5672"    # AMQP 协议端口
    - "15672:15672"  # 管理界面端口
  environment:
    RABBITMQ_DEFAULT_USER: ${RABBITMQ_USERNAME:-guest}
    RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-guest}
  volumes:
    - rabbitmq-data:/var/lib/rabbitmq
  healthcheck:
    test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
    interval: 30s
    timeout: 10s
    retries: 5
  deploy:
    resources:
      limits:
        cpus: "0.5"
        memory: 256M
  networks:
    - oj-network
```

**关键点**：
- `rabbitmq:3-management-alpine`：带管理界面的轻量版
- 管理界面：http://localhost:15672（guest/guest）
- 健康检查间隔较长（30s），因为 RabbitMQ 启动较慢

---

## 10.3 后端服务构建

### 多阶段 Dockerfile

```dockerfile
# ==================== 构建阶段 ====================
FROM maven:3.8-eclipse-temurin-17 AS builder
WORKDIR /app

# 阿里云 Maven 镜像加速
RUN mkdir -p /root/.m2 && \
    echo '<settings><mirrors><mirror><id>aliyun</id><mirrorOf>central</mirrorOf><url>https://maven.aliyun.com/repository/central</url></mirror></mirrors></settings>' > /root/.m2/settings.xml

# 先复制 pom.xml 缓存依赖
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再复制源码和沙箱 Dockerfile
COPY src ./src
COPY sandbox ./sandbox

# 打包
RUN mvn package -DskipTests -B

# ==================== 运行阶段 ====================
FROM eclipse-temurin:17-jre
WORKDIR /app

# 安装 Docker CLI（DooD 模式需要）
RUN apt-get update && apt-get install -y docker.io && rm -rf /var/lib/apt/lists/*

# 复制构建产物
COPY --from=builder /app/target/oj-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8101
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--spring.profiles.active=prod"]
```

**多阶段构建的优势**：
1. **镜像更小**：运行阶段只有 JRE + Docker CLI，没有 Maven 和源码
2. **构建缓存**：先复制 `pom.xml` 缓存依赖，源码变化不会重新下载依赖
3. **安全性**：构建工具不带入生产镜像

**为什么需要 Docker CLI**：

后端代码通过 `Runtime.exec("docker run ...")` 创建沙箱容器，所以运行阶段必须安装 `docker.io`。这是 **Docker-outside-of-Docker（DooD）** 模式的核心。

### 后端 Compose 配置

```yaml
backend:
  build:
    context: ./oj-backend/oj-backend-master
    dockerfile: Dockerfile
  container_name: oj-backend
  restart: unless-stopped
  ports:
    - "8101:8101"
  volumes:
    # Docker Socket（让后端容器能调用宿主机的 Docker）
    - /var/run/docker.sock:/var/run/docker.sock
    # 沙箱 Dockerfile（挂载到容器内）
    - ./oj-backend/oj-backend-master/sandbox:/app/sandbox
  environment:
    DB_URL: jdbc:mysql://mysql:3306/yuoj?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    DB_USERNAME: root
    DB_PASSWORD: ${DB_PASSWORD:-123456}
    REDIS_HOST: redis
    REDIS_PASSWORD: ${REDIS_PASSWORD:-}
    RABBITMQ_HOST: rabbitmq
    RABBITMQ_USERNAME: ${RABBITMQ_USERNAME:-guest}
    RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-guest}
    JWT_SECRET: ${JWT_SECRET:-oj-default-secret-key-must-be-at-least-256-bits-long-for-hs256}
    SPRING_PROFILES_ACTIVE: prod
  # JVM 内存限制
  entrypoint: ["java", "-Xms256m", "-Xmx512m", "-jar", "app.jar"]
  depends_on:
    mysql:
      condition: service_healthy    # 等待 MySQL 健康
    redis:
      condition: service_healthy    # 等待 Redis 健康
    rabbitmq:
      condition: service_healthy    # 等待 RabbitMQ 健康
  deploy:
    resources:
      limits:
        cpus: "2.0"
        memory: 768M
  networks:
    - oj-network
```

**关键配置**：
- `/var/run/docker.sock` 挂载：让后端容器能调用宿主机 Docker 创建沙箱容器
- `sandbox/` 挂载：让后端容器能访问各语言的 Dockerfile
- `entrypoint` 覆盖：JVM 内存限制 `-Xms256m -Xmx512m`
- `depends_on` + `condition: service_healthy`：确保数据库服务就绪后才启动

---

## 10.4 前端服务构建

### 多阶段 Dockerfile

```dockerfile
# ==================== 构建阶段 ====================
FROM node:18-alpine AS builder
WORKDIR /app

# 先复制依赖文件，利用缓存
COPY package.json package-lock.json ./
RUN npm ci --registry=https://registry.npmmirror.com --prefer-offline && npm cache clean --force

# 复制源码并构建
COPY . .
RUN npm run build

# ==================== 运行阶段 ====================
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# 复制构建产物
COPY --from=builder /app/dist .

# 复制 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### Nginx 配置

```nginx
server {
    listen 80;
    server_name localhost;

    # ========== 静态资源 ==========
    location / {
        root /usr/share/nginx/html;
        index index.html;
        # SPA 路由：所有路径都返回 index.html
        try_files $uri $uri/ /index.html;
    }

    # ========== API 反向代理 ==========
    location /api/ {
        proxy_pass http://backend:8101/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # WebSocket 升级（支持 SSE 长连接）
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        # 长超时（判题可能需要较长时间）
        proxy_read_timeout 120s;
        proxy_send_timeout 120s;
    }

    # ========== 静态资源缓存 ==========
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        root /usr/share/nginx/html;
        expires 7d;
        add_header Cache-Control "public, immutable";
    }

    # ========== 安全：拒绝隐藏文件 ==========
    location ~ /\. {
        deny all;
    }
}
```

**Nginx 配置要点**：

| 配置 | 说明 |
|------|------|
| `try_files $uri $uri/ /index.html` | SPA 路由，Vue Router history 模式必需 |
| `proxy_pass http://backend:8101/api/` | API 反向代理，容器间通过服务名通信 |
| `proxy_http_version 1.1` + `Upgrade` | 支持 WebSocket 升级 |
| `proxy_read_timeout 120s` | 长超时，判题请求可能需要 10 秒以上 |
| `expires 7d` + `immutable` | 静态资源 7 天缓存 |
| `deny all` on `/\.` | 阻止访问 `.env`、`.git` 等隐藏文件 |

---

## 10.5 服务依赖与启动顺序

```
启动顺序：

MySQL ──┐
Redis   ├── healthcheck 全部通过 ──→ Backend ──→ Frontend
RabbitMQ┘

依赖关系：
  Backend  depends_on: mysql(healthy), redis(healthy), rabbitmq(healthy)
  Frontend depends_on: backend(started)

健康检查：
  MySQL:    mysqladmin ping      每 10s，超时 5s，重试 5 次
  Redis:    redis-cli ping       每 10s，超时 5s，重试 5 次
  RabbitMQ: rabbitmq-diagnostics  每 30s，超时 10s，重试 5 次
```

---

## 10.6 资源限制

| 服务 | CPU 限制 | 内存限制 | 说明 |
|------|----------|----------|------|
| MySQL | 1.0 | 512M | 数据库，资源需求中等 |
| Redis | 0.5 | 256M | 缓存，资源需求低 |
| RabbitMQ | 0.5 | 256M | 消息队列，资源需求低 |
| Backend | 2.0 | 768M | Java 应用，JVM 内存 256-512M |
| Frontend | 0.5 | 128M | Nginx 静态服务，资源需求最低 |

**总计**：4.5 CPU / 2.125G 内存

---

## 10.7 数据持久化

| 卷名 | 服务 | 持久化内容 |
|------|------|----------|
| `mysql-data` | MySQL | 数据库文件 |
| `redis-data` | Redis | AOF 持久化文件 |
| `rabbitmq-data` | RabbitMQ | 队列数据、用户配置 |

**警告**：`docker compose down -v` 会删除所有数据卷，**数据不可恢复**！

```bash
# 安全停止（保留数据）
docker compose down

# 危险停止（删除数据）
docker compose down -v  # 慎用！
```

---

## 10.8 常用运维命令

```bash
# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f backend     # 实时查看后端日志
docker compose logs --tail=100 mysql  # 查看最近 100 行

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

---

## 10.9 小结

| 要点 | 内容 |
|------|------|
| **服务数量** | 5 个服务 + 1 个网络 + 3 个数据卷 |
| **多阶段构建** | 前端（Node→Nginx）、后端（Maven→JRE+Docker CLI）|
| **Nginx 反代** | SPA 路由、API 代理、SSE 支持、静态缓存 |
| **服务依赖** | healthcheck 确保数据库就绪后才启动后端 |
| **资源限制** | 每个服务独立 CPU/内存限制 |
| **数据持久化** | 3 个 Named Volume，`down -v` 会删除数据 |

> **下一章预告**：我们将了解项目的可选组件——Redis 缓存。
