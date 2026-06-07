/**
 * 题目相关工具函数
 */

/**
 * 根据难度返回 Arco Design Tag 颜色
 */
export const difficultyColor = (difficulty: string): string => {
  switch (difficulty) {
    case "简单":
      return "green";
    case "中等":
      return "orange";
    case "困难":
      return "red";
    default:
      return "gray";
  }
};

/**
 * 计算通过率（百分比，保留两位小数）
 */
export const calculatePassRate = (
  acceptNum: number,
  submitNum: number
): number => {
  if (!submitNum || submitNum === 0) return 0;
  return Math.round((acceptNum / submitNum) * 10000) / 100;
};

/**
 * 默认判题配置
 */
const DEFAULT_JUDGE_CONFIG = { timeLimit: 1000, memoryLimit: 256 };

/**
 * 解析判题配置（兼容字符串和对象）
 */
export const parseJudgeConfig = (
  config: string | object | null | undefined
): { timeLimit: number; memoryLimit: number } => {
  if (!config) return DEFAULT_JUDGE_CONFIG;
  if (typeof config === "string") {
    try {
      return JSON.parse(config);
    } catch {
      return DEFAULT_JUDGE_CONFIG;
    }
  }
  return config as { timeLimit: number; memoryLimit: number };
};

/**
 * 解析 JSON 数组字符串，失败返回空数组
 */
export const parseJsonArray = <T = string>(
  jsonStr: string | null | undefined
): T[] => {
  if (!jsonStr) return [];
  try {
    return JSON.parse(jsonStr);
  } catch {
    return [];
  }
};
