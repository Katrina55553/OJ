# OJ 项目面试问答

---

## 问题 1：6 层隔离具体怎么实现？为什么选 DooD 而不是 DinD？

### 回答

我们项目里每次执行用户代码，都会通过 `docker run` 起一个一次性容器，附带 6 个限制参数：

```bash
docker run \
  --memory=256m \       # 内存
  --cpus=1 \            # CPU
  --network=none \      # 网络
  --pids-limit=50 \     # 进程数
  --read-only \         # 文件系统
  --user=nobody \       # 用户
  -v workDir:/code:ro \
  oj-base-cpp
```

**6 层防护一句话过一遍**：

| 层 | 参数 | 防什么 |
|---|------|--------|
| 内存 | `--memory=256m` | 防 `int a[1e9]` 把宿主机吃爆 |
| CPU | `--cpus=1` | 防死循环占满核 |
| 网络 | `--network=none` | 防 `curl` 外泄答案、攻击内网 |
| PID | `--pids-limit=50` | 防 fork 炸弹 |
| 文件系统 | `--read-only` + `:ro` 挂载 | 防写恶意文件、`rm -rf /` |
| 用户 | `--user=nobody` | 防权限提升 |

除了这 6 层 cgroup 隔离，Java 侧还有 `process.waitFor(10s)` 超时兜底，超时直接 `destroyForcibly` + `docker rm -f`，标记 TLE。内存采集是起个守护线程每 500ms 调一次 `docker stats` 取峰值。

---

**为什么选 DooD 而不是 DinD**：

两者核心区别就一句话——**DinD 在容器里再跑一个 docker daemon，DooD 是后端容器挂载宿主机的 `/var/run/docker.sock`，调宿主机 daemon 创建兄弟容器**。

我选 DooD 有三个理由：

1. **安全性**：DinD 必须用 `--privileged` 特权模式，否则内层 dockerd 起不来。特权模式等于放弃 Docker 所有隔离——对一个专门跑不可信代码的 OJ 来说是底线问题。DooD 后端容器本身可以非特权，只是借用 socket。

2. **资源利用率**：DinD 每个判题实例都要常驻一个 dockerd，吃内存。我 VPS 只有 2GB，DooD 整个宿主机就一个 daemon，沙箱镜像也是构建一次所有容器共享。

3. **架构简洁**：不用维护嵌套的镜像缓存、containerd、存储驱动。DooD 只有一个 daemon，没有 overlay-in-overlay 的坑。

---

**DooD 的代价**（这个一定要主动提，体现你踩过坑）：

最大的坑是**路径视角问题**。后端容器通过 socket 调宿主机 docker，但宿主机 daemon 不认容器内路径。我在容器里创建 `/app/sandbox/tmp/xxx`，宿主机 daemon 看不见——`docker run -v` 会挂载一个空目录，沙箱里找不到用户代码。

解决办法：docker-compose 挂载共享卷 `./oj-backend/sandbox:/app/sandbox`，代码里又配了一个 `hostWorkDirBase`，`docker run -v` 时把容器内路径换算成宿主机绝对路径再传给 daemon。

第二个坑是**权限**。沙箱以 `--user=nobody` 跑，但工作目录是后端容器 root 创建的，nobody 读不了。所以代码里显式 `setPosixFilePermissions` 把 workDir 设 755、代码文件设 644，编译命令末尾还得 `chmod 755 solution` 让产物对 nobody 可执行。

---

**一句话收尾**：

> 6 层隔离是 cgroup + namespace 的纵深防御，每层防一类攻击；选 DooD 核心是为了避免特权模式——OJ 跑不可信代码，特权模式是底线。代价是路径和权限要小心处理，我用共享卷 + 宿主机路径换算解决了。生产级可以再上 gVisor/Kata 做更强隔离，当前 DooD + 6 层是 2GB VPS 上的折中。

---

### 追问：cgroup v1 和 v2 有什么区别？

**回答**：

| 维度 | cgroup v1 | cgroup v2 |
|---|---|---|
| **目录结构** | 每个资源独立目录树：`/sys/fs/cgroup/cpu`、`/sys/fs/cgroup/memory`、`/sys/fs/cgroup/pids` 各一棵 | 统一目录树 `/sys/fs/cgroup`，所有资源在同一个层级下管理 |
| **层级关系** | 不同资源可以挂不同的层级，进程能混入不同层级，管理混乱 | 单一层级，一个进程只属于一个 cgroup，所有资源在该 cgroup 下统一配置 |
| **接口** | 每个控制器独立文件，配置分散 | 统一接口，`cgroup.controllers` 声明启用哪些控制器，配置更聚合 |
| **能力** | 功能够用但设计割裂 | 支持 eBPF 做更细粒度控制，原生支持 PSI（Pressure Stall Information）监控资源压力 |
| **生态** | 老旧系统、老 Docker 版本默认 | Docker 20.10+ / Kubernetes 1.25+ 默认推荐 |

**我项目里的情况**：

我用的 Ubuntu 22.04 + Docker，**默认还是 v1 兼容模式**（Docker 为了向后兼容不强制切 v2）。`docker run --memory=256m --cpus=1` 这些参数在 v1/v2 下都能跑，对上层透明，所以我没特别关注。

