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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * 用户服务实现
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "Katrina";
    private static final String USER_CACHE_KEY = "user:id:";
    private static final long USER_CACHE_EXPIRE_MINUTES = 30;

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
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
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
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 生成 JWT Token
        String token = jwtUtils.generateToken(user.getId(), user.getUserRole());
        LoginUserVO loginUserVO = this.getLoginUserVO(user);
        loginUserVO.setToken(token);
        return loginUserVO;
    }

    /**
     * 获取当前登录用户（从 JwtInterceptor 设置的 Request Attribute 中读取）
     * 优先从 Redis 缓存获取，未命中再查数据库
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 1. 先查缓存
        String cacheKey = USER_CACHE_KEY + userId;
        User currentUser = redisCacheUtils.get(cacheKey, User.class);
        if (currentUser != null) {
            return currentUser;
        }

        // 2. 缓存未命中，查数据库
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 3. 写入缓存
        redisCacheUtils.set(cacheKey, currentUser, USER_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return currentUser;
    }

    @Override
    public User getLoginUserPermitNull(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.ATTR_USER_ID);
        if (userId == null) {
            return null;
        }
        // 先查缓存
        String cacheKey = USER_CACHE_KEY + userId;
        User user = redisCacheUtils.get(cacheKey, User.class);
        if (user != null) {
            return user;
        }
        // 缓存未命中，查数据库
        user = this.getById(userId);
        if (user != null) {
            redisCacheUtils.set(cacheKey, user, USER_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        }
        return user;
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
