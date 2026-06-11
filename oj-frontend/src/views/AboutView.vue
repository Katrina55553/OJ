<template>
  <div class="about">
    <div class="about-page">
      <h1>关于 OJ 在线判题系统</h1>

      <section class="about-section">
        <h2>🧩 这是什么？</h2>
        <p>
          一个开源的在线编程练习平台，支持多语言代码提交与自动判题，采用前后端分离架构，Docker 容器化部署。
        </p>
      </section>

      <section class="about-section">
        <h2>⚡ 核心功能</h2>
        <ul>
          <li>多语言判题 — C++、Java、Python、Go、JavaScript</li>
          <li>Docker 代码沙箱 — 安全隔离，每种语言独立镜像</li>
          <li>在线运行代码 — 即时执行，方便调试</li>
          <li>题库管理 — 增删改查，Markdown + KaTeX 公式编辑</li>
          <li>异步判题 — RabbitMQ 消息队列，支持高并发</li>
          <li>暗色主题 — GitHub Dark 风格，跟随系统偏好</li>
          <li>用户系统 — JWT 无状态认证，三级角色权限</li>
        </ul>
      </section>

      <section class="about-section">
        <h2>🛠 技术栈</h2>
        <ul>
          <li>前端：Vue 3 + TypeScript + Arco Design Vue + Monaco Editor</li>
          <li>后端：Spring Boot 2.7 + MyBatis-Plus + MySQL 8.0</li>
          <li>基础设施：Redis 7 + RabbitMQ + Docker Compose</li>
        </ul>
      </section>

      <section class="about-section">
        <h2>📬 联系我们</h2>
        <p>遇到问题？欢迎通过以下方式反馈：</p>
        <ul>
          <li>
            邮箱：<a href="mailto:ambition55553@gmail.com"
              >ambition55553@gmail.com</a
            >
          </li>
          <li>
            GitHub：<a
              href="https://github.com/Katrina55553/OJ"
              target="_blank"
              rel="noopener"
              >github.com/Katrina55553/OJ</a
            >
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.about-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 24px;
  line-height: 1.6;
  color: #c9d1d9;
}
.about-section {
  margin-bottom: 32px;
}
.about-section h2 {
  margin-bottom: 12px;
  color: #f0f6fc;
}
.about-section ul {
  padding-left: 20px;
}
.about-section li {
  margin-bottom: 8px;
}
</style>
