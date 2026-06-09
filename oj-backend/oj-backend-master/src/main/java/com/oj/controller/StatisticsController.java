package com.oj.controller;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.oj.model.entity.QuestionSubmit;
import com.oj.service.QuestionSubmitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.text.SimpleDateFormat;
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

    // ==================== 用户热力图 ====================

    @GetMapping("/user/question/statistics/heatmap")
    public Map<String, Object> getHeatmap(
            @RequestParam long user_id,
            @RequestParam(defaultValue = "365") int days) {

        Calendar cal = Calendar.getInstance();
        String endDate = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
        cal.add(Calendar.DAY_OF_YEAR, -days + 1);
        String startDate = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());

        // 查询有提交的日期
        QueryWrapper<QuestionSubmit> qw = new QueryWrapper<>();
        qw.select("DATE(createTime) as submit_date", "COUNT(*) as cnt");
        qw.eq("userId", user_id);
        qw.eq("isDelete", 0);
        qw.ge("DATE(createTime)", startDate);
        qw.le("DATE(createTime)", endDate);
        qw.groupBy("DATE(createTime)");

        List<Map<String, Object>> rows = questionSubmitService.listMaps(qw);

        // 构建日期-数量映射
        Map<String, Integer> countMap = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String date = row.get("submit_date") != null ? row.get("submit_date").toString() : "";
            int cnt = ((Number) row.getOrDefault("cnt", 0)).intValue();
            countMap.put(date, cnt);
        }

        // 填充每一天
        List<Map<String, Object>> data = new ArrayList<>();
        int total = 0;
        try {
            Calendar cursor = (Calendar) cal.clone();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Date end = new SimpleDateFormat("yyyy-MM-dd").parse(endDate);

            while (!cursor.getTime().after(end)) {
                String dateStr = sdf.format(cursor.getTime());
                int count = countMap.getOrDefault(dateStr, 0);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("date", dateStr);
                item.put("count", count);
                data.add(item);
                total += count;
                cursor.add(Calendar.DAY_OF_YEAR, 1);
            }
        } catch (Exception e) {
            log.error("热力图日期生成失败", e);
        }

        Map<String, Object> dateRange = new LinkedHashMap<>();
        dateRange.put("start", startDate);
        dateRange.put("end", endDate);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "成功获取 " + days + " 天数据");
        result.put("data", data);
        result.put("total", total);
        result.put("date_range", dateRange);
        return result;
    }

    // ==================== 热力图摘要 ====================

    @GetMapping("/user/question/statistics/heatmap/summary")
    public Map<String, Object> getHeatmapSummary(
            @RequestParam long user_id,
            @RequestParam(defaultValue = "365") int days) {

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days + 1);
        String startDate = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());

        QueryWrapper<QuestionSubmit> qw = new QueryWrapper<>();
        qw.select("COUNT(*) as total", "COUNT(DISTINCT DATE(createTime)) as active_days");
        qw.eq("userId", user_id);
        qw.eq("isDelete", 0);
        qw.ge("DATE(createTime)", startDate);

        Map<String, Object> stats = questionSubmitService.getMap(qw);

        int total = stats != null ? ((Number) stats.getOrDefault("total", 0)).intValue() : 0;
        int activeDays = stats != null ? ((Number) stats.getOrDefault("active_days", 0)).intValue() : 0;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total_submissions", total);
        data.put("active_days", activeDays);
        data.put("max_streak", 0);
        data.put("current_streak", 0);

        Map<String, Object> dateRange = new LinkedHashMap<>();
        dateRange.put("start", startDate);
        dateRange.put("end", new SimpleDateFormat("yyyy-MM-dd").format(new Date()));
        data.put("date_range", dateRange);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", data);
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
