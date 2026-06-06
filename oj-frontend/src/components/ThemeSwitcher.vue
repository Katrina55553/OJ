<template>
  <a-tooltip :content="isDark ? '切换到明亮模式' : '切换到黑暗模式'">
    <a-button type="outline" shape="circle" @click="toggleDark">
      <icon-sun-fill v-if="isDark" />
      <icon-moon-fill v-else />
    </a-button>
  </a-tooltip>
</template>

<script setup lang="ts">
import { useThemeStore } from "@/store/theme";
const { isDark, toggleDark } = useThemeStore();
</script>
