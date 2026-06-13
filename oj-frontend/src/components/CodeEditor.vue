<template>
  <div ref="editorRef" class="monaco-editor-container" />
</template>

<script setup lang="ts">
import * as monaco from "monaco-editor";
import { ref, onMounted, watch, shallowRef, onBeforeUnmount, toRaw } from "vue";

interface Props {
  modelValue: string;
  language?: string;
  readOnly?: boolean;
  theme?: "vs-dark" | "vs-light"; // 可选主题
  minimapEnabled?: boolean; // 是否显示小地图
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: "",
  language: "cpp",
  readOnly: false,
  theme: "vs-dark",
  minimapEnabled: true,
});

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

const editorRef = ref<HTMLDivElement | null>(null);
const editorInstance = shallowRef<monaco.editor.IStandaloneCodeEditor | null>(
  null
);

onMounted(() => {
  if (!editorRef.value) return;

  editorInstance.value = monaco.editor.create(editorRef.value, {
    value: props.modelValue,
    language: props.language,
    readOnly: props.readOnly,
    theme: props.theme,
    automaticLayout: true, // 自动调整布局
    fontSize: 15, // 字体更大，更舒适
    lineHeight: 24,
    minimap: { enabled: props.minimapEnabled },
    scrollBeyondLastLine: false,
    wordWrap: "on", // 自动换行（可选，防止横向滚动）
    renderWhitespace: "selection",
    scrollbar: {
      verticalScrollbarSize: 12,
      horizontalScrollbarSize: 12,
      alwaysConsumeMouseWheel: false,
    },
    suggestOnTriggerCharacters: true,
    quickSuggestions: true,
    parameterHints: { enabled: true },
    folding: true, // 代码折叠
    glyphMargin: true,
    lineNumbers: "on",
    roundedSelection: true,
    cursorBlinking: "smooth",
    smoothScrolling: true,
    padding: { top: 16, bottom: 16 },
  });

  // 内容变化时通知父组件
  editorInstance.value.onDidChangeModelContent(() => {
    const value = editorInstance.value?.getValue() || "";
    emit("update:modelValue", value);
  });

  // 监听焦点
  editorInstance.value.onDidFocusEditorText(() => {
    editorInstance.value?.revealLineInCenter(
      editorInstance.value.getPosition()?.lineNumber || 1
    );
  });
});

onBeforeUnmount(() => {
  if (editorInstance.value) {
    editorInstance.value.dispose();
  }
});

// 外部调用：填充代码
const fillValue = (val: string) => {
  if (!editorInstance.value) return;
  toRaw(editorInstance.value).setValue(val);
};

defineExpose({ fillValue });

// 监听 modelValue 变化
watch(
  () => props.modelValue,
  (newVal) => {
    if (!editorInstance.value) return;
    const editor = toRaw(editorInstance.value);
    if (editor.getValue() !== newVal) {
      editor.setValue(newVal);
    }
  }
);

// 监听语言变化
watch(
  () => props.language,
  (newLang) => {
    if (!editorInstance.value || !newLang) return;
    monaco.editor.setModelLanguage(editorInstance.value.getModel()!, newLang);
  }
);

// 监听主题变化
watch(
  () => props.theme,
  (newTheme) => {
    if (!editorInstance.value) return;
    monaco.editor.setTheme(newTheme);
  }
);
</script>

<style scoped>
.monaco-editor-container {
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid #30363d;
  background: #0d1117;
  box-sizing: border-box;
}

/* 美化滚动条（Webkit 浏览器） */
:deep(.monaco-scrollable-element > .scrollbar > .slider) {
  background: rgba(121, 121, 121, 0.4);
  border-radius: 6px;
}
:deep(.monaco-scrollable-element > .scrollbar > .slider:hover) {
  background: rgba(100, 100, 100, 0.7);
}

/* 光标更柔和 */
:deep(.monaco-editor .cursor) {
  transition: all 0.1s;
}

/* 选中高亮更柔和 */
:deep(.monaco-editor .selected-text) {
  background-color: rgba(38, 79, 120, 0.5);
}
</style>
