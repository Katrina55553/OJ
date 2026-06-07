<template>
  <div class="submission-page">
    <a-card class="general-card" :bordered="false">
      <!-- 头部标题区 -->
      <div class="card-header">
        <div class="title-area">
          <div class="title">提交记录</div>
          <div class="subtitle">Submissions Status</div>
        </div>
        <div class="refresh-btn">
          <a-button shape="circle" size="small" @click="search">
            <icon-refresh />
          </a-button>
        </div>
      </div>

      <!-- 筛选区域 -->
      <div class="filter-area">
        <a-form :model="formModel" layout="inline" class="filter-form">
          <a-form-item field="problemId" hide-label>
            <a-input
              v-model="formModel.problemId"
              placeholder="题目 ID (如 1001)"
              allow-clear
              class="filter-input"
            >
              <template #prefix><icon-code /></template>
            </a-input>
          </a-form-item>

          <a-form-item field="username" hide-label>
            <a-input
              v-model="formModel.username"
              placeholder="提交用户"
              allow-clear
              class="filter-input"
            >
              <template #prefix><icon-user /></template>
            </a-input>
          </a-form-item>

          <a-form-item field="status" hide-label>
            <a-select
              v-model="formModel.status"
              placeholder="判题结果"
              allow-clear
              class="filter-select"
            >
              <template #label="{ data }">
                <span
                  v-if="data?.value"
                  class="select-dot"
                  :style="{ backgroundColor: STATUS_MAP[data.value]?.hex }"
                ></span>
                {{ data?.value || "所有状态" }}
              </template>
              <a-option
                v-for="(val, key) in STATUS_MAP"
                :key="key"
                :value="key"
              >
                <span class="status-option">
                  <span
                    class="status-dot"
                    :style="{ backgroundColor: val.hex }"
                  ></span>
                  {{ key }}
                </span>
              </a-option>
            </a-select>
          </a-form-item>

          <a-form-item hide-label>
            <a-space>
              <a-button type="primary" @click="search">
                <template #icon><icon-search /></template>
                查询
              </a-button>
              <a-button @click="reset">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </div>

      <!-- 卡片式提交记录列表 -->
      <div class="submission-list">
        <div
          v-for="record in renderData"
          :key="record.id"
          class="submission-item"
          @click="openCodeModal(record)"
        >
          <!-- 左侧：用户头像 + 用户名 + 时间 -->
          <div class="user-info">
            <a-avatar
              :size="40"
              :style="{ backgroundColor: '#165dff', fontSize: '16px' }"
            >
              {{ record.username[0].toUpperCase() }}
            </a-avatar>
            <div class="user-detail">
              <div class="username">{{ record.username }}</div>
              <div class="submit-time">{{ record.submitTime }}</div>
            </div>
          </div>

          <!-- 中间：状态 + 题目 -->
          <div class="middle-info">
            <a-tag class="status-tag" :color="getStatusColor(record.status)">
              {{ record.status }}
            </a-tag>

            <router-link
              class="problem-link"
              :to="`/question/${record.problemId}`"
              @click.stop
            >
              P{{ record.problemId }} {{ record.problemTitle }}
            </router-link>
          </div>

          <!-- 右侧：运行指标 + 语言 -->
          <div class="stats-info">
            <div class="stats-line">
              <icon-clock-circle class="stat-icon" /> {{ record.timeCost }} ms
              &nbsp; <icon-storage class="stat-icon" />
              {{ formatMemory(record.memoryCost) }} &nbsp;
              {{ formatCodeLength(record.codeLength || 2400) }} B
            </div>
            <div class="language-tag">
              <a-tag size="small" bordered>{{ record.language }}</a-tag>
            </div>
          </div>
        </div>
      </div>

      <!-- 分页 -->
      <a-pagination
        v-if="pagination.total > pagination.pageSize"
        v-model:current="pagination.current"
        :page-size="pagination.pageSize"
        :total="pagination.total"
        show-less-items
        style="margin-top: 24px; text-align: center"
        @change="onPageChange"
      />
    </a-card>

    <!-- 代码详情模态框 -->
    <a-modal
      v-model:visible="codeModalVisible"
      title="代码详情"
      :width="960"
      :footer="false"
      :mask-closable="true"
      :esc-to-close="true"
      class="code-detail-modal"
    >
      <div class="modal-header">
        <a-tag>{{ currentLanguage }}</a-tag>
        <span class="run-id">Run ID: {{ currentRunId }}</span>
      </div>
      <MdPreview :value="markdownCode" class="code-preview" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, computed } from "vue";