**v2 的核心优势（如果被追问）**：

1. **统一层级解决 v1 的"进程跨层级"混乱**——v1 里一个进程可以在 cpu cgroup A、memory cgroup B，管理上容易出错；v2 强制一个进程一个 cgroup，所有资源一起管
2. **eBPF 集成**——v2 可以挂 eBPF 程序做更细粒度的资源控制，比如按 syscall 限流，这是 v1 做不到的
3. **PSI 指标**——原生提供 CPU/内存/IO 的压力指标，比 v1 的"使用量"更接近真实性能瓶颈

**一句话收尾**：

> 我项目用 v1，因为 Docker 默认兼容、参数对上层透明。但 v2 是趋势——统一层级更简洁，支持 eBPF 做细粒度控制，原生 PSI 指标。生产级新部署我会优先用 v2，老系统兼容性考虑才留 v1。

---

## 问题 2：docker.sock 本身就是安全风险，后端被 RCE 怎么办？为什么没选 gVisor/Kata？

### 回答

**先承认——面试官这个点戳到的是真问题，我前一个回答里把两层威胁模型混在一起了**。

---

**第一问：后端怎么触发 docker run？**

老实说，我项目里就是**后端进程直接调 docker CLI**，没有中间服务：

- `docker-compose.yml` 把 `/var/run/docker.sock` 挂进后端容器
- 后端容器装了 `docker.io` 包，CLI 通过 socket 跟宿主机 daemon 通信
- 代码里是 `new ProcessBuilder("docker", "run", ...)` 直接 fork 子进程调 CLI
- 没有 TLS，没有客户端证书校验，没有独立服务

所以面试官说的风险完全成立——**6 层 cgroup 隔离防的是"沙箱内代码"，docker.sock 暴露的是"后端进程本身"的攻击面，这两层威胁模型不一样**：

- 沙箱内：用户提交的不可信代码 → 6 层隔离挡住
- 后端进程：如果被 RCE（Spring 漏洞、Fastjson 之类的依赖 RCE），攻击者拿到的是 docker.sock 控制权，等于宿主机 root

攻击路径很短：

```bash
# 攻击者拿到后端容器的 shell 后，一行命令拿宿主机 root
docker run -v /:/host -it ubuntu chroot /host
```

后端容器是不是 `--privileged` 已经不重要了，因为 docker.sock 本身就是宿主机 root 的等价物。

---

**当前项目的缓解措施（有限的，必须实话实说）**：

1. 后端容器本身非特权——但对 docker.sock 场景意义不大，前面说了
2. 沙箱容器 `--user=nobody` + `--read-only`——防的是沙箱内代码，不防后端进程
3. **没有**做 TLS 证书校验

所以严格说，我项目的安全边界是：**只要后端进程不被 RCE，6 层隔离就够用；一旦后端被 RCE，宿主机就跟着沦陷**。这是我当前架构的真实风险点。

---

**应该怎么改进（这是我现在能想到的方案）**：

1. **把 docker run 的触发收敛到一个最小权限的独立服务**——比如用 Go 写一个小的 daemon，只暴露"提交代码 + 取结果"的 HTTP 接口，内部调 docker。后端通过 HTTP 调它。这样即使后端被 RCE，攻击者拿到的也只是这个 daemon 的业务 API，不能直接调 docker CLI，攻击面从"任意 docker 命令"收窄到"只能触发判题"

2. **Docker daemon 配 TLS + 客户端证书校验**——Docker daemon 支持 `--tlsverify`，只有持证书的客户端才能连。后端调用方持客户端证书，即使 socket 文件被拿到，没证书也连不上

3. **AppArmor/SELinux 策略白名单**——限制后端容器能调用的 docker 子命令，只允许 `run`/`rm`/`stats`，禁止 `exec`/`inspect` 其他容器、禁止挂载宿主机敏感路径

4. **更彻底——走远程 Docker daemon**——把沙箱 daemon 单独跑在另一台机器上，后端通过 TCP+TLS 调用，判题机和业务机物理隔离，宿主机完全不暴露

---

**第二问：为什么没选 gVisor/Kata/firejail？**

这几个方案我都了解，没选有具体原因：

| 方案 | 隔离强度 | 没选的原因 |
|---|---|---|
| **gVisor** | 用户态实现 Linux syscall ABI，每个沙箱独立内核，逃逸难度远高于 cgroup | 对 syscall 兼容性有限制，部分 libc 边缘用法可能跑不了；需要 Docker 配 `--runtime=runsc`，VPS 上要单独装 |
| **Kata Containers** | 每个容器跑在独立 VM（QEMU/KVM），硬件级隔离 | 每个 VM 启动开销几百 ms 到秒级，OJ 判题对延迟敏感；**KVM 需要 VPS 支持嵌套虚拟化，大多数云厂商 VPS 不开放**——这个是硬伤 |
| **firejail** | 基于 seccomp + namespaces + capabilities 的轻量沙箱，不依赖 Docker | 隔离强度比 cgroup+namespace 强不了太多，主要靠 SUID + 内核特性，依赖内核版本，配置复杂 |

