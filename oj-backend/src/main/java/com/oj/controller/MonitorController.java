package com.oj.controller;

import com.oj.common.BaseResponse;
import com.oj.common.ResultUtils;
import com.oj.service.MonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 监控指标接口
 * 提供接口调用次数、耗时等指标查询
 */
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    @Resource
    private MonitorService monitorService;

    /**
     * 获取全部指标
     *
     * @return
     */
    @GetMapping("/metrics")
    public BaseResponse<Map<String, Map<String, Object>>> getMetrics() {
        return ResultUtils.success(monitorService.getAllMetrics());
    }

    /**
     * 重置指标
     *
     * @return
     */
    @GetMapping("/reset")
    public BaseResponse<String> resetMetrics() {
        monitorService.resetMetrics();
        return ResultUtils.success("ok");
    }
}
