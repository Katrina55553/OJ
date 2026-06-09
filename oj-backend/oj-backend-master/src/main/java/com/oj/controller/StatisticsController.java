package com.oj.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.oj.model.entity.QuestionSubmit;
import com.oj.service.QuestionSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

/**
 * 数据统计接口（整合自 oj-visualize FastAPI 项目）
 */
@Slf4j
@RestController
public class StatisticsController {

    @Resource
    private QuestionSubmitService questionSubmitService;

    // ==================== 语言分布 ====================

    @GetMapping("/statistics/language-distribution")
    public Map<String, Object> getLanguageDistribution(
            @RequestParam(required = false) String judge_message,
            @RequestParam(defaultValue = "0") int min_count) {

        QueryWrapper<QuestionSubmit> qw = new QueryWrapper<>();
        qw.select("language", "COUNT(*) as cnt");
        qw.eq("isDelete", 0);
        qw.groupBy("language");
        qw.orderByDesc("cnt");

        List<Map<String, Object>> rows = questionSubmitService.listMaps(qw);

        List<Map<String, Object>> data = new ArrayList<>();
        int total = 0;

        for (Map<String, Object> row : rows) {
            String lang = (String) row.getOrDefault("language", "Unknown");
            int cnt = ((Number) row.getOrDefault("cnt", 0)).intValue();

            if (judge_message != null && !judge_message.isEmpty()) {
                // 按判题结果过滤需要额外逻辑，此处简化处理
            }

            if (cnt >= min_count) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", lang);
                item.put("value", cnt);
                data.add(item);
                total += cnt;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "共统计 " + data.size() + " 种语言");
        result.put("data", data);
        result.put("total", total);
        return result;
    }

    // ==================== 判题结果分布 ====================

    @GetMapping("/statistics/judge-distribution")
    public Map<String, Object> getJudgeDistribution(
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "0") int min_count) {

        QueryWrapper<QuestionSubmit> qw = new QueryWrapper<>();
        qw.select("judgeInfo");
        qw.eq("isDelete", 0);
        qw.isNotNull("judgeInfo");
        if (language != null && !language.isEmpty()) {
            qw.eq("language", language);
        }

        List<Map<String, Object>> rows = questionSubmitService.listMaps(qw);

        Map<String, Integer> counter = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String judgeInfo = (String) row.get("judgeInfo");
            String message = extractJudgeMessage(judgeInfo);
            counter.merge(message, 1, Integer::sum);
        }

        List<Map<String, Object>> data = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            if (entry.getValue() >= min_count) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getKey());
                item.put("value", entry.getValue());
                data.add(item);
            }
        }
        data.sort((a, b) -> ((Integer) b.get("value")).compareTo((Integer) a.get("value")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "共统计 " + data.size() + " 种判题结果");
        result.put("data", data);
        result.put("total", data.stream().mapToInt(d -> (Integer) d.get("value")).sum());
        return result;
    }

    // ==================== 工具方法 ====================

    private String extractJudgeMessage(String judgeInfo) {
        if (judgeInfo == null || judgeInfo.isEmpty()) {
            return "Unknown";
        }
        try {
            Map<String, Object> map = JSONUtil.toBean(judgeInfo, Map.class);
            Object message = map.get("message");
            return message != null ? message.toString() : "Unknown";
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
