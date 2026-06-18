package com.oj.judge.strategy;

import cn.hutool.core.util.StrUtil;
import com.oj.judge.codesandbox.model.JudgeInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 默认判题策略
 */
@Component
public class DefaultJudgeStrategy implements JudgeStrategy {
    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {
        // 防御：输入列表为空时直接返回错误结果
        List<String> outputList = judgeContext.getOutputList();
        List<String> expectedOutputList = judgeContext.getExpectedOutputList();
        if (outputList == null || expectedOutputList == null) {
            return JudgeInfo.builder()
                    .message("System Error: 输入输出列表为空")
                    .build();
        }

        JudgeInfo judgeInfo = new JudgeInfo();
        // 防御：JudgeInfo 可能为 null（旧沙箱未返回），使用 null-safe 提取耗时/内存
        JudgeInfo sandboxResult = judgeContext.getJudgeInfo();
        if (sandboxResult != null) {
            judgeInfo.setTime(sandboxResult.getTime());
            judgeInfo.setMemory(sandboxResult.getMemory());
        }

        // 检查输出数量
        if (outputList.size() != expectedOutputList.size()) {
            judgeInfo.setMessage(JudgeInfoMessageEnum.WRONG_ANSWER.getValue());
            return judgeInfo;
        }

        // 逐个比较输出（用 Objects.equals 防止 expected 为 null 时 NPE）
        for (int i = 0; i < outputList.size(); i++) {
            String output = StrUtil.trim(outputList.get(i));
            String expected = StrUtil.trim(expectedOutputList.get(i));

            if (!Objects.equals(output, expected)) {
                judgeInfo.setMessage(JudgeInfoMessageEnum.WRONG_ANSWER.getValue());
                return judgeInfo;
            }
        }

        judgeInfo.setMessage(JudgeInfoMessageEnum.ACCEPTED.getValue());
        return judgeInfo;
    }
}