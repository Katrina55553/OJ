# 第 18 章 监控指标与 AOP

## 面试怎么答

> "我在项目里加了一个轻量的监控系统。用 Spring AOP 拦截所有 Controller 方法，记录每个接口的调用次数、总耗时、平均耗时、最大耗时。结果存在 ConcurrentHashMap 里（单机部署场景），通过 /monitor/metrics 接口可以查看全部指标。这样可以快速定位哪个接口最慢，做针对性优化。"

---

## 1. 为什么需要监控？

### 没有监控时的痛点

```
用户投诉："系统变慢了"
  ↓
开发查："哪里慢？"
  ↓
查日志：在海量日志里找耗时信息...
  ↓
花 1 小时找到：某接口每次调用耗时 3 秒
```

有了监控指标，直接可以看到：

| 接口 | 调用次数 | 平均耗时 | 最大耗时 |
|------|---------|---------|---------|
| QuestionController.getQuestionVOPage | 1523 次 | 480ms | 2.1s |
| QuestionSubmitController.doQuestionSubmit | 328 次 | 12.5s | 35.2s |
| UserController.userLogin | 1205 次 | 85ms | 250ms |

**一眼看出**：判题提交接口有严重性能问题，需要优化。

---

## 2. 核心实现

### MonitorService — 指标采集

```java
@Service
public class MonitorServiceImpl implements MonitorService {

    // 指标数据结构：{ metricName → { count, total, max, min } }
    private final ConcurrentHashMap<String, MetricStats> metrics = new ConcurrentHashMap<>();

    @Override
    public void recordCost(String name, long costMs) {
        metrics.computeIfAbsent(name, k -> new MetricStats()).record(costMs);
    }

    @Override
    public Map<String, Map<String, Object>> getAllMetrics() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map.Entry<String, MetricStats> entry : metrics.entrySet()) {
            result.put(entry.getKey(), entry.getValue().snapshot());
        }
        return result;
    }

    private static class MetricStats {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong total = new AtomicLong(0);
        private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);

        void record(long costMs) {
            count.incrementAndGet();
            total.addAndGet(costMs);
            // 更新最大/最小（线程安全的 compareAndSet 循环）
            long curMax = max.get();
            while (costMs > curMax) {
                if (max.compareAndSet(curMax, costMs)) break;
                curMax = max.get();
            }
            long curMin = min.get();
            while (costMs < curMin) {
                if (min.compareAndSet(curMin, costMs)) break;
                curMin = min.get();
            }
        }

        Map<String, Object> snapshot() {
            long c = count.get();
            Map<String, Object> map = new HashMap<>();
            map.put("count", c);
            map.put("total", total.get());
            map.put("avg", c > 0 ? (double) total.get() / c : 0.0);
            map.put("max", max.get() == Long.MIN_VALUE ? 0 : max.get());
            map.put("min", min.get() == Long.MAX_VALUE ? 0 : min.get());
            return map;
        }
    }
}
```

**关键设计**：
- `ConcurrentHashMap` 保证并发安全
- `AtomicLong` 保证原子操作，避免加锁
- `computeIfAbsent` 原子性创建新指标，无需双重检查

### MonitorInterceptor — AOP 拦截

```java
@Aspect
@Component
public class MonitorInterceptor {

    @Resource
    private MonitorService monitorService;

    @Around("execution(* com.oj.controller.*.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();

        // 指标名：类名 + 方法名
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String metricName = className + "." + methodName;

        Object result;
        try {
            result = joinPoint.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            monitorService.recordCost(metricName, cost);
        }
        return result;
    }
}
```

**为什么用 `@Around`**：可以在方法执行前后都插入逻辑（记开始时间 → 执行 → 算耗时）。

### MonitorController — 查看指标

```java
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    @Resource
    private MonitorService monitorService;

    @GetMapping("/metrics")
    public BaseResponse<Map<String, Map<String, Object>>> getMetrics() {
        return ResultUtils.success(monitorService.getAllMetrics());
    }

    @GetMapping("/reset")
    public BaseResponse<String> resetMetrics() {
        monitorService.resetMetrics();
        return ResultUtils.success("ok");
    }
}
```

---

## 3. 使用方式

### 启动 Spring 的定时任务支持

