package nvc.guide.modules.nvcassistant.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import nvc.guide.modules.nvcassistant.metrics.dto.*;

/**
 * 指标统计服务
 * 从 DB 聚合计算各项指标，支持按时间范围筛选
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsStatsService {

    private final AgentMetricsRepository metricsRepository;
    private final ObjectMapper objectMapper;

    /**
     * Token 统计
     */
    public TokenStats getTokenStats(LocalDateTime from, LocalDateTime to) {
        List<AgentMetricsEntity> metrics = metricsRepository
            .findByTypeAndTimeRange(MetricType.TOKEN.name(), from, to);

        if (metrics.isEmpty()) {
            return TokenStats.builder()
                .totalTokens(0).avgTokensPerSession(0)
                .avgInputTokens(0).avgOutputTokens(0)
                .degradedCallCount(0).totalLlmCalls(0)
                .build();
        }

        long totalInput = 0, totalOutput = 0, degradedCount = 0;
        Set<String> sessions = new HashSet<>();

        for (AgentMetricsEntity m : metrics) {
            Map<String, Object> payload = parsePayload(m.getPayload());
            if (payload == null) continue;

            totalInput += getLong(payload, "inputTokens");
            totalOutput += getLong(payload, "outputTokens");
            if (getBool(payload, "degraded")) degradedCount++;
            sessions.add(m.getSessionId());
        }

        long totalTokens = totalInput + totalOutput;
        int count = metrics.size();

        return TokenStats.builder()
            .totalTokens(totalTokens)
            .avgTokensPerSession(sessions.isEmpty() ? 0 : (double) totalTokens / sessions.size())
            .avgInputTokens(count == 0 ? 0 : (double) totalInput / count)
            .avgOutputTokens(count == 0 ? 0 : (double) totalOutput / count)
            .degradedCallCount(degradedCount)
            .totalLlmCalls(count)
            .build();
    }

    /**
     * 延迟统计
     */
    public LatencyStats getLatencyStats(LocalDateTime from, LocalDateTime to) {
        List<AgentMetricsEntity> metrics = metricsRepository
            .findByTypeAndTimeRange(MetricType.LATENCY.name(), from, to);

        if (metrics.isEmpty()) {
            return LatencyStats.builder()
                .p50(0).p90(0).p99(0).avgLatencyMs(0).totalRequests(0)
                .build();
        }

        List<Long> latencies = metrics.stream()
            .map(m -> parsePayload(m.getPayload()))
            .filter(Objects::nonNull)
            .map(p -> getLong(p, "latencyMs"))
            .sorted()
            .toList();

        int size = latencies.size();
        long p50 = latencies.get(size * 50 / 100);
        long p90 = latencies.get(Math.min(size * 90 / 100, size - 1));
        long p99 = latencies.get(Math.min(size * 99 / 100, size - 1));
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0);

        return LatencyStats.builder()
            .p50(p50).p90(p90).p99(p99)
            .avgLatencyMs(Math.round(avg * 100) / 100.0)
            .totalRequests(size)
            .build();
    }

    /**
     * 压缩效果统计
     */
    public CompressionStats getCompressionStats(LocalDateTime from, LocalDateTime to) {
        List<AgentMetricsEntity> metrics = metricsRepository
            .findByTypeAndTimeRange(MetricType.COMPRESSION.name(), from, to);

        if (metrics.isEmpty()) {
            return CompressionStats.builder()
                .compressionTriggerCount(0).avgTokenReduction(0).avgReductionPercent(0)
                .build();
        }

        double totalReduction = 0, totalPercent = 0;
        for (AgentMetricsEntity m : metrics) {
            Map<String, Object> payload = parsePayload(m.getPayload());
            if (payload == null) continue;

            long before = getLong(payload, "beforeTokens");
            long after = getLong(payload, "afterTokens");
            totalReduction += (before - after);
            totalPercent += getDouble(payload, "reductionPercent");
        }

        int count = metrics.size();
        return CompressionStats.builder()
            .compressionTriggerCount(count)
            .avgTokenReduction(count == 0 ? 0 : Math.round(totalReduction / count * 100) / 100.0)
            .avgReductionPercent(count == 0 ? 0 : Math.round(totalPercent / count * 100) / 100.0)
            .build();
    }

    /**
     * 工具调用统计
     */
    public ToolCallStats getToolCallStats(LocalDateTime from, LocalDateTime to) {
        List<AgentMetricsEntity> metrics = metricsRepository
            .findByTypeAndTimeRange(MetricType.TOOL_CALL.name(), from, to);

        if (metrics.isEmpty()) {
            return ToolCallStats.builder()
                .perToolSuccessRate(Map.of()).avgToolLatency(Map.of())
                .perToolCallCount(Map.of()).totalCalls(0).overallSuccessRate(0)
                .build();
        }

        // 按工具名分组统计
        Map<String, List<AgentMetricsEntity>> byTool = metrics.stream()
            .collect(Collectors.groupingBy(m -> {
                Map<String, Object> p = parsePayload(m.getPayload());
                return p != null ? (String) p.getOrDefault("toolName", "unknown") : "unknown";
            }));

        Map<String, Double> successRates = new LinkedHashMap<>();
        Map<String, Double> avgLatencies = new LinkedHashMap<>();
        Map<String, Long> callCounts = new LinkedHashMap<>();
        long totalSuccess = 0;

        for (var entry : byTool.entrySet()) {
            String toolName = entry.getKey();
            List<AgentMetricsEntity> toolMetrics = entry.getValue();
            long successCount = 0;
            long totalLatency = 0;

            for (AgentMetricsEntity m : toolMetrics) {
                Map<String, Object> p = parsePayload(m.getPayload());
                if (p == null) continue;
                if (getBool(p, "success")) successCount++;
                totalLatency += getLong(p, "latencyMs");
            }

            int size = toolMetrics.size();
            successRates.put(toolName, size == 0 ? 0 : Math.round((double) successCount / size * 10000) / 100.0);
            avgLatencies.put(toolName, size == 0 ? 0 : Math.round((double) totalLatency / size * 100) / 100.0);
            callCounts.put(toolName, (long) size);
            totalSuccess += successCount;
        }

        return ToolCallStats.builder()
            .perToolSuccessRate(successRates)
            .avgToolLatency(avgLatencies)
            .perToolCallCount(callCounts)
            .totalCalls(metrics.size())
            .overallSuccessRate(metrics.isEmpty() ? 0 : Math.round((double) totalSuccess / metrics.size() * 10000) / 100.0)
            .build();
    }

    /**
     * 综合概览
     */
    public MetricsOverview getOverview(LocalDateTime from, LocalDateTime to) {
        return MetricsOverview.builder()
            .tokenStats(getTokenStats(from, to))
            .latencyStats(getLatencyStats(from, to))
            .compressionStats(getCompressionStats(from, to))
            .toolCallStats(getToolCallStats(from, to))
            .build();
    }

    // ==================== 内部工具方法 ====================

    private Map<String, Object> parsePayload(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse metrics payload: {}", e.getMessage());
            return null;
        }
    }

    private long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        return 0;
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.doubleValue();
        return 0;
    }

    private boolean getBool(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return Boolean.TRUE.equals(v);
    }
}
