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
        <!-- 标签列 -->
        <template #tags="{ record }">
          <a-space wrap>
            <a-tag
              v-for="tag in record.tags"
              :key="tag"
              color="blue"
              size="small"
            >
              {{ tag }}
            </a-tag>
          </a-space>
        </template>

        <!-- 难度列 -->
        <template #difficulty="{ record }">
          <a-tag :color="getDifficultyColor(record.difficulty)" size="small">
            {{ record.difficulty }}
          </a-tag>
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

interface QuestionRecord {
  id: number;
  title: string;
  tags: string[];
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

// 难度颜色函数
const getDifficultyColor = (difficulty: string): string => {
  switch (difficulty) {
    case "简单":
      return "green";
    case "中等":
      return "orange";
    case "困难":
      return "red";
    default:
      return "gray";
  }
};

// 表格列定义（重点：题号列使用 pagination 和 rowIndex）
const columns: TableColumnData[] = [
  {
    title: "题号",
    width: 80,
    dataIndex: "id",
    align: "center",
  },
  { title: "题目标题", dataIndex: "title", ellipsis: true, tooltip: true },
  { title: "标签", slotName: "tags", width: 200 },
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
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}
</style>
