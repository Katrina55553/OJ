package com.oj.judge.codesandbox.impl;

import com.oj.judge.codesandbox.CodeSandbox;
import com.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.oj.judge.codesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

/**
 * 第三方代码沙箱（调用网上现成的代码沙箱）
 */
@Component
public class ThirdPartyCodeSandbox implements CodeSandbox {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        throw new UnsupportedOperationException("第三方代码沙箱未实现");
    }
}
