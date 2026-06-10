<template>
  <div class="dashboard-page">
    <!-- 🗓️ 提交热力图 -->
    <section class="card heatmap-section">
      <div class="card-header">
        <h3 class="card-title">📅 提交热力图</h3>
        <span class="card-subtitle">最近一年的提交活跃度</span>
      </div>
      <div class="card-body">
        <QuestionHeatmap :userId="loginUserId" />
      </div>
    </section>

    <!-- 📊 语言分布统计 -->
    <section class="card stats-section">
      <div class="card-header">
        <h3 class="card-title">📊 语言提交分布</h3>
        <span class="card-subtitle">不同编程语言的提交占比统计</span>
      </div>

      <div class="card-body">
        <!-- 🔍 筛选条件区域 -->
        <div class="filter-bar">
          <div class="filter-group">
            <label class="filter-label">
              <span class="label-icon">🎯</span>
              判题结果
            </label>
            <div class="filter-control">
              <select
                v-model="filters.judge_message"
                @change="handleFilterChange"
                class="filter-select"
              >
                <option :value="null">✨ 全部</option>
                <option value="Accepted">✅ Accepted</option>
                <option value="Wrong Answer">❌ Wrong Answer</option>
                <option value="Time Limit Exceeded">⏱️ Time Limit</option>
                <option value="Memory Limit Exceeded">💾 Memory Limit</option>
                <option value="Runtime Error">💥 Runtime Error</option>
                <option value="Compilation Error">🔧 Compilation Error</option>
              </select>
            </div>
          </div>

          <div class="filter-group">
            <label class="filter-label">
              <span class="label-icon">📈</span>
              最小提交数
            </label>
            <div class="filter-control">
              <input
                type="number"
                v-model.number="filters.min_count"
                @change="handleFilterChange"
                min="0"
                max="1000"
                placeholder="0"
                class="filter-input"
              />
            </div>
          </div>

          <div class="filter-actions">
            <button
              @click="fetchData"
              :disabled="loading"
              class="btn btn-primary btn-query"
            >
              <span v-if="loading" class="btn-loading">⏳</span>
              <span v-else>🔍</span>
              {{ loading ? "查询中" : "查询" }}
            </button>

            <button @click="resetFilters" class="btn btn-secondary">
              🔄 重置
            </button>
          </div>
        </div>

        <!-- 📊 图表区域 -->
        <div class="chart-wrapper">
          <div v-if="loading" class="chart-placeholder">
            <div class="loading-spinner"></div>
            <p class="loading-text">🔄 数据加载中...</p>
          </div>

          <div v-else-if="error" class="chart-placeholder chart-error">
            <span class="error-icon">⚠️</span>
            <p class="error-text">{{ error }}</p>
            <button @click="fetchData" class="btn btn-sm btn-outline">
              重试
            </button>
          </div>

          <div
            v-else-if="!chartData.length"
            class="chart-placeholder chart-empty"
          >
            <span class="empty-icon">😕</span>
            <p class="empty-text">暂无符合条件的数据</p>
            <small v-if="filters.judge_message" class="empty-hint">
              当前筛选：<code>{{ filters.judge_message }}</code>
              <br />
              尝试
              <button @click="resetFilters" class="link-btn">重置筛选</button>
            </small>
          </div>

          <div
            ref="chartRef"
            class="chart-box"
            v-show="!loading && !error && chartData.length"
          ></div>
        </div>

        <!-- 📋 统计摘要 -->
        <div v-if="total > 0 || chartData.length > 0" class="stats-summary">
          <div class="summary-item">
            <span class="summary-icon">📊</span>
            <span class="summary-label">语言种类</span>
            <span class="summary-value">{{ chartData.length }}</span>
          </div>
          <div class="summary-divider"></div>
          <div class="summary-item">
            <span class="summary-icon">📈</span>
            <span class="summary-label">总提交数</span>
            <span class="summary-value">{{ total.toLocaleString() }}</span>
          </div>
          <div class="summary-divider" v-if="filters.judge_message"></div>
          <div class="summary-item" v-if="filters.judge_message">
            <span class="summary-icon">🎯</span>
            <span class="summary-label">筛选条件</span>
            <span class="summary-value summary-badge">{{
              filters.judge_message
            }}</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, computed } from "vue";
import { useStore } from "vuex";
import * as echarts from "echarts";
import axios from "axios";
import QuestionHeatmap from "@/views/question/QuestionHeatmap.vue";

const store = useStore();
const loginUserId = computed(() => store.state.user?.loginUser?.id);

const filters = ref({
  judge_message: null,
  min_count: 0,
});

const chartRef = ref(null);
const myChart = ref(null);
const chartData = ref([]);
const total = ref(0);

const loading = ref(false);
const error = ref("");

let timer = null;

const handleFilterChange = () => {
  clearTimeout(timer);
  timer = setTimeout(() => {
    fetchData();
  }, 300);
};

const resetFilters = () => {
  filters.value = { judge_message: null, min_count: 0 };
  fetchData();
};

