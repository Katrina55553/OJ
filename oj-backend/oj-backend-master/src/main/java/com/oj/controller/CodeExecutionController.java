package com.oj.controller;

import com.oj.model.dto.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@RestController
@RequestMapping("/code")
@CrossOrigin(origins = "http://localhost:8080")
public class CodeExecutionController {

    private final RestTemplate restTemplate;

    private static final String JD_CLIENT_ID = "fdc3701b496ee42fa3a246ab1e6b4f53";
    private static final String JD_CLIENT_SECRET = "1deb28aafbcd65097e8b60da829a2d1e1bacbd601bd2f37a9d6efb078bdbcade";

    public CodeExecutionController() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    @PostMapping("/execute")
    public ResponseEntity<ExecutionResult> executeCode(@RequestBody CodeExecutionRequest request) {
        try {
            // 构建 JDoodle 请求
            JdoodleRequest jdRequest = new JdoodleRequest();
            jdRequest.setScript(request.getScript());
            jdRequest.setLanguage(request.getLanguage());
            jdRequest.setStdin(request.getStdin());
            jdRequest.setClientId(JD_CLIENT_ID);
            jdRequest.setClientSecret(JD_CLIENT_SECRET);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<JdoodleRequest> entity = new HttpEntity<>(jdRequest, headers);

            // 调用 JDoodle
            JdoodleResponse jdResponse = restTemplate.postForObject(
                    "https://api.jdoodle.com/v1/execute",
                    entity,
                    JdoodleResponse.class
            );

            if (jdResponse == null) {
                throw new RuntimeException("JDoodle returned null response");
            }

            // 构建返回给前端的结果
            ExecutionResult result = new ExecutionResult();
            String output = jdResponse.getOutput() != null ? jdResponse.getOutput() : "";

            String statusCode = jdResponse.getStatusCode();
            if ("200".equals(statusCode)) {
                result.setStatus("Accepted");
                result.setStdout(output);
                result.setStderr("");
            } else if ("400".equals(statusCode)) {
                result.setStatus("Compilation Error");
                result.setStderr(output);
                result.setStdout("");
            } else {
                result.setStatus("Runtime Error");
                result.setStderr(output);
                result.setStdout("");
            }

            // 4. 解析时间和内存（JDoodle 返回字符串）
            try {
                double timeInSeconds = Double.parseDouble(jdResponse.getCpuTime());
                result.setTime((long) (timeInSeconds * 1000)); // 转为毫秒
            } catch (Exception e) {
                result.setTime(0);
            }

            try {
                result.setMemory(Integer.parseInt(jdResponse.getMemory())); // KB
            } catch (Exception e) {
                result.setMemory(0);
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            e.printStackTrace();
            ExecutionResult error = new ExecutionResult();
            error.setStatus("System Error");
            error.setStderr("代码执行服务异常: " + e.getMessage());
            error.setStdout("");
            error.setTime(0);
            error.setMemory(0);
            return ResponseEntity.status(500).body(error);
        }
    }
}