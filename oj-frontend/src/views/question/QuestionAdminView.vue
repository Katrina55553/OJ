<template>
  <div class="admin-container">
    <!-- 头部操作栏 -->
    <div class="header">
      <h2>题目管理</h2>
      <div class="header-actions">
        <a-space>
          <a-button type="primary" @click="handleAdd">
            <template #icon><icon-plus /></template>
            新增题目
          </a-button>
          <a-button status="warning" @click="refreshData">
            <template #icon><icon-refresh /></template>
            刷新
          </a-button>
        </a-space>
      </div>
    </div>

    <!-- 搜索表单 -->
    <QuestionSearchForm
      v-model="searchParams"
      @search="handleSearch"
      @reset="resetSearch"
    />

    <!-- 题目表格 -->
    <QuestionTable
      :data="tableData"
      :loading="loading"
      :pagination="pagination"
      @page-change="onPageChange"
      @page-size-change="onPageSizeChange"
      @edit="handleEdit"
      @view="handleView"
      @delete="handleDelete"
    />

    <!-- 编辑模态框 -->
    <QuestionEditModal
      v-model:visible="modalVisible"
      :title="modalTitle"
      :form-data="formData"
      :is-edit="isEditMode"
      @success="onEditSuccess"
    />

    <!-- 查看详情模态框 -->
    <QuestionDetailModal
      v-model:visible="detailVisible"
      :detail="currentDetail"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, provide } from "vue"; // 引入 ref
import { useRouter } from "vue-router";
import { Message, Modal } from "@arco-design/web-vue";
import { IconPlus, IconRefresh } from "@arco-design/web-vue/es/icon";
import QuestionSearchForm from "@/views/question/components/QuestionSearchForm.vue";
import QuestionTable from "@/views/question/components/QuestionTable.vue";
import QuestionEditModal from "@/views/question/components/QuestionEditModal.vue";
import QuestionDetailModal from "@/views/question/components/QuestionDetailModal.vue";

import { QuestionControllerService } from "../../../generated/index";
import {
  calculatePassRate,
  parseJudgeConfig,
  parseJsonArray,
} from "@/utils/question";
import type {
  QuestionQueryRequest,
  DeleteRequest,
} from "../../../generated/index";

// 注入 loading 状态给子组件
const loading = ref(false);
provide("questionListLoading", loading);

const router = useRouter();
const tableData = ref<any[]>([]);

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showJumper: true,
  showPageSize: true,
});

const searchParams = ref({
  id: "",
  title: "",
  difficulty: "",
  tags: [] as string[],
});

/** 构造查询参数 */
const buildQueryParams = (): QuestionQueryRequest => {
  const params = searchParams.value;
  const finalTags = [...(params.tags || [])];
  if (params.difficulty) {
    finalTags.push(params.difficulty);
  }
  return {
    current: pagination.current,
    pageSize: pagination.pageSize,
    id: params.id ? Number(params.id) : undefined,
    title: params.title ? params.title.trim() : undefined,
    tags: finalTags.length > 0 ? finalTags : undefined,
  };
};

/** 将后端题目记录转为前端表格行 */
const transformQuestionRecord = (item: any) => {
  const tagsArr = parseJsonArray<string>(item.tags);
  const difficultyKeywords = ["简单", "中等", "困难"];
  return {
    id: item.id || 0,
    title: item.title || "",
    content: item.content || "",
    tags: tagsArr,
    difficulty:
      tagsArr.find((t: string) => difficultyKeywords.includes(t)) || "未知",
    submitNum: item.submitNum || 0,
    acceptedNum: item.acceptedNum || 0,
    passRate: calculatePassRate(item.acceptedNum || 0, item.submitNum || 0),
    answer: item.answer || "",
    timeLimit: parseJudgeConfig(item.judgeConfig).timeLimit,
    memoryLimit: parseJudgeConfig(item.judgeConfig).memoryLimit,
    judgeCases: parseJsonArray(item.judgeCase),
    updateTime: item.updateTime || new Date().toISOString(),
  };
};

