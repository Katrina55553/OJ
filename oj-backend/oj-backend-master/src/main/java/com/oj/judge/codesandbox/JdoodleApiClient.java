// com/oj/judge/codesandbox/JdoodleApiClient.java

package com.oj.judge.codesandbox;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.oj.common.ErrorCode;
import com.oj.exception.BusinessException;
import com.oj.judge.codesandbox.model.ExecuteCodeRequest;
import com.oj.judge.codesandbox.model.ExecuteCodeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;


@Slf4j
@Component
public class JdoodleApiClient {

    @Value("${jdoodle.client-id}")
    private String clientId;

    @Value("${jdoodle.client-secret}")
    private String clientSecret;

    private static final String JD_API_URL = "https://api.jdoodle.com/v1/execute";

    @PostConstruct
    public void validateConfig() {
        if (StrUtil.isBlank(clientId) || StrUtil.isBlank(clientSecret)) {
            log.warn("JDoodle clientId 或 clientSecret 未配置，判题功能将不可用。请设置环境变量 JDOODLE_CLIENT_ID 和 JDOODLE_CLIENT_SECRET");
        }
    }

    /**
     * 执行代码（支持多测试用例）
     */
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        if (StrUtil.isBlank(clientId) || StrUtil.isBlank(clientSecret)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "JDoodle API 未配置，请设置环境变量 JDOODLE_CLIENT_ID 和 JDOODLE_CLIENT_SECRET");
        }
        List<String> inputList = request.getInputList();
        List<String> outputList = new ArrayList<>();
        long totalCpuTime = 0;
        long totalMemory = 0;

        // JDoodle 每次只能执行一个输入，需循环调用
        for (String input : inputList) {
            JdoodleRequest jdRequest = new JdoodleRequest();
            jdRequest.setClientId(clientId);
            jdRequest.setClientSecret(clientSecret);
            jdRequest.setScript(request.getCode());
            jdRequest.setLanguage(getJdoodleLanguage(request.getLanguage()));
            jdRequest.setStdin(input == null ? "" : input);
            jdRequest.setVersionIndex("0"); // 使用默认版本

            String jsonBody = JSONUtil.toJsonStr(jdRequest);
            log.debug("Sending to JDoodle: {}", jsonBody);

            HttpResponse response = HttpRequest.post(JD_API_URL)
                    .body(jsonBody)
                    .header("Content-Type", "application/json")
                    .timeout(10000) // 10秒超时
                    .execute();

            if (response.getStatus() != HttpStatus.HTTP_OK) {
                String errorMsg = "JDoodle API error: " + response.getStatus() + " - " + response.body();
                log.error(errorMsg);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码执行失败：" + errorMsg);
            }

            String responseBody = response.body();
            log.debug("JDoodle response: {}", responseBody);

            JdoodleResponse jdResponse = JSONUtil.toBean(responseBody, JdoodleResponse.class);

            // 处理错误（如编译错误、运行时错误）
            if (jdResponse.getError() != null && !jdResponse.getError().trim().isEmpty()) {
                // 如果是第一个用例就错，可直接返回
                outputList.add(jdResponse.getError());
                break;
            }

            // 正常输出
            String output = jdResponse.getOutput() == null ? "" : jdResponse.getOutput();
            outputList.add(output);

            // 累加资源使用（可选）
            try {
                totalCpuTime += Double.parseDouble(jdResponse.getCpuTime()) * 1000; // 转为毫秒
                totalMemory += Long.parseLong(jdResponse.getMemory());
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        // 构造返回对象（模拟 Piston 的结构）
        com.oj.judge.codesandbox.model.JudgeInfo judgeInfo = new com.oj.judge.codesandbox.model.JudgeInfo();
        judgeInfo.setTime(totalCpuTime); // ms
        judgeInfo.setMemory(totalMemory); // KB

        return ExecuteCodeResponse.builder()
                .outputList(outputList)
                .judgeInfo(judgeInfo)
                .build();
    }

    /**
     * 将你的语言标识转为 JDoodle 支持的 language 字符串
     */
    private String getJdoodleLanguage(String lang) {
        switch (lang.toLowerCase()) {
            case "cpp":
                return "cpp17"; // 或 cpp, cpp14
            case "java":
                return "java";
            case "python":
                return "python3";
            case "go":
                return "go";
            case "javascript":
                return "nodejs";
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的语言: " + lang);
        }
    }

    // --- 内部 DTO ---
    public static class JdoodleRequest {
        private String clientId;
        private String clientSecret;
        private String script;
        private String language;
        private String stdin;
        private String versionIndex;

        // getters and setters
        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

        public String getStdin() {
            return stdin;
        }

        public void setStdin(String stdin) {
            this.stdin = stdin;
        }

        public String getVersionIndex() {
            return versionIndex;
        }

        public void setVersionIndex(String versionIndex) {
            this.versionIndex = versionIndex;
        }
    }

    public static class JdoodleResponse {
        private String output;
        private String statusCode;
        private String memory;
        private String cpuTime;
        private String error;

        // getters and setters
        public String getOutput() {
            return output;
        }

        public void setOutput(String output) {
            this.output = output;
        }

        public String getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(String statusCode) {
            this.statusCode = statusCode;
        }

        public String getMemory() {
            return memory;
        }

        public void setMemory(String memory) {
            this.memory = memory;
        }

        public String getCpuTime() {
            return cpuTime;
        }

        public void setCpuTime(String cpuTime) {
            this.cpuTime = cpuTime;
        }

        public String getError() {
            return error;
        }

        public void setError(String error) {
            this.error = error;
        }
    }
}