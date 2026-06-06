<template>
  <div class="daily-check-in-trigger">
    <!-- 动态修改 Tooltip 文字 -->
    <a-tooltip
      :content="isCheckedIn ? '今日已签到' : '今日运势 / 每日签到'"
      position="bottom"
    >
      <a-button
        class="trigger-btn"
        :class="{ 'is-checked': isCheckedIn }"
        shape="circle"
        size="large"
        @click="handleBtnClick"
      >
        <template #icon>
          <!-- 没打卡显示红礼物，打卡后显示绿色的勾 -->
          <icon-check-circle
            v-if="isCheckedIn"
            :style="{ fontSize: '20px', color: '#00b42a' }"
          />
          <icon-gift v-else :style="{ fontSize: '20px', color: '#d93025' }" />
        </template>
      </a-button>
    </a-tooltip>
  </div>

  <a-modal
    v-model:visible="visible"
    :footer="false"
    :hide-title="true"
    :width="320"
    modal-class="mini-fortune-modal"
  >
    <div class="fortune-container">
      <div class="close-icon" @click="visible = false">
        <icon-close />
      </div>

      <!-- 抽签场景 -->
      <div v-if="!showResult" class="draw-scene">
        <h3 class="scene-title">每日一签</h3>
        <div
          class="lucky-box"
          :class="{ shaking: isDrawing }"
          @click="startDraw"
        >
          <div class="box-text">
            <span v-if="!isDrawing">点击<br />抽签</span>
            <span v-else>祈福<br />中...</span>
          </div>
        </div>
        <p class="scene-tip">心诚则灵</p>
      </div>

      <!-- 结果场景 -->
      <div v-else class="result-scene">
        <div class="result-badge">{{ currentFortune.level }}</div>
        <div class="result-text">“ {{ currentFortune.text }} ”</div>
        <div class="result-desc">
          宜：{{ currentFortune.good }} <br />
          忌：{{ currentFortune.bad }}
        </div>
        <a-button
          type="primary"
          size="small"
          shape="round"
          @click="visible = false"
        >
          收下好运
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import {
  IconGift,
  IconClose,
  IconCheckCircle,
} from "@arco-design/web-vue/es/icon";
import { Message } from "@arco-design/web-vue";
import { useStore } from "vuex";
import { useRouter } from "vue-router";
import ACCESS_ENUM from "@/access/ACCESS_ENUM";

// TODO: 后端接口 Service
// import { UserControllerService } from "../../../generated";

const store = useStore();
const router = useRouter();

const visible = ref(false);
const isDrawing = ref(false);
const showResult = ref(false);
const isCheckedIn = ref(false);

interface Fortune {
  level: string;
  text: string;
  good: string;
  bad: string;
}

const currentFortune = reactive<Fortune>({
  level: "",
  text: "",
  good: "",
  bad: "",
});

// 本地模拟池
const fortunePool: Fortune[] = [
  {
    level: "大吉",
    text: "代码一次过，Bug绕道走",
    good: "提交代码",
    bad: "开长会",
  },
  { level: "上上", text: "薪资翻倍，Offer到手", good: "谈薪资", bad: "摸鱼" },
  { level: "中吉", text: "需求明确，不改方案", good: "沟通", bad: "熬夜" },
  { level: "小吉", text: "按时下班，心情舒畅", good: "约饭", bad: "纠结" },
];

onMounted(() => {
  checkTodayStatus();
});

/**
 * 检查今日签到状态
 */
const checkTodayStatus = async () => {
  const loginUser = store.state.user.loginUser;
  if (!loginUser || !loginUser.id) return;

  // =================================================================
  // TODO: 后端对接 获取今日签到状态
  // 接口: GET /api/user/checkin/today
  // try {
  //   const res = await UserControllerService.getCheckInStatus();
  //   if (res.code === 0 && res.data === true) {
  //     isCheckedIn.value = true;
  //     return; // 如果后端确认已签到，直接返回，不再走下面的 localStorage 逻辑
  //   }
  // } catch (e) {
  //   console.error(e);
  // }
  // =================================================================

  // --- 前端模拟逻辑
  const todayStr = new Date().toISOString().split("T")[0];
  const storageKey = `checkin_${loginUser.id}_${todayStr}`;
  if (localStorage.getItem(storageKey)) {
    isCheckedIn.value = true;
  }
};

const handleBtnClick = () => {
  const loginUser = store.state.user.loginUser;
  if (
    !loginUser ||
    !loginUser.userRole ||
    loginUser.userRole === ACCESS_ENUM.NOT_LOGIN
  ) {
    Message.warning("请先登录再进行签到");
    router.push("/user/login");
    return;
  }

  if (isCheckedIn.value) {
    Message.info("今日好运已送达，明天再来吧~");
    return;
  }

  openModal();
};

