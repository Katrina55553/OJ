<template>
  <div class="md-preview" v-html="rendered"></div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import MarkdownIt from "markdown-it";
import hljs from "highlight.js";
import "highlight.js/styles/atom-one-dark.css";
import texmath from "markdown-it-texmath";
import katex from "katex";
import "katex/dist/katex.min.css";

let md: MarkdownIt;

md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight: function (str: string, lang: string) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return (
          '<pre class="hljs"><code>' +
          hljs.highlight(str, { language: lang, ignoreIllegals: true }).value +
          "</code></pre>"
        );
      } catch (error) {
        console.warn("Highlight error:", error);
      }
    }
    return (
      '<pre class="hljs"><code>' + md.utils.escapeHtml(str) + "</code></pre>"
    );
  },
});

md.use(texmath, {
  engine: katex,
  delimiters: "dollars",
});

const props = defineProps<{
  value: string;
}>();

const rendered = computed(() => {
  if (!props.value) return "";
  return md.render(props.value);
});
</script>

<style scoped>
.md-preview {
  position: relative;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  line-height: 1.8;
  font-size: 16px;
  color: #1d2129;
  padding: 24px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e5e6eb;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

/* --- 代码块 --- */
.md-preview :deep(pre) {
  white-space: pre !important;
  overflow-x: auto !important;
  max-width: 100% !important;

  background: #282c34;
  color: #abb2bf;
  padding: 16px;
  border-radius: 8px;
  margin: 16px 0;
  font-family: Consolas, Monaco, "Andale Mono", "Ubuntu Mono", monospace;
}

.md-preview :deep(pre code) {
  white-space: pre !important;
  background: transparent;
  padding: 0;
  border-radius: 0;
  color: inherit;
  font-family: inherit;
}

.md-preview :deep(table) {
  display: block;
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  border-spacing: 0;
  border-collapse: collapse;
  margin: 20px 0;
}

.md-preview :deep(th),
.md-preview :deep(td) {
  padding: 8px 16px;
  border: 1px solid #e5e6eb;
}

.md-preview :deep(th) {
  background-color: #f7f8fa;
  font-weight: 600;
}

.md-preview :deep(img) {
  max-width: 100% !important;
  height: auto !important;
  display: block;
  margin: 16px 0;
  border-radius: 4px;
}

.md-preview :deep(p),
.md-preview :deep(li),
.md-preview :deep(h1),
.md-preview :deep(h2),
.md-preview :deep(h3),
.md-preview :deep(h4),
.md-preview :deep(h5) {
  word-break: break-word;
  overflow-wrap: break-word;
}

.md-preview :deep(.katex-display) {
  overflow-x: auto;
  max-width: 100%;
  padding: 8px 0;
}
</style>
