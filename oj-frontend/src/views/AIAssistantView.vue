<template>
  <div class="chat-layout">
    <div class="chat-container">
      <!-- 顶部导航栏 -->
      <div class="chat-header">
        <div class="header-left">
          <div class="bot-icon">
            <icon-robot size="24" />
          </div>
          <div class="header-info">
            <span class="title">AI 编程助手</span>
            <span class="status-dot"></span>
          </div>
        </div>
        <div class="header-right">
          <a-tooltip content="清空对话">
            <a-button type="text" shape="circle" @click="clearChat">
              <icon-delete />
            </a-button>
          </a-tooltip>
        </div>
      </div>

      <!-- 消息滚动区域 -->
      <div class="messages-area" ref="messagesRef">
        <!-- 空状态 -->
        <transition name="fade">
          <div v-if="messages.length === 0" class="empty-state">
            <div class="welcome-box">
              <icon-message size="48" class="welcome-icon" />
              <h2>👋 Hi, 我能帮你写点什么？</h2>
              <p>我是你的编程助手，支持代码生成、Bug 调试和算法分析。</p>
            </div>
          </div>
        </transition>

        <!-- 消息列表 -->
        <div class="message-list">
          <transition-group name="message-slide">
            <div
              v-for="(msg, index) in messages"
              :key="index"
              class="message-row"
              :class="msg.role"
            >
              <!-- 头像 -->
              <div class="avatar-col">
                <a-avatar
                  v-if="msg.role === 'assistant'"
                  :size="32"
                  class="ai-avatar"
                >
                  AI
                  <icon-robot />
                </a-avatar>
                <a-avatar v-else :size="32" class="user-avatar"> U </a-avatar>
              </div>

              <!-- 内容气泡 -->
              <div class="content-col">
                <div class="bubble-wrapper">
                  <div class="bubble">
                    <MdPreview
                      v-if="msg.role === 'assistant'"
                      :value="msg.content"
                    />
                    <div v-else class="user-text">{{ msg.content }}</div>
                  </div>

                  <!-- 操作栏 (仅AI) -->
                  <div
                    class="message-actions"
                    v-if="msg.role === 'assistant' && !isTyping"
                  >
                    <a-tooltip content="复制内容">
                      <span
                        class="action-btn"
                        @click="copyMessage(msg.content)"
                      >
                        <icon-copy />
                      </span>
                    </a-tooltip>
                    <a-tooltip content="重新生成">
                      <span class="action-btn">
                        <icon-refresh />
                      </span>
                    </a-tooltip>
                  </div>
                </div>
              </div>
            </div>
          </transition-group>

          <!-- 加载中状态 -->
          <div v-if="isTyping" class="message-row assistant typing-row">
            <div class="avatar-col">
              <a-avatar :size="32" class="ai-avatar"><icon-robot /></a-avatar>
            </div>
            <div class="content-col">
              <div class="bubble typing-bubble">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </div>
              <span class="thinking-text">思考中...</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部输入区域 -->
      <div class="input-wrapper">
        <div class="input-box" :class="{ 'is-focus': isInputFocus }">
          <a-textarea
            v-model="input"
            placeholder="输入你的问题... (Shift+Enter 换行)"
            :auto-size="{ minRows: 1, maxRows: 6 }"
            @keydown.enter.prevent="handleEnter"
            @focus="isInputFocus = true"
            @blur="isInputFocus = false"
            class="custom-textarea"
          />
          <div class="send-btn-area">
            <a-button
              type="primary"
              shape="circle"
              size="large"
              :loading="isLoading"
              :disabled="!input.trim()"
              @click="sendMessage"
              class="send-btn"
            >
              <icon-send />
            </a-button>
          </div>
        </div>
        <div class="footer-tip">AI 生成的内容可能包含错误，请仔细核对。</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from "vue";
import { Message } from "@arco-design/web-vue";
import MdPreview from "@/components/MdPreview.vue";
import axios, { AxiosError } from "axios";

interface MessageItem {
  role: "user" | "assistant";
  content: string;
  createdAt?: string;
}

const messages = ref<MessageItem[]>([]);
const input = ref("");
const isLoading = ref(false);
const isTyping = ref(false);
const isInputFocus = ref(false);
const messagesRef = ref<HTMLElement>();

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight;
    }
  });
};

const clearChat = () => {
  messages.value = [];
};

const handleEnter = (e: KeyboardEvent) => {
  if (e.shiftKey) {
    return;
  }
  sendMessage();
};

const sendMessage = async () => {
  if (!input.value.trim() || isLoading.value) return;

  const userMessage = input.value.trim();
  messages.value.push({ role: "user", content: userMessage });
  input.value = "";
  scrollToBottom();

  isLoading.value = true;
  isTyping.value = true;

  const aiIndex = messages.value.push({ role: "assistant", content: "" }) - 1;

  const url = `http://localhost:8001/stream?prompt=${encodeURIComponent(
    userMessage
  )}`;
  const eventSource = new EventSource(url);

  let aiContent = "";

  eventSource.onmessage = (event) => {
    aiContent += event.data;
    messages.value[aiIndex].content = aiContent;
    scrollToBottom();
  };

  eventSource.onerror = () => {
    if (aiContent.trim() === "") {
      messages.value[aiIndex].content = "🔌 与 AI 服务断开连接";
    }
    eventSource.close();
    isLoading.value = false;
    isTyping.value = false;
  };
};

const copyMessage = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text);
    Message.success("已复制到剪贴板");
  } catch {
    Message.error("复制失败");
  }
};

