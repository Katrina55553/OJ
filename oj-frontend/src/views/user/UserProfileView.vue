<template>
  <div class="user-profile-view">
    <a-row :gutter="24" v-if="userInfo.id">
      <!-- 左侧：个人信息卡片 -->
      <a-col :xs="24" :md="8" :lg="6">
        <a-card class="user-card" :bordered="false">
          <div class="user-info-header">
            <a-avatar :size="80" class="user-avatar">
              <img
                alt="avatar"
                :src="
                  userInfo.userAvatar ||
                  'https://p1-arco.byteimg.com/tos-cn-i-uwbnlip3yd/3ee5f13fb09879ecb5185e440cef6eb9.png~tplv-uwbnlip3yd-webp.webp'
                "
              />
            </a-avatar>
            <h2 class="username">{{ userInfo.userName }}</h2>
            <div class="user-role">
              <a-tag
                color="arcoblue"
                v-if="userInfo.userRole === ACCESS_ENUM.ADMIN"
              >
                <template #icon><icon-user-group /></template>
                管理员
              </a-tag>
              <a-tag color="green" v-else>普通用户</a-tag>
            </div>
          </div>

          <a-divider />

          <div class="info-list">
            <div class="info-item">
              <span class="label"><icon-idcard /> 账号ID</span>
              <span class="value">{{ userInfo.id }}</span>
            </div>
            <div class="info-item">
              <span class="label"><icon-email /> 简介</span>
              <span class="value bio" :title="userInfo.userProfile">
                {{ userInfo.userProfile || "这个人很懒，什么都没写" }}
              </span>
            </div>
            <div class="info-item">
              <span class="label"><icon-calendar /> 注册时间</span>
              <span class="value">{{ formatTime(userInfo.createTime) }}</span>
            </div>
          </div>

          <a-divider />

          <a-button type="primary" long shape="round" @click="openUpdateModal">
            <template #icon><icon-edit /></template>
            修改个人资料
          </a-button>
        </a-card>
      </a-col>

      <!-- 右侧数据展示区域保持不变 -->
      <a-col :xs="24" :md="16" :lg="18">
        <QuestionHeatmap />

        <a-card title="最近提交记录" :bordered="false">
          <a-empty description="暂无提交记录" />
        </a-card>
      </a-col>
    </a-row>

    <!-- 未登录的兜底展示 -->
    <div v-else class="not-login-state">
      <a-empty description="请先登录后查看个人主页">
        <a-button type="primary" href="/user/login">去登录</a-button>
      </a-empty>
    </div>

    <!-- 修改资料弹窗 -->
    <a-modal
      v-model:visible="updateModalVisible"
      title="修改个人资料"
      @ok="handleUpdateUser"
      @cancel="updateModalVisible = false"
    >
      <a-form :model="updateForm">
        <a-form-item field="userName" label="昵称">
          <a-input v-model="updateForm.userName" placeholder="请输入新昵称" />
        </a-form-item>
        <a-form-item field="userProfile" label="个人简介">
          <a-textarea
            v-model="updateForm.userProfile"
            placeholder="请输入个人简介"
            :max-length="200"
            show-word-limit
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useStore } from "vuex";
import { Message } from "@arco-design/web-vue";
import { useRouter } from "vue-router";
import ACCESS_ENUM from "@/access/ACCESS_ENUM";
import {
  IconUserGroup,
  IconIdcard,
  IconEmail,
  IconCalendar,
  IconEdit,
} from "@arco-design/web-vue/es/icon";
import QuestionHeatmap from "@/views/question/QuestionHeatmap.vue";

const store = useStore();
const router = useRouter();

const userInfo = ref({
  id: null,
  userName: "",
  userAvatar: "",
  userProfile: "",
  userRole: "",
  createTime: "",
});

const updateModalVisible = ref(false);
const updateForm = reactive({
  userName: "",
  userProfile: "",
});

/**
 * 加载数据
 */
const loadData = async () => {
  const loginUser = store.state.user.loginUser;

  // ------ 严格的登录校验 ------
  if (
    !loginUser ||
    !loginUser.id ||
    loginUser.userRole === ACCESS_ENUM.NOT_LOGIN
  ) {
    Message.warning("请先登录");
    router.push({
      path: "/user/login",
      replace: true,
    });
    return;
  }

  // 登录了，才赋值
  userInfo.value = { ...loginUser };
  updateForm.userName = loginUser.userName;
  updateForm.userProfile = loginUser.userProfile;

  // Todo 后续加载统计数据的逻辑...
};

const handleUpdateUser = () => {
  Message.success("更新成功（模拟）");
  updateModalVisible.value = false;
};

const openUpdateModal = () => {
  updateForm.userName = userInfo.value.userName;
  updateForm.userProfile = userInfo.value.userProfile;
  updateModalVisible.value = true;
};

const formatTime = (time: string) => {
  if (!time) return "未知";
  return new Date(time).toLocaleDateString();
};

onMounted(() => {
  loadData();
});
</script>

<style scoped>
.user-profile-view {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.not-login-state {
  margin-top: 100px;
  text-align: center;
}
.user-card {
  text-align: center;
  border-radius: 8px;
}
.user-info-header {
  padding: 10px 0;
}
.username {
  margin: 16px 0 8px;
  font-size: 22px;
  color: #1d2129;
}
.info-list {
  text-align: left;
  padding: 0 12px;
}
.info-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
}
.info-item .label {
  color: #86909c;
  display: flex;
  align-items: center;
  gap: 8px;
}
.info-item .value {
  color: #1d2129;
  font-weight: 500;
}
</style>