**最终没选的核心原因**：

1. **VPS 资源限制**——2GB 内存 + 单核，gVisor/Kata 都会显著增加开销
2. **KVM 不可用**——VPS 不开放嵌套虚拟化，Kata 直接装不了
3. **当前威胁模型**——我面对的是普通 OJ 题目代码，不是 APT 攻击，6 层 cgroup + 超时 + nobody 已经能挡住 99% 的常见攻击
4. **项目定位**——目的是练手 Docker + cgroup 隔离，gVisor 配置偏运维向，工程收益和成本不匹配

---

**如果做生产级 OJ，我会怎么选**：

首选 **gVisor** 作为 Docker 的 runtime——开销比 Kata 小（没有 VM 启动成本），隔离强度比纯 cgroup 强很多，对 OJ 这种"代码不可信但不需要硬件级隔离"的场景最合适。

更彻底的话选 **Firecracker**（AWS 的 microVM）——每沙箱独立 VM，启动时间 100ms 级，专门为 serverless/不可信代码设计，但运维复杂度高，得自己写编排层。

---

**一句话收尾**：

> 我之前的回答确实有漏洞——6 层隔离只防沙箱内代码，没防后端进程被 RCE 后通过 docker.sock 拿宿主机 root。当前架构的真实边界是"后端不被 RCE 就够用，被 RCE 就全完"。改进方向是把 docker 调用收敛到独立服务 + TLS 证书校验，生产级再上 gVisor。这个坑我记下了。

---

## 问题 3：异步判题的消息流转链路？消费失败重试还是进死信？毒消息怎么防？

### 回答

**先画清楚整条链路（happy path）**：

```
用户点"提交"
   │
   ▼
QuestionController.doQuestionSubmit
   │  1. 校验题目/语言
   │  2. 提交记录入库，status = WAITING(0)
   │  3. 立即返回 submitId（接口毫秒级响应）
   │
   ▼
JudgeMessageProducer.sendJudgeMessage(submitId)
   │  rabbitTemplate.convertAndSend
   │  exchange: oj.judge.exchange (Direct)
   │  routing key: oj.judge
   │  消息体: 只有 submitId（Long）
   │  PERSISTENT 持久化
   ▼
┌──────────────────────────────────┐
│  oj.judge.queue                  │  ← 主队列
│  绑定 DLX: oj.judge.dlx          │  ← 配了死信交换机
│  死信 routing key: oj.judge.dlq  │
│  prefetch: 1                     │  ← 串行消费，避免压垮沙箱
│  acknowledge-mode: manual        │
└──────────────────────────────────┘
   │
   ▼
JudgeMessageConsumer.handleJudgeMessage
   │  judgeService.doJudge(submitId)
   │     ├── 状态改 RUNNING(1)
   │     ├── 调 DockerCodeSandbox 跑代码
   │     ├── JudgeStrategy 比对结果
   │     └── 状态改 SUCCEED(2) / FAILED(3)
   │
   ▼
channel.basicAck   ← 成功才 ACK
```

**两个交换机、两个队列**：

| 组件 | 名字 | 作用 |
|---|---|---|
| 主交换机 | `oj.judge.exchange` (Direct) | 路由提交消息 |
| 主队列 | `oj.judge.queue` | 持久化判题消息，绑了 DLX |
| 死信交换机 | `oj.judge.dlx` (Direct) | 接收被 nack 的消息 |
| 死信队列 | `oj.judge.queue.dlq` | 兜底处理失败消息 |

主队列建队列时通过 `x-dead-letter-exchange` + `x-dead-letter-routing-key` 绑定 DLX，所以消息被 nack(requeue=false) 时会自动路由到死信队列。

---

**异常路径（重点讲）**：

判题过程中任何异常——沙箱崩了、Docker 超时、代码编译错误、数据库更新失败——都会走这条路径：

```
judgeService.doJudge 抛异常
   │
   ▼
Spring retry 本地重试（application.yml 配的）
   │  max-attempts: 3
   │  initial-interval: 3000ms
   │  同一消费者线程内重试 2 次，每次间隔 3 秒
   │
   ▼ 重试耗尽仍失败
channel.basicNack(deliveryTag, false, /*requeue=*/false)
   │  ↑ requeue=false 是关键！不回主队列
   ▼
死信交换机 oj.judge.dlx 路由
   │
   ▼
死信队列 oj.judge.queue.dlq
   │
   ▼
DeadLetterConsumer.handleDeadLetter
   │  1. 把提交状态更新为 FAILED(3)
   │  2. judgeInfo 写入 {"message":"判题异常，请稍后重试","time":0,"memory":0}
   │  3. basicAck
   │
   ▼
用户前端轮询看到"判题失败"，而不是永远卡在"判题中"
```

---

**毒消息防护——这是面试官真正想听的**：

防毒消息无限重试的核心就一句话：**requeue=false**。

如果 nack 时 `requeue=true`，消息会回到主队列头部，又被同一个消费者捞出来，又失败，又回队列——无限循环，队列被一条毒消息堵死。

我的设计是三道防线：

