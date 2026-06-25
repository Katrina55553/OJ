/* eslint-disable */
/// <reference types="vite/client" />

declare module "*.vue" {
  import type { DefineComponent } from "vue";
  const component: DefineComponent<{}, {}, any>;
  export default component;
}
// src/env.d.ts 或 src/types.d.ts

declare module "markdown-it-texmath";
