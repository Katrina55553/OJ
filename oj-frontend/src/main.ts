import { createApp } from "vue";
import App from "./App.vue";
import { createPinia } from "pinia";
import ArcoVue from "@arco-design/web-vue";
import "@arco-design/web-vue/dist/arco.css";
import "./assets/dark-theme.css";
import "@arco-design/web-vue/es/config-provider/style/css.js";
import ArcoVueIcon from "@arco-design/web-vue/es/icon";
import router from "./router";
import store from "./store";
import "bytemd/dist/index.css";
import "katex/dist/katex.min.css";
import "@/access";

// Monaco Editor Worker 配置（Vite 下需手动注册 ESM worker）
// 语言与原 vue.config.js 中 MonacoWebpackPlugin 保持一致
import editorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import jsonWorker from "monaco-editor/esm/vs/language/json/json.worker?worker";
import cssWorker from "monaco-editor/esm/vs/language/css/css.worker?worker";
import htmlWorker from "monaco-editor/esm/vs/language/html/html.worker?worker";
import tsWorker from "monaco-editor/esm/vs/language/typescript/ts.worker?worker";

self.MonacoEnvironment = {
  getWorker(_, label) {
    if (label === "json") return new jsonWorker();
    if (label === "css" || label === "scss" || label === "less")
      return new cssWorker();
    if (label === "html" || label === "handlebars" || label === "razor")
      return new htmlWorker();
    if (label === "typescript" || label === "javascript") return new tsWorker();
    return new editorWorker();
  },
};

const app = createApp(App);

// 注册
app.use(createPinia());
app.use(ArcoVue);
app.use(router);
app.use(store);
app.use(ArcoVueIcon);

// 全局错误处理
app.config.errorHandler = (err: unknown, vm, info) => {
  console.error("Vue Error:", err);
  console.error("Info:", info);
};

// 挂载应用
app.mount("#app");