确保 `@EnableScheduling` 在主类：

```java
@SpringBootApplication
@EnableScheduling
public class MainApplication {
    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }
}
```

### 查看接口指标

访问 `http://localhost:8101/api/monitor/metrics`，返回：

```json
{
  "code": 0,
  "data": {
    "QuestionController.getQuestionVOPage": {
      "count": 1523,
      "total": 731040,
      "avg": 480.0,
      "max": 2100,
      "min": 45
    },
    "QuestionSubmitController.doQuestionSubmit": {
      "count": 328,
      "total": 4100000,
      "avg": 12500.0,
      "max": 35200,
      "min": 8000
    }
  }
}
```

### 重置指标（测试用）

访问 `http://localhost:8101/api/monitor/reset`，清空所有指标。

---

## 4. 本项目的扩展

### Docker 容器定期清理任务

```java
@Slf4j
@Component
public class DockerCleanupTask {

    // 每小时：清理已停止的容器
    @Scheduled(fixedRate = 3600000)
    public void cleanupContainers() {
        ProcessBuilder pb = new ProcessBuilder("docker", "container", "prune", "-f");
        Process process = pb.start();
        ...
    }

    // 每6小时：清理悬空镜像
    @Scheduled(fixedRate = 21600000)
    public void cleanupImages() {
        ProcessBuilder pb = new ProcessBuilder("docker", "image", "prune", "-f");
        ...
    }

    // 每天：清理 oj-sandbox-* 临时镜像（保留 oj-base-* 基础镜像）
    @Scheduled(fixedRate = 86400000)
    public void cleanupSandboxImages() {
        ...
    }
}
```

### JWT 过期快速判断

```java
public boolean isTokenExpired(String token) {
    // 先快速检查 exp 字段（不做完整 JWT 解析）
    String[] parts = token.split("\\.");
    String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

    int expIndex = payload.indexOf("\"exp\"");
    if (expIndex == -1) return true;

    int colonIndex = payload.indexOf(":", expIndex);
    int endIndex = payload.indexOf(",", expIndex);
    String expStr = payload.substring(colonIndex + 1, endIndex > 0 ? endIndex : payload.indexOf("}")).trim();
    long expTime = Long.parseLong(expStr);

    return System.currentTimeMillis() > expTime * 1000;
}
```

**好处**：JWT 黑名单检查在 Redis 中查询有网络开销。先快速判断 token 是否已过期，过期的 token 直接拒绝，无需查 Redis。

---

## 5. 面试常见追问

### Q: 为什么不用成熟的监控方案（Prometheus + Grafana）？

> 本项目是单机部署的小型项目，用 ConcurrentHashMap 足够。如果将来部署多实例或需要更专业的监控，可以无缝升级为 Prometheus + Micrometer（Spring Boot 2.7 已内置支持）。

### Q: ConcurrentHashMap 在高并发下会有性能问题吗？

> 不会。ConcurrentHashMap 采用分段锁（Java 8 是 CAS + synchronized），并发写入的性能很好。监控指标的写入频率一般远低于业务接口，完全够用。

### Q: 监控数据只在内存里，重启后丢失？

> 是的，当前设计就是这样。监控数据的意义在于"观测当前系统状态"，历史数据可以持久化到数据库或 Prometheus。本项目的轻量设计只关注"此刻哪些接口慢"。

### Q: AOP 切面的顺序和其他切面（如 AuthCheck）冲突吗？

> Spring AOP 的默认顺序是未定义的。可以用 `@Order` 注解明确指定顺序。监控应该放在最外层（最先进入、最后退出），这样可以记录完整的耗时（包括其他切面的开销）。

### Q: 为什么不用 `System.nanoTime()` 记录耗时？

> `System.currentTimeMillis()` 足够精确到毫秒级别，而且成本更低。`nanoTime` 主要用于测量非常短的代码块执行时间。接口级别的监控用毫秒足够。

### Q: 监控接口本身会不会被监控？

> 会的。`MonitorController.getMetrics` 也在 `com.oj.controller.*` 范围内，会被 AOP 记录。这样反而可以看到监控接口自身的性能。如果不想记录，可以把切入点缩小到 `Question*Controller` 或在方法上加 `@MonitorIgnore` 注解排除。
