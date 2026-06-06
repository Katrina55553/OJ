<template>
  <div id="userLayout">
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
  background: url("https://gw.alipayobjects.com/zos/rmsportal/FfdJeJRQWjEeGTpqgBKj.png")
    0% 0% / 100% 100%;
  background-size: cover;
  background-position: center;
}

#userLayout :deep(.arco-layout),
#userLayout :deep(.arco-layout-header),
#userLayout :deep(.arco-layout-content),
#userLayout :deep(.arco-layout-footer) {
  background-color: transparent !important;
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
  color: #1d2129;
  font-weight: 600;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
    "Helvetica Neue", Arial, sans-serif;
  letter-spacing: 1px;
}

#userLayout .content {
  padding: 24px;
  display: flex;
  justify-content: center;
  /* 如果想让登录框垂直居中，可以打开下面的注释，否则就是靠上显示 */
  /* align-items: center; */
}

#userLayout .footer {
  padding: 16px;
  text-align: center;
  color: rgba(0, 0, 0, 0.45);
  font-size: 14px;
  position: sticky;
  bottom: 0;
}
</style>
