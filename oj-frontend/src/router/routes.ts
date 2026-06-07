import { RouteRecordRaw } from "vue-router";
import ACCESS_ENUM from "@/access/ACCESS_ENUM";

export const routes: Array<RouteRecordRaw> = [
  {
    path: "/user",
    name: "用户",
    component: () => import("@/layouts/UserLayout.vue"),
    meta: {
      hideInMenu: true,
    },
    children: [
      {
        path: "/user/login",
        name: "登录",
        component: () => import("@/views/user/UserLoginView.vue"),
      },
      {
        path: "/user/register",
        name: "注册",
        component: () => import("@/views/user/UserRegisterView.vue"),
      },
    ],
  },
  {
    path: "/",
    name: "主页",
    component: () => import("@/views/HomeView.vue"),
    meta: {
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
  {
    path: "/question",
    name: "题库",
    component: () => import("@/views/question/QuestionListView.vue"),
    meta: { access: ACCESS_ENUM.NOT_LOGIN },
  },
  {
    path: "/competition",
    name: "比赛",
    component: () => import("@/views/contest/ContestListView.vue"),
    meta: { access: ACCESS_ENUM.NOT_LOGIN },
  },
  {
    path: "/submission",
    name: "评测记录",
    component: () => import("@/views/SubmissionListView.vue"),
    meta: {
      access: ACCESS_ENUM.USER,
    },
  },
  {
    path: "/profile",
    name: "我的",
    component: () => import("@/views/user/UserProfileView.vue"),
    meta: { hideInMenu: true, access: ACCESS_ENUM.USER },
  },
  {
    path: "/hide",
    name: "隐藏页面",
    component: () => import("@/views/question/QuestionListView.vue"),
    meta: {
      hideInMenu: true,
      access: ACCESS_ENUM.USER,
    },
  },
  {
    path: "/stats",
    name: "统计",
    component: () => import("@/views/LanguageStats.vue"),
    meta: {
      access: ACCESS_ENUM.USER,
    },
  },
  {
    path: "/discussion",
    name: "讨论区",
    component: () => import("@/views/question/QuestionDiscussionView.vue"),
    meta: {
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
  {
    path: "/ai/assistant",
    name: "AI智能体",
    component: () => import("@/views/AIAssistantView.vue"),
    meta: { title: "AI 编程助手", access: ACCESS_ENUM.NOT_LOGIN },
  },
  {
    path: "/about",
    name: "关于",
    component: () => import("@/views/AboutView.vue"),
    meta: {
      access: ACCESS_ENUM.NOT_LOGIN,
    },
  },
  {
    path: "/contact",
    name: "反馈",
    component: () => import("@/views/FeedbackView.vue"),
    meta: {
      access: ACCESS_ENUM.USER,
    },
  },
  {
    path: "/question/:id",
    component: () => import("@/views/question/QuestionDetailsView.vue"),
    meta: { hideInMenu: true, access: ACCESS_ENUM.NOT_LOGIN },
  },
  {
    path: "/contest/:id",
    component: () => import("@/views/contest/ContestDetailView.vue"),
    meta: { hideInMenu: true, access: ACCESS_ENUM.NOT_LOGIN },
  },
  {
    path: "/submit/view/:id",
    name: "SubmitView",
    component: () => import("@/views/SubmissionListView.vue"),
    meta: { hideInMenu: true, access: ACCESS_ENUM.USER },
  },
  {
    path: "/admin",
    name: "题库管理",
    component: () => import("@/views/question/QuestionAdminView.vue"),
    meta: {
      access: ACCESS_ENUM.ADMIN,
    },
  },
  {
    path: "/question/add",
    name: "添加题目",
    component: () => import("@/views/question/AddQuestionView.vue"),
    meta: {
      access: ACCESS_ENUM.ADMIN,
    },
  },
  {
    path: "/:pathMatch(.*)*",
    name: "404",
    component: () => import("@/views/NotFoundView.vue"),
    meta: { hideInMenu: true },
  },
];
