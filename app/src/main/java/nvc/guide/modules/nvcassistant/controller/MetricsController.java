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
 * <p>TODO [安全] 此控制器暴露内部运营数据（token 使用量、延迟、工具调用统计等），
 * 目前无认证保护。生产环境应添加 @PreAuthorize("hasRole('ADMIN')") 或等效认证。
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
