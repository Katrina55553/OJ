package com.oj.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oj.model.entity.AiMessage;
import com.oj.service.AiMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * SSE 流式聊天
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam String prompt) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

        executor.execute(() -> {
            try {
                // 保存用户消息
                AiMessage userMsg = new AiMessage();
                userMsg.setRole("user");
                userMsg.setContent(prompt);
                userMsg.setCreateTime(new Date());
                aiMessageService.save(userMsg);

                // 调用 Ollama API
                HttpURLConnection conn = callOllama(prompt);
                StringBuilder fullResponse = new StringBuilder();

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
                            emitter.send(SseEmitter.event().name("message").data(token));
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

                emitter.complete();
                log.info("AI 聊天完成: promptLength={}, responseLength={}", prompt.length(), aiContent.length());

            } catch (Exception e) {
                log.error("AI 聊天异常", e);
                try {
                    emitter.send(SseEmitter.event().name("error").data("AI 服务异常: " + e.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 获取历史消息
     */
    @GetMapping("/messages")
    public List<AiMessage> getMessages() {
        return aiMessageService.list();
    }

    /**
     * 调用 Ollama generate API（非流式由 API 参数控制，这里固定用流式）
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

        return conn;
    }
}
