<template>
  <div class="feedback-view">
    <div class="main-container">
      <div class="content-wrapper">
        <!-- 头部标题 -->
        <div class="header">
          <h2 class="title">意见反馈</h2>
          <p class="subtitle">
            您的反馈是我们前进的动力，欢迎提出宝贵建议或报告问题。
          </p>
        </div>

        <a-divider />

        <!-- 表单区域 -->
        <a-form
          ref="formRef"
          :model="formData"
          :rules="rules"
          layout="vertical"
          size="large"
          @submit="handleSubmit"
        >
          <!-- 1. 反馈类型 -->
          <a-form-item field="type" label="反馈类型" required>
            <a-radio-group type="button" v-model="formData.type">
              <a-radio value="bug">功能异常</a-radio>
              <a-radio value="content">内容错误</a-radio>
              <a-radio value="suggestion">真诚建议</a-radio>
              <a-radio value="other">其他</a-radio>
            </a-radio-group>
          </a-form-item>

          <!-- 2. 反馈详情 -->
          <a-form-item field="content" label="反馈详情" required>
            <a-textarea
              v-model="formData.content"
              placeholder="请详细描述您遇到的问题或建议，以便我们要更快地为您解决..."
              :max-length="500"
              show-word-limit
              :auto-size="{ minRows: 5, maxRows: 10 }"
            />
          </a-form-item>

          <!-- 3. 图片上传  -->
          <a-form-item field="images" label="截图/图片（选填，最多3张）">
            <a-upload
              list-type="picture-card"
              :limit="3"
              image-preview
              :custom-request="customUpload"
              v-model:file-list="fileList"
              @before-upload="beforeUpload"
            />
          </a-form-item>

          <!-- 4. 联系方式 -->
          <a-form-item field="contact" label="联系方式（选填）">
            <a-input
              v-model="formData.contact"
              placeholder="留下您的邮箱或QQ号，方便我们联系您"
            >
              <template #prefix>
                <icon-user />
              </template>
            </a-input>
          </a-form-item>

          <!-- 5. 提交按钮 -->
          <a-form-item>
            <a-button
              type="primary"
              html-type="submit"
              long
              size="large"
              :loading="submitting"
            >
              提交反馈
            </a-button>
          </a-form-item>
        </a-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from "vue";
import {
  Message,
  type FileItem,
  type RequestOption,
} from "@arco-design/web-vue";
import { IconUser } from "@arco-design/web-vue/es/icon";

// --- 数据定义 ---
const formRef = ref();
const submitting = ref(false);
const fileList = ref<FileItem[]>([]);

// 表单数据模型
const formData = reactive({
  type: "bug", // 默认选中功能异常
  content: "",
  contact: "",
  images: [] as string[], // 存储上传后的图片 URL
});

// 表单验证规则
const rules = {
  type: [{ required: true, message: "请选择反馈类型" }],
  content: [
    { required: true, message: "请输入反馈详情" },
    { minLength: 5, message: "描述太短了，请至少输入5个字" },
  ],
};

// --- 方法 ---

// 图片上传前的校验
const beforeUpload = (file: File) => {
  const isImage = file.type.startsWith("image/");
  const isLt2M = file.size / 1024 / 1024 < 2;

  if (!isImage) {
    Message.error("只能上传图片文件!");
    return false;
  }
  if (!isLt2M) {
    Message.error("图片大小不能超过 2MB!");
    return false;
  }
  return true;
};

// 自定义上传逻辑 (模拟上传)
const customUpload = (option: RequestOption) => {
  const { onProgress, onSuccess, onError } = option;

  // 模拟异步上传过程
  const controller = new AbortController();
  (async () => {
    try {
      onProgress(0.5); // 模拟进度
      await new Promise((resolve) => setTimeout(resolve, 1000));

      // TODO: 后端上传接口
      // const res = await uploadApi(option.fileItem.file);
      // const url = res.data.url;

      // 模拟返回一个图片地址
      const mockUrl =
        "https://p1-arco.byteimg.com/tos-cn-i-uwbnlip3yd/cd7a1aaea8e1c5e3d26fe2591e561798.png~tplv-uwbnlip3yd-webp.webp";

      onSuccess(mockUrl);
    } catch (error) {
      onError(error);
    }
  })();

  return {
    abort: () => controller.abort(),
  };
};

// 提交表单
const handleSubmit = async ({
  errors,
  values,
}: {
  errors: Record<string, unknown> | null;
  values: Record<string, unknown>;
}) => {
  if (errors) return;

  submitting.value = true;
  try {
    // 1. 收集图片 URL
    const imageUrls = fileList.value
      .filter((item) => item.status === "done")
      .map((item) => item.response || item.url);

    // 2. 构造请求参数
    const submitData = {
      ...formData,
      images: imageUrls,
    };

    // TODO: 调用后端提交反馈接口
    // await FeedbackController.add(submitData);

    // 模拟网络请求延迟
    await new Promise((resolve) => setTimeout(resolve, 1500));

    Message.success("感谢您的反馈，我们会尽快处理！");

    // 重置表单
    formRef.value.resetFields();
    fileList.value = [];
  } catch (error) {
    Message.error("提交失败，请稍后重试");
  } finally {
    submitting.value = false;
  }
};
</script>

<style scoped>
.feedback-view {
  min-height: calc(100vh - 60px);
  background-color: #21262d;
  padding: 40px 20px;
  display: flex;
  justify-content: center;
}

.main-container {
  width: 100%;
  max-width: 800px;
}

.content-wrapper {
  background: #161b22;
  padding: 40px;
  border-radius: 8px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.05);
}

.header {
  text-align: center;
  margin-bottom: 24px;
}

.title {
  margin: 0 0 8px;
  font-size: 24px;
  color: #f0f6fc;
  font-weight: 600;
}

.subtitle {
  margin: 0;
  color: #8b949e;
  font-size: 14px;
}

@media (max-width: 600px) {
  .content-wrapper {
    padding: 24px;
  }
}
</style>
