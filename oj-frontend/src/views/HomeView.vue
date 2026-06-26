<template>
  <div class="home-page">
    <!-- Hero 区域 -->
    <section class="hero">
      <div class="hero-content">
        <div class="hero-badge">
          <icon-fire />
          <span>面向编程学习者的在线判题平台</span>
        </div>
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
          <a-button outline size="large" @click="router.push('/submission')">
            <template #icon><icon-history /></template>
            评测记录
          </a-button>
        </div>
      </div>

      <!-- 统计数据带 -->
      <div class="hero-stats">
        <div class="stat-item">
          <div class="stat-value">5+</div>
          <div class="stat-label">编程语言</div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <div class="stat-value">秒级</div>
          <div class="stat-label">判题响应</div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <div class="stat-value">Docker</div>
          <div class="stat-label">安全沙箱</div>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <div class="stat-value">异步</div>
          <div class="stat-label">消息队列</div>
        </div>
      </div>
    </section>

    <!-- 特性介绍 -->
    <section class="features-section">
      <h2 class="section-title">平台特性</h2>
      <p class="section-subtitle">为编程学习提供完整的技术支撑</p>
      <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon icon-blue">
            <icon-thunderbolt />
          </div>
          <h3>实时判题</h3>
          <p>提交代码后秒级返回结果，支持详细判题信息</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon icon-green">
            <icon-code />
          </div>
          <h3>多语言支持</h3>
          <p>支持 C++、Java、Python、Go、JavaScript 等主流语言</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon icon-orange">
            <icon-dashboard />
          </div>
          <h3>进度追踪</h3>
          <p>记录每次提交，追踪通过率，可视化学习进度</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon icon-red">
            <icon-safe />
          </div>
          <h3>安全隔离</h3>
          <p>Docker 容器沙箱执行，资源限制，确保系统安全</p>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
import {
  IconCodeSquare,
  IconPlayArrow,
  IconHistory,
  IconFire,
  IconThunderbolt,
  IconCode,
  IconDashboard,
  IconSafe,
} from "@arco-design/web-vue/es/icon";

const router = useRouter();
</script>

<style scoped>
.home-page {
  min-height: 100vh;
  background: #0d1117;
}

/* Hero */
.hero {
  background: linear-gradient(135deg, #0d1117 0%, #161b22 50%, #1a2a3a 100%);
  padding: 80px 24px 0;
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
      rgba(56, 139, 253, 0.12) 0%,
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

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  background: rgba(56, 139, 253, 0.1);
  border: 1px solid rgba(56, 139, 253, 0.3);
  border-radius: 20px;
  color: #58a6ff;
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 24px;
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
  margin: 0 0 40px;
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  gap: 16px;
  justify-content: center;
}

/* 统计数据带 */
.hero-stats {
  position: relative;
  z-index: 1;
  max-width: 900px;
  margin: 64px auto 0;
  padding: 28px 24px;
  background: rgba(22, 27, 34, 0.6);
  border-top: 1px solid #30363d;
  border-left: 1px solid #30363d;
  border-right: 1px solid #30363d;
  border-radius: 16px 16px 0 0;
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: space-around;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #58a6ff;
}

.stat-label {
  font-size: 13px;
  color: #8b949e;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: #30363d;
}

/* Features */
.features-section {
  padding: 64px 24px 80px;
  max-width: 1000px;
  margin: 0 auto;
}

.section-title {
  font-size: 28px;
  font-weight: 700;
  color: #f0f6fc;
  text-align: center;
  margin: 0 0 8px;
}

.section-subtitle {
  font-size: 15px;
  color: #8b949e;
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
  transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.feature-card:hover {
  transform: translateY(-4px);
  border-color: #58a6ff;
  box-shadow: 0 8px 24px rgba(56, 139, 253, 0.12);
}

.feature-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  margin-bottom: 16px;
}

.feature-icon.icon-blue {
  background: rgba(56, 139, 253, 0.12);
  color: #58a6ff;
}

.feature-icon.icon-green {
  background: rgba(63, 185, 80, 0.12);
  color: #3fb950;
}

.feature-icon.icon-orange {
  background: rgba(240, 136, 62, 0.12);
  color: #f0883e;
}

.feature-icon.icon-red {
  background: rgba(248, 81, 73, 0.12);
  color: #f85149;
}

.feature-card h3 {
  font-size: 22px;
  font-weight: 700;
  color: #f0f6fc;
  margin: 0 0 8px;
}

.feature-card p {
  font-size: 14px;
  color: #8b949e;
  margin: 0;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .hero-title {
    font-size: 32px;
  }

  .features-grid {
    grid-template-columns: 1fr;
  }

  .hero-actions {
    flex-direction: column;
    align-items: center;
  }

  .hero-stats {
    flex-wrap: wrap;
    gap: 16px;
  }

  .stat-divider {
    display: none;
  }
}
</style>
