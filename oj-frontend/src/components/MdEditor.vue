<template>
  <Editor :value="modelValue" :plugins="plugins" @change="onChange" />
</template>

<script setup lang="ts">
import { Editor } from "@bytemd/vue-next";
import gfm from "@bytemd/plugin-gfm";
import highlight from "@bytemd/plugin-highlight";
import "bytemd/dist/index.css";
import "highlight.js/styles/vs.css";

const props = defineProps<{
  modelValue: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", v: string): void;
}>();

const plugins = [gfm(), highlight()];

const onChange = (v: unknown) => {
  if (typeof v === "string") {
    emit("update:modelValue", v);
  } else {
    console.error("❌ MdEditor 收到非字符串:", v);
  }
};
</script>
