<template>
  <div class="home-page">
    <!-- Hero 区域 -->
    <section class="hero">
      <h1 class="hero-title">在线判题系统</h1>
      <p class="hero-desc">多语言支持 · 实时判题</p>
      <div class="hero-actions">
        <a-button type="primary" size="large" @click="router.push('/question')">
          开始刷题
        </a-button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();
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
}
</style>
