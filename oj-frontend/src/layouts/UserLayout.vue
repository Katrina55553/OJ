<template>
  <div id="userLayout">
    <!-- 背景光晕装饰 -->
    <div class="bg-decoration">
      <div class="glow glow-blue"></div>
      <div class="glow glow-green"></div>
    </div>

    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <a-space>
          <img src="@/assets/logo.png" class="logo" alt="OJ Logo" />
          <div class="title">OJ 在线判题系统</div>
        </a-space>
      </a-layout-header>

      <a-layout-content class="content">
        <router-view />
      </a-layout-content>

      <a-layout-footer class="footer">
        <a-space direction="vertical" :size="2">
          <span>OJ Online Judge System</span>
          <span>Designed by Katrina</span>
        </a-space>
      </a-layout-footer>
    </a-layout>
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
#userLayout {
  text-align: center;
  background: #0d1117;
  position: relative;
  overflow: hidden;
}

/* 背景光晕装饰 */
.bg-decoration {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 0;
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
}

.glow-blue {
  width: 500px;
  height: 500px;
  background: #1f6feb;
  top: -100px;
  left: -100px;
}

.glow-green {
  width: 400px;
  height: 400px;
  background: #238636;
  bottom: -100px;
  right: -100px;
}

#userLayout :deep(.arco-layout),
#userLayout :deep(.arco-layout-header),
#userLayout :deep(.arco-layout-content),
#userLayout :deep(.arco-layout-footer) {
  background-color: transparent !important;
  position: relative;
  z-index: 1;
}

#userLayout .header {
  margin-top: 60px;
  margin-bottom: 16px;
}

#userLayout .logo {
  width: 44px;
  height: 44px;
}

#userLayout .title {
  font-size: 33px;
  color: #f0f6fc;
  font-weight: 600;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
    "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 1px;
}

#userLayout .content {
  padding: 24px;
  display: flex;
  justify-content: center;
}

#userLayout .footer {
  padding: 16px;
  text-align: center;
  color: #484f58;
  font-size: 14px;
  position: sticky;
  bottom: 0;
}
</style>
