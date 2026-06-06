// src/types/markdown-it-katex.d.ts  或直接在 src/markdown-it-katex.d.ts
declare module "markdown-it-katex" {
  import MarkdownIt from "markdown-it";
  function markdownItKatex(md: MarkdownIt, options?: any): void;
  export = markdownItKatex;
}
