<template>
  <div class="table-card">
    <a-spin :loading="loading">
      <a-table
        :columns="columns"
        :data="data"
        :pagination="pagination"
        :bordered="{ cell: true }"
        @page-change="emit('page-change', $event)"
        @page-size-change="emit('page-size-change', $event)"
      >
        <!-- 难度列 -->
        <template #difficulty="{ record }">
          <span
            class="difficulty-badge"
            :class="`difficulty-${record.difficulty}`"
            >{{ difficultyLabel(record.difficulty) }}</span
          >
        </template>

        <!-- 通过率列 -->
        <template #passRate="{ record }">
          <a-progress
            :percent="record.passRate"
            :stroke-width="20"
            :animation="true"
            size="small"
            :format="(percent: number) => `${percent}%`"
          />
        </template>

        <!-- 操作列 -->
        <template #operations="{ record }">
          <a-space>
            <a-button
              type="text"
              size="small"
              @click="emit('edit', record)"
              title="编辑"
            >
              <template #icon><icon-edit /></template>
            </a-button>
            <a-button
              type="text"
              size="small"
              @click="emit('view', record)"
              title="查看详情"
            >
              <template #icon><icon-eye /></template>
            </a-button>
            <a-button
              type="text"
              size="small"
              status="danger"
              @click="emit('delete', record)"
              title="删除"
            >
              <template #icon><icon-delete /></template>
            </a-button>
          </a-space>
        </template>
      </a-table>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { IconEdit, IconEye, IconDelete } from "@arco-design/web-vue/es/icon";
import type { TableColumnData } from "@arco-design/web-vue";
import { difficultyLabel } from "@/utils/question";

interface QuestionRecord {
  id: number;
  title: string;
  difficulty: string;
  acceptedNum: number;
  submitNum: number;
  passRate: number;
  updateTime: string;
}

// 接收父组件传来的 props，并明确类型
const props = defineProps<{
  data: QuestionRecord[];
  loading: boolean;
  pagination: {
    current: number;
    pageSize: number;
    total?: number;
    [key: string]: any;
  };
}>();

const emit = defineEmits<{
  "page-change": [page: number];
  "page-size-change": [size: number];
  edit: [record: QuestionRecord];
  view: [record: QuestionRecord];
  delete: [record: QuestionRecord];
}>();

// 表格列定义（重点：题号列使用 pagination 和 rowIndex）
const columns: TableColumnData[] = [
  {
    title: "题号",
    width: 80,
    dataIndex: "id",
    align: "center",
  },
  { title: "题目标题", dataIndex: "title", ellipsis: true, tooltip: true },
  { title: "难度", slotName: "difficulty", width: 100, align: "center" },
  {
    title: "提交/通过",
    width: 150,
    render: ({ record }: { record: QuestionRecord }) =>
      `${record.acceptedNum}/${record.submitNum}`,
  },
  { title: "通过率", slotName: "passRate", width: 150 },
  {
    title: "更新时间",
    dataIndex: "updateTime",
    width: 180,
    render: ({ record }: { record: QuestionRecord }) =>
      new Date(record.updateTime).toLocaleString(),
  },
  { title: "操作", slotName: "operations", width: 180, fixed: "right" },
];
</script>

<style scoped>
.table-card {
  padding: 20px;
  background: #161b22;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
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
</style>
