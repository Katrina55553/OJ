# OJ 项目改进与扩展清单

## 一、安全修复（优先级：🔴 紧急）

### 1.1 后端凭据泄露
- [x] `oj-backend/.../application.yml` 中硬编码了 JDoodle API 密钥（`fdc3701b...` / `1deb28aa...`）和 MySQL 密码（`123456`）— 移至环境变量或 `.env` 文件，并加入 `.gitignore`
- [x] 清理 git 历史中的敏感信息（需用 `git filter-repo` 或 BFG）

### 1.2 前端 XSS 风险
- [x] `MdPreview.vue` — `markdown-it` 启用了 `html: true`，用户提交的 Markdown 可注入任意脚本。建议：禁用 `html` 选项，或引入 DOMPurify 对输出做 sanitize
- [x] `LanguageStats.vue` ECharts tooltip 使用 HTML 模板字符串拼接用户输入 — 对 `params.name` 做转义

### 1.3 安全机制缺失
- [x] 后端无 CSRF 防护 — Spring Session 场景下需配置 CSRF Token
- [ ] 前端无 Content Security Policy (CSP) — 在 `vue.config.js` 或 nginx 层添加 CSP 响应头
- [x] `DailyCheckIn.vue.vue` — 签到状态存在 `localStorage`，用户可篡改 — 已移除该组件
- [ ] `QuestionDetailModal.vue` — 普通用户也能看到参考答案 — 后端接口需做权限过滤

---

## 二、代码质量（优先级：🟡 重要）

### 2.1 消除重复代码（前端）
- [x] 提取 `difficultyColor` 函数到 `src/utils/question.ts`（7 个文件重复）
- [x] 提取 `allTags` 常量到 `src/constants/question.ts`（3 个文件重复）
- [x] 提取 `calculatePassRate` 到 `src/utils/question.ts`（2 个文件重复）
- [x] 提取 `judgeConfig` 解析逻辑到 `src/utils/question.ts`（多处重复）

### 2.2 消除重复代码（后端）
- [x] `AnswerServiceImpl.java` — 文件不存在，跳过

### 2.3 拆分过长函数（前端）
- [x] `QuestionAdminView.vue` — `loadData` 拆分为 buildQueryParams + transformQuestionRecord
- [x] `SubmissionListView.vue` — `fetchData` 拆分为 buildQueryParams + transformSubmitRecord + parseJudgeInfo + getDisplayStatus
- [x] `LanguageStats.vue` — `updateChart` 拆分为 buildChartOption + buildTooltipHtml

### 2.4 TypeScript 类型安全
- [x] 修复 `any` 类型滥用（8 个文件，添加 LoginUser/UserState/QuestionDetail/RunResult/ContestInfo/SubmitRecord 等接口）
- [x] `LanguageStats.vue` 补充 `lang="ts"` 和类型注解（项 2.3 已完成）
- [x] 用类型守卫替代不安全的类型断言（QuestionEditModal.vue）

### 2.5 清理调试代码
- [x] 移除前端所有 `console.log`（8 处）
- [x] 移除后端 `System.out.println`（7 处，改用 @Slf4j + log.debug）

### 2.6 文件命名修复
- [x] `DailyCheckIn.vue.vue` → 已移除该组件

### 2.7 修复语法错误（后端）
- [x] `CodeController.java` — 文件不存在，误报
- [x] `QuestionSubmitQueryRequest.java` — 语法正确，误报
- [x] `CodeExecutionController.java` — 已删除（硬编码密钥 + 与 JdoodleApiClient 重复）

---

## 三、架构改进（优先级：🟡 重要）

### 3.1 前端状态管理统一
- [ ] 将 Vuex 用户状态迁移到 Pinia，移除 Vuex 依赖（当前两个库共存增加包体积）
- [ ] 统一状态操作方式：通过 action/store 方法修改状态，禁止组件直接 `store.commit`

### 3.2 API 调用规范化
- [ ] 统一 API 调用方式：全部使用 OpenAPI 生成的 Service，移除原生 axios 直接调用（`LanguageStats.vue`、`AIAssistantView.vue`、`QuestionHeatmap.vue`、`QuestionDetailsView.vue`）
- [ ] 提取硬编码 API 地址到环境变量：
  - `LanguageStats.vue:150` — `http://127.0.0.1:8000`
  - `QuestionHeatmap.vue:79` — `http://127.0.0.1:8000`
  - `AIAssistantView.vue:194/230` — `http://localhost:8001`
- [ ] 实现全局 API 错误拦截器和统一的 loading 状态管理