const openModal = () => {
  visible.value = true;
  if (!isCheckedIn.value) {
    showResult.value = false;
    isDrawing.value = false;
  }
};

const startDraw = () => {
  if (isDrawing.value) return;
  isDrawing.value = true;

  // 模拟抽签动画延迟
  setTimeout(async () => {
    // =================================================================
    // TODO: 后端对接 执行签到动作，并获取运势结果
    // 接口: POST /api/user/checkin
    // try {
    //   const res = await UserControllerService.doCheckIn();
    //   if (res.code === 0) {
    //     // 1. 设置运势结果
    //     currentFortune.level = res.data.fortuneLevel; // 例如 "大吉"
    //     currentFortune.text = res.data.fortuneText;
    //     currentFortune.good = res.data.goodThing;
    //     currentFortune.bad = res.data.badThing;
    //
    //     // 2. 更新状态
    //     isDrawing.value = false;
    //     showResult.value = true;
    //     finishCheckIn(); // 标记为已签到
    //     return;
    //   } else {
    //     Message.error(res.message || "签到失败");
    //     isDrawing.value = false;
    //     return;
    //   }
    // } catch (e) {
    //   Message.error("网络错误");
    //   isDrawing.value = false;
    //   return;
    // }
    // =================================================================

    // 前端模拟
    const random = fortunePool[Math.floor(Math.random() * fortunePool.length)];
    currentFortune.level = random.level;
    currentFortune.text = random.text;
    currentFortune.good = random.good;
    currentFortune.bad = random.bad;

    isDrawing.value = false;
    showResult.value = true;
    finishCheckIn();
    // --------------------------------------------------
  }, 1200);
};

const finishCheckIn = () => {
  const loginUser = store.state.user.loginUser;
  if (!loginUser || !loginUser.id) return;

  isCheckedIn.value = true;

  // 本地存储兜底
  const todayStr = new Date().toISOString().split("T")[0];
  const storageKey = `checkin_${loginUser.id}_${todayStr}`;
  localStorage.setItem(storageKey, "true");
};
</script>

<style scoped>
.trigger-btn {
  background: #fff;
  border: 1px solid #f2f3f5;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s;
}
.trigger-btn:not(.is-checked):hover {
  background: #fff7f7;
  transform: scale(1.05);
  border-color: #ffb4ba;
}
.is-checked {
  background: #f7f8fa;
  border-color: #e5e6eb;
  cursor: default;
}
.is-checked:hover {
  transform: none;
}
.fortune-container {
  position: relative;
  text-align: center;
  padding: 10px 0;
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.close-icon {
  position: absolute;
  top: -10px;
  right: 0;
  cursor: pointer;
  color: #86909c;
}
.close-icon:hover {
  color: #1d2129;
}
.scene-title {
  margin: 0 0 20px 0;
  color: #d93025;
  font-size: 18px;
}
.lucky-box {
  width: 100px;
  height: 140px;
  background: linear-gradient(135deg, #ff512f 0%, #dd2476 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(221, 36, 118, 0.3);
  user-select: none;
}
.box-text {
  writing-mode: vertical-lr;
  font-size: 16px;
  letter-spacing: 4px;
  font-weight: bold;
}
.scene-tip {
  margin-top: 16px;
  font-size: 12px;
  color: #86909c;
}
.shaking {
  animation: shake-hard 0.6s ease-in-out infinite;
}
@keyframes shake-hard {
  0% {
    transform: rotate(0deg);
  }
  25% {
    transform: rotate(5deg);
  }
  50% {
    transform: rotate(0deg);
  }
  75% {
    transform: rotate(-5deg);
  }
  100% {
    transform: rotate(0deg);
  }
}
.result-scene {
  animation: fadeIn 0.5s ease;
}
.result-badge {
  font-size: 32px;
  font-weight: bold;
  color: #d93025;
  font-family: "KaiTi", serif;
  margin-bottom: 12px;
}
.result-text {
  font-size: 16px;
  font-weight: 500;
  color: #1d2129;
  margin-bottom: 16px;
  padding: 0 20px;
}
.result-desc {
  background: #f7f8fa;
  padding: 8px 16px;
  border-radius: 4px;
  font-size: 12px;
  color: #4e5969;
  margin-bottom: 20px;
  line-height: 1.6;
}
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}
</style>

<style>
.mini-fortune-modal .arco-modal {
  border-radius: 16px !important;
  overflow: hidden;
}
</style>
