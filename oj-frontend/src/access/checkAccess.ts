import ACCESS_ENUM from "./ACCESS_ENUM";
/**
 * 检查权限 （判断当前用户是否具有某权限）
 * @param loginUser
 * @param needAccess
 * @return boolean
 */
const checkAccess = (loginUser: any, needAccess: any) => {
  // 获取当前登录用户具有的权限
  const loginUserAccess = loginUser?.userRole ?? ACCESS_ENUM.NOT_LOGIN;

  // 未登录
  if (needAccess === ACCESS_ENUM.NOT_LOGIN) {
    return true;
  }
  // 普通用户
  else if (needAccess === ACCESS_ENUM.USER) {
    if (loginUserAccess === ACCESS_ENUM.NOT_LOGIN) {
      return false;
    }
  }
  // 管理员
  else if (needAccess === ACCESS_ENUM.ADMIN) {
    if (loginUserAccess !== ACCESS_ENUM.ADMIN) {
      return false;
    }
  }
  return true;
};

export default checkAccess;
