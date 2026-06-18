package com.oj.judge.strategy;

import cn.hutool.json.JSONUtil;
import com.oj.model.dto.question.JudgeCase;
import com.oj.model.dto.question.JudgeConfig;
import com.oj.judge.codesandbox.model.JudgeInfo;
import com.oj.model.entity.Question;
import com.oj.model.enums.JudgeInfoMessageEnum;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Java 程序的判题策略
 */
public class JavaLanguageJudgeStrategy implements JudgeStrategy {

    /**
     * 执行判题
     * @param judgeContext
     * @return
     */
    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {
        // 防御：JudgeInfo 可能为 null（旧沙箱未返回）
        JudgeInfo judgeInfo = judgeContext.getJudgeInfo();
        Long memory = judgeInfo == null ? 0L : Optional.ofNullable(judgeInfo.getMemory()).orElse(0L);
        Long time = judgeInfo == null ? 0L : Optional.ofNullable(judgeInfo.getTime()).orElse(0L);
        List<String> inputList = judgeContext.getInputList();
        List<String> outputList = judgeContext.getOutputList();
        Question question = judgeContext.getQuestion();
        List<JudgeCase> judgeCaseList = judgeContext.getJudgeCaseList();
        JudgeInfoMessageEnum judgeInfoMessageEnum = JudgeInfoMessageEnum.ACCEPTED;
        JudgeInfo judgeInfoResponse = new JudgeInfo();
        judgeInfoResponse.setMemory(memory);
        judgeInfoResponse.setTime(time);

        // 防御：输入输出列表为 null 时直接返回错误
        if (inputList == null || outputList == null) {
            judgeInfoResponse.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
            return judgeInfoResponse;
        }

        // 先判断沙箱执行的结果输出数量是否和预期输出数量相等
        if (outputList.size() != inputList.size()) {
            judgeInfoMessageEnum = JudgeInfoMessageEnum.WRONG_ANSWER;
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        // 依次判断每一项输出和预期输出是否相等（用 Objects.equals 防止 outputList.get(i) 为 null 时 NPE）
        for (int i = 0; i < judgeCaseList.size(); i++) {
            JudgeCase judgeCase = judgeCaseList.get(i);
            if (!Objects.equals(judgeCase.getOutput(), outputList.get(i))) {
                judgeInfoMessageEnum = JudgeInfoMessageEnum.WRONG_ANSWER;
                judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
                return judgeInfoResponse;
            }
        }
        // 判断题目限制（judgeConfig 可能为 null，字段也可能为 null）
        String judgeConfigStr = question.getJudgeConfig();
        JudgeConfig judgeConfig = judgeConfigStr == null
                ? new JudgeConfig()
                : JSONUtil.toBean(judgeConfigStr, JudgeConfig.class);
        Long needMemoryLimit = judgeConfig.getMemoryLimit() != null ? judgeConfig.getMemoryLimit() : Long.MAX_VALUE;
        Long needTimeLimit = judgeConfig.getTimeLimit() != null ? judgeConfig.getTimeLimit() : Long.MAX_VALUE;
        if (memory > needMemoryLimit) {
            judgeInfoMessageEnum = JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED;
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        // Java 程序本身需要额外执行 10 秒钟
        long JAVA_PROGRAM_TIME_COST = 10000L;
        if ((time - JAVA_PROGRAM_TIME_COST) > needTimeLimit) {
            judgeInfoMessageEnum = JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED;
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
        return judgeInfoResponse;
    }
}
