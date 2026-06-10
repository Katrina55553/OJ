<template>
  <div class="discussion-view">
    <div class="main-container">
      <!-- 顶部：发表评论区 -->
      <div class="post-box">
        <h2 class="section-title">参与讨论</h2>
        <div class="editor-container">
          <a-input
            v-model="newCommentContent"
            type="textarea"
            :rows="4"
            placeholder="说点什么吧…支持 Markdown"
          />
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
          <h2 class="section-title">全部评论 ({{ total }})</h2>
        </div>

        <a-list
          :bordered="false"
          :data="commentList"
          :loading="loading"
          class="custom-list"
        >
          <template #item="{ item }">
            <a-list-item class="comment-item">
              <a-comment
                :author="item.userName"
                :datetime="formatTime(item.createTime)"
                align="right"
              >
                <!-- 头像 -->
                <template #avatar>
                  <a-avatar>
                    <img alt="avatar" :src="item.userAvatar || defaultAvatar" />
                  </a-avatar>
                </template>

                <!-- 评论内容 -->
                <template #content>
                  <div class="comment-content">{{ item.content }}</div>
                </template>

                <!-- 底部操作栏 -->
                <template #actions>
                  <span class="action" @click="handleLike(item)">
                    <icon-heart
                      :style="{ color: item.hasThumb ? '#f53f3f' : '' }"
                    />
                    {{ item.thumbNum }}
                  </span>
                  <span
                    v-if="isMyComment(item)"
                    class="action delete"
                    @click="handleDelete(item)"
                  >
                    <icon-delete /> 删除
                  </span>
                </template>
              </a-comment>
            </a-list-item>
          </template>
        </a-list>

        <!-- 分页 -->
        <div v-if="total > pageSize" class="pagination-wrapper">
          <a-pagination
            :total="total"
            :current="current"
            :page-size="pageSize"
            show-total
            @change="handlePageChange"
          />
        </div>

        <!-- 空状态 -->
        <a-empty v-if="!loading && commentList.length === 0" description="暂无评论，快来抢沙发吧~" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { Message, Modal } from "@arco-design/web-vue";
import {
  IconHeart,
  IconDelete,
} from "@arco-design/web-vue/es/icon";
import { useStore } from "vuex";
import axios from "axios";

const store = useStore();
const currentUserId = ref<number | null>(null);

const submitting = ref(false);
const loading = ref(false);
const newCommentContent = ref("");
const commentList = ref<any[]>([]);
const total = ref(0);
const current = ref(1);
const pageSize = ref(10);

const defaultAvatar =
  "https://p1-arco.byteimg.com/tos-cn-i-uwbnlip3yd/3ee5f13fb09879ecb5185e440cef6eb9.png~tplv-uwbnlip3yd-webp.webp";

// 判断是否是自己的评论
const isMyComment = (item: any) => {
  return currentUserId.value && item.userId === currentUserId.value;
};

// 时间格式化
const formatTime = (time: string) => {
  if (!time) return "";
  const date = new Date(time);
  const now = new Date();
  const diff = (now.getTime() - date.getTime()) / 1000; // 秒
  if (diff < 60) return "刚刚";
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`;
  if (diff < 604800) return `${Math.floor(diff / 86400)} 天前`;
  return date.toLocaleDateString("zh-CN");
};

// 加载评论列表
const loadComments = async () => {
  loading.value = true;
  try {
    const res = await axios.post("/api/post/list/page/vo", {
      current: current.value,
      pageSize: pageSize.value,
      sortField: "createTime",
      sortOrder: "descend",
    });
    if (res.data?.code === 0) {
      commentList.value = res.data.data.records || [];
      total.value = Number(res.data.data.total) || 0;
    } else {
      Message.error(res.data?.message || "加载失败");
    }
  } catch (e: any) {
    Message.error("加载评论失败：" + (e.message || "网络错误"));
  } finally {
    loading.value = false;
  }
};

// 提交评论
const handleSubmit = async () => {
  const content = newCommentContent.value.trim();
  if (!content) {
    Message.warning("评论内容不能为空");
    return;
  }

  submitting.value = true;
  try {
    const res = await axios.post("/api/post/add", {
      title: content.substring(0, 30), // 取前 30 字作为标题（必填）
      content,
      tags: [],
    });
    if (res.data?.code === 0) {
      Message.success("评论发表成功");
      newCommentContent.value = "";
      current.value = 1;
      await loadComments();
    } else {
      Message.error(res.data?.message || "发表失败");
    }
  } catch (e: any) {
    Message.error("发表失败：" + (e.message || "网络错误"));
  } finally {
    submitting.value = false;
  }
};

// 点赞 / 取消点赞
const handleLike = async (item: any) => {
  try {
    const res = await axios.post("/api/post_thumb/", {
      postId: item.id,
    });
    if (res.data?.code === 0) {
      // result: 1=点赞成功, 0=取消点赞, -1=未登录
      const result = res.data.data;
      if (result === 1) {
        item.hasThumb = true;
        item.thumbNum = (item.thumbNum || 0) + 1;
      } else if (result === 0) {
        item.hasThumb = false;
        item.thumbNum = Math.max(0, (item.thumbNum || 0) - 1);
      } else if (result === -1) {
        Message.warning("请先登录");
      }
    } else {
      Message.error(res.data?.message || "操作失败");
    }
  } catch (e: any) {
    Message.error("点赞失败：" + (e.message || "网络错误"));
  }
};

// 删除评论
const handleDelete = (item: any) => {
  Modal.confirm({
    title: "确认删除",
    content: "删除后不可恢复，确认删除该评论？",
    okText: "确认",
    cancelText: "取消",
    onOk: async () => {
      try {
        const res = await axios.post("/api/post/delete", { id: item.id });
        if (res.data?.code === 0) {
          Message.success("删除成功");
          await loadComments();
        } else {
          Message.error(res.data?.message || "删除失败");
        }
      } catch (e: any) {
        Message.error("删除失败：" + (e.message || "网络错误"));
      }
    },
  });
};

// 分页切换
const handlePageChange = (page: number) => {
  current.value = page;
  loadComments();
};

// 获取当前用户信息
const loadCurrentUser = () => {
  const user = store.state.user?.loginUser;
  if (user?.id) {
    currentUserId.value = user.id;
  }
};

onMounted(async () => {
  loadCurrentUser();
  await loadComments();
});
</script>

<style scoped>
.discussion-view {
  min-height: 100vh;
  background: #0d1117;
  padding: 24px;
}

.main-container {
  max-width: 1000px;
  margin: 0 auto;
}

/* --- 发帖区样式 --- */
.post-box {
  background: #161b22;
  padding: 24px;
  border-radius: 8px;
  border: 1px solid #30363d;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.section-title {
  margin: 0 0 16px 0;
  font-size: 18px;
  color: #f0f6fc;
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
  background: #161b22;
  padding: 24px;
  border-radius: 8px;
  border: 1px solid #30363d;
  margin-top: 24px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 12px;
  border-bottom: 1px solid #21262d;
}

.custom-list .arco-list-item {
  padding: 20px 0;
  border-bottom: 1px solid #21262d;
}

.comment-content {
  margin-top: 8px;
  max-width: 100%;
  overflow: hidden;
  color: #c9d1d9;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 操作栏样式 */
.action {
  cursor: pointer;
  color: #8b949e;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  margin-right: 16px;
  transition: all 0.2s;
}

.action:hover {
  color: #58a6ff;
}

.action.delete:hover {
  color: #f85149;
}

/* 分页 */
.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
