package com.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.aop.JwtInterceptor;
import com.oj.common.ErrorCode;
import com.oj.constant.CommonConstant;
import com.oj.exception.BusinessException;
import com.oj.mapper.UserMapper;
import com.oj.model.dto.user.UserQueryRequest;
import com.oj.model.entity.User;
import com.oj.model.enums.UserRoleEnum;
import com.oj.model.vo.LoginUserVO;
import com.oj.model.vo.UserVO;
import com.oj.service.UserService;
import com.oj.utils.JwtUtils;
import com.oj.utils.RedisCacheUtils;
import com.oj.utils.SqlUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 用户服务实现
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值，混淆密码（用于 BCrypt 自动加盐，无需显式 salt 仅用于兼容旧数据
     */
    private static final String SALT = "Katrina";
    private static final String USER_CACHE_KEY = "user:id:";
    private static final long USER_CACHE_EXPIRE_MINUTES = 30;
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    // 缓存防护配置
    private static final String USER_LOCK_KEY = "lock:user:";
    private static final long USER_LOCK_EXPIRE_SECONDS = 10;      // 互斥锁过期时间（防死锁）
    private static final long USER_EMPTY_EXPIRE_MINUTES = 2;       // 空值缓存短 TTL（防内存浪费）
    private static final int MAX_RETRY_COUNT = 5;                 // 互斥锁重试上限（防 StackOverflow）
    private static final long RETRY_SLEEP_MS = 50;                // 重试间隔
    private static final long RANDOM_TTL_BOUND_MINUTES = 5;       // 随机 TTL 偏移上限（防雪崩）

    private final JwtUtils jwtUtils;
    private final RedisCacheUtils redisCacheUtils;

    public UserServiceImpl(JwtUtils jwtUtils, RedisCacheUtils redisCacheUtils) {
        this.jwtUtils = jwtUtils;
        this.redisCacheUtils = redisCacheUtils;
    }

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            String encryptPassword = PASSWORD_ENCODER.encode(userPassword);
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            user.setUserName(userAccount);  // 默认用户名与账号相同
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        User user = this.baseMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 优先尝试 BCrypt，失败则回退到旧的 MD5（兼容历史数据
        String storedPassword = user.getUserPassword();
        boolean passwordOk = false;
        try {
            passwordOk = PASSWORD_ENCODER.matches(userPassword, storedPassword);
        } catch (Exception e) {
            passwordOk = false;
        }
        if (!passwordOk) {
            // 回退到 MD5 校验
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            if (!storedPassword.equals(encryptPassword)) {
                log.info("user login failed, userAccount cannot match userPassword");
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
            }
        }
        // 生成 JWT Token
        String token = jwtUtils.generateToken(user.getId(), user.getUserRole());
        LoginUserVO loginUserVO = this.getLoginUserVO(user);
        loginUserVO.setToken(token);
        return loginUserVO;
    }

    /**
     * 获取当前登录用户（从 JwtInterceptor 设置的 Request Attribute 中读取）
     * 带缓存防护：防穿透（缓存空值）+ 防击穿（互斥锁）+ 防雪崩（随机 TTL）
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User user = getUserWithCacheProtection(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return user;
    }

    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        if (userId == null) {
            return null;
        }
        return getUserWithCacheProtection(userId);
    }

    /**
     * 带三大缓存防护的用户查询：
     * 1. 防穿透：DB 查不到时缓存空值占位符（User.id = null），短 TTL
     * 2. 防击穿：缓存未命中时用 SETNX 互斥锁，只有一个请求查 DB，其他重试
     * 3. 防雪崩：正常缓存的 TTL 加随机偏移，避免同时过期
     *
     * @return 用户对象；如果用户不存在返回 null（包括命中空值占位符的情况）
     */
    private User getUserWithCacheProtection(Long userId) {
        String cacheKey = USER_CACHE_KEY + userId;

        // 1. 先查缓存
        User cachedUser = redisCacheUtils.get(cacheKey, User.class);
        if (cachedUser != null) {
            // 空值占位符：id 为 null 表示用户不存在
            if (cachedUser.getId() == null) {
                return null;
            }
            return cachedUser;
        }

        // 2. 缓存未命中，获取互斥锁防击穿
        String lockKey = USER_LOCK_KEY + userId;
        int retryCount = 0;
        while (retryCount < MAX_RETRY_COUNT) {
            boolean locked = redisCacheUtils.setIfAbsent(
                    lockKey, "1", USER_LOCK_EXPIRE_SECONDS, TimeUnit.SECONDS);
            if (locked) {
                try {
                    // 双重检查：可能其他线程已经查完写入了
                    cachedUser = redisCacheUtils.get(cacheKey, User.class);
                    if (cachedUser != null) {
                        return cachedUser.getId() == null ? null : cachedUser;
                    }

                    // 查数据库
                    User dbUser = this.getById(userId);
                    if (dbUser == null) {
                        // 防穿透：缓存空值占位符，短 TTL（2 分钟）
                        redisCacheUtils.set(cacheKey, new User(),
                                USER_EMPTY_EXPIRE_MINUTES, TimeUnit.MINUTES);
                        return null;
                    }

                    // 防雪崩：基础 TTL + 随机偏移（30~35 分钟）
                    long ttl = USER_CACHE_EXPIRE_MINUTES
                            + ThreadLocalRandom.current().nextLong(0, RANDOM_TTL_BOUND_MINUTES);
                    redisCacheUtils.set(cacheKey, dbUser, ttl, TimeUnit.MINUTES);
                    return dbUser;
                } finally {
                    redisCacheUtils.delete(lockKey);
                }
            }

            // 未获取到锁，短暂等待后重试
            retryCount++;
            try {
                Thread.sleep(RETRY_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 重试耗尽，降级直接查 DB（避免请求永远失败）
        log.warn("获取用户缓存锁失败，重试耗尽，降级查 DB: userId={}", userId);
        return this.getById(userId);
    }

    @Override
    public boolean isAdmin(HttpServletRequest request) {
        String userRole = (String) request.getAttribute(JwtInterceptor.ATTR_USER_ROLE);
        return UserRoleEnum.ADMIN.getValue().equals(userRole);
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    /**
     * 用户注销
     * 将当前 Token 加入 Redis 黑名单，实现主动失效
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null) {
            return true;
        }

        // 获取 Token 的 jti 和剩余有效期
        String tokenId = jwtUtils.getTokenId(token);
        if (tokenId == null) {
            return true;
        }

        long remainingMs = jwtUtils.getTokenRemainingMs(token);
        if (remainingMs <= 0) {
            return true;  // Token 已过期，无需加入黑名单
        }

        // 加入黑名单，TTL 等于 Token 剩余有效期
        String blacklistKey = "blacklist:token:" + tokenId;
        redisCacheUtils.set(blacklistKey, "1", remainingMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        log.info("Token 已加入黑名单: tokenId={}, remainingMs={}", tokenId, remainingMs);

        // 清除用户缓存
        Long userId = jwtUtils.getUserId(token);
        if (userId != null) {
            clearUserCache(userId);
        }

        return true;
    }

    /**
     * 从 Request 中提取 Token
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    @Override
    public void clearUserCache(Long userId) {
        if (userId != null) {
            redisCacheUtils.delete(USER_CACHE_KEY + userId);
        }
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollectionUtils.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }
}