1. **本地重试有上限**：Spring retry 最多 3 次，间隔 3 秒。这 3 次是在消费者内存里重试，不重新入队，不占用队列位置
2. **重试耗尽直接 nack(requeue=false)**：消息进死信队列，不会再回主队列
3. **死信队列消费失败也丢弃**：死信消费者 catch 块里也是 `basicNack(requeue=false)`，不会回到死信队列头部，避免死信队列自己也被毒消息堵死

所以一条毒消息的完整生命周期是：**主队列 → 本地重试 3 次 → 死信队列 → 处理或丢弃**，最多占用消费者 9 秒（3次×3秒）+ 一次死信处理，不会无限循环。

---

**死信队列到底干嘛用？为什么不直接丢弃？**

这是个设计取舍。如果只是为了防毒消息，直接丢弃也行。但死信队列在我这里有三个实际作用：

1. **状态兜底**：判题异常时主流程可能没来得及更新状态，记录会卡在 RUNNING。死信消费者把它改成 FAILED，保证数据最终一致——用户不会永远看到"判题中"
2. **可观测**：死信队列里的消息就是异常判题清单，可以接告警、做报表
3. **人工兜底**：极端情况下可以人工捞死信队列重投

---

**主动提局限（面试加分点）**：

这套设计有几个我能想到的坑：

1. **本地重试会阻塞消费者 9 秒**：retry 3 次每次间隔 3 秒，期间这个消费者线程被占住，prefetch=1 意味着这 9 秒内不消费新消息。如果短时间内大量毒消息，队列会积压。改进方案是把重试改成**延迟队列**——nack 后进延迟队列，N 秒后重新投递，不阻塞消费者

2. **死信队列失败直接丢弃会丢消息**：死信消费者如果连数据库都连不上，消息就真没了。生产级应该配"死信队列的死信队列"，或者落库做人工兜底

3. **没有用 TTL 触发死信**：死信队列注释里写了"本项目未配置 TTL"，只通过 nack 触发。如果消费者进程直接挂了（OOM、kill -9），消息会卡在 unacked 状态，要等 RabbitMQ 的 connection 断开才会重新投递。这个间隔取决于心跳超时，可能几十秒。改进方案是配 `x-message-ttl`，超时自动进死信

4. **retry 和 manual ACK 混用的语义微妙**：开了 `spring.rabbitmq.listener.simple.retry` 后，重试耗尽 Spring 会自动 nack，和代码里手动 nack 的行为有重叠。其实更干净的做法是二选一——要么纯手动控制重试，要么纯靠 Spring retry。我项目里两个都开了，是历史遗留

---

**一句话收尾**：

> 主队列 prefetch=1 串行消费 + manual ACK，失败本地重试 3 次仍失败就 nack(requeue=false) 进死信队列，死信消费者把状态兜底改成 FAILED。毒消息防护的核心是 requeue=false——本地重试不回队列，死信队列失败直接丢弃，保证一条毒消息最多占消费者 9 秒。局限是本地重试会阻塞消费者，生产级应该改延迟队列。

---

## 问题 4：Redis + Lua 滑动窗口限流，为什么用 Lua？为什么选滑动窗口？

### 回答

**先讲为什么用 Lua——核心是原子性**。

我的限流逻辑在 Lua 脚本里有四步：

```lua
-- 1. 移除窗口外的旧记录
ZREMRANGEBYSCORE key 0 (now - window)
-- 2. 统计当前窗口请求数
ZCARD key
-- 3. 判断是否超限
if count < limit then
-- 4. 没超限就 ZADD 写入当前请求
ZADD key now member
```

这四步必须**原子执行**，否则有竞态条件。

举个反例：如果不用 Lua，Java 代码里分四次调 Redis：

```
线程 A: ZREMRANGEBYSCORE → ZCARD (count=9) → 准备 ZADD
线程 B: ZREMRANGEBYSCORE → ZCARD (count=9) → 准备 ZADD
线程 A: ZADD (count=10，达到上限)
线程 B: ZADD (count=11，超限了！)
```

两个线程都读到 count=9，都判断"没超限"，都写入了，结果窗口里 11 条记录，限流形同虚设。这就是典型的 check-then-act 竞态。

**解决竞态有三种方案，我对比过**：

| 方案 | 问题 |
|---|---|
| Redis 事务 (MULTI/EXEC) | 不支持中间结果条件分支——我要先读 ZCARD 再判断，事务里读到的结果在 EXEC 前拿不到 |
| 分布式锁 (SETNX) | 能解决问题，但每次限流都要加锁释放锁，串行化太重，QPS 上不去 |
| **Lua 脚本** | Redis 单线程执行 Lua，整个脚本是一个原子操作，没有竞态；无锁，性能好 |

所以 Lua 是唯一既能保证原子性、又能保留条件分支逻辑的方案。Redis 官方也推荐用 Lua 做复杂原子操作。

---

**再讲为什么选滑动窗口，而不是固定窗口或令牌桶**。

三种主流限流算法我都评估过：

