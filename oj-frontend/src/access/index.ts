import router from "@/router";
import store from "@/store";
import ACCESS_ENUM from "@/access/ACCESS_ENUM";
import checkAccess from "@/access/checkAccess";

router.beforeEach(async (to, from, next) => {
  console.log(store.state.user.loginUser);

  let loginUser = store.state.user.loginUser;
  if (!loginUser || !loginUser.userRole) {
    await store.dispatch("user/getLoginUser");
    loginUser = store.state.user.loginUser;
  }
  const needAccess = (to.meta?.access as string) ?? ACCESS_ENUM.NOT_LOGIN;
  if (needAccess !== ACCESS_ENUM.NOT_LOGIN) {
    if (!loginUser || !loginUser.userRole) {
      next(`/user/login?redirectUrl=${to.fullPath}`);
      return;
    }
    if (!checkAccess(loginUser, needAccess)) {
      next("/noAuth");
      return;
    }
  }
  next();
});
