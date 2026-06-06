<!-- src/components/LanguageCodeEditor.vue -->
<template>
  <div class="language-code-editor">
    <!-- 头部工具栏 -->
    <div class="editor-header">
      <a-select
        v-model="innerLanguage"
        placeholder="选择语言"
        style="width: 200px"
        :options="languageOptions"
      />
    </div>

    <!-- 编辑器主体：占满剩余空间 -->
    <div class="editor-wrapper">
      <CodeEditor
        v-model="innerCode"
        :language="innerLanguage"
        class="code-editor"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from "vue";
import CodeEditor from "./CodeEditor.vue";

// 支持语言
const languageOptions = [
  { value: "cpp", label: "C++" },
  { value: "java", label: "Java" },
  { value: "python", label: "Python" },
  { value: "go", label: "Go" },
  { value: "javascript", label: "JavaScript" },
];

// 各语言默认模板
const templates: Record<string, string> = {
  cpp: `#include <bits/stdc++.h>
using namespace std;

int main() {
    // 在此编写你的代码

    return 0;
}`,

  java: `import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // 在此编写你的代码

        sc.close();
    }
}`,

  python: `# 在此编写你的代码
`,

  go: `package main

import "fmt"

func main() {
    // 在此编写你的代码
}
`,

  javascript: `// 在此编写你的代码
const readline = require('readline');
const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

rl.on('line', (line) => {
  // 处理每一行输入
});
`,
};

// props：允许父组件传入初始语言和代码
const props = defineProps<{
  initialLanguage?: string; // 初始语言，默认 cpp
  initialCode?: string; // 初始代码（如果有提交记录等）
}>();

const emit = defineEmits<{
  (e: "update:language", value: string): void;
  (e: "update:code", value: string): void;
}>();

// 内部状态
const innerLanguage = ref(props.initialLanguage || "cpp");
const innerCode = ref(props.initialCode || "");

// 同步到父组件
watch(innerLanguage, (val) => emit("update:language", val));
watch(innerCode, (val) => emit("update:code", val));

// 语言切换时自动填充模板（仅当代码为空或为旧模板时）
watch(
  innerLanguage,
  (newLang, oldLang) => {
    const oldTemplate = templates[oldLang || "cpp"];
    if (
      !innerCode.value.trim() ||
      innerCode.value.trim() === oldTemplate?.trim()
    ) {
      innerCode.value = templates[newLang] || "// 请编写代码";
    }
  },
  { immediate: true }
);

// 组件挂载时初始化
onMounted(() => {
  if (!innerCode.value.trim()) {
    innerCode.value = templates[innerLanguage.value] || "// 请编写代码";
  }
});
</script>

<style scoped>
.language-code-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.editor-header {
  padding: 12px;
  flex-shrink: 0;
}

.editor-wrapper {
  flex: 1;
  width: 100%;
  overflow: hidden;
  min-height: 0;
  border: 1px solid #e5e6eb;
  border-radius: 8px;
}

.code-editor {
  width: 100%;
  height: 100%;
}
</style>