import { Message } from "@arco-design/web-vue";
import MdPreview from "@/components/MdPreview.vue";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
dayjs.extend(utc);

import {
  QuestionControllerService,
  UserControllerService,
  UserQueryRequest,
} from "../../generated";
import type { QuestionSubmitQueryRequest } from "../../generated";

/**
 * 状态映射（整数 → 字符串 + 颜色）
 * 后端 status：
 * 0 - 待判题
 * 1 - 判题中
 * 2 - 成功（Accepted）
 * 3 - 失败（后续可根据 judgeInfo.message 细化）
 */
const STATUS_MAP: Record<string, { color: string; hex: string }> = {
  Accepted: { color: "green", hex: "#00b42a" },
  "Wrong Answer": { color: "red", hex: "#f53f3f" },
  "Time Limit Exceeded": { color: "orange", hex: "#ff7d00" },
  "Memory Limit Exceeded": { color: "orange", hex: "#ff7d00" },
  "Compilation Error": { color: "gold", hex: "#f7ba1e" },
  "Runtime Error": { color: "magenta", hex: "#f5319d" },
  Pending: { color: "blue", hex: "#165dff" },
  Judging: { color: "arcoblue", hex: "#526ecc" },
};

const loading = ref(false);
const renderData = ref<any[]>([]);

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
});

const formModel = reactive({
  problemId: "",
  username: "",
  status: "",
});

// 模态框相关
const codeModalVisible = ref(false);
const currentRunId = ref<number | null>(null);
const currentLanguage = ref("");
const currentSource = ref("");

const getStatusColor = (status: string) => {
  return STATUS_MAP[status]?.color || "gray";
};

const formatMemory = (kb?: number) => {
  if (!kb) return "0 KB";
  if (kb >= 1024) return (kb / 1024).toFixed(2) + " MB";
  return kb + " KB";
};

const formatCodeLength = (bytes?: number) => {
  return bytes ? bytes.toLocaleString() : "0";
};

const markdownCode = computed(() => {
  if (!currentSource.value) return "";
  let lang = currentLanguage.value.toLowerCase();
  const langMap: Record<string, string> = {
    cpp: "cpp",
    "c++": "cpp",
    java: "java",
    python: "python",
    python3: "python",
    go: "go",
    javascript: "javascript",
    js: "javascript",
  };
  const normalizedLang = langMap[lang] || lang || "text";
  return "```" + normalizedLang + "\n" + currentSource.value.trim() + "\n```";
});

// 点击打开代码模态框
const openCodeModal = (record: any) => {
  currentRunId.value = record.id;
  currentLanguage.value = record.language;
  currentSource.value = record.code || "// 无代码内容";
  codeModalVisible.value = true;
};

/** 解析 judgeInfo（兼容字符串和对象） */
const parseJudgeInfo = (raw: any) => {
  if (!raw) return null;
  if (typeof raw === "string") {
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }
  return raw;
};

/** 根据 status 和 judgeInfo 确定显示状态 */
const getDisplayStatus = (status: number, judgeInfo: any) => {
  if (status === 2) return judgeInfo?.message || "Accepted";
  if (status === 3) return "Wrong Answer";
  if (status === 1) return "Judging";
  if (status === 0) return "Pending";
  return `Unknown(${status})`;
};

/** 将后端提交记录转为前端表格行 */
const transformSubmitRecord = (item: any) => {
  const judgeInfo = parseJudgeInfo(item.judgeInfo);
  return {
    id: item.id,
    problemId: item.questionId,
    problemTitle: item.questionVO?.title,
    username: item.userVO?.userName || `用户${item.userId}`,
    userAvatar: item.userVO?.userAvatar || "",
    status: getDisplayStatus(item.status, judgeInfo),
    language: item.language || "Unknown",
    timeCost: item.status === 2 ? judgeInfo?.time || 0 : 0,
    memoryCost: item.status === 2 ? judgeInfo?.memory || 0 : 0,
    codeLength: item.code?.length || 0,
    submitTime: item.createTime
      ? dayjs(item.createTime).local().format("YYYY-MM-DD HH:mm:ss")
      : "未知时间",
    code: item.code,
    judgeInfo,
  };
};

