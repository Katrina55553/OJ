<template>
  <div id="app">
    <ConfigProvider>
      <!-- 使用 BasicLayout 或 RouterView -->
      <template v-if="route.path.startsWith('/user')">
        <router-view />
      </template>
      <template v-else>
        <BasicLayout />
      </template>
    </ConfigProvider>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from "vue";
import { useRoute } from "vue-router";
import { useStore } from "vuex";
import BasicLayout from "@/layouts/BasicLayout.vue";

const route = useRoute();
const store = useStore();

/**
 * 全局初始化函数 每次刷新页面都会执行
 */
const doInit = () => {
  store.dispatch("user/getLoginUser");
};

onMounted(() => {
  doInit();
});
</script>
