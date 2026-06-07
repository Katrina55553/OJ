import { createStore } from "vuex";
import user from "./user";

export interface RootState {
  user: {
    loginUser: {
      userName: string;
      userRole: string;
      [key: string]: unknown;
    };
  };
}

export default createStore({
  mutations: {},
  actions: {},
  modules: {
    user,
  },
});
