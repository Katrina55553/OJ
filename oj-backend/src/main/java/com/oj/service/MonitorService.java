package com.oj.service;

import java.util.Map;

/**
 * 监控指标服务
 * 提供接口调用次数、耗时等关键指标的采集和查询
 */
public interface MonitorService {

    /**
     * 记录接口调用耗时
     *
     * @param name  指标名称
     * @param costMs 耗时（毫秒
     */
    void recordCost(String name, long costMs);

    /**
     * 获取指标统计
     *
     * @param name 指标名称
     * @return 统计信息
     */
    Map<String, Object> getMetric(String name);

    /**
     * 获取全部指标统计
     *
     * @return 全部指标统计
     */
    Map<String, Map<String, Object>> getAllMetrics();

    /**
     * 重置指标
     */
    void resetMetrics();
}
