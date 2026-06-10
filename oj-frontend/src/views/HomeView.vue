<template>
  <div class="home-page">
    <!-- Hero 区域 -->
    <section class="hero">
      <h1 class="hero-title">在线判题系统</h1>
      <p class="hero-desc">多语言支持 · 实时判题 · AI 编程助手</p>
      <div class="hero-actions">
        <a-button type="primary" size="large" @click="router.push('/question')">
          开始刷题
        </a-button>
        <a-button size="large" @click="router.push('/competition')"> 查看比赛 </a-button>
      </div>
      <div class="hero-stats">
        <div class="stat-card" v-for="s in stats" :key="s.label">
          <span class="stat-val" :style="{ color: s.color }">{{ s.value }}</span>
          <span class="stat-label">{{ s.label }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();

const stats = [
  { value: "2,368", label: "题目总数", color: "#58a6ff" },
  { value: "12,854", label: "提交记录", color: "#3fb950" },
  { value: "896", label: "活跃用户", color: "#a371f7" },
];
</script>

<style scoped>
.home-page {
  min-height: calc(100vh - 64px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.hero {
  text-align: center;
  padding: 60px 24px;
  max-width: 800px;
}
.hero-title {
  font-size: 42px;
  font-weight: 700;
  color: #f0f6fc;
  margin: 0 0 12px;
  letter-spacing: -1px;
}
.hero-desc {
  font-size: 16px;
  color: #8b949e;
  margin: 0 0 40px;
}
.hero-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  margin-bottom: 48px;
}
.hero-stats {
  display: flex;
  gap: 24px;
  justify-content: center;
  flex-wrap: wrap;
}
.stat-card {
  background: #161b22;
  border: 1px solid #30363d;
  border-radius: 12px;
  padding: 20px 32px;
  text-align: center;
  min-width: 140px;
}
.stat-val {
  display: block;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 4px;
}
.stat-label {
  font-size: 12px;
  color: #8b949e;
}
</style>
