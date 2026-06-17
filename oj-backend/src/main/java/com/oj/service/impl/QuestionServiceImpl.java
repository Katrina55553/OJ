package com.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.common.ErrorCode;
import com.oj.constant.CommonConstant;
import com.oj.exception.BusinessException;
import com.oj.exception.ThrowUtils;
import com.oj.model.dto.question.QuestionQueryRequest;
import com.oj.model.entity.Question;
import com.oj.model.entity.User;
import com.oj.model.entity.*;
import com.oj.model.vo.QuestionVO;
import com.oj.model.vo.UserVO;
import com.oj.service.QuestionService;
import com.oj.mapper.QuestionMapper;
import com.oj.service.UserService;
import com.oj.utils.RedisCacheUtils;
import com.oj.utils.SqlUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question>
    implements QuestionService{

    private static final String QUESTION_CACHE_KEY_PREFIX = "question:page:";
    private static final long QUESTION_CACHE_EXPIRE_MINUTES = 5;

    @Resource
    private UserService userService;

    @Resource
    private RedisCacheUtils redisCacheUtils;

    /**
     * 校验题目是否合法
     * @param question
     * @param add
     */
    @Override
    public void validQuestion(Question question, boolean add) {
        if (question == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String title = question.getTitle();
        String content = question.getContent();
        String difficulty = question.getDifficulty();
        String answer = question.getAnswer();
        String judgeCase = question.getJudgeCase();
        String judgeConfig = question.getJudgeConfig();
        // 创建时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isAnyBlank(title, content, difficulty), ErrorCode.PARAMS_ERROR);
        }
        // 有参数则校验
        if (StringUtils.isNotBlank(title) && title.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(content) && content.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "内容过长");
        }
        if (StringUtils.isNotBlank(answer) && answer.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "答案过长");
        }
        if (StringUtils.isNotBlank(judgeCase) && judgeCase.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判题用例过长");
        }
        if (StringUtils.isNotBlank(judgeConfig) && judgeConfig.length() > 8192) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "判题配置过长");
        }
    }

    /**
     * 获取查询包装类（用户根据哪些字段查询，根据前端传来的请求对象，得到 mybatis 框架支持的查询 QueryWrapper 类）
     *
     * @param questionQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Question> getQueryWrapper(QuestionQueryRequest questionQueryRequest) {
        QueryWrapper<Question> queryWrapper = new QueryWrapper<>();
        if (questionQueryRequest == null) {
            return queryWrapper;
        }
        Long id = questionQueryRequest.getId();
        String title = questionQueryRequest.getTitle();
        String content = questionQueryRequest.getContent();
        String difficulty = questionQueryRequest.getDifficulty();
        String answer = questionQueryRequest.getAnswer();
        Long userId = questionQueryRequest.getUserId();
        String sortField = questionQueryRequest.getSortField();
        String sortOrder = questionQueryRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.like(StringUtils.isNotBlank(answer), "answer", answer);
        queryWrapper.eq(StringUtils.isNotBlank(difficulty), "difficulty", difficulty);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionVO getQuestionVO(Question question, HttpServletRequest request) {
        QuestionVO questionVO = QuestionVO.objToVo(question);
        // 1. 关联查询用户信息
        Long userId = question.getUserId();
        User user = null;
        if (userId != null && userId > 0) {
            user = userService.getById(userId);
        }
        UserVO userVO = userService.getUserVO(user);
        questionVO.setUserVO(userVO);
        return questionVO;
    }

    @Override
    public Page<QuestionVO> getQuestionVOPage(Page<Question> questionPage, HttpServletRequest request) {
        List<Question> questionList = questionPage.getRecords();
        Page<QuestionVO> questionVOPage = new Page<>(questionPage.getCurrent(), questionPage.getSize(), questionPage.getTotal());
        if (CollectionUtils.isEmpty(questionList)) {
            return questionVOPage;
        }
        // 1. 关联查询用户信息
        Set<Long> userIdSet = questionList.stream().map(Question::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 填充信息
        List<QuestionVO> questionVOList = questionList.stream().map(question -> {
            QuestionVO questionVO = QuestionVO.objToVo(question);
            Long userId = question.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            questionVO.setUserVO(userService.getUserVO(user));
            return questionVO;
        }).collect(Collectors.toList());
        questionVOPage.setRecords(questionVOList);
        return questionVOPage;
    }

    @Override
    public Page<QuestionVO> getQuestionVOPageWithCache(QuestionQueryRequest questionQueryRequest, HttpServletRequest request) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        
        String cacheKey = generateCacheKey(questionQueryRequest);
        Page<QuestionVO> cachedPage = redisCacheUtils.get(cacheKey, Page.class);
        if (cachedPage != null) {
            return cachedPage;
        }

        Page<Question> questionPage = this.page(new Page<>(current, size), getQueryWrapper(questionQueryRequest));
        Page<QuestionVO> questionVOPage = getQuestionVOPage(questionPage, request);

        redisCacheUtils.set(cacheKey, questionVOPage, QUESTION_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
        return questionVOPage;
    }

    private String generateCacheKey(QuestionQueryRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(request.getCurrent()).append(":")
          .append(request.getPageSize()).append(":")
          .append(request.getId() != null ? request.getId() : "0").append(":")
          .append(StringUtils.defaultString(request.getTitle())).append(":")
          .append(StringUtils.defaultString(request.getDifficulty())).append(":")
          .append(StringUtils.defaultString(request.getSortField())).append(":")
          .append(StringUtils.defaultString(request.getSortOrder()));
        
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return QUESTION_CACHE_KEY_PREFIX + hexString.toString();
        } catch (Exception e) {
            return QUESTION_CACHE_KEY_PREFIX + sb.toString().hashCode();
        }
    }

    @Override
    public void clearQuestionCache() {
        Set<String> keys = redisCacheUtils.getRedisTemplate().keys(QUESTION_CACHE_KEY_PREFIX + "*");
        if (CollectionUtils.isNotEmpty(keys)) {
            redisCacheUtils.getRedisTemplate().delete(keys);
        }
    }

    @Override
    public boolean save(Question question) {
        boolean result = super.save(question);
        if (result) {
            clearQuestionCache();
        }
        return result;
    }

    @Override
    public boolean updateById(Question question) {
        boolean result = super.updateById(question);
        if (result) {
            clearQuestionCache();
        }
        return result;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        boolean result = super.removeById(id);
        if (result) {
            clearQuestionCache();
        }
        return result;
    }

}

