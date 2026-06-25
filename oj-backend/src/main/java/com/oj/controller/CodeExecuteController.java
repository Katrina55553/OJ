package com.oj.controller;

import com.oj.common.ErrorCode;
import com.oj.exception.BusinessException;
import com.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.oj.judge.codesandbox.model.ExecuteCodeResponse;
import com.oj.model.dto.CodeExecuteRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 代码执行接口（"运行代码"功能，直接返回输出，不存入数据库、不走判题流程）
 */
@Slf4j
@RestController
@RequestMapping("/code")
public class CodeExecuteController {

    @Resource
    private com.oj.judge.codesandbox.impl.DockerCodeSandbox dockerCodeSandbox;

    /**
     * 前端语言名 → 沙箱内部语言名
     */
    private static final Map<String, String> LANGUAGE_MAPPING = new HashMap<>();

    static {
        LANGUAGE_MAPPING.put("python3", "python");
        LANGUAGE_MAPPING.put("nodejs", "javascript");
        LANGUAGE_MAPPING.put("cpp", "cpp");
        LANGUAGE_MAPPING.put("java", "java");
        LANGUAGE_MAPPING.put("go", "go");
    }

    @PostMapping("/execute")
    public Map<String, Object> executeCode(@RequestBody CodeExecuteRequest request) {
        String language = LANGUAGE_MAPPING.get(request.getLanguage());
        if (language == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的语言: " + request.getLanguage());
        }

        if (request.getScript() == null || request.getScript().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码不能为空");
        }

        String stdin = request.getStdin() != null ? request.getStdin() : "";

        ExecuteCodeRequest executeRequest = ExecuteCodeRequest.builder()
                .code(request.getScript())
                .language(language)
                .inputList(Collections.singletonList(stdin))
                .build();

        ExecuteCodeResponse response = dockerCodeSandbox.executeCode(executeRequest);

        // 构造前端期望的响应格式
        Map<String, Object> result = new HashMap<>();
        result.put("status", response.getJudgeInfo() != null
                ? response.getJudgeInfo().getMessage()
                : "Unknown");
        result.put("stdout", response.getOutputList() != null && !response.getOutputList().isEmpty()
                ? response.getOutputList().get(0)
                : "");
        result.put("stderr", response.getMessage() != null ? response.getMessage() : "");
        result.put("time", response.getJudgeInfo() != null
                ? response.getJudgeInfo().getTime()
                : 0L);
        result.put("memory", response.getJudgeInfo() != null
                ? response.getJudgeInfo().getMemory()
                : 0L);

        log.info("代码执行完成: language={}, status={}, time={}ms",
                language, result.get("status"), result.get("time"));

        return result;
    }
}
