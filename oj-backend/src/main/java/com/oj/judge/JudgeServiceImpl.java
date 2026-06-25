package com.oj.judge;

import cn.hutool.json.JSONUtil;
import com.oj.common.ErrorCode;
import com.oj.exception.BusinessException;
import com.oj.judge.codesandbox.CodeSandbox;
import com.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.oj.judge.strategy.JudgeContext;
import com.oj.model.dto.question.JudgeCase;
import com.oj.judge.codesandbox.model.JudgeInfo;
import com.oj.model.entity.Question;
import com.oj.model.entity.QuestionSubmit;
import com.oj.model.enums.QuestionSubmitStatusEnum;
import com.oj.service.QuestionService;
import com.oj.service.QuestionSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeManager judgeManager;

    @Value("${codesandbox.type:docker}")
    private String sandboxType;

    @Resource
    private com.oj.judge.codesandbox.impl.DockerCodeSandbox dockerCodeSandbox;

    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        // 1）获取提交信息和题目
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }

        Long questionId = questionSubmit.getQuestionId();
        Question question = questionService.getById(questionId);
        if (question == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
        }

        // 2）检查状态
        if (!questionSubmit.getStatus().equals(QuestionSubmitStatusEnum.WAITING.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
        }

        // 3）更新状态为判题中
        QuestionSubmit questionSubmitUpdate = new QuestionSubmit();
        questionSubmitUpdate.setId(questionSubmitId);
        questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.RUNNING.getValue());
        boolean update = questionSubmitService.updateById(questionSubmitUpdate);
        if (!update) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
        }

        try {
            // 4）获取测试用例
            String judgeCaseStr = question.getJudgeCase();
            List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
            List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());

            log.debug("测试用例输入: {}", inputList);

            // 5）调用代码沙箱执行代码
            ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                    .code(questionSubmit.getCode())
                    .language(questionSubmit.getLanguage())
                    .inputList(inputList)
                    .build();

            // 根据配置选择沙箱类型
            CodeSandbox codeSandbox = getCodeSandbox();
            ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);

            log.debug("沙箱执行结果: {}", JSONUtil.toJsonStr(executeCodeResponse));

            // 6）获取期望输出列表
            List<String> expectedOutputList = judgeCaseList.stream()
                    .map(JudgeCase::getOutput)
                    .collect(Collectors.toList());

            log.debug("期望输出: {}", expectedOutputList);
            log.debug("实际输出: {}", executeCodeResponse.getOutputList());

            // 7）判题
            JudgeContext judgeContext = new JudgeContext();
            judgeContext.setJudgeInfo(executeCodeResponse.getJudgeInfo());
            judgeContext.setInputList(inputList);
            judgeContext.setOutputList(executeCodeResponse.getOutputList());
            judgeContext.setExpectedOutputList(expectedOutputList);  // 需要确保 JudgeContext 有这个字段
            judgeContext.setJudgeCaseList(judgeCaseList);
            judgeContext.setQuestion(question);
            judgeContext.setQuestionSubmit(questionSubmit);

            JudgeInfo judgeInfo = judgeManager.doJudge(judgeContext);

            log.debug("判题结果: {}", JSONUtil.toJsonStr(judgeInfo));

            // 8）更新数据库
            questionSubmitUpdate = new QuestionSubmit();
            questionSubmitUpdate.setId(questionSubmitId);
            questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
            questionSubmitUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));

            update = questionSubmitService.updateById(questionSubmitUpdate);
            if (!update) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
            }

            // 9）更新题目提交数和通过数
            Question questionUpdate = new Question();
            questionUpdate.setId(questionId);
            questionUpdate.setSubmitNum(question.getSubmitNum() + 1);
            if (judgeInfo != null && "Accepted".equals(judgeInfo.getMessage())) {
                questionUpdate.setAcceptedNum(question.getAcceptedNum() + 1);
            }
            questionService.updateById(questionUpdate);

            // 10）缓存判题结果（5分钟内相同代码可直接返回）
            questionSubmitService.cacheSubmitResult(questionId, questionSubmit.getLanguage(),
                    questionSubmit.getCode(), JSONUtil.toJsonStr(judgeInfo));

        } catch (Exception e) {
            log.error("判题异常", e);

            // 判题失败，更新状态
            questionSubmitUpdate = new QuestionSubmit();
            questionSubmitUpdate.setId(questionSubmitId);
            questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());

            // 使用 JudgeInfo + JSONUtil 构建合法 JSON，避免手写字符串拼接导致非法 JSON
            JudgeInfo errorJudgeInfo = new JudgeInfo();
            errorJudgeInfo.setMessage(e.getMessage() != null ? e.getMessage() : "Unknown Error");
            questionSubmitUpdate.setJudgeInfo(JSONUtil.toJsonStr(errorJudgeInfo));
            questionSubmitService.updateById(questionSubmitUpdate);

            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "判题失败: " + e.getMessage());
        }

        return questionSubmitService.getById(questionSubmitId);
    }

    /**
     * 根据配置获取代码沙箱实例
     */
    private CodeSandbox getCodeSandbox() {
        // 直接使用注入的 Docker 沙箱
        return dockerCodeSandbox;
    }
}