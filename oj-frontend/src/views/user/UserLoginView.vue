<template>
  <div class="login-view">
    <div class="login-form-container">
      <h2 class="login-title">用户登录</h2>
      <div class="login-sub-title">欢迎使用 Katrina's OJ</div>

      <a-form
        layout="vertical"
        :model="form"
        @submit="handleSubmit"
        class="login-form"
      >
        <a-form-item field="userAccount" label="账号" hide-asterisk>
          <a-input v-model="form.userAccount" placeholder="请输入账号">
            <template #prefix>
              <icon-user />
            </template>
          </a-input>
        </a-form-item>

        <a-form-item field="userPassword" label="密码" hide-asterisk>
          <a-input-password
            v-model="form.userPassword"
            placeholder="请输入密码"
            allow-clear
          >
            <template #prefix>
              <icon-lock />
            </template>
          </a-input-password>
        </a-form-item>

        <div class="form-actions">
          <a-checkbox v-model="autoLogin">自动登录</a-checkbox>
          <a-link @click.prevent="handleForgotPassword">忘记密码？</a-link>
        </div>

        <a-form-item style="margin-top: 20px">
          <a-button type="primary" html-type="submit" long size="large">
            登录
          </a-button>
          <div style="width: 100%; text-align: center; margin-top: 16px">
            还没有账号？<a-link href="/user/register">立即注册</a-link>
          </div>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { UserControllerService } from "../../../generated";
import { Message } from "@arco-design/web-vue";
import { IconUser, IconLock } from "@arco-design/web-vue/es/icon"; // 引入图标
import router from "@/router";
import store from "@/store";
import ACCESS_ENUM from "@/access/ACCESS_ENUM";

const handleForgotPassword = () => {
  Message.info("功能暂未开放，如有需要请联系管理员");
  // TODO: 跳转到忘记密码页面
  // router.push("/user/forgot-password");
};

const form = reactive({
  userAccount: "",
  userPassword: "",
});
const autoLogin = ref(true);

const handleSubmit = async () => {
  if (!form.userAccount || !form.userPassword) {
    Message.warning("请输入账号和密码");
    return;
  }

  try {
    const res = await UserControllerService.userLoginUsingPost(form);
    if (res.code === 0 && res.data) {
      Message.success("登录成功");

      // 存储 JWT Token
      if (res.data.token) {
        localStorage.setItem("token", res.data.token);
      }

      store.commit("user/updateUser", res.data);

      router.push({ path: "/", replace: true });
    } else {
      Message.error(res.message || "登录失败");
    }
  } catch (error) {
    console.error("登录异常:", error);
    Message.error("登录服务异常，请稍后重试");
  }
};

/**
 * 页面加载时检查登录状态
 */
onMounted(() => {
  const loginUser = store.state.user.loginUser;

  // 如果存在 loginUser 且角色不是 "未登录"
  if (loginUser && loginUser.userRole !== ACCESS_ENUM.NOT_LOGIN) {
    router.push({
      path: "/",
      replace: true, // replace 防止用户点浏览器的“后退”又回到登录页
    });
  }
});
</script>

<style scoped>
.login-form-container {
  width: 100%;
  max-width: 400px;
  background: #161b22;
  padding: 32px 40px;
  border-radius: 8px;
  border: 1px solid #30363d;
}

.login-title {
  text-align: center;
  margin-bottom: 8px;
  color: #f0f6fc;
  font-weight: bold;
  font-size: 24px;
}

.login-sub-title {
  text-align: center;
  color: #8b949e;
  margin-bottom: 32px;
  font-size: 14px;
}

.login-form :deep(.arco-form-item) {
  margin-bottom: 20px;
}

.form-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.form-actions :deep(.arco-checkbox-label) {
  color: #c9d1d9 !important;
}
</style>
