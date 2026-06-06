<template>
  <a-modal
    v-model:visible="visible"
    :title="title"
    width="1000px"
    :mask-closable="false"
    :confirm-loading="confirmLoading"
    @ok="handleOk"
    @cancel="handleCancel"
  >
    <a-form
      ref="formRef"
      :model="localForm"
      :rules="formRules"
      layout="vertical"
      :label-col-props="{ span: 24 }"
      :wrapper-col-props="{ span: 24 }"
    >
      <!-- 题目标题 -->
      <a-form-item field="title" label="题目标题" required>
        <a-input
          v-model="localForm.title"
          placeholder="请输入题目标题（支持较长标题）"
          :max-length="200"
          show-word-limit
          allow-clear
        />
      </a-form-item>

      <!-- 难度 + 标签 -->
      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item field="difficulty" label="难度" required>
            <a-select v-model="localForm.difficulty" placeholder="请选择难度">
              <a-option value="简单">简单</a-option>
              <a-option value="中等">中等</a-option>
              <a-option value="困难">困难</a-option>
            </a-select>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item field="tags" label="标签" required>
            <a-select
              v-model="localForm.tags"
              placeholder="请选择或创建标签"
              multiple
              allow-create
              :max-tag-count="8"
            >
              <a-option v-for="tag in algorithmTags" :key="tag" :value="tag">
                {{ tag }}
              </a-option>
            </a-select>
          </a-form-item>
        </a-col>
      </a-row>

      <!-- 题目描述 -->
      <a-form-item field="content" label="题目描述" required>
        <a-textarea
          v-model="localForm.content"
          placeholder="请输入题目描述，支持 Markdown 格式"
          :auto-size="{ minRows: 6, maxRows: 12 }"
          show-word-limit
          :max-length="10000"
        />
      </a-form-item>

      <!-- 参考答案 -->
      <a-form-item field="answer" label="参考答案（代码）" required>
        <a-textarea
          v-model="localForm.answer"
          placeholder="请输入标准参考答案代码"
          :auto-size="{ minRows: 10, maxRows: 20 }"
          show-word-limit
          :max-length="10000"
        />
      </a-form-item>

      <!-- 判题配置 -->
      <a-divider orientation="left">判题配置</a-divider>
      <a-row :gutter="16">
        <a-col :span="8">
          <a-form-item field="timeLimit" label="时间限制 (ms)" required>
            <a-input-number
              v-model="localForm.timeLimit"
              :min="100"
              :max="10000"
              :step="100"
              :precision="0"
              style="width: 100%"
              placeholder="默认 1000"
            />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item field="memoryLimit" label="内存限制 (MB)" required>
            <a-input-number
              v-model="localForm.memoryLimit"
              :min="64"
              :max="1024"
              :step="64"
              :precision="0"
              style="width: 100%"
              placeholder="默认 256"
            />
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item field="stackLimit" label="栈限制 (MB)">
            <a-input-number
              v-model="localForm.stackLimit"
              :min="1"
              :max="256"
              :step="16"
              :precision="0"
              style="width: 100%"
              placeholder="默认 64（可选）"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <!-- 测试用例 -->
      <a-form-item
        field="judgeCases"
        label="测试用例（JSON 数组格式）"
        :validate-trigger="['blur']"
      >
        <a-textarea
          v-model="judgeCasesJson"
          placeholder='例如：\n[\n  {"input": "21", "output": "7"},\n  {"input": "6", "output": "3"}\n]'
          :auto-size="{ minRows: 8, maxRows: 15 }"
          show-word-limit
          :max-length="20000"
        />
        <div class="tip">
          <icon-info-circle />
          每组用例必须包含 <code>input</code> 和
          <code>output</code> 字段，必须是合法的 JSON 数组。
        </div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, computed } from "vue";
import { Message, type FormInstance } from "@arco-design/web-vue";
import { QuestionControllerService } from "../../../../generated";
import type {
  QuestionUpdateRequest,
  JudgeCase,
  JudgeConfig,
} from "generated/index";

const props = defineProps<{
  visible: boolean;
  title: string;
  formData: {
    id?: number;
    title?: string;
    content?: string;
    tags?: string[];
    answer?: string;
    timeLimit?: number;
    memoryLimit?: number;
    stackLimit?: number;
    judgeCases?: JudgeCase[];
  };
  isEdit: boolean;
}>();

const emit = defineEmits<{
  "update:visible": [value: boolean];
  success: [];
}>();

const visible = computed({
  get: () => props.visible,
  set: (val) => emit("update:visible", val),
});

const formRef = ref<FormInstance>();
const confirmLoading = ref(false);

