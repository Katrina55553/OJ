# RabbitMQ 异步判题系统 — 手动确认 + 死信队列

## 面试怎么答

> "判题流程使用 RabbitMQ 做异步解耦。用户提交后立即返回，判题消息发送到队列，消费者异步执行。使用手动 ACK 确保消息不丢失，配合死信队列处理失败消息，实现重试和兜底。"

---

## 1. 为什么用异步？

### 同步 vs 异步

```
同步模式（问题很多）：
  用户提交代码 → 等待 10 秒判题 → 返回结果
  问题：
    - 用户体验差（白等 10 秒）
    - HTTP 连接可能超时
    - 100 个用户同时提交 → 100 个线程阻塞 → 服务器扛不住

异步模式（本项目）：
  用户提交代码 → 立即返回"已提交"→ MQ 缓冲 → 后台慢慢判
  优势：
    - 用户体验好（秒回）
    - 削峰填谷（高峰期消息排队，不会打垮服务器）
    - 失败可重试（消息不会丢）
```

---

## 2. 整体架构

```
用户点击"提交"
    │
    ▼
QuestionController.doQuestionSubmit()
    │
    ▼
QuestionSubmitServiceImpl
    ├── 1. 参数校验（题目是否存在、语言是否支持）
    ├── 2. 入库（status = WAITING）
    └── 3. 发送消息到 RabbitMQ
         │
         ▼
    ┌─────────────────────────┐
    │   RabbitMQ              │
    │   Queue: oj.judge.queue │
    │   手动 ACK，prefetch=1  │
    └─────────────────────────┘
         │
         ▼
    JudgeMessageConsumer（消费者）
         │
         ▼
    JudgeServiceImpl.doJudge()
         ├── 更新 status = RUNNING
         ├── 调用 DockerCodeSandbox 执行
         ├── 比对结果
         └── 更新 status = SUCCEED / FAILED
```

---

## 3. 消息发送（生产者）

```java
@Component
public class JudgeMessageProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送判题消息
     * @param questionSubmitId 提交记录 ID
     */
    public void sendJudgeMessage(long questionSubmitId) {
        rabbitTemplate.convertAndSend(
            "oj.judge.queue",           // 队列名
            String.valueOf(questionSubmitId)  // 消息内容（提交 ID）
        );
    }
}
```

**关键点**：只发送提交 ID，不发送代码。消费者拿到 ID 后去数据库查完整信息。

---

## 4. 消息消费（消费者）— 手动 ACK

```java
@Component
public class JudgeMessageConsumer {

    @Resource
    private JudgeService judgeService;

    @RabbitListener(queues = "oj.judge.queue")
    public void onMessage(Message message, Channel channel) throws IOException {
        // 获取消息
        String body = new String(message.getBody());
        long questionSubmitId = Long.parseLong(body);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            // 1. 执行判题
            judgeService.doJudge(questionSubmitId);

            // 2. 判题成功，手动确认
            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            // 3. 判题失败，拒绝消息，不重新入队
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
```

### 为什么用手动 ACK？

| ACK 模式 | 行为 | 风险 |
|----------|------|------|
| **自动 ACK** | 消费者收到消息就确认 | 如果消费者崩溃，消息丢失 |
| **手动 ACK** | 消费者处理完才确认 | 消费者崩溃，消息回到队列，重新投递 |

**场景**：消费者收到消息，开始判题，执行到一半崩溃了。
- 自动 ACK：消息已确认，丢失了，这个提交永远没有结果
- 手动 ACK：消息没确认，回到队列，其他消费者重新处理

### prefetch=1 是什么？

```yaml
# application.yml
spring:
  rabbitmq:
    listener:
      simple:
        prefetch: 1  # 每次只拿 1 条消息
```

**作用**：消费者一次只处理 1 条消息，处理完才拿下一条。

**为什么需要**：判题是 CPU 密集型操作。如果一个消费者同时处理 10 条消息，10 个 Docker 容器同时运行，CPU 直接打满。

---

## 5. 死信队列（DLQ）— 失败兜底

### 什么是死信队列？

```
正常队列：oj.judge.queue
    │
    │ 消息被 reject / 超时 / 队列满了
    ▼
死信队列：oj.judge.queue.dlq
    │
    ▼
死信消费者：记录日志、告警、人工处理
```

### 配置

```java
@Configuration
public class RabbitMQConfig {

    // 正常队列
    @Bean
    public Queue judgeQueue() {
        return QueueBuilder.durable("oj.judge.queue")
            .withArgument("x-dead-letter-exchange", "")           // 死信交换机（默认）
            .withArgument("x-dead-letter-routing-key", "oj.judge.queue.dlq")  // 死信路由
            .withArgument("x-message-ttl", 30000)                 // 消息 TTL：30 秒
            .build();
    }

    // 死信队列
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable("oj.judge.queue.dlq").build();
    }
}
```

### 消息什么时候进入死信队列？

| 触发条件 | 说明 |
|----------|------|
| `basicNack` + `requeue=false` | 消费者拒绝消息，不重新入队 |
| 消息 TTL 过期 | 消息在队列中超过 30 秒没被消费 |
| 队列满了 | 队列达到最大长度 |

### 死信消费者

```java
@RabbitListener(queues = "oj.judge.queue.dlq")
public void onDeadLetter(Message message, Channel channel) throws IOException {
    String body = new String(message.getBody());
    log.error("判题消息进入死信队列: {}", body);

    // 更新状态为失败
    long questionSubmitId = Long.parseLong(body);
    // 更新数据库状态...

    channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
}
```

---

## 6. 完整消息生命周期

```
1. 用户提交 → 发送消息到 oj.judge.queue
2. 消费者收到消息 → 手动 ACK → 判题成功 → 结束
3. 消费者收到消息 → 判题异常 → basicNack(requeue=false) → 进入死信队列
4. 死信消费者记录日志 → 人工排查
```

---

## 7. 面试常见追问

### Q: 为什么不直接用同步判题？

> 判题需要执行用户代码，耗时 1-10 秒。同步模式下 HTTP 请求会阻塞，用户体验差，且高并发时会耗尽服务器线程池。异步模式让请求立即返回，判题在后台异步执行。

### Q: 消息丢失了怎么办？

> 三个环节保障：
> 1. **生产者**：`rabbitTemplate.convertAndSend` 默认是确认模式，消息到达交换机会回调
> 2. **队列**：队列和消息都持久化（`durable=true`），RabbitMQ 重启不丢
> 3. **消费者**：手动 ACK，消费者崩溃消息回到队列

### Q: 为什么用 prefetch=1？

> 判题是 CPU 密集型，同时运行多个沙箱容器会打满 CPU。prefetch=1 保证同一时间一个消费者只处理一个判题任务，资源可控。

### Q: 死信队列有什么用？

> 当消息处理失败（异常、超时）时，消息进入死信队列而不是丢失。可以记录日志、告警、人工处理，实现"异常兜底"。
