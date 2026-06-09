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
    loginUser: null as unknown as UserState["loginUser"],
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
            userName: "",
            userRole: ACCESS_ENUM.NOT_LOGIN,
          });
        }
      } catch {
        // Token 无效或过期，清除本地存储
        localStorage.removeItem("token");
        commit("updateUser", {
          userName: "",
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