const fetchData = async () => {
  loading.value = true;
  error.value = "";

  try {
    const params = {};

    if (filters.value.judge_message) {
      params.judge_message = filters.value.judge_message;
    }
    if (filters.value.min_count > 0) {
      params.min_count = filters.value.min_count;
    }

    const response = await axios.get(`/api/statistics/language-distribution`, {
      params,
      timeout: 10000,
    });

    const result = response.data;

    if (result.success) {
      chartData.value = result.data || [];
      total.value = result.total || 0;

      await nextTick();
      await nextTick();

      setTimeout(() => {
        updateChart();
      }, 100);
    } else {
      error.value = result.message || "获取数据失败";
    }
  } catch (err) {
    console.error("请求失败:", err);
    error.value = `请求失败：${err.message}`;
  } finally {
    loading.value = false;
  }
};

/** 转义 HTML 特殊字符，防止 XSS */
const escapeHtml = (str: string): string => {
  if (!str) return "";
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
};

/** 配色方案 */
const CHART_COLORS = [
  "#5470C6",
  "#91CC75",
  "#FAC858",
  "#EE6666",
  "#73C0DE",
  "#3BA272",
  "#FC8452",
  "#9A60B4",
];

/** 生成饼图 tooltip HTML */
const buildTooltipHtml = (params: any): string => {
  const safeName = escapeHtml(params.name);
  const color = CHART_COLORS[params.dataIndex % CHART_COLORS.length];
  return `
    <div style="padding:8px 12px;font-family:system-ui">
      <div style="font-weight:600;margin-bottom:4px">${safeName}</div>
      <div style="color:#666;font-size:13px">
        提交数：<strong style="color:#333">${params.value}</strong> 次
      </div>
      <div style="color:#666;font-size:13px">
        占比：<strong style="color:${color}">${params.percent}%</strong>
      </div>
    </div>
  `;
};

/** 构造 ECharts 配置项 */
const buildChartOption = () => ({
  color: CHART_COLORS,
  tooltip: {
    trigger: "item",
    formatter: buildTooltipHtml,
    backgroundColor: "rgba(255,255,255,0.95)",
    borderColor: "#eee",
    borderWidth: 1,
    textStyle: { color: "#333" },
  },
  legend: {
    orient: "vertical",
    right: "5%",
    top: "middle",
    type: "scroll",
    itemWidth: 12,
    itemHeight: 12,
    formatter: (name: string) => {
      const item = chartData.value.find((d) => d.name === name);
      return item ? `${name} (${item.value})` : name;
    },
    textStyle: { color: "#666", fontSize: 12 },
  },
  series: [
    {
      name: "提交语言",
      type: "pie",
      radius: ["50%", "75%"],
      center: ["40%", "50%"],
      avoidLabelOverlap: true,
      itemStyle: {
        borderRadius: 10,
        borderColor: "#161b22",
        borderWidth: 3,
        shadowBlur: 10,
        shadowColor: "rgba(0,0,0,0.1)",
      },
      label: {
        show: true,
        position: "outside",
        formatter: "{b}",
        fontSize: 12,
        color: "#666",
        fontWeight: 500,
      },
      labelLine: {
        show: true,
        length: 15,
        length2: 20,
        smooth: true,
        lineStyle: { width: 1, color: "#ddd" },
      },
      emphasis: {
        scale: true,
        scaleSize: 10,
        label: {
          show: true,
          fontSize: 14,
          fontWeight: "bold",
          formatter: "{b}\n{c}次 ({d}%)",
        },
        itemStyle: {
          shadowBlur: 20,
          shadowColor: "rgba(0,0,0,0.3)",
        },
      },
      data: chartData.value,
    },
  ],
});

/** 更新图表 */
const updateChart = () => {
  if (!chartRef.value) return;

  const rect = chartRef.value.getBoundingClientRect();
  if (rect.width === 0 || rect.height === 0) {
    setTimeout(() => updateChart(), 200);
    return;
  }

  if (!myChart.value) {
    myChart.value = echarts.init(chartRef.value);
  }

  myChart.value.setOption(buildChartOption());
};

const handleResize = () => {
  myChart.value?.resize();
};

onMounted(async () => {
  await nextTick();
  await nextTick();
  setTimeout(() => fetchData(), 100);
  window.addEventListener("resize", handleResize);
});

onUnmounted(() => {
  window.removeEventListener("resize", handleResize);
  clearTimeout(timer);
  myChart.value?.dispose();
});
</script>

<style scoped>
.dashboard-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8f0 100%);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.card {
  background: #161b22;
  border-radius: 16px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  border: 1px solid rgba(0, 0, 0, 0.04);
  transition: all 0.3s ease;
  overflow: hidden;
}

.card:hover {
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
}

