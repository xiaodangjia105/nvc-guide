package nvc.guide.modules.nvcassistant.evaluation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcassistant.evaluation.dto.EvaluationReport;
import nvc.guide.modules.nvcassistant.metrics.MetricType;
import nvc.guide.modules.nvcassistant.metrics.AgentMetricsEntity;
import nvc.guide.modules.nvcassistant.metrics.AgentMetricsRepository;
import nvc.guide.modules.nvcassistant.trace.AgentSpanEntity;
import nvc.guide.modules.nvcassistant.trace.AgentSpanRepository;
import nvc.guide.modules.nvcassistant.trace.AgentTraceEntity;
import nvc.guide.modules.nvcassistant.trace.AgentTraceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 离线评估服务
 *
 * <p>基于 Trace 和 Metrics 数据，计算 4 个维度的系统质量指标：
 * <ul>
 *   <li>意图路由准确率 — 从 Trace 的 INTENT_ROUTING Span 统计</li>
 *   <li>工具调用稳定性 — 从 Metrics 的 TOOL_CALL 类型统计</li>
 *   <li>端到端性能 — 从 Metrics 的 LATENCY 类型统计</li>
 *   <li>Token 消耗 — 从 Metrics 的 TOKEN 类型统计</li>
 * </ul>
 *
 * <p>手动触发：通过 API 或管理页面按钮按需运行。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OfflineEvaluationService {

    private final AgentTraceRepository traceRepository;
    private final AgentSpanRepository spanRepository;
    private final AgentMetricsRepository metricsRepository;
    private final ObjectMapper objectMapper;

    /**
     * 运行离线评估
     *
     * @param from 评估起始时间
     * @param to   评估结束时间
     * @return 评估报告
     */
    public EvaluationReport evaluate(LocalDateTime from, LocalDateTime to) {
        log.info("[OfflineEvaluation] Starting evaluation: from={}, to={}", from, to);

        List<AgentTraceEntity> traces = traceRepository.findByCreatedAtBetween(from, to);
        List<AgentMetricsEntity> allMetrics = metricsRepository.findByCreatedAtBetweenOrderByCreatedAtAsc(from, to);

        // 1. 意图路由准确率
        double intentAccuracy = calculateIntentRoutingAccuracy(traces);

        // 2. 工具调用稳定性
        Map<String, Double> toolSuccessRates = calculateToolCallStability(allMetrics);

        // 3. 端到端性能
        long[] latencyPercentiles = calculateLatencyPercentiles(allMetrics);

        // 4. Token 消耗
        double avgTokens = calculateAvgTokens(allMetrics);

        // 综合评语
        String summary = buildSummary(traces.size(), intentAccuracy, toolSuccessRates, latencyPercentiles, avgTokens);

        return EvaluationReport.builder()
            .reportId(UUID.randomUUID().toString())
            .evaluatedAt(LocalDateTime.now())
            .totalTraces(traces.size())
            .intentRoutingAccuracy(intentAccuracy)
            .toolSuccessRates(toolSuccessRates)
            .overallToolSuccessRate(toolSuccessRates.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0))
            .latencyP50(latencyPercentiles[0])
            .latencyP90(latencyPercentiles[1])
            .latencyP99(latencyPercentiles[2])
            .avgLatencyMs(latencyPercentiles[3])
            .avgTokensPerSession(avgTokens)
            .summary(summary)
            .build();
    }

    private double calculateIntentRoutingAccuracy(List<AgentTraceEntity> traces) {
        int total = 0, success = 0;
        for (AgentTraceEntity trace : traces) {
            List<AgentSpanEntity> spans = spanRepository.findByTraceIdAndSpanTypeOrderBySequenceAsc(
                trace.getTraceId(), "INTENT_ROUTING");
            for (AgentSpanEntity span : spans) {
                total++;
                if ("SUCCESS".equals(span.getStatus())) success++;
            }
        }
        return total > 0 ? Math.round((double) success / total * 10000) / 100.0 : 0;
    }

    private Map<String, Double> calculateToolCallStability(List<AgentMetricsEntity> allMetrics) {
        List<AgentMetricsEntity> toolMetrics = allMetrics.stream()
            .filter(m -> MetricType.TOOL_CALL.name().equals(m.getMetricType()))
            .toList();

        Map<String, List<AgentMetricsEntity>> byTool = toolMetrics.stream()
            .collect(Collectors.groupingBy(m -> {
                Map<String, Object> p = parsePayload(m.getPayload());
                return p != null ? (String) p.getOrDefault("toolName", "unknown") : "unknown";
            }));

        Map<String, Double> rates = new LinkedHashMap<>();
        for (var entry : byTool.entrySet()) {
            long success = entry.getValue().stream()
                .filter(m -> {
                    Map<String, Object> p = parsePayload(m.getPayload());
                    return p != null && Boolean.TRUE.equals(p.get("success"));
                })
                .count();
            rates.put(entry.getKey(), Math.round((double) success / entry.getValue().size() * 10000) / 100.0);
        }
        return rates;
    }

    private long[] calculateLatencyPercentiles(List<AgentMetricsEntity> allMetrics) {
        List<Long> latencies = allMetrics.stream()
            .filter(m -> MetricType.LATENCY.name().equals(m.getMetricType()))
            .map(m -> parsePayload(m.getPayload()))
            .filter(Objects::nonNull)
            .map(p -> getLong(p, "latencyMs"))
            .sorted()
            .toList();

        if (latencies.isEmpty()) return new long[]{0, 0, 0, 0};

        int size = latencies.size();
        long p50 = latencies.get(size * 50 / 100);
        long p90 = latencies.get(Math.min(size * 90 / 100, size - 1));
        long p99 = latencies.get(Math.min(size * 99 / 100, size - 1));
        long avg = (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0);

        return new long[]{p50, p90, p99, avg};
    }

    private double calculateAvgTokens(List<AgentMetricsEntity> allMetrics) {
        List<AgentMetricsEntity> tokenMetrics = allMetrics.stream()
            .filter(m -> MetricType.TOKEN.name().equals(m.getMetricType()))
            .toList();

        if (tokenMetrics.isEmpty()) return 0;

        long totalTokens = tokenMetrics.stream()
            .map(m -> parsePayload(m.getPayload()))
            .filter(Objects::nonNull)
            .mapToLong(p -> getLong(p, "inputTokens") + getLong(p, "outputTokens"))
            .sum();

        Set<String> sessions = tokenMetrics.stream()
            .map(AgentMetricsEntity::getSessionId)
            .collect(Collectors.toSet());

        return sessions.isEmpty() ? 0 : Math.round((double) totalTokens / sessions.size() * 100) / 100.0;
    }

    private String buildSummary(int totalTraces, double intentAccuracy,
                                Map<String, Double> toolRates, long[] latency, double avgTokens) {
        return String.format(
            "评估完成：共 %d 次对话。意图路由准确率 %.1f%%，工具调用平均成功率 %.1f%%，" +
            "端到端延迟 P50=%dms/P90=%dms，平均每会话 %.0f tokens。",
            totalTraces, intentAccuracy,
            toolRates.values().stream().mapToDouble(Double::doubleValue).average().orElse(0),
            latency[0], latency[1], avgTokens);
    }

    private Map<String, Object> parsePayload(String json) {
        try { return objectMapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception e) { return null; }
    }

    private long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number n ? n.longValue() : 0;
    }
}
