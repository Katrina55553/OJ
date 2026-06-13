<template>
  <div class="question-view">
    <div class="main-container">
      <a-spin
        :loading="loading"
        tip="题目加载中..."
        v-if="loading"
        class="loading-spin"
      />

      <!-- 左右横向分栏布局 -->
      <div v-else-if="question" class="question-layout">
        <!-- 左侧：题目详情 -->
        <div class="left-panel">
          <!-- 题目头部 -->
          <div class="panel-header">
            <h1 class="title">{{ question.title }}</h1>
            <div class="meta-row">
              <span
                class="difficulty-badge"
                :class="`difficulty-${question.difficulty}`"
                >{{ question.difficulty }}</span
              >
              <span>通过率：{{ question.passRate.toFixed(1) }}%</span>
              <span>提交：{{ question.submitNum }}</span>
              <span>通过：{{ question.acceptedNum }}</span>
            </div>
          </div>

          <!-- 题目描述 -->
          <div class="panel-content">
            <MdPreview :value="question.content" />

            <!-- 判题配置 -->
            <div class="judge-config">
              <a-descriptions :column="2" bordered size="small">
                <a-descriptions-item label="时间限制">
                  {{ question.timeLimit }} ms
                </a-descriptions-item>
                <a-descriptions-item label="内存限制">
                  {{ question.memoryLimit }} MB
                </a-descriptions-item>
              </a-descriptions>
            </div>
          </div>
        </div>

        <!-- 右侧：代码编辑与提交 -->
        <div class="right-panel">
          <!-- 代码编辑器 -->
          <div class="editor-container">
            <LanguageCodeEditor
              v-model:language="language"
              v-model:code="code"
            />
          </div>

          <!-- 按钮区：运行 + 提交 -->
          <div class="submit-bar">
            <a-space size="large">
              <a-button
                type="outline"
                status="success"
                size="large"
                :loading="running"
                @click="handleRun"
              >
                <template #icon><icon-play-arrow /></template>
                运行代码
              </a-button>

              <a-button
                type="primary"
                size="large"
                :loading="submitting"
                @click="handleSubmit"
              >
                <template #icon><icon-upload /></template>
                提交代码
              </a-button>
            </a-space>
          </div>

          <!-- 自测运行 -->
          <div
            class="input-area"
            style="padding: 16px; border-top: 1px solid #30363d"
          >
            <div style="margin-bottom: 8px">
              <span style="font-weight: 500">自测输入：</span>
            </div>
            <a-textarea v-model="customInput" :rows="3" />
          </div>

          <!-- 运行结果展示 -->
          <div v-if="runResult" class="run-result">
            <a-card title="运行结果" :bordered="false">
              <a-descriptions :column="2" size="small">
                <a-descriptions-item label="状态">
                  <a-tag
                    :color="runResult.status === 'Accepted' ? 'green' : 'red'"
                  >
                    {{ runResult.status }}
                  </a-tag>
                </a-descriptions-item>
                <a-descriptions-item label="耗时">
                  {{ runResult.time }} ms
                </a-descriptions-item>
                <a-descriptions-item label="内存">
                  {{ (runResult.memory / 1024).toFixed(1) }} MB
                </a-descriptions-item>
                <a-descriptions-item label="输出" :span="2">
                  <pre class="output-pre">{{
                    runResult.stdout || "无输出"
                  }}</pre>
                  <pre v-if="runResult.stderr" class="error-pre">{{
                    runResult.stderr
                  }}</pre>
                </a-descriptions-item>
              </a-descriptions>
            </a-card>
          </div>
        </div>
      </div>

      <!-- 题目不存在 -->
      <div v-else class="empty-state">
        <icon-empty size="64" />
        <p>题目不存在</p>
        <a-button @click="router.back()">返回</a-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import { IconEmpty } from "@arco-design/web-vue/es/icon";
import {
  QuestionControllerService,
  QuestionSubmitAddRequest,
} from "../../../generated/index";
import LanguageCodeEditor from "@/components/LanguageCodeEditor.vue";
import MdPreview from "@/components/MdPreview.vue";
import axios, { AxiosError } from "axios";
import { parseJudgeConfig } from "@/utils/question";

interface QuestionDetail {
  id: number;
  title: string;
  content: string;
  difficulty: string;
  passRate: number;
  timeLimit: number;
  memoryLimit: number;
  submitNum: number;
  acceptedNum: number;
}

interface RunResult {
  status: string;
  stdout: string;
  stderr: string;
  time: number;
  memory: number;
}

const route = useRoute();
const router = useRouter();
const id = Number(route.params.id);

const loading = ref(true);
const submitting = ref(false);
const running = ref(false);
const question = ref<QuestionDetail | null>(null);
const language = ref("cpp");
const code = ref("");
const customInput = ref("");
const runResult = ref<RunResult | null>(null);

const loadQuestion = async () => {
  if (!id || isNaN(id)) {
    Message.error("无效的题目 ID");
    return;
  }

  loading.value = true;
  try {
    const res = await QuestionControllerService.getQuestionVoByIdUsingGet(id);

    if (res.code === 0 && res.data) {
      const data = res.data;
      const judgeConfig = parseJudgeConfig(data.judgeConfig);

      question.value = {
        ...data,
        difficulty: data.difficulty || "未知",
        passRate: data.submitNum
          ? ((data.acceptedNum ?? 0) / data.submitNum) * 100
          : 0,
        timeLimit: judgeConfig.timeLimit ?? 1000,
        memoryLimit: judgeConfig.memoryLimit ?? 256,
      };
    } else {
      Message.error("加载失败：" + res.message);
    }
  } catch (e: any) {
    Message.error("加载失败");
    console.error(e);
  } finally {
    loading.value = false;
  }
};

