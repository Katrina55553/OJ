<template>
  <div class="about">
    <div class="about-page">
      <!-- 头部 -->
      <div class="about-header">
        <h1>关于 OJ 在线判题系统</h1>
        <p class="about-intro">
          一个开源的在线编程练习平台，采用前后端分离架构与 Docker 容器化部署
        </p>
      </div>

      <!-- 卡片网格 -->
      <div class="card-grid">
        <section class="about-card">
          <div class="card-icon icon-blue">
            <icon-bulb />
          </div>
          <h2>这是什么？</h2>
          <p>
            一个开源的在线编程练习平台，支持多语言代码提交与自动判题，采用前后端分离架构，Docker
            容器化部署。
          </p>
        </section>

        <section class="about-card">
          <div class="card-icon icon-green">
            <icon-thunderbolt />
          </div>
          <h2>核心功能</h2>
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

        <section class="about-card">
          <div class="card-icon icon-orange">
            <icon-tool />
          </div>
          <h2>技术栈</h2>
          <ul>
            <li>前端：Vue 3 + TypeScript + Arco Design Vue + Monaco Editor</li>
            <li>后端：Spring Boot 2.7 + MyBatis-Plus + MySQL 8.0</li>
            <li>基础设施：Redis 7 + RabbitMQ + Docker Compose</li>
          </ul>
        </section>

        <section class="about-card">
          <div class="card-icon icon-red">
            <icon-email />
          </div>
          <h2>联系我们</h2>
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
  </div>
</template>

<script setup lang="ts">
import {
  IconBulb,
  IconThunderbolt,
  IconTool,
  IconEmail,
} from "@arco-design/web-vue/es/icon";
</script>

<style scoped>
.about-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 48px 24px;
  color: #c9d1d9;
}

.about-header {
  text-align: center;
  margin-bottom: 48px;
}

.about-header h1 {
  font-size: 32px;
  font-weight: 700;
  color: #f0f6fc;
  margin: 0 0 12px;
}

.about-intro {
  font-size: 16px;
  color: #8b949e;
  margin: 0;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.about-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 32px;
  transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.about-card:hover {
  transform: translateY(-2px);
  border-color: #58a6ff;
  box-shadow: 0 8px 24px rgba(56, 139, 253, 0.1);
}

.card-icon {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  margin-bottom: 16px;
}

.card-icon.icon-blue {
  background: rgba(56, 139, 253, 0.12);
  color: #58a6ff;
}

.card-icon.icon-green {
  background: rgba(63, 185, 80, 0.12);
  color: #3fb950;
}

.card-icon.icon-orange {
  background: rgba(240, 136, 62, 0.12);
  color: #f0883e;
}

.card-icon.icon-red {
  background: rgba(248, 81, 73, 0.12);
  color: #f85149;
}

.about-card h2 {
  margin: 0 0 12px;
  color: #f0f6fc;
  font-size: 20px;
  font-weight: 700;
}

.about-card p {
  line-height: 1.7;
  color: #c9d1d9;
  margin: 0 0 8px;
}

.about-card ul {
  padding-left: 20px;
  line-height: 1.8;
}

.about-card li {
  margin-bottom: 6px;
  color: #c9d1d9;
}

@media (max-width: 768px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
}
</style>