| 算法 | 原理 | 致命问题 |
|---|---|---|
| **固定窗口计数器** | 每 60 秒一个计数器，count++ | **边界突刺**——窗口切换瞬间放过 2 倍流量。比如限 60 秒 10 次，用户在 0:59 发 10 次 + 1:00 发 10 次，1 秒内 20 次请求穿过去 |
| **滑动窗口** | ZSET 存每个请求的时间戳，实时统计过去 N 秒内的请求数 | 没有突刺问题，统计精确 |
| **令牌桶** | 按固定速率发令牌，桶有容量上限，允许突发 | 允许突发是特性也是问题——OJ 提交接口不希望任何突发 |

**选滑动窗口的核心原因**：

1. **OJ 场景对边界突刺零容忍**——固定窗口的边界突刺会让用户在 1 秒内提交 20 次代码，对后端和沙箱都是冲击
2. **不需要突发流量**——令牌桶允许突发，但 OJ 提交是用户主动行为，没有"突发友好"的需求
3. **实现简单可控**——ZSET 天然支持按 score 范围操作，`ZREMRANGEBYSCORE` 移除窗口外 + `ZCARD` 统计窗口内，一行命令搞定

---

**ZSET 滑动窗口的实现细节**：

```lua
-- 每个请求存进 ZSET，score 是时间戳，member 是 UUID
ZADD key <now_ms> <uuid>

-- 为什么 member 用 UUID 不用时间戳？
-- 因为 ZSET member 唯一，同一毫秒内的多个请求如果都用时间戳当 member 会被去重
-- UUID 保证每个请求都是独立的 ZSET 元素
```

| ZSET 角色 | 值 |
|---|---|
| score | 请求时间戳（毫秒） |
| member | UUID（保证唯一） |

每次请求 Lua 脚本执行：
1. `ZREMRANGEBYSCORE` 删掉窗口外的旧请求
2. `ZCARD` 数窗口内还剩多少
3. 没超限就 `ZADD` 写入当前请求，超限就返回 -1
4. `PEXPIRE` 续期 key，防止冷 key 永远占内存

---

**主动提局限（加分点）**：

1. **内存占用随请求数线性增长**——ZSET 存每个请求的 UUID，限 60 秒 1000 次的接口，每个 key 占 1000 个 ZSET 节点。高 QPS 接口要小心 Redis 内存。改进方案是**桶折衷**——把窗口切成 N 个小桶，每个桶计数，精度换内存