function mapLanguageToJDoodle(lang: string): string {
  const mapping: Record<string, string> = {
    python: "python3",
    java: "java",
    cpp: "cpp",
    javascript: "nodejs",
    go: "go",
  };

  const jdLang = mapping[lang];
  if (!jdLang) {
    throw new Error(`Unsupported language: ${lang}`);
  }
  return jdLang;
}

const handleRun = async () => {
  if (!code.value.trim()) return Message.warning("请输入代码");

  running.value = true;
  runResult.value = null;

  try {
    const timeLimit = question.value?.timeLimit || 1000;
    const memoryLimit = question.value?.memoryLimit || 256;
    const stdin = customInput.value || question.value?.sampleInput || "";
    const response = await axios.post(
      "/api/code/execute",
      {
        script: code.value, // JDoodle 用 script
        language: mapLanguageToJDoodle(language.value),
        stdin: stdin,
      },
      {
        timeout: timeLimit + 5000,
        headers: {
          "Content-Type": "application/json",
        },
      }
    );

    const data = response.data;

    runResult.value = {
      status: data.status || "Unknown",
      stdout: data.stdout || "",
      stderr: data.stderr || "",
      time: data.time || 0,
      memory: data.memory || 0,
    };

    if (data.status === "Accepted") {
      Message.success("运行完成");
    } else {
      Message.warning(data.status);
    }
  } catch (error) {
    let errorMsg = "运行失败";
    let status = "System Error";
    const err = error as Error | AxiosError;

    if (axios.isAxiosError(err)) {
      if (err.code === "ECONNABORTED") {
        errorMsg = `运行超时 (超过 ${question.value?.timeLimit || 1000}ms)`;
        status = "Time Limit Exceeded";
      } else if (err.response) {
        errorMsg = `服务器错误 (${err.response.status})`;
      } else if (err.request) {
        errorMsg = "网络连接失败";
      } else {
        errorMsg = err.message || "未知错误";
      }
    } else if (err instanceof Error) {
      errorMsg = err.message;
    } else {
      errorMsg = String(err) || "未知错误";
    }

    Message.error(errorMsg);
    runResult.value = {
      status: status,
      stdout: "",
      stderr: errorMsg,
      time: 0,
      memory: 0,
    };
  } finally {
    running.value = false;
  }
};

const handleSubmit = async () => {
  if (!code.value.trim()) {
    return Message.warning("请输入代码");
  }

  submitting.value = true;

  try {
    // 构造提交请求体
    const submitRequest: QuestionSubmitAddRequest = {
      questionId: id,
      code: code.value,
      language: language.value,
    };

    const res = await QuestionControllerService.doQuestionSubmitUsingPost(
      submitRequest
    );

    // 后端统一返回结构 { code: 0, data: xxx, message: '' }
    if (res.code === 0) {
      Message.success("提交成功！正在跳转到提交详情...");
      router.push(`/submit/view/${res.data}`);
    } else {
      Message.error(res.message || "提交失败");
    }
  } catch (e: any) {
    console.error("提交异常", e);
    Message.error("提交失败：" + (e.message || "网络错误"));
  } finally {
    submitting.value = false;
  }
};

onMounted(() => loadQuestion());
</script>

<style scoped>
.question-view {
  height: 100%;
  background: #21262d;
  padding: 16px;
  box-sizing: border-box;
  overflow: hidden;
}

.loading-spin,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.question-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  height: 100%;
  min-width: 0;
}

.left-panel {
  background: #161b22;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.panel-header {
  padding: 20px 24px 0;
}

.title {
  margin: 0 0 12px;
  font-size: 24px;
}

.meta-row {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  color: #8b949e;
  font-size: 13px;
}

.difficulty-badge {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 4px;
}

.difficulty-简单 {
  background: #0d4429;
  color: #3fb950;
}

.difficulty-中等 {
  background: #3d2800;
  color: #f0883e;
}

.difficulty-困难 {
  background: #490202;
  color: #f85149;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px 24px;
}

.judge-config {
  margin-top: 24px;
}

.judge-config .arco-descriptions-item-content {
  color: #c9d1d9 !important;
}
.judge-config .arco-descriptions-item-label {
  color: #8b949e !important;
}

.right-panel {
  background: #161b22;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  height: 100%;
  min-width: 0;
  overflow: hidden;
}

.editor-container {
  flex: 1;
  min-height: 0;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.editor-container :deep(.language-code-editor) {
  height: 100%;
}

.submit-bar {
  flex-shrink: 0;
  padding: 16px;
  border-top: 1px solid #21262d;
  text-align: right;
}
.run-result {
  margin-top: 16px;
  padding: 0 16px 16px;
}

.output-pre {
  background: #21262d;
  color: #c9d1d9;
  padding: 12px;
  border-radius: 6px;
  white-space: pre-wrap;
  font-family: monospace;
  margin: 8px 0;
}

.error-pre {
  background: #3d1f1a;
  color: #cf1322;
  padding: 12px;
  border-radius: 6px;
  border: 1px solid #ffa39e;
  white-space: pre-wrap;
  font-family: monospace;
  margin: 8px 0;
}
</style>