/** 构造查询参数 */
const buildQueryParams = (): QuestionSubmitQueryRequest => ({
  current: pagination.current,
  pageSize: pagination.pageSize,
  questionId: formModel.problemId ? Number(formModel.problemId) : undefined,
  status: formModel.status ? getStatusInt(formModel.status) : undefined,
});

/** 获取提交记录列表 */
const fetchData = async () => {
  loading.value = true;
  try {
    const res =
      await QuestionControllerService.listQuestionSubmitByPageUsingPost(
        buildQueryParams()
      );

    if (res.code === 0 && res.data) {
      const pageData = res.data;
      renderData.value = pageData.records.map(transformSubmitRecord);
      pagination.total = pageData.total || 0;
      pagination.current = pageData.current || 1;
      pagination.pageSize = pageData.size || 20;
    } else {
      Message.error(res.message || "加载失败");
      renderData.value = [];
      pagination.total = 0;
    }
  } catch (e: any) {
    Message.error("请求失败");
    renderData.value = [];
  } finally {
    loading.value = false;
  }
};

// 将前端显示状态转回后端整数（用于筛选）
const getStatusInt = (displayStatus: string): number | undefined => {
  const map: Record<string, number> = {
    Pending: 0,
    Judging: 1,
    Accepted: 2,
    "Wrong Answer": 3,
  };
  return map[displayStatus];
};

// 查询、重置、分页
const search = () => {
  pagination.current = 1;
  fetchData();
};

const reset = () => {
  formModel.problemId = "";
  formModel.username = "";
  formModel.status = "";
  pagination.current = 1;
  fetchData();
};

const onPageChange = (page: number) => {
  pagination.current = page;
  fetchData();
};

// 筛选条件变化自动查询
watch(
  formModel,
  () => {
    search();
  },
  { deep: true }
);

onMounted(() => {
  fetchData();
});
</script>

<style scoped>
.submission-page {
  background-color: #f7f8fa;
  padding: 16px;
  min-height: 100vh;
  max-width: 1200px;
  margin: 0 auto;
}

.general-card {
  border-radius: 8px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.03);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: #1d2129;
}

.subtitle {
  font-size: 11px;
  color: #86909c;
  margin-top: 2px;
}

.filter-input {
  width: 200px;
  height: 32px;
  font-size: 14px;
}

.filter-select {
  width: 180px;
  height: 32px;
  font-size: 14px;
}

/* 卡片列表 */
.submission-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 20px;
}

.submission-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 3px rgba(26, 26, 26, 0.1);
  cursor: pointer;
  transition: all 0.2s ease;
  font-size: 15px;
  color: rgba(0, 0, 0, 0.75);
}

.submission-item:hover {
  box-shadow: 0 4px 12px rgba(26, 26, 26, 0.15);
  transform: translateY(-2px);
}

/* 左侧用户区 */
.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 200px;
}

.user-detail {
  display: flex;
  flex-direction: column;
  line-height: 1.4;
}

.username {
  font-weight: 600;
  color: #1d2129;
}

.submit-time {
  font-size: 13px;
  color: #86909c;
}

/* 中间状态 + 题目 */
.middle-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16px;
}

.status-tag {
  font-size: 13px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 4px;
}

.problem-link {
  color: #3498db;
  font-weight: 500;
  text-decoration: none;
  font-size: 15px;
}

.problem-link:hover {
  text-decoration: underline;
}

/* 右侧：运行指标 + 语言 */
.stats-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  min-width: 220px;
}

.stats-line {
  font-family: "Menlo", "Monaco", "Courier New", monospace;
  font-size: 14px;
  color: #4e5969;
  white-space: nowrap;
}

.stat-icon {
  font-size: 14px;
  margin: 0 4px;
  color: #86909c;
}

.language-tag {
  margin-top: 4px;
}

/* 模态框 */
.code-detail-modal :deep(.arco-modal-content) {
  border-radius: 12px;
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fafafa;
  border-bottom: 1px solid #f0f0f0;
  margin: -24px -24px 20px -24px;
}

.run-id {
  font-size: 13px;
  color: #86909c;
}

.code-preview {
  padding: 0 !important;
  background: transparent !important;
  border: none !important;
  box-shadow: none !important;
  overflow: hidden;
}

.code-preview :deep(pre) {
  margin: 0 !important;
  border-radius: 0 !important;
  max-height: 70vh;
  overflow-y: auto;
}
</style>
