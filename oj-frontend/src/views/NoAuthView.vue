<template>
  <div class="no-auth">
    <div class="no-auth-content">
      <h1 class="error-code">🔒</h1>
      <p class="error-text">你没有权限访问此页面</p>
      <a-button type="primary" @click="router.push('/')">返回首页</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";
const router = useRouter();
</script>

<style scoped>
.no-auth {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.no-auth-content {
  text-align: center;
}
.error-code {
  font-size: 64px;
  margin: 0;
}
.error-text {
  font-size: 18px;
  color: #8b949e;
  margin: 16px 0 32px;
}
</style>