const loadHistory = async () => {
  try {
    const res = await axios.get("http://localhost:8001/messages");
    messages.value = res.data;
  } catch (err) {
    if (err instanceof AxiosError) {
      console.error("请求失败:", err.response?.status);
      Message.warning(`加载失败: ${err.response?.status}`);
    } else {
      console.error("未知错误:", err);
      Message.error("网络异常，请稍后重试");
    }
  }
};

onMounted(() => {
  loadHistory();
  scrollToBottom();
});
</script>

<style scoped>
:root {
  --primary-color: #165dff;
  --bg-color: #f7f8fa;
  --chat-bg: #ffffff;
}

.chat-layout {
  height: 100vh;
  width: 100vw;
  background-color: #f2f3f5;
  display: flex;
  justify-content: center;
  align-items: center;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto,
    "Helvetica Neue", Arial, sans-serif;
}

.chat-container {
  width: 100%;
  max-width: 1000px;
  height: 100%;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  position: relative;
  box-shadow: 0 0 20px rgba(0, 0, 0, 0.05);
}

/* 适配大屏幕，让它像一个App窗口 */
@media (min-width: 768px) {
  .chat-container {
    height: 95vh;
    border-radius: 16px;
    overflow: hidden;
  }
}

/* --- Header --- */
.chat-header {
  height: 60px;
  padding: 0 24px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.06);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.bot-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #e0f2fe 0%, #165dff 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.header-info {
  display: flex;
  flex-direction: column;
}

.title {
  font-weight: 600;
  font-size: 16px;
  color: #1d2129;
}

.status-dot {
  width: 8px;
  height: 8px;
  background: #00b42a;
  border-radius: 50%;
  display: inline-block;
  margin-top: 2px;
  box-shadow: 0 0 4px #00b42a;
}

/* --- Messages Area --- */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: #fcfcfc;
  scroll-behavior: smooth;
}

/* 自定义滚动条 */
.messages-area::-webkit-scrollbar {
  width: 6px;
}
.messages-area::-webkit-scrollbar-thumb {
  background: #e5e6eb;
  border-radius: 3px;
}

/* --- Empty State --- */
.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  max-width: 600px;
  margin: 0 auto;
}

.welcome-box {
  text-align: center;
  margin-bottom: 40px;
}

.welcome-icon {
  color: #165dff;
  margin-bottom: 16px;
  filter: drop-shadow(0 4px 6px rgba(22, 93, 255, 0.2));
}

.welcome-box h2 {
  font-size: 24px;
  color: #1d2129;
  margin-bottom: 8px;
}

.welcome-box p {
  color: #86909c;
  font-size: 14px;
}

/* --- Message List --- */
.message-row {
  display: flex;
  gap: 16px;
  margin-bottom: 24px;
  align-items: flex-start;
}

.assistant {
  flex-direction: row;
}

.user {
  flex-direction: row-reverse;
  text-align: right;
}

.avatar-col {
  flex-shrink: 0;
}

.ai-avatar {
  background-color: #fff;
  color: #165dff;
  border: 1px solid #e5e6eb;
}

.user-avatar {
  background: linear-gradient(135deg, #165dff 0%, #0e42d2 100%);
  color: #fff;
}

.content-col {
  max-width: 80%;
  display: flex;
  flex-direction: column;
}

.user .content-col {
  align-items: flex-end;
}

.bubble {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  position: relative;
  word-break: break-word;
}

/* AI Bubble */
.assistant .bubble {
  background: #fff;
  border: 1px solid #e5e6eb;
  border-top-left-radius: 2px; /* 气泡角 */
  color: #1d2129;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.03);
}

/* User Bubble */
.user .bubble {
  background: linear-gradient(135deg, #165dff 0%, #3a7afe 100%);
  color: #fff;
  border-top-right-radius: 2px;
  box-shadow: 0 4px 10px rgba(22, 93, 255, 0.2);
}

.user-text {
  white-space: pre-wrap;
}

/* 操作栏 */
.message-actions {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  margin-left: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}

.message-row:hover .message-actions {
  opacity: 1;
}

.action-btn {
  font-size: 12px;
  color: #86909c;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.action-btn:hover {
  background: #f2f3f5;
  color: #4e5969;
}

/* --- Typing Animation --- */
.typing-bubble {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 12px 20px !important;
  background: #f2f3f5 !important;
  border: none !important;
}

.dot {
  width: 6px;
  height: 6px;
  background: #86909c;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) {
  animation-delay: -0.32s;
}
.dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%,
  80%,
  100% {
    transform: scale(0);
  }
  40% {
    transform: scale(1);
  }
}

.thinking-text {
  font-size: 12px;
  color: #86909c;
  margin-top: 4px;
  margin-left: 4px;
}

/* --- Input Area --- */
.input-wrapper {
  padding: 24px;
  background: #fff;
  border-top: 1px solid rgba(0, 0, 0, 0.05);
}

.input-box {
  border: 1px solid #e5e6eb;
  border-radius: 12px;
  padding: 12px;
  background: #fff;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.02);
}

.input-box.is-focus {
  border-color: #165dff;
  box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.1);
}

.custom-textarea {
  background: transparent !important;
  border: none !important;
  padding: 0 !important;
  font-size: 15px;
}

.send-btn-area {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.send-btn {
  transition: all 0.3s;
}

.footer-tip {
  text-align: center;
  font-size: 12px;
  color: #c9cdd4;
  margin-top: 12px;
}
</style>
