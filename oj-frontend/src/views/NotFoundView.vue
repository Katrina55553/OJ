<template>
  <div class="not-found">
    <div class="not-found-content">
      <h1 class="error-code">404</h1>
      <p class="error-text">页面不存在</p>
      <a-button type="primary" @click="router.push('/')">返回首页</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();
</script>

<style scoped>
.not-found {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.not-found-content {
  text-align: center;
}

.error-code {
  font-size: 96px;
  font-weight: 700;
  color: #30363d;
  margin: 0;
  line-height: 1;
}

.error-text {
  font-size: 18px;
  color: #8b949e;
  margin: 16px 0 32px;
}
</style>
