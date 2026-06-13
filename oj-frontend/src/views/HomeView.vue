<template>
  <div class="home-page">
    <!-- Hero 区域 -->
    <section class="hero">
      <div class="hero-content">
        <h1 class="hero-title">
          <icon-code-square style="margin-right: 12px" />
          在线判题系统
        </h1>
        <p class="hero-desc">
          多语言支持 · 实时判题 · 提升编程能力的最佳试炼场
        </p>
        <div class="hero-actions">
          <a-button
            type="primary"
            size="large"
            @click="router.push('/question')"
          >
            <template #icon><icon-play-arrow /></template>
            开始刷题
          </a-button>
          <a-button outline size="large" @click="router.push('/submit')">
            <template #icon><icon-history /></template>
            评测记录
          </a-button>
        </div>
      </div>
    </section>

    <!-- 统计数据 -->
    <section class="stats-section">
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-number">{{ stats.problems }}</div>
          <div class="stat-label">题目总数</div>
        </div>
        <div class="stat-card">
          <div class="stat-number">{{ stats.submissions }}</div>
          <div class="stat-label">提交次数</div>
        </div>
        <div class="stat-card">
          <div class="stat-number">{{ stats.users }}</div>
          <div class="stat-label">注册用户</div>
        </div>
        <div class="stat-card">
          <div class="stat-number">{{ stats.accepted }}</div>
          <div class="stat-label">通过总数</div>
        </div>
      </div>
    </section>

    <!-- 特性介绍 -->
    <section class="features-section">
      <h2 class="section-title">平台特性</h2>
      <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon">⚡</div>
          <h3>实时判题</h3>
          <p>提交代码后秒级返回结果，支持详细判题信息</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">🌐</div>
          <h3>多语言支持</h3>
          <p>支持 C++、Java、Python、Go、JavaScript 等主流语言</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">📊</div>
          <h3>进度追踪</h3>
          <p>记录每次提交，追踪通过率，可视化学习进度</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">🔒</div>
          <h3>安全隔离</h3>
          <p>Docker 容器沙箱执行，资源限制，确保系统安全</p>
        </div>
      </div>
    </section>

    <!-- 快速开始 -->
    <section class="quickstart-section">
      <h2 class="section-title">快速开始</h2>
      <div class="steps">
        <div class="step">
          <div class="step-number">1</div>
          <div class="step-content">
            <h4>选择题目</h4>
            <p>从题库中选择感兴趣的题目</p>
          </div>
        </div>
        <div class="step">
          <div class="step-number">2</div>
          <div class="step-content">
            <h4>编写代码</h4>
            <p>使用在线编辑器编写解决方案</p>
          </div>
        </div>
        <div class="step">
          <div class="step-number">3</div>
          <div class="step-content">
            <h4>提交评测</h4>
            <p>提交代码，获取实时判题结果</p>
          </div>
        </div>
      </div>
    </section>

    <!-- 底部 CTA -->
    <section class="cta-section">
      <h2>准备好挑战了吗？</h2>
      <p>立即开始，提升你的编程能力</p>
      <a-button type="primary" size="large" @click="router.push('/question')">
        立即开始
      </a-button>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import {
  IconCodeSquare,
  IconPlayArrow,
  IconHistory,
} from "@arco-design/web-vue/es/icon";
import { QuestionControllerService } from "../../generated/index";

const router = useRouter();

const stats = ref({
  problems: 0,
  submissions: 0,
  users: 0,
  accepted: 0,
});

onMounted(async () => {
  try {
    const res = await QuestionControllerService.listQuestionVoByPageUsingPost({
      current: 1,
      pageSize: 1,
    });
    if (res.code === 0 && res.data) {
      stats.value.problems = res.data.total || 0;
    }
  } catch (e) {
    console.error("Failed to load stats:", e);
  }
});
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #0d1117;
}

/* Hero */
.hero {
  background: linear-gradient(135deg, #0d1117 0%, #161b22 50%, #1a2a3a 100%);
  padding: 100px 24px 80px;
  text-align: center;
  position: relative;
  overflow: hidden;
}

.hero::before {
  content: "";
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: radial-gradient(
      circle at 20% 50%,
      rgba(56, 139, 253, 0.1) 0%,
      transparent 50%
    ),
    radial-gradient(
      circle at 80% 50%,
      rgba(63, 185, 80, 0.1) 0%,
      transparent 50%
    );
  pointer-events: none;
}

.hero-content {
  position: relative;
  z-index: 1;
  max-width: 800px;
  margin: 0 auto;
}

.hero-title {
  font-size: 48px;
  font-weight: 800;
  color: #f0f6fc;
  margin: 0 0 16px;
  letter-spacing: -1px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.hero-desc {
  font-size: 18px;
  color: #8b949e;
  margin: 0 0 48px;
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

/* Stats */
.stats-section {
  padding: 60px 24px;
  max-width: 1000px;
  margin: 0 auto;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24px;
}

.stat-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 32px 24px;
  text-align: center;
  transition: transform 0.2s, border-color 0.2s;
}

.stat-card:hover {
  transform: translateY(-4px);
  border-color: #58a6ff;
}

.stat-number {
  font-size: 36px;
  font-weight: 700;
  color: #58a6ff;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #8b949e;
}

/* Features */
.features-section {
  padding: 60px 24px;
  max-width: 1000px;
  margin: 0 auto;
}

.section-title {
  font-size: 28px;
  font-weight: 700;
  color: #f0f6fc;
  text-align: center;
  margin: 0 0 48px;
}

.features-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 24px;
}

.feature-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 32px;
  transition: transform 0.2s, border-color 0.2s;
}

.feature-card:hover {
  transform: translateY(-4px);
  border-color: #58a6ff;
}

.feature-icon {
  font-size: 32px;
  margin-bottom: 16px;
}

.feature-card h3 {
  font-size: 18px;
  font-weight: 600;
  color: #f0f6fc;
  margin: 0 0 8px;
}

.feature-card p {
  font-size: 14px;
  color: #8b949e;
  margin: 0;
  line-height: 1.6;
}

/* Quick Start */
.quickstart-section {
  padding: 60px 24px;
  max-width: 800px;
  margin: 0 auto;
}

.steps {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.step {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 24px;
  transition: transform 0.2s, border-color 0.2s;
}

.step:hover {
  transform: translateX(8px);
  border-color: #58a6ff;
}

.step-number {
  width: 40px;
  height: 40px;
  background: #1f6feb;
  color: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  flex-shrink: 0;
}

.step-content h4 {
  font-size: 16px;
  font-weight: 600;
  color: #f0f6fc;
  margin: 0 0 4px;
}

.step-content p {
  font-size: 14px;
  color: #8b949e;
  margin: 0;
}

/* CTA */
.cta-section {
  text-align: center;
  padding: 80px 24px;
  background: linear-gradient(135deg, #1a2a3a 0%, #161b22 100%);
}

.cta-section h2 {
  font-size: 28px;
  font-weight: 700;
  color: #f0f6fc;
  margin: 0 0 12px;
}

.cta-section p {
  font-size: 16px;
  color: #8b949e;
  margin: 0 0 32px;
}

/* Responsive */
@media (max-width: 768px) {
  .hero-title {
    font-size: 32px;
  }

  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    flex-direction: column;
    align-items: center;
  }
}
</style>
