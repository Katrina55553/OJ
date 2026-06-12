# Docker 沙箱架构（DooD）— 6 层安全隔离

## 面试怎么答

> "我使用了 Docker-outside-of-Docker（DooD）架构来实现代码沙箱。后端容器通过挂载宿主机的 Docker Socket，直接调用宿主机的 Docker Engine 为每种编程语言创建独立的沙箱容器。每个沙箱容器都有 6 层安全限制：内存、CPU、网络、PID、文件系统、用户权限。"

---

## 1. 为什么需要沙箱？

用户提交的代码可能是恶意的：

```java
// 死循环 → CPU 打满
while (true) {}

// 大内存分配 → 内存耗尽
byte[] bomb = new byte[Integer.MAX_VALUE];

// 读取系统文件 → 信息泄露
Files.readAllBytes(Paths.get("/etc/passwd"));

// 发起网络请求 → 攻击内网
new URL("http://192.168.1.1/admin").openStream();
```

所以必须在**隔离环境**中执行用户代码。

---

## 2. 什么是 DooD（Docker-outside-of-Docker）？

### 两种模式对比

| 模式 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| **DinD** (Docker-in-Docker) | 在容器内安装完整的 Docker Engine | 完全隔离 | 镜像大、性能差、存储层问题 |
| **DooD** (Docker-outside-of-Docker) | 挂载宿主机的 Docker Socket，容器内调用宿主机的 Docker | 轻量、性能好 | 需要挂载 socket 文件 |

### 本项目选择 DooD

```
后端容器（Spring Boot）
    │
    │  调用 Runtime.exec("docker run ...")
    │
    ▼
宿主机的 Docker Engine
    │
    ├── 创建沙箱容器 1（用户的 C++ 代码）
    ├── 创建沙箱容器 2（用户的 Python 代码）
    └── 创建沙箱容器 3（用户的 Java 代码）
```

**关键配置**（`docker-compose.yml`）：

```yaml
backend:
  volumes:
    - /var/run/docker.sock:/var/run/docker.sock  # 挂载 Docker Socket
    - ./sandbox:/app/sandbox                      # 挂载沙箱 Dockerfile
```

`/var/run/docker.sock` 是 Docker Engine 的 Unix Socket 文件。挂载后，容器内的进程可以通过它与宿主机的 Docker Engine 通信。

---

## 3. 6 层安全隔离详解

### 完整的 Docker 命令

```java
// DockerCodeSandbox.java 核心代码
docker run \
  --memory=256m \          # 第 1 层：内存限制
  --cpus=1 \               # 第 2 层：CPU 限制
  --network=none \         # 第 3 层：禁止网络
  --pids-limit=50 \        # 第 4 层：进程数限制
  --read-only \            # 第 5 层：只读文件系统
  --user=nobody \          # 第 6 层：非 root 用户
  --rm \                   # 执行完自动删除容器
  oj-sandbox-cpp           # 镜像名
```

### 逐层解释

#### 第 1 层：内存限制 `--memory=256m`

```bash
# 容器最多使用 256MB 内存
# 超过会被 OOM Killer 杀掉
--memory=256m
```

**防护场景**：防止 `new byte[Integer.MAX_VALUE]` 这样的大内存分配。

#### 第 2 层：CPU 限制 `--cpus=1`

```bash
# 容器最多使用 1 个 CPU 核心
# 防止死循环占满所有 CPU
--cpus=1
```

**防护场景**：防止 `while (true) {}` 把宿主机 CPU 打满，影响其他服务。

#### 第 3 层：网络隔离 `--network=none`

```bash
# 容器完全断网，无法访问任何网络
--network=none
```

**防护场景**：
- 防止用户代码发起 HTTP 请求攻击内网
- 防止反弹 Shell 连接外部服务器
- 防止 DNS 泄露信息

#### 第 4 层：进程数限制 `--pids-limit=50`

```bash
# 容器内最多 50 个进程
--pids-limit=50
```

**防护场景**：防止 Fork 炸弹：

```bash
# Fork 炸弹：无限创建进程，耗尽系统资源
:(){ :|:& };:
```

#### 第 5 层：只读文件系统 `--read-only`

```bash
# 容器的文件系统是只读的
--read-only
```

**防护场景**：
- 防止写入恶意脚本到磁盘
- 防止占用磁盘空间
- 防止修改容器内的系统文件

#### 第 6 层：非 root 用户 `--user=nobody`

```bash
# 以 nobody 用户运行，没有 root 权限
--user=nobody
```

**防护场景**：
- 即使逃逸了容器，也只是 nobody 用户，权限极低
- 无法访问需要 root 权限的系统调用

---

## 4. 沙箱执行流程

```
用户提交代码
    │
    ▼
创建临时目录 /tmp/sandbox_xxx/
    │
    ▼
写入用户代码到 main.cpp / Main.java / solution.py
    │
    ▼
根据语言选择 Dockerfile，构建镜像
    │  docker build -t oj-sandbox-cpp -f Dockerfile.cpp .
    ▼
对每个测试用例，创建容器执行
    │  docker run --memory=256m --cpus=1 ... oj-sandbox-cpp
    │
    ├── 通过 stdin 输入测试数据
    ├── 通过 stdout 读取输出
    ├── 后台线程监控 docker stats 获取内存使用
    └── 超时（10s）则 docker kill 强制终止
    │
    ▼
比对输出与预期结果
    │
    ▼
清理临时目录和镜像（docker image prune）
```

---

## 5. 各语言 Dockerfile 示例

### C++

```dockerfile
# sandbox/Dockerfile.cpp
FROM gcc:latest
WORKDIR /app
COPY main.cpp .
RUN g++ -o main main.cpp -O2
CMD ["./main"]
```

### Java

```dockerfile
# sandbox/Dockerfile.java
FROM openjdk:17-slim
WORKDIR /app
COPY Main.java .
RUN javac Main.java
CMD ["java", "Main"]
```

### Python

```dockerfile
# sandbox/Dockerfile.python
FROM python:3.11-slim
WORKDIR /app
COPY solution.py .
CMD ["python", "solution.py"]
```

---

## 6. 面试常见追问

### Q: 为什么不用 Docker-in-Docker（DinD）？

> DinD 需要在容器内安装完整的 Docker Engine，镜像很大（几百 MB），而且有存储层嵌套问题。DooD 只需要挂载一个 Socket 文件，轻量高效。

### Q: 如果用户代码写入大量数据到 /tmp 怎么办？

> `--read-only` 已经阻止了写入。如果需要临时文件，可以挂载一个 tmpfs 并限制大小：
> `--tmpfs /tmp:size=10m`

### Q: 如何防止用户代码消耗过多磁盘 I/O？

> 目前没有直接限制 I/O 的方案，但 `--read-only` + `--memory=256m` 间接限制了大部分场景。更严格的方案可以用 cgroup v2 的 I/O 限制。

### Q: 为什么每种语言用独立镜像？

> 1. 环境隔离：不同语言的编译器/运行时互不影响
> 2. 镜像复用：构建一次，多次执行
> 3. 安全性：镜像内只有必要的工具，减少攻击面
