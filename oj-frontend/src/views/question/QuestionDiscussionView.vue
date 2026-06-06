<template>
  <div class="discussion-view">
    <div class="main-container">
      <!-- 顶部：发表评论区 -->
      <div class="post-box">
        <h2 class="section-title">参与讨论</h2>
        <div class="editor-container">
          <MdEditor v-model="newCommentContent" />
        </div>
        <div class="post-actions">
          <a-button
            type="primary"
            size="large"
            :loading="submitting"
            @click="handleSubmit"
            style="width: 120px"
          >
            发表评论
          </a-button>
        </div>
      </div>

      <a-divider />

      <!-- 列表：评论展示区 -->
      <div class="comment-list-wrapper">
        <div class="list-header">
          <h2 class="section-title">全部评论 ({{ commentList.length }})</h2>
          <a-radio-group type="button" v-model="sortOrder" size="small">
            <a-radio value="newest">最新</a-radio>
            <a-radio value="hottest">最热</a-radio>
          </a-radio-group>
        </div>

        <a-list :bordered="false" :data="commentList" class="custom-list">
          <template #item="{ item }">
            <a-list-item class="comment-item">
              <a-comment
                :author="item.userName"
                :datetime="item.createTime"
                align="right"
              >
                <!-- 头像 -->
                <template #avatar>
                  <a-avatar>
                    <img alt="avatar" :src="item.userAvatar" />
                  </a-avatar>
                </template>

                <!-- 评论内容：使用 MdPreview 渲染 -->
                <template #content>
                  <div class="comment-content">
                    <MdPreview :value="item.content" />
                  </div>
                </template>

                <!-- 底部操作栏 -->
                <template #actions>
                  <span class="action" @click="handleLike(item)">
                    <icon-heart
                      :style="{ color: item.isLiked ? '#f53f3f' : '' }"
                    />
                    {{ item.likes }}
                  </span>
                  <span class="action" @click="toggleReply(item)">
                    <icon-message /> 回复
                  </span>
                  <span
                    v-if="item.isMyComment"
                    class="action delete"
                    @click="handleDelete(item)"
                  >
                    <icon-delete /> 删除
                  </span>
                </template>

                <!-- 回复输入框 (点击回复后显示) -->
                <div v-if="item.showReplyInput" class="reply-box">
                  <a-textarea
                    v-model="item.replyContent"
                    placeholder="请输入回复内容..."
                    :auto-size="{ minRows: 2, maxRows: 4 }"
                    style="margin-bottom: 8px"
                  />
                  <div style="text-align: right">
                    <a-space>
                      <a-button
                        size="small"
                        @click="item.showReplyInput = false"
                        >取消</a-button
                      >
                      <a-button
                        type="primary"
                        size="small"
                        @click="submitReply(item)"
                        >回复</a-button
                      >
                    </a-space>
                  </div>
                </div>
              </a-comment>
            </a-list-item>
          </template>
        </a-list>

        <!-- 空状态 -->
        <a-empty
          v-if="commentList.length === 0"
          description="暂无评论，快来抢沙发吧~"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { Message } from "@arco-design/web-vue";
import {
  IconHeart,
  IconMessage,
  IconDelete,
} from "@arco-design/web-vue/es/icon";
import MdEditor from "@/components/MdEditor.vue";
import MdPreview from "@/components/MdPreview.vue";

// --- 状态定义 ---
const submitting = ref(false);
const newCommentContent = ref("");
const sortOrder = ref("newest");

// 模拟数据
interface CommentItem {
  id: number;
  userName: string;
  userAvatar: string;
  content: string;
  createTime: string;
  likes: number;
  isLiked: boolean;
  isMyComment: boolean;
  showReplyInput?: boolean;
  replyContent?: string;
}

const commentList = ref<CommentItem[]>([]);

// --- 方法 ---

