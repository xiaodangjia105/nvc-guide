package nvc.guide.modules.nvcassistant.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcassistant.evaluation.OfflineEvaluationService;
import nvc.guide.modules.nvcassistant.evaluation.dto.EvaluationReport;
import nvc.guide.modules.nvcassistant.trace.AgentSpanRepository;
import nvc.guide.modules.nvcassistant.trace.AgentTraceEntity;
import nvc.guide.modules.nvcassistant.trace.AgentTraceRepository;
import nvc.guide.modules.nvcassistant.trace.TraceStatsService;
import nvc.guide.modules.nvcassistant.trace.dto.TraceStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent Trace 查询 API
 *
 * <p>TODO [安全] 此控制器暴露内部追踪数据（包括对话内容），
 * 目前无认证保护。生产环境应添加 @PreAuthorize("hasRole('ADMIN')") 或等效认证。
 */
@RestController
@RequestMapping("/api/nvc/traces")
@RequiredArgsConstructor
@Validated
public class TraceController {

    private final AgentTraceRepository traceRepository;
    private final AgentSpanRepository spanRepository;
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
        Page<AgentTraceEntity> result;
        if (sessionId != null && !sessionId.isBlank()) {
            result = traceRepository.findBySessionIdOrderByCreatedAtDesc(
                sessionId, PageRequest.of(page, size));
        } else {
            result = traceRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
        }
        return Result.success(result.getContent());
    }

    /**
     * 查询单个 Trace 详情（含 Spans）
     */
    @GetMapping("/{traceId}")
    @Transactional(readOnly = true)
    public Result<AgentTraceEntity> getDetail(@PathVariable String traceId) {
        return traceRepository.findById(traceId)
            .map(trace -> {
                // 显式触发懒加载，确保 spans 被加载
                trace.getSpans().size();
                return Result.success(trace);
            })
            .orElse(Result.success(null));
    }

    /**
     * 按时间范围查询 Trace（支持筛选）
     */
    @GetMapping("/search")
    public Result<List<AgentTraceEntity>> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mode) {

        List<AgentTraceEntity> traces;
        if (status != null) {
            traces = traceRepository.findByStatusAndTimeRange(status, from, to);
        } else if (mode != null) {
            traces = traceRepository.findByModeAndTimeRange(mode, from, to);
        } else {
            traces = traceRepository.findByCreatedAtBetween(from, to);
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
