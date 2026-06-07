import ACCESS_ENUM from "./ACCESS_ENUM";

interface LoginUser {
  userRole?: string;
}

/**
 * 检查权限（判断当前用户是否具有某权限）
 * @param loginUser 当前登录用户
 * @param needAccess 需要的权限级别
 */
const checkAccess = (
  loginUser: LoginUser | null,
  needAccess: string
): boolean => {
  const loginUserAccess = loginUser?.userRole ?? ACCESS_ENUM.NOT_LOGIN;

  if (needAccess === ACCESS_ENUM.NOT_LOGIN) {
    return true;
  }

  if (needAccess === ACCESS_ENUM.USER) {
    return loginUserAccess !== ACCESS_ENUM.NOT_LOGIN;
  }

  if (needAccess === ACCESS_ENUM.ADMIN) {
    return loginUserAccess === ACCESS_ENUM.ADMIN;
  }

  return true;
};

export default checkAccess;
