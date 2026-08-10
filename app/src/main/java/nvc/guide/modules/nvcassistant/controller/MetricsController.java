package nvc.guide.modules.nvcassistant.controller;

import lombok.RequiredArgsConstructor;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcassistant.metrics.MetricsStatsService;
import nvc.guide.modules.nvcassistant.metrics.dto.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Agent 指标查询 API
 *
 * <p>由 AdminApiAuthInterceptor 通过 X-Api-Key 请求头进行鉴权保护。
 * 生产环境需配置 app.security.admin-api-key。
 */
@RestController
@RequestMapping("/api/nvc/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsStatsService metricsStatsService;

    @GetMapping("/token")
    public Result<TokenStats> getTokenStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(metricsStatsService.getTokenStats(from, to));
    }

    @GetMapping("/latency")
    public Result<LatencyStats> getLatencyStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(metricsStatsService.getLatencyStats(from, to));
    }

    @GetMapping("/compression")
    public Result<CompressionStats> getCompressionStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(metricsStatsService.getCompressionStats(from, to));
    }

    @GetMapping("/tools")
    public Result<ToolCallStats> getToolCallStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(metricsStatsService.getToolCallStats(from, to));
    }

    @GetMapping("/overview")
    public Result<MetricsOverview> getOverview(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(metricsStatsService.getOverview(from, to));
    }
}
