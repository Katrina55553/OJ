<template>
  <div class="contest-detail-view">
    <!-- 顶部 Banner：展示比赛概况 -->
    <div class="contest-header" v-if="contest.title">
      <div class="header-content">
        <div class="contest-status-badge">
          <a-tag v-if="contest.status === '进行中'" color="green" size="large">
            <template #icon><icon-play-circle-fill /></template>
            进行中
          </a-tag>
          <a-tag
            v-else-if="contest.status === '未开始'"
            color="arcoblue"
            size="large"
          >
            <template #icon><icon-schedule /></template>
            未开始
          </a-tag>
          <a-tag v-else color="gray" size="large">
            <template #icon><icon-stop /></template>
            已结束
          </a-tag>
        </div>

        <h1 class="contest-title">{{ contest.title }}</h1>

        <!-- 时间进度区域 -->
        <div class="time-info">
          <div class="time-block">
            <span class="label">开始时间</span>
            <span class="time">{{ contest.startTime }}</span>
          </div>
          <div class="progress-bar">
            <a-progress
              :percent="progressPercent"
              :color="progressColor"
              :animation="true"
              size="large"
            />
          </div>
          <div class="time-block">
            <span class="label">结束时间</span>
            <span class="time">{{ contest.endTime }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 无比赛时的提示 -->
    <div v-else class="no-contest-placeholder">
      <a-empty description="暂无比赛信息" />
    </div>

    <!-- 主内容区：题目列表 -->
    <div class="main-container" v-if="contest.title">
      <a-card class="problem-list-card" :bordered="false">
        <template #title>
          <div class="card-title">
            <icon-list style="margin-right: 8px" /> 题目列表
          </div>
        </template>

        <a-table
          v-if="problemList.length > 0"
          row-key="id"
          :columns="columns"
          :data="problemList"
          :pagination="false"
          stripe
          :bordered="{ wrapper: false, cell: false }"
        >
          <!-- 题号列 -->
          <template #id="{ record }">
            <span class="problem-id">{{ record.letter || record.id }}</span>
          </template>

          <!-- 难度列 -->
          <template #difficulty="{ record }">
            <a-tag :color="difficultyColor(record.difficulty)" bordered>
              {{ record.difficulty }}
            </a-tag>
          </template>

          <!-- 状态列：用图标代替文字 -->
          <template #status="{ record }">
            <div v-if="record.status === 'AC'" class="status-ac">
              <icon-check /> 已通过
            </div>
            <div v-else class="status-none"><icon-minus /> 未尝试</div>
          </template>

          <!-- 操作列 -->
          <template #action="{ record }">
            <a-button
              type="outline"
              size="small"
              shape="round"
              class="do-btn"
              @click="goQuestion(record.id)"
            >
              <template #icon><icon-code /></template>
              去挑战
            </a-button>
          </template>
        </a-table>

        <a-empty v-else description="暂无题目" />
      </a-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  IconPlayCircleFill,
  IconSchedule,
  IconStop,
  IconList,
  IconCheck,
  IconMinus,
  IconCode,
} from "@arco-design/web-vue/es/icon";
import { difficultyColor } from "@/utils/question";

// TODO: 接口引入
// import { ContestControllerService } from "@/generated";

interface ContestInfo {
  id: number;
  title: string;
  description: string;
  status: string;
  startTime: string;
  endTime: string;
  [key: string]: unknown;
}

interface ContestProblem {
  id: number;
  title: string;
  difficulty: string;
  status: string;
  [key: string]: unknown;
}

const route = useRoute();
const router = useRouter();
const contestId = Number(route.params.id);

const contest = ref<ContestInfo>({} as ContestInfo);
const problemList = ref<ContestProblem[]>([]);

// 计算进度条百分比
const progressPercent = computed(() => {
  if (contest.value.status === "已结束") return 1;
  if (contest.value.status === "未开始") return 0;
  return 0.6; // 默认进行中 60%（实际应根据时间计算）
});

const progressColor = computed(() => {
  if (contest.value.status === "进行中") return "#00b42a"; // 绿色
  if (contest.value.status === "已结束") return "#86909c"; // 灰色
  return "#165dff"; // 蓝色
});

const columns = [
  { title: "序号", slotName: "id", width: 80, align: "center" as const },
  { title: "题目名称", dataIndex: "title" },
  {
    title: "难度",
    slotName: "difficulty",
    width: 100,
    align: "center" as const,
  },
  { title: "分数", dataIndex: "score", width: 100, align: "center" as const },
  {
    title: "解答状态",
    slotName: "status",
    width: 120,
    align: "center" as const,
  },
  { title: "操作", slotName: "action", width: 140, align: "center" as const },
];

const loadContest = async () => {
  // TODO: 后端接口调用
  // const res = await ContestControllerService.getContestByIdUsingGet(contestId);
  // contest.value = res.data || {};

  // 模拟无数据（已删除原模拟数据）
  contest.value = {};
};

const loadProblems = async () => {
  // TODO: 后端接口调用
  // 如果有比赛，再请求题目列表
  if (contest.value.title) {
    // problemList.value = ...
  } else {
    problemList.value = [];
  }
};

const goQuestion = (id: number) => {
  router.push(`/question/${id}?contestId=${contestId}`);
};

onMounted(() => {
  loadContest().then(() => {
    loadProblems();
  });
});
</script>

<style scoped>
.contest-detail-view {
  min-height: 100vh;
  background-color: #f7f8fa;
}

/* 无比赛占位样式 */
.no-contest-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
  padding: 40px;
}

/* 顶部 Banner */
.contest-header {
  background: linear-gradient(135deg, #165dff 0%, #722ed1 100%);
  color: #fff;
  padding: 40px 20px 100px;
  text-align: center;
}

.header-content {
  max-width: 1000px;
  margin: 0 auto;
}

.contest-title {
  font-size: 32px;
  font-weight: 700;
  margin: 16px 0 32px;
  text-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
}

/* 时间进度条区域 */
.time-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  background: rgba(255, 255, 255, 0.1);
  padding: 20px 40px;
  border-radius: 12px;
  backdrop-filter: blur(10px);
}

.time-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 140px;
}

.time-block .label {
  font-size: 12px;
  opacity: 0.8;
  margin-bottom: 4px;
}

.time-block .time {
  font-size: 16px;
  font-weight: 600;
}

.progress-bar {
  flex: 1;
  max-width: 400px;
}

:deep(.arco-progress-text) {
  color: #fff !important;
}

/* 主内容区 */
.main-container {
  max-width: 1200px;
  margin: -60px auto 40px;
  padding: 0 16px;
  position: relative;
  z-index: 1;
}

.problem-list-card {
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.05);
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #1d2129;
  display: flex;
  align-items: center;
}

.problem-id {
  font-weight: bold;
  color: #165dff;
}

.status-ac {
  color: #00b42a;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  font-weight: 500;
}

.status-none {
  color: #c9cdd4;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.do-btn:hover {
  background-color: #165dff;
  color: #fff;
}

:deep(.arco-table-th) {
  background-color: #f7f8fa;
  font-weight: 600;
}
</style>
