import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import path from "path";

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@": path.resolve(__dirname, "src"),
    },
  },
  // 兼容 generated/core/OpenAPI.ts 中使用的 process.env.NODE_ENV
  define: {
    "process.env.NODE_ENV": JSON.stringify(process.env.NODE_ENV),
  },
  server: {
    port: 8080,
    proxy: {
      "/api": {
        target: "http://localhost:8101",
        changeOrigin: true,
        // 保持原路径，不重写
      },
    },
  },
  build: {
    // 禁用 source map 减小构建体积（与原 Dockerfile 构建策略一致）
    sourcemap: false,
    // 对于 Monaco 等大依赖，适当提高 chunk 大小警告阈值
    chunkSizeWarningLimit: 1500,
  },
});