### 3.3 路由完善
- [x] 添加 404 catch-all 路由 + NotFoundView.vue
- [x] 补充 7 个路由的 meta.access 权限定义
- [x] 退出登录调用后端 /api/user/logout 销毁 Session

### 3.4 后端架构改进
- [ ] 统一 API 路径风格：`/question_submit/do` 和 `/question_submit/list/page` 不一致
- [ ] `QuestionSubmitController` 标记了 `@Deprecated` 但未删除 — 清理或恢复
- [ ] 将判题系统从"Spring Bean 直接调用"改为**异步消息队列**（如 RabbitMQ/Kafka），支持高并发提交
- [ ] 代码执行增加超时控制和资源限制（当前 JDoodle 调用超时 10s，无重试机制）
- [ ] 完善 `JudgeInfo` 的内存/时间单位统一（JDoodle 返回值单位不一致时需标准化）

---

## 四、功能缺失与完善（优先级：🟢 扩展）

### 4.1 前端功能
- [ ] `HomeView.vue` — 当前仅为占位 `<h1>aaa</h1>`，需实现首页内容（推荐题目、公告、统计概览等）
- [ ] `AdminView.vue` — 仅显示"管理员可见"，需实现管理员仪表盘
- [ ] `NoAuthView.vue` — 仅显示"你没权限"，需设计友好的无权限页面
- [x] 缺少 404 页面 — 已创建 `NotFoundView.vue`（项 3.3）
- [ ] `QuestionDiscussionView.vue` — 讨论功能使用硬编码模拟数据，需对接后端 API
- [ ] `ContestListView.vue` / `ContestDetailView.vue` — 比赛功能使用硬编码数据，需对接后端
- [ ] `FeedbackView.vue` — 反馈功能前端就绪，后端接口待实现

### 4.2 后端功能
- [ ] Redis 功能恢复 — `MainApplication` 中 Redis 被 exclude，需按需启用并用于 Session/缓存
- [ ] Elasticsearch 帖子搜索同步 — `IncSyncPostToEs` 和 `FullSyncPostToEs` 已有实现但 ES 未配置
- [ ] 微信登录集成 — `WxMpController` 和相关配置存在但为占位符
- [ ] 实现代码执行速率限制 — 防止恶意刷提交
- [ ] 添加判题结果缓存 — 相同代码 + 相同输入可缓存结果

### 4.3 测试覆盖
- [ ] 后端测试目录 `src/test/java/com/oj/` 存在但内容不完整 — 补充 Service 层单元测试
- [ ] 前端无测试 — 添加关键组件的单元测试（登录、题目提交、判题流程）
- [ ] 添加 E2E 测试覆盖核心用户流程

### 4.4 运维与部署
- [x] 后端 Dockerfile 使用 `maven:3.8.1-jdk-8-slim` 但 `pom.xml` 要求 Java 17 — 修复基础镜像
- [x] 前端缺少 Dockerfile 和 nginx 部署配置
- [x] 添加 `docker-compose.yml` 编排前后端 + MySQL + Redis
- [ ] 配置 CI/CD 流水线（GitHub Actions）

---

## 五、用户体验（优先级：🟢 扩展）

### 5.1 响应式设计
- [ ] `QuestionDetailsView.vue` — 左右分栏在小屏幕下需改为上下布局（`@media` 查询）
- [ ] `SubmissionListView.vue` — 提交记录列表移动端溢出
- [ ] `ContestDetailView.vue` / `QuestionAdminView.vue` — 补充移动端样式
- [ ] `GlobalHeader.vue` — 右侧操作区 `flex="180px"` 在移动端挤压菜单
- [ ] `BasicLayout.vue` — `100vh` 在移动端浏览器中包含地址栏高度，改用 `100dvh`

### 5.2 可访问性
- [ ] `ThemeSwitcher.vue` — 添加 `aria-label`
- [x] `DailyCheckIn.vue.vue` — 已移除该组件
- [ ] `UserLayout.vue:6` — 图片 `alt` 属性为空字符串，改为有意义的描述
- [ ] `AIAssistantView.vue` — 聊天输入框添加 `aria-label`
- [ ] `LanguageStats.vue` — 原生 HTML 控件改为 Arco Design 组件

### 5.3 交互优化
- [ ] 添加全局 loading 骨架屏（当前各页面独立处理 loading 状态）
- [ ] 表单提交后给出明确的成功/失败反馈（部分页面缺少）
- [ ] 分页组件添加"回到顶部"功能
