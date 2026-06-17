package com.oj.service.impl;

import com.oj.service.MonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 监控指标服务实现
 * 使用内存 ConcurrentHashMap 存储指标，适用于单机部署场景
 */
@Service
@Slf4j
public class MonitorServiceImpl implements MonitorService {

    private final ConcurrentHashMap<String, MetricStats> metrics = new ConcurrentHashMap<>();

    @Override
    public void recordCost(String name, long costMs) {
        metrics.computeIfAbsent(name, k -> new MetricStats()).record(costMs);
    }

    @Override
    public Map<String, Object> getMetric(String name) {
        MetricStats stats = metrics.get(name);
        if (stats == null) {
            return new HashMap<>();
        }
        return stats.snapshot();
    }

    @Override
    public Map<String, Map<String, Object>> getAllMetrics() {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map.Entry<String, MetricStats> entry : metrics.entrySet()) {
            result.put(entry.getKey(), entry.getValue().snapshot());
        }
        return result;
    }

    @Override
    public void resetMetrics() {
        metrics.clear();
    }

    /**
     * 单个指标的统计数据
     */
    private static class MetricStats {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong total = new AtomicLong(0);
        private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong errorCount = new AtomicLong(0);

        void record(long costMs) {
            count.incrementAndGet();
            total.addAndGet(costMs);
            // 更新最大/最小
            long curMax = max.get();
            while (costMs > curMax) {
                if (max.compareAndSet(curMax, costMs)) break;
                curMax = max.get();
            }
            long curMin = min.get();
            while (costMs < curMin) {
                if (min.compareAndSet(curMin, costMs)) break;
                curMin = min.get();
            }
        }

        Map<String, Object> snapshot() {
            long c = count.get();
            long t = total.get();
            Map<String, Object> map = new HashMap<>();
            map.put("count", c);
            map.put("total", t);
            map.put("avg", c > 0 ? (double) t / c : 0.0);
            long mx = max.get();
            long mn = min.get();
            map.put("max", mx == Long.MIN_VALUE ? 0 : mx);
            map.put("min", mn == Long.MAX_VALUE ? 0 : mn);
            return map;
        }
    }
}
