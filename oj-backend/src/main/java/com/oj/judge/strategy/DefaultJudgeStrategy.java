package com.oj.judge.strategy;

import com.oj.judge.codesandbox.model.JudgeInfo;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 默认判题策略
 */
@Component
public class DefaultJudgeStrategy implements JudgeStrategy {
    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {
        List<String> outputList = judgeContext.getOutputList();
        List<String> expectedOutputList = judgeContext.getExpectedOutputList();

        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(judgeContext.getJudgeInfo().getTime());
        judgeInfo.setMemory(judgeContext.getJudgeInfo().getMemory());

        // 检查输出数量
        if (outputList.size() != expectedOutputList.size()) {
            judgeInfo.setMessage("Wrong Answer");
            return judgeInfo;
        }

        // 逐个比较输出
        for (int i = 0; i < outputList.size(); i++) {
            String output = outputList.get(i).trim();
            String expected = expectedOutputList.get(i).trim();

            if (!output.equals(expected)) {
                judgeInfo.setMessage("Wrong Answer");
                return judgeInfo;
            }
        }

        judgeInfo.setMessage("Accepted");
        return judgeInfo;
    }
}