const localForm = ref({
  id: 0,
  title: "",
  content: "",
  tags: [] as string[],
  difficulty: "简单" as "简单" | "中等" | "困难",
  answer: "",
  timeLimit: 1000,
  memoryLimit: 256,
  stackLimit: 64,
});

const judgeCasesJson = ref("[]");

const allTags = [
  "简单",
  "中等",
  "困难",
  "数组",
  "字符串",
  "链表",
  "树",
  "图论",
  "动态规划",
  "回溯",
  "贪心",
  "二分查找",
  "栈",
  "队列",
  "哈希表",
  "堆",
  "排序",
  "双指针",
  "滑动窗口",
  "前缀和",
  "位运算",
  "数学",
  "矩阵",
  "模拟",
];

const algorithmTags = allTags.filter(
  (tag) => !["简单", "中等", "困难"].includes(tag)
);

const formRules = {
  title: [{ required: true, message: "请输入题目标题" }],
  content: [{ required: true, message: "请输入题目描述" }],
  difficulty: [{ required: true, message: "请选择难度" }],
  tags: [{ required: true, message: "请至少选择一个算法标签" }],
  answer: [{ required: true, message: "请输入参考答案" }],
  timeLimit: [{ required: true, message: "请设置时间限制" }],
  memoryLimit: [{ required: true, message: "请设置内存限制" }],
};

// 监听 formData，回显所有字段（包括测试用例）
watch(
  () => props.formData,
  (newVal) => {
    if (!newVal) return;

    const difficultyTags = ["简单", "中等", "困难"];
    const foundDifficulty =
      difficultyTags.find((tag) => newVal.tags?.includes(tag)) || "简单";
    const otherTags =
      newVal.tags?.filter((tag) => !difficultyTags.includes(tag)) || [];

    localForm.value = {
      id: newVal.id || 0,
      title: newVal.title || "",
      content: newVal.content || "",
      tags: otherTags,
      difficulty: foundDifficulty as any,
      answer: newVal.answer || "",
      timeLimit: newVal.timeLimit || 1000,
      memoryLimit: newVal.memoryLimit || 256,
      stackLimit: newVal.stackLimit || 64,
    };

    // 回显测试用例（自动格式化带缩进）
    if (Array.isArray(newVal.judgeCases)) {
      judgeCasesJson.value = JSON.stringify(newVal.judgeCases, null, 2);
    } else {
      judgeCasesJson.value = "[]";
    }
  },
  { deep: true, immediate: true }
);

// 关闭时清除验证
watch(visible, (val) => {
  if (!val) {
    nextTick(() => formRef.value?.clearValidate());
  }
});

// 提交保存
const handleOk = async () => {
  await formRef.value?.validate();
  confirmLoading.value = true;

  let parsedJudgeCases: JudgeCase[] = [];
  try {
    const parsed = JSON.parse(judgeCasesJson.value);
    if (!Array.isArray(parsed)) throw new Error("必须是数组");
    parsedJudgeCases = parsed;
  } catch (e) {
    Message.error("测试用例 JSON 格式不正确，请检查语法");
    confirmLoading.value = false;
    return;
  }

  const judgeConfig: JudgeConfig = {
    timeLimit: localForm.value.timeLimit,
    memoryLimit: localForm.value.memoryLimit,
    stackLimit: localForm.value.stackLimit || undefined,
  };

  const finalTags = [
    localForm.value.difficulty,
    ...localForm.value.tags,
  ].filter(Boolean);

  const updateRequest: QuestionUpdateRequest = {
    id: localForm.value.id || undefined,
    title: localForm.value.title,
    content: localForm.value.content,
    tags: finalTags,
    answer: localForm.value.answer,
    judgeCase: parsedJudgeCases,
    judgeConfig,
  };

  try {
    const res = await QuestionControllerService.updateQuestionUsingPost(
      updateRequest
    );
    if (res.code === 0) {
      Message.success(props.isEdit ? "更新成功" : "创建成功");
      emit("success");
      emit("update:visible", false);
    } else {
      Message.error(res.message || "操作失败");
    }
  } catch (error: any) {
    Message.error(error.message || "请求失败");
  } finally {
    confirmLoading.value = false;
  }
};

const handleCancel = () => {
  emit("update:visible", false);
};
</script>

<style scoped>
:deep(.arco-modal-body) {
  padding: 20px 30px;
  max-height: 70vh;
  overflow-y: auto;
}

.tip {
  margin-top: 8px;
  font-size: 13px;
  color: #86909c;
  display: flex;
  align-items: center;
  gap: 6px;
}

.tip code {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}
</style>
