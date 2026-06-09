package com.oj.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.model.entity.AiMessage;
import com.oj.service.AiMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * AI 聊天接口 — SSE 流式响应 + 历史消息
 */
@Slf4j
@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Resource
    private AiMessageService aiMessageService;

    @Value("${ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ai.ollama.model:deepseek-r1:7b}")
    private String ollamaModel;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * SSE 流式聊天
     * 直接写 HttpServletResponse，绕过 @RestControllerAdvice 的 BaseResponse 包装
     */
    @GetMapping("/stream")
    public void stream(@RequestParam String prompt, HttpServletResponse response) {
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no"); // 禁用 nginx 缓冲

        StringBuilder fullResponse = new StringBuilder();
        OutputStream os = null;

        try {
            os = response.getOutputStream();

            // 立即发送注释行，防止 EventSource 超时断开
            os.write(":ok\n\n".getBytes(StandardCharsets.UTF_8));
            os.flush();

            // 保存用户消息
            AiMessage userMsg = new AiMessage();
            userMsg.setRole("user");
            userMsg.setContent(prompt);
            userMsg.setCreateTime(new Date());
            aiMessageService.save(userMsg);

            HttpURLConnection conn = callOllama(prompt);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) continue;

                    JsonNode node = objectMapper.readTree(line);
                    String token = node.has("response") ? node.get("response").asText() : "";
                    boolean done = node.has("done") && node.get("done").asBoolean();

                    if (!token.isEmpty()) {
                        fullResponse.append(token);
                        os.write(("data:" + token + "\n\n").getBytes(StandardCharsets.UTF_8));
                        os.flush();
                    }

                    if (done) break;
                }
            }

            // 保存 AI 回复
            String aiContent = fullResponse.toString();
            if (!aiContent.isEmpty()) {
                AiMessage aiMsg = new AiMessage();
                aiMsg.setRole("assistant");
                aiMsg.setContent(aiContent);
                aiMsg.setCreateTime(new Date());
                aiMessageService.save(aiMsg);
            }

            log.info("AI 聊天完成: promptLength={}, responseLength={}", prompt.length(), aiContent.length());

        } catch (Exception e) {
            log.error("AI 聊天异常", e);
            String errMsg = e.getMessage();
            if (errMsg == null || errMsg.isEmpty()) {
                // 连接拒绝、DNS 失败等异常没有 message，用 toString()
                errMsg = e.toString();
            }
            try {
                if (os != null) {
                    os.write(("data:🔌 AI 服务异常: " + errMsg + "\n\n").getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            } catch (Exception ignored) {}
        } finally {
            if (os != null) {
                try { os.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 获取历史消息
     */
    @GetMapping("/messages")
    public List<AiMessage> getMessages() {
        return aiMessageService.list();
    }

    /**
     * 调用 Ollama generate API
     */
    private HttpURLConnection callOllama(String prompt) throws Exception {
        URL url = new URL(ollamaBaseUrl + "/api/generate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(300_000);

        String body = objectMapper.createObjectNode()
                .put("model", ollamaModel)
                .put("prompt", prompt)
                .put("stream", true)
                .toString();

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int status = conn.getResponseCode();
        if (status != 200) {
            // 读取 Ollama 返回的错误信息
            String errorBody = "";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                errorBody = sb.toString();
            } catch (Exception ignored) {}
            throw new RuntimeException("Ollama HTTP " + status + ": " + errorBody);
        }

        return conn;
    }
}