2. **Redis 宕机限流全失效**——我的代码里 Redis 异常时降级放行（[RateLimitInterceptor.java#L87-L91](file:///d:/Code/OJ/oj-backend/src/main/java/com/oj/aop/RateLimitInterceptor.java#L87-L91)），这是有意的——限流挂了不能把业务也挂了。但代价是 Redis 抖动期间限流失效，可能被刷接口。生产级应该配 Redis 集群 + 本地兜底限流（Guava RateLimiter）

3. **member 用 UUID 有性能开销**——每个请求生成一个 UUID 字符串存进 ZSET，高 QPS 下字符串分配是开销。优化可以用 `now + 随机后缀` 或者直接用 Redis 自增序列

4. **滑动窗口对分布式限流不够友好**——当前所有请求打同一个 Redis，Redis 是单点。要真正分布式限流得用 Redis Cluster，但 ZSET 跨 slot 操作有坑

---

**一句话收尾**：

> 用 Lua 是为了原子性——四步"删旧 → 数当前 → 判断 → 写入"必须在一个 Redis 单线程执行周期内完成，否则 check-then-act 竞态会让限流形同虚设。选滑动窗口是因为 OJ 场景对边界突刺零容忍，固定窗口在窗口切换瞬间能放过 2 倍流量，令牌桶又允许突发，都不合适。ZSET 的 score 天然适合按时间范围统计，配合 Lua 实现简洁。局限是内存随请求数线性增长，高 QPS 接口要换桶计数方案。

---

## 问题 5：策略模式怎么注册和选择？新增语言改哪些代码？为什么选策略模式不选简单工厂？

### 回答

**先说实话——我这个"策略模式"实现得不纯粹，是有水分的**。

看 `JudgeManager.doJudge()` 的真实代码：

```java
JudgeStrategy judgeStrategy = new DefaultJudgeStrategy();
if ("java".equals(language)) {
    judgeStrategy = new JavaLanguageJudgeStrategy();
}
return judgeStrategy.doJudge(judgeContext);
```

注意三个问题：
1. `new` 直接创建实例，没用 Spring 依赖注入
2. `if-else` 还在，并没有真正消除分支
3. `DefaultJudgeStrategy` 加了 `@Component` 但根本没被注入进来（死代码）

所以严格说，我这是**"策略接口 + if-else 选择"的混合实现**，不是教科书式的策略模式。这一点我不藏着，面试官真去看代码也能看出来。

---

**新增一种语言（比如 Go，其实已经支持了）要改的代码**：

| 文件 | 改什么 | 用什么模式 |
|---|---|---|
| `DockerCodeSandbox.getCodeFileName()` | switch 加 case "go" → "solution.go" | switch |
| `DockerCodeSandbox.getCompileCommand()` | switch 加 case "go" → "go build..." | switch |
| `DockerCodeSandbox.getRunCommand()` | switch 加 case "go" → "./solution" | switch |
| `DockerCodeSandbox.needsCompilation()` | switch 加 case "go" → true | switch |
| `JudgeManager.doJudge()` | 如果 Go 需要特殊判题逻辑，加 if 分支 | if-else |
| `sandbox/Dockerfile.base.go` | 新建一个 Dockerfile | 新文件 |

**老实说——加一种语言要改 4-5 个 switch/if 分支，没做到开闭原则**。这是当前实现最大的问题。

---

**正确的策略模式应该怎么写（我重做会这么改）**：

用 Spring DI 自动收集所有策略 Bean，每个策略声明自己支持哪些语言：

```java
public interface JudgeStrategy {
    JudgeInfo doJudge(JudgeContext ctx);
    boolean supports(String language);  // 新增方法
}

@Component
public class DefaultJudgeStrategy implements JudgeStrategy {
    public boolean supports(String language) {
        return true;  // 兜底，所有语言都支持
    }
    // ...
}

@Component
public class JavaLanguageJudgeStrategy implements JudgeStrategy {
    public boolean supports(String language) {
        return "java".equals(language);
    }
    // ...
}
```

`JudgeManager` 注入所有策略，运行时按 language 匹配：

```java
@Service
public class JudgeManager {
    @Resource
    private List<JudgeStrategy> strategies;  // Spring 自动收集所有 Bean

    public JudgeInfo doJudge(JudgeContext ctx) {
        String language = ctx.getQuestionSubmit().getLanguage();
        return strategies.stream()
            .filter(s -> s.supports(language))
            .findFirst()
            .orElseGet(DefaultJudgeStrategy::new)
            .doJudge(ctx);
    }
}
```

这样新增一种语言只需要：写一个 `GoLanguageJudgeStrategy` 类，加 `@Component`，**零改动 JudgeManager**——这才是真正的开闭原则。

---

**为什么选策略模式而不是简单工厂？**

老实说，**在当前这个 if-else 实现下，两者效果差不多**——都是"按 language 选实现"。区别在语义和扩展方向：

| 维度 | 简单工厂 | 策略模式 |
|---|---|---|
| 关注点 | **对象创建**——"给我一个 language，我给你一个 strategy 实例" | **算法替换**——"同一件事（判题）有不同做法，运行时切换" |
| 客户端 | 客户端拿到对象后调用，不关心怎么创建 | 客户端持有策略引用，可以运行时替换 |
| 扩展方式 | 加工厂方法分支 | 加新策略类 + 注册 |
| 开闭原则 | 工厂类要改（加分支） | 完全符合，加类即可 |

我选策略模式的核心原因：**判题逻辑是"可替换的算法"**——同样是"比对输出"，Java 要额外处理 JVM 启动时间（多给 10 秒），未来可能还有 Python 的特殊处理、Go 的特殊处理。这是典型的"同一接口、多种实现、运行时选择"场景，策略模式的语义更贴切。

如果只是"按 language 创建不同对象"没有算法替换，简单工厂更合适。但我这里的 if-else 选择 + 算法替换两层都有，策略模式更准确。

---

**一句话收尾**：

> 当前实现其实是"策略接口 + if-else 选择"，没做到真正的开闭原则——加语言要改 JudgeManager 的 if 分支。重做会用 Spring DI 自动收集所有策略 Bean，每个策略声明 `supports(language)`，新增语言只加类不改 Manager。选策略模式不选工厂是因为判题是"可替换算法"场景，策略语义更准。这个不纯粹我承认，是历史实现，记在改进清单里。

---

## 问题 6：JWT 无状态和 Redis 黑名单矛盾吗？ban 角色怎么实时生效？

### 回答

**先承认——这是真矛盾，不是伪命题**。

JWT 的核心卖点是服务端不存 session，靠签名 + 过期时间保证合法性。我加了 Redis 黑名单，等于在"无状态"基础上又引入了"有状态"——服务端要查 Redis 才能确认 token 有效。

**但这个矛盾是必要的妥协**，因为纯 JWT 有三个解决不了的场景：

| 场景 | 纯 JWT 的问题 | 黑名单怎么解决 |
|---|---|---|
| **主动登出** | 用户点登出，token 还没过期（7天），别人捡到还能用 | 登出时把 token 的 jti 写入黑名单，TTL = 剩余有效期 |
| **封禁用户** | 用户被 ban，但 token 还有 7 天有效期，期间还能操作 | （我项目里的实际做法见下面） |
| **Token 泄露** | 用户 token 被盗，无法主动作废 | 管理员把 jti 加黑名单，立即失效 |

**黑名单 vs 白名单的取舍**：

- **白名单**（每次请求查 Redis 确认 token 还有效）：完全有状态，等于退化成 session，违背 JWT 初衷
- **黑名单**（只存被作废的 token）：**最小状态**——只存少数异常 token，正常 token 不入 Redis，绝大多数请求不查 Redis（或者查了但 key 不存在）

我的实现是黑名单，`JwtInterceptor` 每次请求查一次 `blacklist:token:{jti}`，命中率极低（只有登出/封禁的 token 才在黑名单里），Redis 内存开销可控。

---

**黑名单的具体实现细节**：

JWT 里带一个 `jti`（JWT ID，UUID），登出时：

```java
// UserServiceImpl.userLogout()
String tokenId = jwtUtils.getTokenId(token);  // 从 JWT 解析 jti
long remainingMs = jwtUtils.getTokenRemainingMs(token);  // 剩余有效期
if (remainingMs <= 0) return true;  // 已过期，不用加黑名单

// 加入黑名单，TTL = 剩余有效期（到期自动清理）
redisCacheUtils.set("blacklist:token:" + tokenId, "1", remainingMs, MILLISECONDS);

// 同时清除用户缓存
clearUserCache(userId);
```

关键设计：**黑名单 TTL = token 剩余有效期**。token 过期后黑名单条目也自动消失，不会无限堆积。

请求时 `JwtInterceptor` 检查：

```java
String tokenId = jwtUtils.getTokenId(token);
if (tokenId != null && !jwtUtils.isTokenExpired(token)
        && redisCacheUtils.hasKey("blacklist:token:" + tokenId)) {
    // 401 Token 已失效
}
```

注意：只对**未过期**的 token 检查黑名单——过期的 token 直接被 JWT 自身机制拒绝，不需要查 Redis。

---

**ban 角色怎么实时生效——这里有个坑，必须主动讲**：

先说当前实现的真实路径：

```
请求进来
   │
   ▼
JwtInterceptor: 解析 token，userId 和 userRole 存进 Request Attribute
   │  注意：userRole 是从 JWT 里读的，JWT 签发后不可变！
   │  用户被 ban 时，JWT 里的 userRole 还是 "user"
   ▼
AuthInterceptor (@AuthCheck 注解触发):
   │  User loginUser = userService.getLoginUser(request);
   │     ├── 先查 Redis 缓存 user:id:{userId}（TTL 30 分钟）
   │     └── 缓存未命中查 DB
   │  String userRole = loginUser.getUserRole();  ← 从缓存/DB 读，不是从 JWT 读
   │  if (mustRole == ADMIN && !mustRole.equals(userRole)) 拒绝
   ▼
```

**关键点**：`AuthInterceptor` 用的是 `loginUser.getUserRole()`，来自 **Redis 缓存或 DB**，不是 JWT 里的 userRole。所以 ban 能生效——但**不是实时的**：

1. 用户被 ban 时，DB 里 userRole 改成 "ban"
2. 但 Redis 缓存 `user:id:{userId}` 里还是旧的 "user"，TTL 30 分钟
3. 这 30 分钟内 ban 不生效！
4. 缓存过期后，下次请求查 DB，读到 "ban"，ban 才生效

**这是一个真实的延迟问题，最多 30 分钟 ban 才生效**。

而且——**用户的 token 本身没有被加黑名单**，所以 JwtInterceptor 不会拦它，token 在 7 天内一直有效。ban 只能通过 `AuthInterceptor` 的角色检查拦住。

---

**改进方案（这个一定要讲）**：

1. **ban 操作时主动清缓存**：`userService.banUser(userId)` 里调一次 `clearUserCache(userId)`，下次请求强制查 DB，ban 立即生效。这是最小改动

2. **更彻底——ban 时把用户所有 token 加黑名单**：但纯 JWT 拿不到用户所有签发过的 jti，要么维护一个 `user:{userId}:tokens` 的集合存所有 jti，要么用 refresh token 机制。复杂度高

3. **不依赖 JWT 里的 userRole**：JWT 里完全不存 role，每次都从缓存/DB 读。我已经这么做了——role 校验走 `loginUser.getUserRole()`，不走 JWT。这是对的设计

4. **缓存短 TTL + 主动失效**：把用户缓存 TTL 从 30 分钟降到 5 分钟，配合 ban 时清缓存，延迟可接受

---

**一句话收尾**：

> JWT 无状态和 Redis 黑名单确实矛盾，但黑名单是"最小状态"妥协——只存被作废的 token，正常 token 不入 Redis，解决主动登出、token 泄露这些纯 JWT 搞不定的场景。ban 角色我没走黑名单，而是靠 `AuthInterceptor` 从缓存/DB 读 userRole 校验，但有个坑——用户缓存 TTL 30 分钟，ban 后最多 30 分钟才生效。改进方案是 ban 时主动清缓存，下次请求强制查 DB。这个延迟问题我承认是设计缺陷，记在改进清单里。

---

## 问题 7：如果重做一次，最想改的架构决策是什么？

### 回答

**最想改的是——把判题服务从后端单体里拆出来，作为独立的判题微服务**。

不是改某个技术选型，是改**服务的边界划分**。

---

**先说为什么当初做成单体**：

老实说是图快。Spring Boot 一个工程、一个 jar、一个容器、一次部署，CRUD 业务和判题逻辑都在 `oj-backend` 里。初期这样最快，业务代码和判题代码共享同一套 entity/mapper，省了一层 RPC。

但项目做下来发现这个决策的代价越来越大，主要体现在四个方面：

---

**问题 1：安全边界没分开（这是最严重的）**

就是上一轮面试官追问的那个点——后端进程直接持有 `docker.sock`，等于把"跑不可信代码"的攻击面和"业务接口"的攻击面焊在了同一个进程里。

Spring 任意一个依赖出 RCE（Fastjson、Log4j 那种），攻击者直接拿到宿主机 root。如果判题服务是独立的，业务后端被 RCE 也只影响业务库，碰不到 docker.sock。

**这一条就够我推翻重做了**。

---

**问题 2：资源争抢**

后端 JVM 默认吃 256-512MB，沙箱每次 `docker run` 还要 fork 进程、走 socket 通信，编译型语言还要起一个编译容器。这俩挤在同一个容器里：

- 判题高峰期，JVM GC 和 docker fork 抢 CPU，接口响应变慢
- 2GB VPS 上，JVM + Redis 客户端 + docker CLI + 沙箱容器，内存常年吃紧
- 沙箱 OOM 时整个后端容器都可能被牵连（cgroup 粒度没分开）

拆开之后判题服务可以单独限内存、单独扩容，业务接口不受影响。

---

**问题 3：扩展性被锁死**

单体下"加判题机器"等于"加整套后端机器"——MySQL、Redis、RabbitMQ 客户端全都跟着起一份，扩容成本极高。

实际上判题是 CPU/内存密集型，业务接口是 IO 密集型，两者的扩容策略完全不同。判题服务拆出来后，可以单独起 N 个 judge-worker 实例消费同一个 RabbitMQ 队列，业务后端一台就够。这是真正的横向扩展。

---

**问题 4：故障域没隔离**

判题服务出问题——Docker daemon 卡死、沙箱内存泄漏、`docker stats` 子进程僵尸——会拖垮整个后端。我项目里就遇到过 `docker stats` 轮询线程卡住导致 Tomcat 线程池被打满的情况。

拆分后判题服务挂了只是判题暂停，用户还能浏览题目、看历史提交，业务可用性不受影响。

---

**重做的话我会这么设计**：

```
┌──────────────────┐         ┌──────────────────────┐
│  oj-backend      │         │  oj-judge-worker     │
│  (业务后端)      │         │  (判题服务，可多实例)│
│                  │         │                      │
│  - 用户/题目 CRUD│         │  - 消费 RabbitMQ     │
│  - JWT 鉴权      │  MQ 解耦 │  - 调 docker.sock   │
│  - 发判题消息 ───┼────────►│  - 跑沙箱 + 判题    │
│  - 查判题结果    │         │  - 写回数据库        │
│                  │         │                      │
│  无 docker.sock  │         │  无对外 HTTP 端口    │
│  无业务 RCE 面   │         │  内网通信 only       │
└──────────────────┘         └──────────────────────┘
        │                              │
        └────── 共享 MySQL ────────────┘
                  共享 Redis
```

关键点：
- 业务后端**不挂 docker.sock**，彻底切断 RCE → 宿主机 root 的路径
- 判题服务**不暴露 HTTP**，只消费 MQ，攻击面收窄到 MQ 消息本身
- 判题服务可以多实例，靠 MQ 天然负载均衡
- 判题服务所在机器可以单独上 gVisor/Kata，不用拖累业务后端

---

**这个新设计的代价（不能只说好的）**：

1. **多了一个服务要部署运维**——docker-compose 多一个 service，CI/CD 多一条流水线。对我这个练手项目来说，复杂度翻倍
2. **判题服务要直接连 MySQL**——共享数据库是反微服务最佳实践的，理想情况应该走业务后端的 API 写结果。但判题是高频写，走 HTTP 太重，我妥协了
3. **本地开发调试变麻烦**——之前起一个后端就能调判题，现在要起两个服务
4. **事务边界变复杂**——"入库 + 发消息"现在跨服务，要么用本地消息表，要么接受最终一致

所以严格说，**对一个练手项目，单体是对的；对一个要上生产的 OJ，拆分是对的**。我重做会拆，是因为我现在更看重架构的扩展性和安全边界，而不是初期的开发速度。

---

**其他几个想改的（次要，简单带过）**：

1. **判题结果用 WebSocket/SSE 推送，替代前端轮询**——现在前端每 2 秒查一次状态，体验差且浪费请求
2. **多个测试用例并行执行**——现在串行 `docker run` N 次，N 个用例判题时间 = N × 单次时间。可以并发跑完再汇总，判题时间从 N×T 降到 T
3. **沙箱容器池预热**——每次提交都创建销毁容器，启动开销 200-500ms。可以预热一批空闲容器复用
4. **Vuex 直接全量迁 Pinia**——当时为了赶进度保留了 Vuex 4，现在双状态管理库共存是技术债

---

**一句话收尾**：

> 如果只能改一个决策，我把判题服务从后端单体里拆出来。核心收益不是性能也不是扩展性，是**安全边界**——业务后端不再持有 docker.sock，RCE 攻击面和沙箱攻击面物理隔离。代价是多一个服务要运维，但对生产级 OJ 这个代价值得。其他像 WebSocket 推送、用例并行、容器池这些都是优化项，不是架构项，优先级低于拆服务。
