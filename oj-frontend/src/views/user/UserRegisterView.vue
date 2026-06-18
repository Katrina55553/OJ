<template>
  <div class="register-view">
    <div class="register-form-container">
      <h2 class="register-title">用户注册</h2>
      <div class="register-sub-title">加入 OJ，开启编程之旅</div>

      <a-form
        layout="vertical"
        :model="form"
        @submit="handleSubmit"
        class="register-form"
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

        <a-form-item field="checkPassword" label="确认密码" hide-asterisk>
          <a-input-password
            v-model="form.checkPassword"
            placeholder="请再次输入密码"
            allow-clear
          >
            <template #prefix>
              <icon-lock />
            </template>
          </a-input-password>
        </a-form-item>

        <a-form-item style="margin-top: 20px">
          <a-button type="primary" html-type="submit" long size="large">
            注册
          </a-button>
          <div style="width: 100%; text-align: center; margin-top: 16px">
            已有账号？<a-link @click.prevent="handleLogin">立即登录</a-link>
          </div>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive } from "vue";
import { UserControllerService } from "../../../generated";
import { Message } from "@arco-design/web-vue";
import { IconUser, IconLock } from "@arco-design/web-vue/es/icon";
import router from "@/router";

const handleLogin = () => {
  router.push("/user/login");
};

// 表单数据，包含确认密码字段
const form = reactive({
  userAccount: "",
  userPassword: "",
  checkPassword: "",
});

const handleSubmit = async () => {
  // 非空校验
  if (!form.userAccount || !form.userPassword || !form.checkPassword) {
    Message.warning("请输入账号、密码及确认密码");
    return;
  }

  // 校验两次密码是否一致
  if (form.userPassword !== form.checkPassword) {
    Message.error("两次输入的密码不一致");
    return;
  }

  try {
    // 调用注册接口
    const res = await UserControllerService.userRegisterUsingPost(form);

    if (res.code === 0) {
      Message.success("注册成功，请登录");
      // 注册成功后跳转到登录页
      await router.push({
        path: "/user/login",
        replace: true,
      });
    } else {
      Message.error(res.message || "注册失败");
    }
  } catch (error) {
    Message.error("注册服务异常，请稍后重试");
  }
};
</script>

<style scoped>
.register-form-container {
  width: 100%;
  max-width: 400px;
  background: #161b22;
  padding: 32px 40px;
  border-radius: 8px;
  border: 1px solid #30363d;
}

.register-title {
  text-align: center;
  margin-bottom: 8px;
  color: #f0f6fc;
  font-weight: bold;
  font-size: 24px;
}

.register-sub-title {
  text-align: center;
  color: #8b949e;
  margin-bottom: 32px;
  font-size: 14px;
}

.register-form :deep(.arco-form-item) {
  margin-bottom: 20px;
}
</style>
