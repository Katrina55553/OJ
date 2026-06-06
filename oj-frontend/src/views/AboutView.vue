<template>
  <div class="about">
    <div class="about-page">
      <h1>关于 OJ 在线判题系统</h1>

      <section class="about-section">
        <h2>🧩 这是什么？</h2>
        <p>
          一个开源的在线编程练习与竞赛平台，支持自动评测、比赛管理、AI辅助解题、用户成长追踪。
        </p>
      </section>

      <section class="about-section">
        <h2>⚡ 核心功能</h2>
        <ul>
          <li>实时代码评测（C/C++/Java/Python/Go等）</li>
          <li>创建私有/公开比赛</li>
          <li>发布题解/讨论</li>
          <li>AI智能体在线问答</li>
          <li>可视化个人做题热力图</li>
        </ul>
      </section>

      <section class="about-section">
        <h2>👨‍💻 开发者</h2>
        <p>
          由 Katrina 独立开发，<br />
          源码托管于 GitHub，欢迎参与共建！
        </p>
      </section>

      <section class="about-section">
        <h2>📬 联系我们</h2>
        <p>遇到问题？欢迎通过以下方式反馈：</p>
        <ul>
          <li>
            邮箱：<a href="mailto:yourname@example.com"
              >ambition55553@gmail.com</a
            >
          </li>
          <li>
            GitHub Issues：<a
              href="https://github.com/Katrina/oj/issues"
              target="_blank"
              rel="noopener"
              >链接</a
            >
          </li>
          <li>系统内「意见反馈」入口</li>
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
}
.about-section {
  margin-bottom: 32px;
}
.about-section h2 {
  margin-bottom: 12px;
  color: #1d2129;
}
.about-section ul {
  padding-left: 20px;
}
.about-section li {
  margin-bottom: 8px;
}
</style>
