import ACCESS_ENUM from "@/access/ACCESS_ENUM";
import { UserControllerService } from "../../generated";

interface UserState {
  loginUser: {
    userName: string;
    userRole: string;
    [key: string]: unknown;
  };
}

export default {
  namespaced: true,
  state: (): UserState => ({
    loginUser: {
      userName: "未登录",
      userRole: ACCESS_ENUM.NOT_LOGIN,
    },
  }),
  actions: {
    async getLoginUser({
      commit,
      state,
    }: {
      commit: (type: string, payload?: unknown) => void;
      state: UserState;
    }) {
      try {
        const res = await UserControllerService.getLoginUserUsingGet();
        if (res.code === 0) {
          commit("updateUser", res.data);
        } else {
          commit("updateUser", {
            ...state.loginUser,
            userRole: ACCESS_ENUM.NOT_LOGIN,
          });
        }
      } catch {
        // Token 无效或过期，清除本地存储
        localStorage.removeItem("token");
        commit("updateUser", {
          ...state.loginUser,
          userRole: ACCESS_ENUM.NOT_LOGIN,
        });
      }
    },
  },
  mutations: {
    updateUser(state: UserState, payload: UserState["loginUser"]) {
      state.loginUser = payload;
    },
  },
};
