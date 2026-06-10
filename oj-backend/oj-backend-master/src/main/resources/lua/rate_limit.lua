-- 滑动窗口限流 Lua 脚本
-- 基于 Redis ZSET 实现，窗口内以时间戳为 score、UUID 为 member
--
-- KEYS[1]: 限流 key（rate_limit:{method}:{identifier}）
-- ARGV[1]: 当前时间戳（毫秒）
-- ARGV[2]: 窗口大小（毫秒）
-- ARGV[3]: 限流上限
-- ARGV[4]: 唯一标识（UUID，避免同一毫秒内 ZADD 冲突）
--
-- 返回值：
--   > 0: 当前窗口内请求数（放行）
--   -1: 超过限流上限（拒绝）

local key = KEYS[1]
local now = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local member = ARGV[4]

-- 1. 移除窗口外的旧记录（score < now - window）
redis.call('ZREMRANGEBYSCORE', key, 0, now - window)

-- 2. 获取当前窗口内的请求数
local count = redis.call('ZCARD', key)

-- 3. 判断是否超限
if count < limit then
    -- 未超限：添加当前请求，score 为当前时间戳
    redis.call('ZADD', key, now, member)
    -- 设置 key 过期（兜底清理，防止内存泄漏）
    redis.call('PEXPIRE', key, window)
    return count + 1
else
    -- 已超限：仅续期 key，不添加请求
    redis.call('PEXPIRE', key, window)
    return -1
end
