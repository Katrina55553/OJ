<template>
  <div class="question-heatmap">
    <h3 class="heatmap-title">做题热度图</h3>
    <div class="heatmap-wrapper">
      <!-- 月份标签 -->
      <div class="months">
        <div v-for="month in months" :key="month" class="month-label">
          {{ month }}
        </div>
      </div>

      <div class="graph">
        <!-- 星期标签（左侧） -->
        <div class="weekdays">
          <div class="weekday" v-for="day in weekdayLabels" :key="day">
            {{ day }}
          </div>
        </div>

        <!-- 主网格：每一列是一周 -->
        <div class="weeks">
          <div v-for="(week, weekIndex) in weeks" :key="weekIndex" class="week">
            <div
              v-for="day in week"
              :key="day.date"
              class="day"
              :class="getColorClass(day.count)"
              :title="formatTooltip(day)"
            >
              <span class="tooltip">{{ formatTooltip(day) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 图例 -->
      <div class="legend">
        <span>更少</span>
        <div class="legend-blocks">
          <div class="day level-0"></div>
          <div class="day level-1"></div>
          <div class="day level-2"></div>
          <div class="day level-3"></div>
          <div class="day level-4"></div>
        </div>
        <span>更多</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, watch } from "vue";
import { Message } from "@arco-design/web-vue";
import dayjs from "dayjs";
import axios from "axios";

// 🔴 新增 props
const props = defineProps<{
  userId?: number;
  days?: number;
}>();

interface DayData {
  date: string;
  count: number;
}

const data = ref<DayData[]>([]);
const weeks = ref<Array<{ date: string; count: number }[]>>([]);

// 🔗 真实接口调用
const loadHeatmapData = async (userId?: number) => {
  try {
    const uid = userId || props.userId || 1; // 🔴 替换为你的用户获取逻辑
    const days = props.days || 365;

    const response = await axios.get("/api/user/question/statistics/heatmap", {
      params: { user_id: uid, days },
      timeout: 10000,
    });

    const result = response.data;
    console.log("热力图返回:", { userId: uid, days, count: result.data?.length, total: result.total });

    if (result.success && Array.isArray(result.data)) {
      data.value = result.data; // ✅ 后端返回格式已匹配，直接赋值
      generateWeeks(); // 🔄 重新生成网格
    } else {
      throw new Error(result.message || "接口返回格式错误");
    }
  } catch (e: any) {
    console.error("❌ 加载热力图失败:", e);
    Message.error(`加载失败：${e.message || "未知错误"}`);
    data.value = [];
    generateWeeks();
  }
};

const dataMap = computed(() => {
  const map = new Map<string, number>();
  data.value.forEach((d) => map.set(d.date, d.count));
  return map;
});

const generateWeeks = () => {
  const today = dayjs();
  const startDate = today.subtract(51 * 7, "day").startOf("week");

  const newWeeks: typeof weeks.value = [];
  let currentDate = startDate;

  for (let w = 0; w < 53; w++) {
    const week: { date: string; count: number }[] = [];
    for (let d = 0; d < 7; d++) {
      const dateStr = currentDate.format("YYYY-MM-DD");
      week.push({
        date: dateStr,
        count: dataMap.value.get(dateStr) || 0,
      });
      currentDate = currentDate.add(1, "day");
    }
    newWeeks.push(week);
  }
  weeks.value = newWeeks;
};

const months = computed(() => {
  const labels: string[] = [];
  const today = dayjs();
  let current = today.subtract(51 * 7, "day").startOf("week");

  for (let i = 0; i < 53; i++) {
    const monthStart = current.startOf("month");
    if (
      i === 0 ||
      monthStart.isAfter(current.subtract(1, "day").startOf("month"))
    ) {
      labels.push(monthStart.format("MMM"));
    } else {
      labels.push("");
    }
    current = current.add(7, "day");
  }
  return labels.filter((m) => m !== "");
});

const weekdayLabels = ["", "Mon", "", "Wed", "", "Fri", ""];

const getColorClass = (count: number) => {
  if (count === 0) return "level-0";
  if (count <= 2) return "level-1";
  if (count <= 5) return "level-2";
  if (count <= 9) return "level-3";
  return "level-4";
};

const formatTooltip = (day: { date: string; count: number }) => {
  if (day.count === 0) return "No submissions";
  return `${dayjs(day.date).format("YYYY-MM-DD")}: ${day.count} submissions`;
};

onMounted(() => {
  loadHeatmapData().catch(console.error);
});

// userId 变化时重新请求
watch(
  () => props.userId,
  (newId) => {
    if (newId) loadHeatmapData(newId);
  }
);
</script>

<style scoped>
.question-heatmap {
  background: white;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.heatmap-title {
  margin-bottom: 20px;
  font-size: 18px;
}

.heatmap-wrapper {
  overflow-x: auto;
}

.graph {
  display: flex;
}

.weekdays {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 30px;
  font-size: 12px;
  color: #86909c;
  padding-top: 20px;
  text-align: right;
  margin-right: 8px;
}

.weeks {
  display: flex;
  gap: 3px;
}

.week {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.day {
  width: 11px;
  height: 11px;
  border-radius: 2px;
  background: #ebedf0;
  position: relative;
  cursor: pointer;
}

.day:hover {
  outline: 1px solid #999;
  z-index: 10;
}

.tooltip {
  visibility: hidden;
  position: absolute;
  bottom: 18px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.75);
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 100;
}

.day:hover .tooltip {
  visibility: visible;
}

/* GitHub 经典绿色阶梯 */
.level-0 {
  background: #ebedf0;
}
.level-1 {
  background: #9be9a8;
}
.level-2 {
  background: #40c463;
}
.level-3 {
  background: #30a14e;
}
.level-4 {
  background: #216e39;
}

.months {
  display: flex;
  margin-bottom: 8px;
  margin-left: 38px; /* 对齐左侧星期标签 */
  gap: 45px; /* 粗略控制月份间距 */
}

.month-label {
  font-size: 12px;
  color: #86909c;
  min-width: 40px;
}

.legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: 16px;
  font-size: 12px;
  color: #86909c;
}

.legend-blocks {
  display: flex;
  margin: 0 8px;
  gap: 4px;
}

.legend-blocks .day {
  width: 11px;
  height: 11px;
}
</style>
