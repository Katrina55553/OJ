package com.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.oj.common.ErrorCode;
import com.oj.constant.CommonConstant;
import com.oj.exception.BusinessException;
import com.oj.mapper.QuestionSubmitMapper;
import com.oj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.oj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.oj.model.entity.Question;
import com.oj.model.entity.QuestionSubmit;
import com.oj.model.entity.User;
import com.oj.model.enums.QuestionSubmitLanguageEnum;
import com.oj.model.enums.QuestionSubmitStatusEnum;
import com.oj.model.vo.QuestionSubmitVO;
import com.oj.model.vo.QuestionVO;
import com.oj.model.vo.UserVO;
import com.oj.service.QuestionService;
import com.oj.service.QuestionSubmitService;
import com.oj.service.UserService;
import com.oj.utils.RedisCacheUtils;
import com.oj.utils.SqlUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Service
public class QuestionSubmitServiceImpl extends ServiceImpl<QuestionSubmitMapper, QuestionSubmit>
    implements QuestionSubmitService{

    private static final String SUBMIT_CACHE_KEY_PREFIX = "submit:cache:";
    private static final long SUBMIT_CACHE_EXPIRE_MINUTES = 5;

    @Resource
    private QuestionService questionService;

    @Resource
    private UserService userService;

    @Resource
    private JudgeMessageProducer judgeMessageProducer;

    @Resource
    private RedisCacheUtils redisCacheUtils;

    /**
     * 提交题目
     *
     * @param questionSubmitAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public long doQuestionSubmit(QuestionSubmitAddRequest questionSubmitAddRequest, User loginUser) {
        // 校验编程语言是否合法
        String language = questionSubmitAddRequest.getLanguage();
        QuestionSubmitLanguageEnum languageEnum = QuestionSubmitLanguageEnum.getEnumByValue(language);
        if (languageEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "编程语言错误");
        }
        long questionId = questionSubmitAddRequest.getQuestionId();
        // 判断实体是否存在，根据类别获取实体
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        long userId = loginUser.getId();
        String code = questionSubmitAddRequest.getCode();

        // 检查是否有相同的代码在缓存中（5分钟内相同题目+语言+代码直接返回缓存结果）
        String cacheKey = generateSubmitCacheKey(questionId, language, code);
        String cachedResult = redisCacheUtils.get(cacheKey);
        if (cachedResult != null) {
            // 使用缓存结果创建提交记录
            QuestionSubmit questionSubmit = new QuestionSubmit();
            questionSubmit.setUserId(userId);
            questionSubmit.setQuestionId(questionId);
            questionSubmit.setCode(code);
            questionSubmit.setLanguage(language);
            questionSubmit.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
            questionSubmit.setJudgeInfo(cachedResult);
            this.save(questionSubmit);

            // 更新题目统计（不增加submitNum，因为用的是缓存结果）
            return questionSubmit.getId();
        }

        // 每个用户串行提交题目
        QuestionSubmit questionSubmit = new QuestionSubmit();
        questionSubmit.setUserId(userId);
        questionSubmit.setQuestionId(questionId);
        questionSubmit.setCode(code);
        questionSubmit.setLanguage(language);
        // 设置初始状态
        questionSubmit.setStatus(QuestionSubmitStatusEnum.WAITING.getValue());
        questionSubmit.setJudgeInfo("{}");
        boolean save = this.save(questionSubmit);
        if (!save){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "数据插入失败");
        }
        Long questionSubmitId = questionSubmit.getId();
        // 发送判题消息到 RabbitMQ
        judgeMessageProducer.sendJudgeMessage(questionSubmitId);
        return questionSubmitId;
    }

    private String generateSubmitCacheKey(long questionId, String language, String code) {
        String raw = questionId + ":" + language + ":" + code;
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return SUBMIT_CACHE_KEY_PREFIX + hexString.toString();
        } catch (Exception e) {
            return SUBMIT_CACHE_KEY_PREFIX + raw.hashCode();
        }
    }

    /**
     * 缓存判题结果（供判题服务调用）
     */
    @Override
    public void cacheSubmitResult(long questionId, String language, String code, String judgeInfo) {
        String cacheKey = generateSubmitCacheKey(questionId, language, code);
        redisCacheUtils.set(cacheKey, judgeInfo, SUBMIT_CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }


    /**
     * 获取查询包装类（用户根据哪些字段查询，根据前端传来的请求对象，得到 mybatis 框架支持的查询 QueryWrapper 类）
     *
     * @param questionSubmitQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest questionSubmitQueryRequest) {
        QueryWrapper<QuestionSubmit> queryWrapper = new QueryWrapper<>();
        if (questionSubmitQueryRequest == null) {
            return queryWrapper;
        }
        String language = questionSubmitQueryRequest.getLanguage();
        Integer status = questionSubmitQueryRequest.getStatus();
        Long questionId = questionSubmitQueryRequest.getQuestionId();
        Long userId = questionSubmitQueryRequest.getUserId();
        String sortField = questionSubmitQueryRequest.getSortField();
        String sortOrder = questionSubmitQueryRequest.getSortOrder();

        // 拼接查询条件
        queryWrapper.eq(StringUtils.isNotBlank(language), "language", language);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
        queryWrapper.eq(QuestionSubmitStatusEnum.getEnumByValue(status) != null, "status", status);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit questionSubmit, User loginUser) {
        QuestionSubmitVO questionSubmitVO = QuestionSubmitVO.objToVo(questionSubmit);
        // 脱敏：未登录用户、非本人、非管理员均隐藏代码
        if (loginUser == null) {
            questionSubmitVO.setCode(null);
        } else {
            long userId = loginUser.getId();
            if (userId != questionSubmit.getUserId() && !userService.isAdmin(loginUser)) {
                questionSubmitVO.setCode(null);
            }
        }
        return questionSubmitVO;
    }

    @Override
    public Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> questionSubmitPage, User loginUser) {
        List<QuestionSubmit> questionSubmitList = questionSubmitPage.getRecords();
        Page<QuestionSubmitVO> questionSubmitVOPage = new Page<>(questionSubmitPage.getCurrent(), questionSubmitPage.getSize(), questionSubmitPage.getTotal());
        if (CollectionUtils.isEmpty(questionSubmitList)) {
            return questionSubmitVOPage;
        }

        // 批量查询用户信息（避免 N+1）
        Set<Long> userIds = questionSubmitList.stream()
                .map(QuestionSubmit::getUserId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap() :
                userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        // 批量查询题目信息（避免 N+1）
        Set<Long> questionIds = questionSubmitList.stream()
                .map(QuestionSubmit::getQuestionId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, Question> questionMap = questionIds.isEmpty() ? Collections.emptyMap() :
                questionService.listByIds(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, q -> q));

        List<QuestionSubmitVO> questionSubmitVOList = questionSubmitList.stream()
                .map(questionSubmit -> {
                    QuestionSubmitVO vo = getQuestionSubmitVO(questionSubmit, loginUser);
                    // 填充用户信息
                    User user = userMap.get(questionSubmit.getUserId());
                    if (user != null) {
                        UserVO userVO = new UserVO();
                        userVO.setId(user.getId());
                        userVO.setUserName(user.getUserName());
                        userVO.setUserAvatar(user.getUserAvatar());
                        userVO.setUserProfile(user.getUserProfile());
                        userVO.setUserRole(user.getUserRole());
                        vo.setUserVO(userVO);
                    }
                    // 填充题目信息
                    Question question = questionMap.get(questionSubmit.getQuestionId());
                    if (question != null) {
                        QuestionVO questionVO = new QuestionVO();
                        questionVO.setId(question.getId());
                        questionVO.setTitle(question.getTitle());
                        vo.setQuestionVO(questionVO);
                    }
                    return vo;
                })
                .collect(Collectors.toList());
        questionSubmitVOPage.setRecords(questionSubmitVOList);
        return questionSubmitVOPage;
    }
}




