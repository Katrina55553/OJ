<template>
  <div class="search-form">
    <a-form layout="inline" :model="formModel">
      <a-form-item label="题目 ID">
        <a-input
          v-model="formModel.id"
          placeholder="题目 ID"
          allow-clear
          style="width: 120px"
          @press-enter="handleSearch"
        />
      </a-form-item>

      <a-form-item label="题目标题">
        <a-input
          v-model="formModel.title"
          placeholder="输入标题"
          allow-clear
          style="width: 160px"
          @press-enter="handleSearch"
        />
      </a-form-item>

      <a-form-item label="难度">
        <a-select
          v-model="formModel.difficulty"
          placeholder="难度"
          allow-clear
          style="width: 100px"
          @change="handleSearch"
        >
          <a-option value="简单">简单</a-option>
          <a-option value="中等">中等</a-option>
          <a-option value="困难">困难</a-option>
        </a-select>
      </a-form-item>

      <a-form-item>
        <a-space>
          <a-button type="primary" @click="handleSearch" :loading="loading">
            <template #icon><icon-search /></template>
            搜索
          </a-button>
          <a-button @click="handleReset">
            <template #icon><icon-rotate-left /></template>
            重置
          </a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, inject } from "vue";
import { IconSearch, IconRotateLeft } from "@arco-design/web-vue/es/icon";

const loading = inject("questionListLoading", ref(false));

const props = defineProps<{
  modelValue: {
    id: string;
    title: string;
    difficulty: string;
  };
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: any): void;
  (e: "search"): void;
  (e: "reset"): void;
}>();

const formModel = ref({ ...props.modelValue });

watch(
  formModel,
  (newVal) => {
    emit("update:modelValue", newVal);
  },
  { deep: true }
);

watch(
  () => props.modelValue,
  (newVal) => {
    if (JSON.stringify(newVal) !== JSON.stringify(formModel.value)) {
      formModel.value = { ...newVal };
    }
  },
  { deep: true }
);

const handleSearch = () => {
  emit("search");
};

const handleReset = () => {
  formModel.value = {
    id: "",
    title: "",
    difficulty: "",
  };
  emit("update:modelValue", formModel.value);
  emit("reset");
};
</script>

<style scoped>
.search-form {
  background: #161b22;
  padding: 20px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  margin-bottom: 20px;
}
</style>
