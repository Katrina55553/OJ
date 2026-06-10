<template>
  <div id="BasicLayout">
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>
      <a-layout-content class="content">
        <router-view />
      </a-layout-content>
    </a-layout>
  </div>
</template>

<style scoped>
#BasicLayout {
  height: 100vh;
  background: #0d1117;
}

/* 顶部栏 */
.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  background: #161b22;
  z-index: 1000;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.4);
  border-bottom: 1px solid #30363d;
}

.content {
  padding: 0;
  margin-top: 64px;
  background: #0d1117;
  min-height: calc(100vh - 64px);
}
</style>

<script setup lang="ts">
import GlobalHeader from "@/components/GlobalHeader.vue";
</script>