.card-header {
  padding: 20px 24px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  background: linear-gradient(135deg, #161b22 0%, #fafbfc 100%);
}

.card-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-subtitle {
  display: block;
  margin-top: 4px;
  font-size: 13px;
  color: #888;
  font-weight: 400;
}

.card-body {
  padding: 24px;
}

.heatmap-section {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.stats-section {
  max-width: 1200px;
  margin: 0 auto;
  width: 100%;
}

.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  align-items: flex-end;
  padding: 20px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-radius: 12px;
  border: 1px solid rgba(0, 0, 0, 0.05);
  margin-bottom: 24px;
}

.filter-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 180px;
}

.filter-label {
  font-size: 13px;
  font-weight: 500;
  color: #475569;
  display: flex;
  align-items: center;
  gap: 6px;
}

.label-icon {
  font-size: 14px;
}

.filter-control {
  position: relative;
}

.filter-select,
.filter-input {
  width: 100%;
  padding: 10px 14px;
  border: 2px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  color: #1e293b;
  background: #161b22;
  transition: all 0.2s ease;
  appearance: none;
  background-image: url("data:image/svg+xml,%3csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 20 20'%3e%3cpath stroke='%236b7280' stroke-linecap='round' stroke-linejoin='round' stroke-width='1.5' d='M6 8l4 4 4-4'/%3e%3c/svg%3e");
  background-position: right 12px center;
  background-repeat: no-repeat;
  background-size: 16px;
  padding-right: 36px;
}

.filter-select:hover,
.filter-input:hover {
  border-color: #cbd5e1;
}

.filter-select:focus,
.filter-input:focus {
  outline: none;
  border-color: #3b82f6;
  box-shadow: 0 0 0 4px rgba(59, 130, 246, 0.1);
}

.filter-input {
  padding-right: 14px;
  background-image: none;
}

.filter-input::-webkit-outer-spin-button,
.filter-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}

.filter-input[type="number"] {
  -moz-appearance: textfield;
}

.filter-actions {
  display: flex;
  gap: 12px;
  margin-left: auto;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 10px 20px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-primary {
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: #161b22;
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.3);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%);
  box-shadow: 0 6px 20px rgba(59, 130, 246, 0.4);
  transform: translateY(-1px);
}

.btn-primary:active:not(:disabled) {
  transform: translateY(0);
}

.btn-secondary {
  background: #f1f5f9;
  color: #475569;
  border: 2px solid #e2e8f0;
}

.btn-secondary:hover {
  background: #e2e8f0;
  color: #1e293b;
}

.btn-sm {
  padding: 6px 14px;
  font-size: 13px;
}

.btn-outline {
  background: transparent;
  border: 2px solid #cbd5e1;
  color: #64748b;
}

.btn-outline:hover {
  background: #f1f5f9;
  border-color: #94a3b8;
  color: #334155;
}

.btn-loading {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.chart-wrapper {
  position: relative;
  min-height: 420px;
  margin-bottom: 20px;
}

.chart-box {
  width: 100%;
  height: 420px;
  border-radius: 12px;
  overflow: hidden;
}

.chart-placeholder {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  background: #fafbfc;
  border-radius: 12px;
  border: 2px dashed #e2e8f0;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid #e2e8f0;
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.loading-text {
  margin: 0;
  font-size: 14px;
  color: #64748b;
}

.chart-error,
.chart-empty {
  text-align: center;
  padding: 20px;
}

.error-icon,
.empty-icon {
  font-size: 48px;
  margin-bottom: 8px;
}

.error-text {
  margin: 0 0 16px 0;
  font-size: 14px;
  color: #ef4444;
  font-weight: 500;
}

.empty-text {
  margin: 0 0 8px 0;
  font-size: 15px;
  color: #64748b;
}

.empty-hint {
  color: #94a3b8;
  line-height: 1.6;
}

.empty-hint code {
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  color: #475569;
  font-weight: 500;
}

.link-btn {
  background: none;
  border: none;
  color: #3b82f6;
  font-size: inherit;
  cursor: pointer;
  padding: 0;
  text-decoration: underline;
}

.link-btn:hover {
  color: #2563eb;
}

.stats-summary {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 16px 24px;
  background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
  border-radius: 12px;
  border: 1px solid #bae6fd;
}

.summary-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.summary-icon {
  font-size: 18px;
}

.summary-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 400;
}

.summary-value {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}

.summary-badge {
  background: #3b82f6;
  color: #161b22;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 13px;
}

.summary-divider {
  width: 1px;
  height: 32px;
  background: #cbd5e1;
}

@media (max-width: 768px) {
  .dashboard-page {
    padding: 16px;
    gap: 16px;
  }

  .card-header {
    padding: 16px 20px;
  }

  .card-body {
    padding: 20px;
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
    padding: 16px;
  }

  .filter-group {
    min-width: auto;
  }

  .filter-actions {
    margin-left: 0;
    justify-content: center;
  }

  .stats-summary {
    flex-wrap: wrap;
    gap: 16px;
  }

  .summary-divider {
    display: none;
  }

  .chart-box {
    height: 360px;
  }
}
</style>
