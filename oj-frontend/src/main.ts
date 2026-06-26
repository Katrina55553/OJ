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
