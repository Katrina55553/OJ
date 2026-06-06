<template>
  <a-row id="globalHeader" align="center" :wrap="false">
    <!-- 1. 菜单栏区域 (Logo + 动态路由) -->
    <a-col flex="auto">
      <a-menu
        mode="horizontal"
        :selected-keys="selectedKeys"
        @menu-item-click="doMenuClick"
      >
        <!-- Logo区 -->
        <a-menu-item
          key="0"
          :style="{ padding: 0, marginRight: '38px' }"
          disabled
        >
          <div class="title-bar" @click="router.push('/')">
            <img class="logo" src="../assets/logo.png" alt="OJ Logo" />
            <div class="title">OJ 在线判题</div>
          </div>
        </a-menu-item>

        <!-- 普通用户菜单 -->
        <a-menu-item v-for="item in visibleRoutes" :key="item.path">
          {{ item.name }}
        </a-menu-item>

        <!-- 管理员菜单  -->
        <a-menu-item v-for="item in adminRoutes" :key="item.path">
          {{ item.name }}
        </a-menu-item>
      </a-menu>
    </a-col>

    <!-- 2. 右侧用户交互区域 -->
    <a-col flex="180px">
      <div class="right-actions">
        <a-space size="large">
          <!-- 每日签到组件 -->
          <DailyCheckIn />

          <!-- 已登录状态 -->
          <div v-if="isLogin">
            <a-dropdown @select="handleSelect">
              <div class="user-avatar-trigger">
                <a-avatar
                  :size="32"
                  :style="{
                    backgroundColor: !loginUser.userAvatar ? '#6a5454' : '',
                  }"
                  :image-url="loginUser.userAvatar"
                >
                  {{ loginUser.userName?.[0]?.toUpperCase() ?? "U" }}
                </a-avatar>
                <span class="user-name-text">{{ loginUser.userName }}</span>
                <icon-down />
              </div>

              <!-- 下拉菜单 -->
              <template #content>
                <a-doption value="userCenter">
                  <template #icon><icon-user /></template>
                  个人主页
                </a-doption>
                <a-doption value="logout">
                  <template #icon><icon-export /></template>
                  退出登录
                </a-doption>
              </template>
            </a-dropdown>
          </div>

          <!-- 未登录状态 -->
          <div v-else>
            <a-button type="primary" class="login-btn" @click="toLogin">
              登录
            </a-button>
          </div>
          <ThemeSwitcher />
        </a-space>
      </div>
    </a-col>
  </a-row>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { useStore } from "vuex";
import { routes } from "@/router/routes";
import checkAccess from "@/access/checkAccess";
import ACCESS_ENUM from "@/access/ACCESS_ENUM";
import DailyCheckIn from "@/components/DailyCheckIn.vue";
import { IconUser, IconExport, IconDown } from "@arco-design/web-vue/es/icon";
import ThemeSwitcher from "@/components/ThemeSwitcher.vue";

const router = useRouter();
const store = useStore();

// -------- 用户信息 --------
const loginUser = computed(() => store.state.user.loginUser);
const isLogin = computed(
  () => loginUser.value && loginUser.value.userRole !== ACCESS_ENUM.NOT_LOGIN
);
const isAdmin = computed(
  () => loginUser.value && loginUser.value.userRole === ACCESS_ENUM.ADMIN
);

// -------- 菜单路由 --------
// 普通用户菜单
const visibleRoutes = computed(() =>
  routes.filter(
    (item) =>
      !item.meta?.hideInMenu &&
      !item.meta?.adminOnly &&
      checkAccess(store.state.user.loginUser, item.meta?.access)
  )
);

// 管理员菜单
const adminRoutes = computed(() => {
  if (!isAdmin.value) return [];
  return routes.filter(
    (item) =>
      item.meta?.adminOnly &&
      checkAccess(store.state.user.loginUser, item.meta?.access)
  );
});

// 当前选中菜单
const selectedKeys = ref<string[]>(["/"]);
router.afterEach((to) => {
  selectedKeys.value = [to.path];
});

// 点击菜单跳转
const doMenuClick = (key: string) => {
  router.push(key);
};

// -------- 用户操作 --------
const toLogin = () => {
  router.push("/user/login");
};

const handleSelect = (value: string) => {
  if (value === "userCenter") {
    router.push("/profile");
  } else if (value === "logout") {
    store.commit("user/updateUser", {
      userName: "未登录",
      userRole: ACCESS_ENUM.NOT_LOGIN,
    });
    router.push("/user/login");
  }
};
</script>

<style scoped>
#globalHeader {
  background: #fff;
  z-index: 100;
  height: 100%;
}

.title-bar {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.logo {
  height: 40px;
  margin-right: 12px;
}
.title {
  color: #1d2129;
  font-size: 18px;
  font-weight: 600;
}
.right-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  padding-right: 24px;
  height: 100%;
}
.user-avatar-trigger {
  cursor: pointer;
  display: flex;
  align-items: center;
  color: #1d2129;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s;
}
.user-avatar-trigger:hover {
  background-color: #f2f3f5;
}
.user-name-text {
  margin-left: 8px;
  margin-right: 4px;
  font-size: 14px;
  font-weight: 500;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.login-btn {
  border-radius: 4px;
}
</style>
