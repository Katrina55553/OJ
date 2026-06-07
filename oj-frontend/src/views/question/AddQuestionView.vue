<template>
  <div class="add-question-container">
    <!-- 页面标题 -->
    <div class="page-header">
      <h2>
        <icon-plus />
        新增题目
      </h2>
    </div>

    <!-- 表单卡片 -->
    <div class="form-card">
      <a-form
        ref="formRef"
        :model="formData"
        :rules="formRules"
        layout="vertical"
        :label-col-props="{ span: 24 }"
        :wrapper-col-props="{ span: 24 }"
      >
        <!-- 题目标题 -->
        <a-form-item field="title" label="题目标题" required>
          <a-input
            v-model="formData.title"
            placeholder="请输入题目标题"
            :max-length="100"
            show-word-limit
            allow-clear
          />
        </a-form-item>

        <!-- 难度 + 标签 -->
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item field="difficulty" label="难度" required>
              <a-select v-model="formData.difficulty" placeholder="请选择难度">
                <a-option value="简单">简单</a-option>
                <a-option value="中等">中等</a-option>
                <a-option value="困难">困难</a-option>
              </a-select>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item field="tags" label="标签" required>
              <a-select
                v-model="formData.tags"
                multiple
                allow-create
                placeholder="请选择或创建标签"
                :max-tag-count="6"
              >
                <a-option v-for="tag in allTags" :key="tag" :value="tag">
                  {{ tag }}
                </a-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 题目描述 -->
        <a-form-item field="content" label="题目描述" required>
          <MdEditor
            v-model="formData.content"
            style="min-width: 100%; z-index: 1"
          />
        </a-form-item>

        <!-- 参考答案 -->
        <a-form-item field="answer" label="参考答案（代码）" required>
          <CodeEditor
            v-model="formData.answer"
            language="java"
            style="min-height: 400px; width: 100%; border: 1px solid #eee"
          />
        </a-form-item>

        <!-- 时间和内存限制 -->
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item field="timeLimit" label="时间限制(ms)" required>
              <a-input-number
                v-model="formData.timeLimit"
                style="width: 100%"
                :min="100"
                :step="100"
                :precision="0"
                placeholder="例如：1000"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item field="memoryLimit" label="内存限制(MB)" required>
              <a-input-number
                v-model="formData.memoryLimit"
                style="width: 100%"
                :min="64"
                :step="64"
                :precision="0"
                placeholder="例如：256"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 测试用例 -->
        <a-form-item field="judgeCase" label="测试用例（JSON 数组格式）">
          <a-textarea
            v-model="formData.judgeCase"
            placeholder='例如：[{"input": "1 2", "output": "3"}]'
            :auto-size="{ minRows: 5, maxRows: 10 }"
            :max-length="5000"
            show-word-limit
          />
        </a-form-item>
      </a-form>

      <!-- 提交按钮 -->
      <div class="form-actions">
        <a-space>
          <a-button type="primary" @click="handleSubmit" :loading="submitting">
            提交新增
          </a-button>
          <a-button @click="handleCancel">取消</a-button>
        </a-space>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import { useRouter } from "vue-router";
import { Message } from "@arco-design/web-vue";
import { IconPlus } from "@arco-design/web-vue/es/icon";
import { QuestionControllerService } from "../../../generated";
import type { QuestionAddRequest } from "../../../generated";
import type { FormInstance } from "@arco-design/web-vue";
import MdEditor from "@/components/MdEditor.vue";
import CodeEditor from "@/components/CodeEditor.vue";
import { ALL_TAGS } from "@/constants/question";

const router = useRouter();
const formRef = ref<FormInstance>();
const submitting = ref(false);

const formData = reactive({
  title: "",
  content: "",
  tags: [] as string[],
  difficulty: "简单",
  answer: "",
  timeLimit: 1000,
  memoryLimit: 256,
  judgeCase: "",
});

const formRules = {
  title: [{ required: true, message: "请输入题目标题" }],
  content: [{ required: true, message: "请输入题目描述" }],
  difficulty: [{ required: true, message: "请选择难度" }],
  tags: [{ required: true, message: "请至少选择一个标签" }],
  answer: [{ required: true, message: "请输入参考答案" }],
  timeLimit: [{ required: true, message: "请设置时间限制" }],
  memoryLimit: [{ required: true, message: "请设置内存限制" }],
};

const allTags = ALL_TAGS;

const handleSubmit = async () => {
  try {
    // 验证表单
    const valid = await formRef.value?.validate();
    if (valid) return; // 如果有错误返回对象，则停止提交

    submitting.value = true;

    // 解析测试用例
    let judgeCaseArray = [];
    try {
      if (formData.judgeCase.trim()) {
        judgeCaseArray = JSON.parse(formData.judgeCase);
        if (!Array.isArray(judgeCaseArray)) {
          Message.error("测试用例必须是 JSON 数组格式");
          return;
        }
      }
    } catch (error) {
      Message.error("测试用例格式错误，请检查 JSON 格式");
      return;
    }

    // 构造请求参数
    const addRequest: QuestionAddRequest = {
      title: formData.title,
      content: formData.content,
      tags: formData.tags,
      answer: formData.answer,
      judgeCase: judgeCaseArray,
      judgeConfig: {
        timeLimit: formData.timeLimit,
        memoryLimit: formData.memoryLimit,
      },
    };

    const res = await QuestionControllerService.addQuestionUsingPost(
      addRequest
    );

    if (res.code === 0) {
      Message.success("新增题目成功");
      await router.push("/admin/question");
    } else {
      Message.error(res.message || "新增失败");
    }
  } catch (error: any) {
    console.error(error);
    Message.error("提交失败，请检查网络或控制台");
  } finally {
    submitting.value = false;
  }
};

const handleCancel = () => {
  router.push("/admin/question");
};
</script>

<style scoped>
.add-question-container {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
  background: #f7f8fa;
  min-height: 100vh;
}

.page-header {
  margin-bottom: 24px;
  padding: 20px 24px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.page-header h2 {
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
}

.form-card {
  padding: 32px;
  background: white;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.form-actions {
  margin-top: 40px;
  text-align: center;
}
</style>
