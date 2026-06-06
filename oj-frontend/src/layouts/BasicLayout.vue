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
}

/* 顶部栏 */
.header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 64px;
  background: #808080;
  z-index: 1000;
  box-shadow: none;
  border-bottom: 1px solid #f0f0f0;
}

.content {
  padding: 0;
  margin-top: 64px;
  background: #f0f2f5;
}
</style>

<script setup lang="ts">
import GlobalHeader from "@/components/GlobalHeader.vue";
</script>
