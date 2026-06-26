<template>
  <div ref="editorRef" class="cm-editor-container" />
</template>

<script setup lang="ts">
import { ref, onMounted, watch, onBeforeUnmount, shallowRef } from "vue";
import { EditorState, Compartment } from "@codemirror/state";
import { EditorView, keymap, lineNumbers, highlightActiveLine, highlightActiveLineGutter } from "@codemirror/view";
import { defaultKeymap, history, historyKeymap } from "@codemirror/commands";
import { searchKeymap } from "@codemirror/search";
import { indentOnInput, bracketMatching, foldGutter, foldKeymap } from "@codemirror/language";
import { autocompletion, completionKeymap, closeBrackets, closeBracketsKeymap } from "@codemirror/autocomplete";
import { oneDark } from "@codemirror/theme-one-dark";
import { cpp } from "@codemirror/lang-cpp";
import { java } from "@codemirror/lang-java";
import { python } from "@codemirror/lang-python";
import { go } from "@codemirror/lang-go";
import { javascript } from "@codemirror/lang-javascript";

interface Props {
  modelValue: string;
  language?: string;
  readOnly?: boolean;
  theme?: "dark" | "light";
  minimapEnabled?: boolean; // 兼容旧 props，CodeMirror 无 minimap，忽略
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: "",
  language: "cpp",
  readOnly: false,
  theme: "dark",
  minimapEnabled: true,
});

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();

const editorRef = ref<HTMLDivElement | null>(null);
const editorView = shallowRef<EditorView | null>(null);

// 动态配置 compartments，支持切换语言/主题不重建编辑器
const languageCompartment = new Compartment();
const themeCompartment = new Compartment();
const readOnlyCompartment = new Compartment();

function getLanguageExtension(lang: string) {
  switch (lang.toLowerCase()) {
    case "cpp":
    case "c":
    case "c++":
      return cpp();
    case "java":
      return java();
    case "python":
    case "python3":
      return python();
    case "go":
      return go();
    case "javascript":
    case "js":
    case "typescript":
    case "ts":
      return javascript();
    default:
      return cpp();
  }
}

function getThemeExtension(theme: string) {
  return theme === "dark" ? oneDark : [];
}

onMounted(() => {
  if (!editorRef.value) return;

  const updateListener = EditorView.updateListener.of((vu) => {
    if (vu.docChanged) {
      emit("update:modelValue", vu.state.doc.toString());
    }
  });

  const state = EditorState.create({
    doc: props.modelValue,
    extensions: [
      lineNumbers(),
      highlightActiveLine(),
      highlightActiveLineGutter(),
      history(),
      foldGutter(),
      indentOnInput(),
      bracketMatching(),
      closeBrackets(),
      autocompletion(),
      keymap.of([
        ...closeBracketsKeymap,
        ...defaultKeymap,
        ...searchKeymap,
        ...historyKeymap,
        ...foldKeymap,
        ...completionKeymap,
      ]),
      languageCompartment.of(getLanguageExtension(props.language)),
      themeCompartment.of(getThemeExtension(props.theme)),
      readOnlyCompartment.of(EditorState.readOnly.of(props.readOnly)),
      EditorView.lineWrapping,
      updateListener,
    ],
  });

  editorView.value = new EditorView({
    state,
    parent: editorRef.value,
  });
});

onBeforeUnmount(() => {
  editorView.value?.destroy();
  editorView.value = null;
});

// 外部调用：填充代码
const fillValue = (val: string) => {
  const view = editorView.value;
  if (!view) return;
  view.dispatch({
    changes: { from: 0, to: view.state.doc.length, insert: val },
  });
};

defineExpose({ fillValue });

// 监听 modelValue 变化（外部修改时同步到编辑器）
watch(
  () => props.modelValue,
  (newVal) => {
    const view = editorView.value;
    if (!view) return;
    const current = view.state.doc.toString();
    if (current !== newVal) {
      view.dispatch({
        changes: { from: 0, to: current.length, insert: newVal },
      });
    }
  }
);

// 监听语言变化
watch(
  () => props.language,
  (newLang) => {
    const view = editorView.value;
    if (!view || !newLang) return;
    view.dispatch({
      effects: languageCompartment.reconfigure(getLanguageExtension(newLang)),
    });
  }
);

// 监听主题变化
watch(
  () => props.theme,
  (newTheme) => {
    const view = editorView.value;
    if (!view) return;
    view.dispatch({
      effects: themeCompartment.reconfigure(getThemeExtension(newTheme)),
    });
  }
);

// 监听 readOnly 变化
watch(
  () => props.readOnly,
  (newReadOnly) => {
    const view = editorView.value;
    if (!view) return;
    view.dispatch({
      effects: readOnlyCompartment.reconfigure(EditorState.readOnly.of(newReadOnly)),
    });
  }
);
</script>

<style scoped>
.cm-editor-container {
  width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border-radius: 12px;
  border: 1px solid #30363d;
  background: #0d1117;
  box-sizing: border-box;
}

:deep(.cm-editor) {
  height: 100%;
  font-size: 15px;
  line-height: 1.6;
}

:deep(.cm-scroller) {
  font-family: "Menlo", "Monaco", "Courier New", monospace;
}

:deep(.cm-gutters) {
  background: transparent;
  border-right: 1px solid #30363d;
}
</style>