// 加载评论列表
const loadComments = () => {
  // TODO: 调用后端 API 获取评论
  // 模拟数据
  commentList.value = [
    {
      id: 1,
      userName: "CodeMaster",
      userAvatar:
        "https://p1-arco.byteimg.com/tos-cn-i-uwbnlip3yd/3ee5f13fb09879ecb5185e440cef6eb9.png~tplv-uwbnlip3yd-webp.webp",
      content:
        "这道题的关键在于使用 **动态规划**。\n\n状态转移方程为：\n$$ dp[i] = max(dp[i-1], dp[i-2] + nums[i]) $$",
      createTime: "1小时前",
      likes: 24,
      isLiked: false,
      isMyComment: false,
    },
    {
      id: 2,
      userName: "菜鸟小白",
      userAvatar:
        "https://p1-arco.byteimg.com/tos-cn-i-uwbnlip3yd/a8c8cdb109cb051163646151a4a5083b.png~tplv-uwbnlip3yd-webp.webp",
      content:
        "为什么我的代码会超时？有没有大佬帮忙看看？\n```cpp\n// 暴力解法...\n```",
      createTime: "2小时前",
      likes: 5,
      isLiked: true,
      isMyComment: true,
    },
  ];
};

// 提交主评论
const handleSubmit = async () => {
  if (!newCommentContent.value.trim()) {
    Message.warning("评论内容不能为空");
    return;
  }

  submitting.value = true;
  try {
    // TODO: 调用后端提交 API
    await new Promise((r) => setTimeout(r, 800)); // 模拟请求

    // 模拟插入新评论
    commentList.value.unshift({
      id: Date.now(),
      userName: "当前用户",
      userAvatar:
        "https://p1-arco.byteimg.com/tos-cn-i-uwbnlip3yd/3ee5f13fb09879ecb5185e440cef6eb9.png~tplv-uwbnlip3yd-webp.webp",
      content: newCommentContent.value,
      createTime: "刚刚",
      likes: 0,
      isLiked: false,
      isMyComment: true,
    });

    newCommentContent.value = ""; // 清空输入框
    Message.success("评论发表成功");
  } catch (e) {
    Message.error("发表失败");
  } finally {
    submitting.value = false;
  }
};

// 点赞
const handleLike = (item: CommentItem) => {
  item.isLiked = !item.isLiked;
  item.likes += item.isLiked ? 1 : -1;
};

// 切换回复框
const toggleReply = (item: CommentItem) => {
  item.showReplyInput = !item.showReplyInput;
  item.replyContent = ""; // 清空之前的输入
};

// 提交回复
const submitReply = (item: CommentItem) => {
  if (!item.replyContent?.trim()) return Message.warning("请输入内容");

  Message.success("回复成功");
  item.showReplyInput = false;
};

// 删除评论
const handleDelete = (item: CommentItem) => {
  // TODO: 调用后端删除 API
  commentList.value = commentList.value.filter((c) => c.id !== item.id);
  Message.success("删除成功");
};

onMounted(() => {
  loadComments();
});
</script>

<style scoped>
.discussion-view {
  min-height: 100vh;
  background: #f7f8fa;
  padding: 24px;
}

.main-container {
  max-width: 1000px;
  margin: 0 auto;
}

/* --- 发帖区样式 --- */
.post-box {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.section-title {
  margin: 0 0 16px 0;
  font-size: 18px;
  color: #1d2129;
  font-weight: 600;
}

.editor-container {
  margin-bottom: 16px;
}

.post-actions {
  text-align: right;
}

/* --- 列表区样式 --- */
.comment-list-wrapper {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  margin-top: 24px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f2f3f5;
}

.custom-list .arco-list-item {
  padding: 20px 0;
  border-bottom: 1px solid #f2f3f5;
}

/* 评论内容容器：防止 Markdown 溢出 */
.comment-content {
  margin-top: 8px;
  max-width: 100%;
  overflow: hidden;
}

/* 操作栏样式 */
.action {
  cursor: pointer;
  color: #86909c;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  transition: all 0.2s;
}

.action:hover {
  color: #165dff;
}

.action.delete:hover {
  color: #f53f3f;
}

/* 回复框 */
.reply-box {
  margin-top: 12px;
  background: #f7f8fa;
  padding: 12px;
  border-radius: 4px;
}
</style>
