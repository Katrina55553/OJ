<template>
  <div class="home-view">
    <!-- 顶部 Banner -->
    <div class="banner">
      <div class="banner-content">
        <h1 class="page-title">
          <icon-code-square style="margin-right: 12px" /> 题目列表
        </h1>
        <p class="page-sub-title">挑战自我，提升编程能力的最佳试炼场</p>
      </div>
    </div>

    <div class="main-container">
      <!-- 搜索组件 -->
      <QuestionSearchForm
        v-model="searchParams"
        @search="doSearch"
        @reset="doSearch"
        class="search-section"
      />

      <!-- 题目列表 -->
      <a-spin
        :loading="loading"
        tip="正在加载题目..."
        style="width: 100%; min-height: 300px"
      >
        <div class="problem-list-container">
          <a-empty
            v-if="problemList.length === 0 && !loading"
            description="未找到相关题目"
          />

          <div
            v-for="item in problemList"
            :key="item.id"
            class="problem-row-item"
            @click="toProblemDetail(item.id)"
          >
            <!-- ID & 标题 -->
            <div class="row-left">
              <span class="problem-id">#{{ item.id }}</span>
              <span class="problem-title" :title="item.title">
                {{ item.title }}
              </span>
            </div>

            <!-- 标签 & 难度 -->
            <div class="row-middle">
              <a-space wrap>
                <a-tag
                  v-for="tag in item.tags"
                  :key="tag"
                  color="gray"
                  size="small"
                  bordered
                >
                  {{ tag }}
                </a-tag>
              </a-space>
              <a-tag
                size="small"
                :color="difficultyColor(item.difficulty)"
                style="margin-left: 16px; width: 50px; text-align: center"
              >
                {{ item.difficulty }}
              </a-tag>
            </div>

            <!-- 数据 & 按钮 -->
            <div class="row-right">
              <div class="stat-info">
                <span class="label">通过率</span>
                <span class="value">{{ item.passRate }}%</span>
              </div>
              <a-button
                type="primary"
                status="success"
                size="small"
                shape="round"
                class="enter-btn"
                @click.stop="toProblemDetail(item.id)"
              >
                <template #icon><icon-edit /></template>
                做题
              </a-button>
            </div>
          </div>
        </div>
      </a-spin>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <a-pagination
          :total="total"
          :current="searchParams.current"
          :page-size="searchParams.pageSize"
          show-total
          show-jumper
          @change="onPageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, provide } from "vue";
import { useRouter } from "vue-router";
import { IconCodeSquare, IconEdit } from "@arco-design/web-vue/es/icon";
import { Message } from "@arco-design/web-vue";
import QuestionSearchForm from "./components/QuestionSearchForm.vue";
import { QuestionControllerService } from "../../../generated/index";
import { difficultyColor, calculatePassRate } from "@/utils/question";

const loading = ref(true);
provide("questionListLoading", loading);

const router = useRouter();

interface Problem {
  id: number;
  title: string;
  difficulty: string;
  tags: string[];
  passRate: number;
}

const problemList = ref<Problem[]>([]);
const total = ref(0);

const searchParams = ref({
  current: 1,
  pageSize: 10,
  id: "",
  title: "",
  difficulty: "",
  tags: [] as string[],
});

const loadData = async () => {
  loading.value = true;
  try {
    const params = searchParams.value;

    const queryTags = [...(params.tags || [])];
    if (params.difficulty) {
      queryTags.push(params.difficulty);
    }

    const requestBody = {
      current: params.current,
      pageSize: params.pageSize,
      id: params.id ? Number(params.id) : undefined,
      title: params.title ? params.title.trim() : undefined,
      tags: queryTags.length > 0 ? queryTags : undefined,
    };

    const res = await QuestionControllerService.listQuestionVoByPageUsingPost(
      requestBody
    );

    if (res.code === 0 && res.data) {
      problemList.value = res.data.records.map((item: any) => ({
        id: item.id,
        title: item.title,
        difficulty: item.difficulty || item.tags?.[0] || "未知",
        tags: item.tags || [],
        passRate: calculatePassRate(item.acceptedNum, item.submitNum),
      }));
      total.value = Number(res.data.total) || 0;
    } else {
      Message.error(res.message || "加载题目失败");
    }
  } catch (error: any) {
    console.error("加载题目失败:", error);
    Message.error("网络错误，请稍后重试");
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadData();
});

const doSearch = () => {
  searchParams.value.current = 1;
  loadData();
};

const onPageChange = (page: number) => {
  searchParams.value.current = page;
  loadData();
};

const toProblemDetail = (id: number) => {
  router.push(`/question/${id}`);
};
</script>

<style scoped>
.home-view {
  min-height: 100vh;
  background-color: #161b22;
}

.banner {
  background: linear-gradient(135deg, #1a2a3a 0%, #293448 100%);
  color: #161b22;
  padding: 60px 20px 80px;
  text-align: center;
}

.page-title {
  font-size: 32px;
  margin: 0 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.page-sub-title {
  margin: 0;
  color: rgba(255, 255, 255, 0.8);
  font-size: 16px;
}

.main-container {
  max-width: 1200px;
  margin: -40px auto 0;
  padding: 0 24px;
  position: relative;
  z-index: 10;
}

.search-section {
  background: #161b22;
  padding: 24px;
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  margin-bottom: 24px;
}

.problem-list-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.problem-row-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #161b22;
  padding: 16px 24px;
  border-radius: 8px;
  border: 1px solid #30363d;
  transition: all 0.2s ease;
  cursor: pointer;
}

.problem-row-item:hover {
  border-color: #c9cdd4;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  transform: translateY(-2px);
}

.row-left {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.problem-id {
  color: #8b949e;
  font-weight: bold;
  margin-right: 16px;
  min-width: 40px;
}

.problem-title {
  font-size: 16px;
  font-weight: 500;
  color: #f0f6fc;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-middle {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-basis: 300px;
  margin: 0 24px;
}

.row-right {
  display: flex;
  align-items: center;
  gap: 24px;
  min-width: 180px;
  justify-content: flex-end;
}

.stat-info {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.2;
}

.stat-info .label {
  font-size: 12px;
  color: #8b949e;
}

.stat-info .value {
  font-size: 14px;
  font-weight: bold;
  color: #f0f6fc;
}

.pagination-wrapper {
  margin: 40px 0;
  display: flex;
  justify-content: center;
}

@media (max-width: 768px) {
  .problem-row-item {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .row-middle,
  .row-right {
    margin: 0;
    width: 100%;
    justify-content: space-between;
    flex-basis: auto;
  }
  .row-left {
    width: 100%;
  }
}
</style>
