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
          <a-button
            type="text"
            shape="circle"
            href="https://github.com/Katrina55553/OJ"
            target="_blank"
            title="GitHub"
          >
            <template #icon>
              <svg
                viewBox="0 0 24 24"
                width="20"
                height="20"
                fill="currentColor"
              >
                <path
                  d="M12 0C5.37 0 0 5.37 0 12c0 5.3 3.438 9.8 8.205 11.385.6.113.82-.258.82-.577 0-.285-.01-1.04-.015-2.04-3.338.724-4.042-1.61-4.042-1.61-.546-1.385-1.335-1.755-1.335-1.755-1.087-.744.084-.729.084-.729 1.205.084 1.838 1.236 1.838 1.236 1.07 1.835 2.809 1.305 3.495.998.108-.776.417-1.305.76-1.605-2.665-.3-5.466-1.332-5.466-5.93 0-1.31.465-2.38 1.235-3.22-.135-.303-.54-1.523.105-3.176 0 0 1.005-.322 3.3 1.23.96-.267 1.98-.399 3-.405 1.02.006 2.04.138 3 .405 2.28-1.552 3.285-1.23 3.285-1.23.645 1.653.24 2.873.12 3.176.765.84 1.23 1.91 1.23 3.22 0 4.61-2.805 5.625-5.475 5.92.42.36.81 1.096.81 2.22 0 1.605-.015 2.896-.015 3.286 0 .315.21.69.825.57C20.565 21.795 24 17.295 24 12c0-6.63-5.37-12-12-12z"
                />
              </svg>
            </template>
          </a-button>
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
import { IconUser, IconExport, IconDown } from "@arco-design/web-vue/es/icon";
import { UserControllerService } from "../../generated";

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

const handleSelect = async (value: string) => {
  if (value === "logout") {
    try {
      await UserControllerService.userLogoutUsingPost();
    } catch {
      // 即使后端调用失败，也清除本地状态
    }
    // 清除 JWT Token
    localStorage.removeItem("token");
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
  background: #161b22;
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
  color: #f0f6fc;
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
  color: #c9d1d9;
  padding: 4px 8px;
  border-radius: 4px;
  transition: all 0.2s;
}
.user-avatar-trigger:hover {
  background-color: #21262d;
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
  color: #c9d1d9;
}
.login-btn {
  border-radius: 4px;
}
</style>
