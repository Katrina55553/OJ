<template>
  <a-modal
    v-model:visible="visible"
    title="题目详情"
    width="900px"
    :footer="false"
    @cancel="handleCancel"
  >
    <div v-if="detail" class="detail-content">
      <h3>{{ detail.title }}</h3>
      <div class="detail-meta">
        <a-space wrap size="large">
          <span><strong>ID：</strong>{{ detail.id }}</span>
          <span
            class="difficulty-badge"
            :class="`difficulty-${detail.difficulty}`"
            >{{ detail.difficulty }}</span
          >
          <span><strong>通过率：</strong>{{ detail.passRate }}%</span>
          <span><strong>时间限制：</strong>{{ detail.timeLimit }} ms</span>
          <span><strong>内存限制：</strong>{{ detail.memoryLimit }} MB</span>
        </a-space>
      </div>

      <a-divider />

      <div class="detail-section">
        <h4>题目描述</h4>
        <div class="content-box">{{ detail.content }}</div>
      </div>

      <a-divider />

      <div class="detail-section">
        <h4>参考答案代码</h4>
        <pre class="code-box">{{ detail.answer }}</pre>
      </div>

      <a-divider />

      <div class="detail-section">
        <h4>判题配置</h4>
        <a-descriptions :column="2" bordered size="medium">
          <a-descriptions-item label="时间限制">
            <strong>{{ detail.timeLimit }} ms</strong>
          </a-descriptions-item>
          <a-descriptions-item label="内存限制">
            <strong>{{ detail.memoryLimit }} MB</strong>
          </a-descriptions-item>
        </a-descriptions>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  visible: boolean;
  detail: {
    id: number;
    title: string;
    content: string;
    difficulty: string;
    passRate: number;
    timeLimit: number;
    memoryLimit: number;
    answer: string;
  } | null;
}>();

const emit = defineEmits<{
  "update:visible": [value: boolean];
}>();

const visible = computed({
  get: () => props.visible,
  set: (val) => emit("update:visible", val),
});

// 关闭模态框
const handleCancel = () => {
  emit("update:visible", false);
};
</script>

<style scoped>
.detail-content {
  max-height: 70vh;
  overflow-y: auto;
}
.detail-content h3 {
  margin-top: 0;
  color: #f0f6fc;
}
.detail-meta {
  margin: 12px 0;
  color: #8b949e;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.difficulty-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 4px;
}

.difficulty-easy {
  background: #0d4429;
  color: #3fb950;
}

.difficulty-medium {
  background: #3d2800;
  color: #f0883e;
}

.difficulty-hard {
  background: #490202;
  color: #f85149;
}

.detail-section {
  margin: 24px 0;
}
.detail-section h4 {
  margin-bottom: 12px;
  color: #f0f6fc;
}
.content-box {
  padding: 16px;
  background: #161b22;
  border-radius: 4px;
  white-space: pre-wrap;
  line-height: 1.6;
}
.code-box {
  padding: 16px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 4px;
  font-family: "Consolas", "Monaco", monospace;
  overflow-x: auto;
}
</style>
