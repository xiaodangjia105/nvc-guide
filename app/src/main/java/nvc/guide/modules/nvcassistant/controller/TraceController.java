package nvc.guide.modules.nvcassistant.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcassistant.evaluation.OfflineEvaluationService;
import nvc.guide.modules.nvcassistant.evaluation.dto.EvaluationReport;
import nvc.guide.modules.nvcassistant.trace.AgentTraceEntity;
import nvc.guide.modules.nvcassistant.trace.AgentTraceService;
import nvc.guide.modules.nvcassistant.trace.TraceStatsService;
import nvc.guide.modules.nvcassistant.trace.dto.TraceStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent Trace 查询 API
 *
 * <p>由 AdminApiAuthInterceptor 通过 X-Api-Key 请求头进行鉴权保护。
 * 生产环境需配置 app.security.admin-api-key。
 */
@RestController
@RequestMapping("/api/nvc/traces")
@RequiredArgsConstructor
@Validated
public class TraceController {

    private final AgentTraceService agentTraceService;
    private final TraceStatsService traceStatsService;
    private final OfflineEvaluationService offlineEvaluationService;

    /**
     * 查询 Trace 列表（sessionId 可选，不传则返回所有）
     */
    @GetMapping
    public Result<List<AgentTraceEntity>> list(
            @RequestParam(required = false) String sessionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<AgentTraceEntity> result =
            agentTraceService.listTraces(sessionId, PageRequest.of(page, size));
        return Result.success(result.getContent());
    }

    /**
     * 查询单个 Trace 详情（含 Spans）
     */
    @GetMapping("/{traceId}")
    public Result<AgentTraceEntity> getDetail(@PathVariable String traceId) {
        return agentTraceService.getTraceDetail(traceId)
            .map(Result::success)
            .orElse(Result.success(null));
    }

    /**
     * 按时间范围查询 Trace（支持筛选，分页）
     */
    @GetMapping("/search")
    public Result<Page<AgentTraceEntity>> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<AgentTraceEntity> traces;
        if (status != null) {
            traces = agentTraceService.searchByStatus(status, from, to, pageRequest);
        } else if (mode != null) {
            traces = agentTraceService.searchByMode(mode, from, to, pageRequest);
        } else {
            traces = agentTraceService.searchByTimeRange(from, to, pageRequest);
        }
        return Result.success(traces);
    }

    /**
     * Trace 统计概览
     */
    @GetMapping("/stats")
    public Result<TraceStats> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(traceStatsService.getStats(from, to));
    }

    /**
     * 运行离线评估（手动触发）
     */
    @PostMapping("/evaluate")
    public Result<EvaluationReport> runOfflineEvaluation(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.success(offlineEvaluationService.evaluate(from, to));
    }
}