/** 加载题目列表 */
const loadData = async () => {
  loading.value = true;
  try {
    const res = await QuestionControllerService.listQuestionByPageUsingPost(
      buildQueryParams()
    );
    if (res.code === 0 && res.data) {
      tableData.value = res.data.records.map(transformQuestionRecord);
      pagination.total = Number(res.data.total) || 0;
    } else {
      Message.error(res.message || "加载题目列表失败");
    }
  } catch (error: any) {
    Message.error("网络错误，请检查连接");
  } finally {
    loading.value = false;
  }
};

// 分页 & 搜索相关
const handleSearch = () => {
  pagination.current = 1;
  loadData();
};

const resetSearch = () => {
  pagination.current = 1;
  loadData();
};

const refreshData = () => loadData();

const onPageChange = (page: number) => {
  pagination.current = page;
  loadData();
};

const onPageSizeChange = (size: number) => {
  pagination.pageSize = size;
  pagination.current = 1;
  loadData();
};

// 模态框相关变量
const modalVisible = ref(false);
const detailVisible = ref(false);
const modalTitle = ref("编辑题目");
const isEditMode = ref(false);

const formData = reactive({
  id: 0,
  title: "",
  content: "",
  tags: [] as string[],
  difficulty: "",
  answer: "",
  timeLimit: 1000,
  memoryLimit: 256,
  stackLimit: 64,
  judgeCases: [] as any[],
});

const currentDetail = ref<any>(null);

// 操作相关
const handleAdd = () => {
  router.push("/question/add");
};

const handleEdit = (record: any) => {
  isEditMode.value = true;
  modalTitle.value = "编辑题目";

  const difficulty = record.difficulty || "";
  const pureTags = record.tags.filter((t: string) => t !== difficulty);

  Object.assign(formData, {
    id: record.id,
    title: record.title,
    content: record.content,
    tags: pureTags,
    difficulty: difficulty,
    answer: record.answer,
    timeLimit: record.timeLimit,
    memoryLimit: record.memoryLimit,
    stackLimit: 64,
    judgeCases: Array.isArray(record.judgeCases)
      ? JSON.parse(JSON.stringify(record.judgeCases))
      : [],
  });

  modalVisible.value = true;
};

const handleView = async (record: any) => {
  try {
    const res = await QuestionControllerService.getQuestionByIdUsingGet(
      record.id
    );
    if (res.code === 0 && res.data) {
      const tags = res.data.tags ? JSON.parse(res.data.tags) : [];
      currentDetail.value = {
        id: res.data.id || 0,
        title: res.data.title || "",
        content: res.data.content || "",
        tags: tags,
        passRate: calculatePassRate(
          res.data.acceptedNum || 0,
          res.data.submitNum || 0
        ),
        timeLimit: parseJudgeConfig(res.data.judgeConfig).timeLimit,
        memoryLimit: parseJudgeConfig(res.data.judgeConfig).memoryLimit,
        answer: res.data.answer || "",
      };
      detailVisible.value = true;
    } else {
      Message.error(res.message || "获取题目详情失败");
    }
  } catch (error: any) {
    Message.error(error.message || "获取详情失败");
  }
};

const handleDelete = (record: any) => {
  Modal.warning({
    title: "确认删除",
    content: `确定要删除题目 "${record.title}" 吗？此操作不可恢复。`,
    okText: "确定删除",
    cancelText: "取消",
    onOk: async () => {
      try {
        const deleteRequest: DeleteRequest = { id: record.id };
        const res = await QuestionControllerService.deleteQuestionUsingPost(
          deleteRequest
        );
        if (res.code === 0) {
          Message.success("删除成功");
          loadData();
        } else {
          Message.error(res.message || "删除失败");
        }
      } catch (error: any) {
        Message.error(error.message || "删除失败");
      }
    },
  });
};

const onEditSuccess = () => {
  modalVisible.value = false;
  loadData();
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.admin-container {
  background-color: #161b22;
  min-height: 100vh;
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 20px;
  background: #161b22;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.header h2 {
  margin: 0;
}
</style>
