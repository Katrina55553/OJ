# OJ 项目问题记录

## 判题系统

### 1. RabbitMQ 消费者无法反序列化消息
- **现象**：提交代码后状态永远停留在 `WAITING`
- **根因**：`RabbitMQConfig` 只给 `RabbitTemplate` 配置了 `Jackson2JsonMessageConverter`，但 `@RabbitListener` 消费者容器默认用 `SimpleMessageConverter`，无法将 JSON 反序列化为 `Long`
- **修复**：新增 `SimpleRabbitListenerContainerFactory` Bean，注册 `Jackson2JsonMessageConverter` + `MANUAL` ACK

### 2. DockerCodeSandbox 内存使用量硬编码
- **现象**：判题结果 `memory` 永远返回 50MB
- **根因**：`getContainerMemoryUsage()` 返回硬编码 `50 * 1024 * 1024`
- **修复**：移除 `--rm`，改用唯一 `--name` 的容器；后台线程每 500ms 通过 `docker stats --no-stream` 采集实时内存峰值；执行完 `docker rm` 清理

### 3. 判题异常时 JSON 拼接不安全
- **现象**：错误消息中包含引号或特殊字符时，`judgeInfo` 成为非法 JSON
- **根因**：`JudgeServiceImpl` 异常处理中手写 `"{\"message\":\"" + e.getMessage() + "\"}"`
- **修复**：改用 `JudgeInfo` + `JSONUtil.toJsonStr()` 构建合法 JSON

### 4. CodeSandboxFactory 绕过 Spring DI
- **现象**：`new DockerCodeSandbox()` 创建的实例中 `@Value` 注解全部失效
- **根因**：工厂方法用 `new` 创建实例，不受 Spring 管理
- **修复**：工厂改为 `@Component`，通过 `@Resource` 注入所有沙箱实现

### 5. `process.waitFor()` 返回类型错误
- **现象**：`docker compose build backend` 编译失败
- **错误**：`incompatible types: boolean cannot be converted to int`
- **根因**：`Process.waitFor(long, TimeUnit)` 返回 `boolean`，不是 `int`
- **修复**：改为 `boolean finished = process.waitFor(5, TimeUnit.SECONDS)`

### 6. 三个沙箱实现类缺少 `@Component`
- **现象**：后端启动失败，`NoSuchBeanDefinitionException: No qualifying bean of type 'RemoteCodeSandbox'`
- **根因**：`CodeSandboxFactory` 通过 `@Resource` 注入，但 `RemoteCodeSandbox`、`ThirdPartyCodeSandbox`、`ExampleCodeSandbox` 没有 `@Component`
- **修复**：给三个实现类添加 `@Component` 注解

### 7. questionId 为 null 时 NPE
- **现象**：提交空 body `{}` 时报 500 而非友好参数错误
- **根因**：`questionSubmitAddRequest.getQuestionId() <= 0` 中 `null <= 0` 触发自动拆箱 NPE
- **修复**：拆分为 `questionId == null || questionId <= 0` 显式判断

---

## 代码执行（运行按钮）

### 8. 前端 /api/code/execute 返回 404
- **现象**："运行代码" 按钮报 404
- **根因**：前端 `QuestionDetailsView.vue` 的 `handleRun` 用 axios 直连硬编码的 JDoodle API，但后端没有对应端点
- **修复**：新建 `CodeExecuteController`（`/api/code/execute`），接收 `{ script, language, stdin }`，调用 `DockerCodeSandbox` 执行并返回结果

---

## AI Chat 整合

### 9. Ollama 模型内存不足被杀
- **现象**：`"Ollama HTTP 500: llama-server process has terminated: signal: killed"`
- **根因**：`deepseek-r1:7b` 需要约 8GB 内存，Docker 限制只有 4G
- **修复**：换用轻量模型（推荐 `qwen2.5:3b`，约 3GB）

### 10. AI SSE 流被全局异常处理器拦截
- **现象**：`No converter for [class BaseResponse] with preset Content-Type 'text/event-stream'`
- **根因**：`SseEmitter` 返回时异常被 `@RestControllerAdvice` 捕获后试图返回 `BaseResponse`，与 `text/event-stream` Content-Type 冲突
- **修复**：改用 `HttpServletResponse.getOutputStream()` 直接写 SSE 数据，绕过返回值包装

### 11. EventSource 2 秒超时断开
- **现象**：AI 对话开始后立即显示 "🔌 与 AI 服务断开连接"
- **根因**：Ollama 首次 token 响应需要 2-5 秒，`EventSource` 默认 2 秒无数据就断开重连
- **修复**：
  1. 连接建立后立即发送 SSE 注释 `:ok\n\n`（心跳）
  2. 添加 `X-Accel-Buffering: no` 禁用 nginx 缓冲
  3. 去掉方法上的 `throws Exception`，所有异常内部 try-catch 处理

### 12. ai_message 表不存在
- **现象**：`SQLSyntaxErrorException: Table 'yuoj.ai_message' doesn't exist`
- **根因**：已存在的数据库没有运行最新 DDL
- **修复**：手动 `CREATE TABLE IF NOT EXISTS ai_message`

### 13. AI 异常信息为空
- **现象**：前端显示 "🔌 AI 服务异常:" 后面没有内容
- **根因**：`e.getMessage()` 对 `ConnectException` 等返回 null
- **修复**：
  1. Ollama HTTP 非 200 时读取 `getErrorStream()` 获得真实错误
  2. `e.getMessage()` 为空时兜底用 `e.toString()`

---

## 数据统计整合

### 14. 统计接口路径重复 /api
- **现象**：`/api/statistics/language-distribution` 返回 404
- **根因**：Controller 路径写了 `/api/statistics/...`，但 context-path 已追加 `/api`，实际路径变为 `/api/api/statistics/...`
- **修复**：去掉所有 Controller 上的 `/api` 前缀

### 15. 热力图硬编码 user_id=1
- **现象**：热力图不显示当前用户的数据
- **根因**：`<QuestionHeatmap />` 没传 `userId` prop，默认 fallback 为 1
- **修复**：从 Vuex store 获取当前登录用户 ID，传 `:userId="loginUserId"`

---

## 路由与认证

### 16. 刷新页面跳转 /noAuth
- **现象**：在任何页面按 F5 刷新都跳转到无权访问页面
- **根因**：Vuex 初始 state `loginUser: { userRole: "notLogin" }` 是 truthy 值，router guard 的 `!loginUser.userRole` 判断为 false，不再调用 `getLoginUser` 恢复登录态。后续权限校验 `notLogin` 不匹配 `USER/ADMIN`，跳到 `/noAuth`
- **修复**：初始 loginUser 改为 `null`，使 guard 正确触发 `getLoginUser`

---

## 前端格式

### 17. Prettier 格式问题反复出现
- **现象**：`docker compose build frontend` 因 ESLint/Prettier error 失败
- **原因**：多行模板字符串、参数换行等不符合项目 Prettier 规则
- **修复**：手动缩成单行，之后写代码注意 Prettier 约束

---

## Git

### 18. 提交消息出现多余 `@` 前缀
- **现象**：commit message 首行是 `@`，第二行才是主题
- **原因**：PowerShell here-string `@'...'@` 的 `@` 被写入了提交消息
- **修复**：`git filter-branch --msg-filter "sed '/^@$/d'"` + `git push --force-with-lease`
