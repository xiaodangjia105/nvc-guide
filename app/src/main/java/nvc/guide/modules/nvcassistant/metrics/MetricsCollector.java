package nvc.guide.modules.nvcassistant.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 指标采集器
 *
 * <p>在 AgentLoop 各阶段采集指标，通过 MetricsStreamProducer 异步写入 Redis Stream，
 * 再由 MetricsStreamConsumer 批量落库 PostgreSQL。
 *
 * <p>采集的 4 项核心指标：
 * <ul>
 *   <li>TOKEN — 每次 LLM 调用的 input/output tokens</li>
 *   <li>LATENCY — 端到端延迟</li>
 *   <li>COMPRESSION — 上下文压缩效果</li>
 *   <li>TOOL_CALL — 工具调用成功/失败/耗时</li>
 * </ul>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MetricsCollector {

    private final MetricsStreamProducer metricsStreamProducer;
    private final ObjectMapper objectMapper;

    /**
     * 记录 LLM 调用 Token 消耗
     *
     * @param sessionId   会话 ID
     * @param traceId     Trace ID（可为 null，P0-2 阶段填充）
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @param model        模型名称
     * @param degraded     是否为降级调用
     */
    public void recordLlmCall(String sessionId, String traceId,
                              int inputTokens, int outputTokens,
                              String model, boolean degraded) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("inputTokens", inputTokens);
            payload.put("outputTokens", outputTokens);
            payload.put("model", model);
            payload.put("degraded", degraded);

            sendMetric(sessionId, traceId, MetricType.TOKEN.name(), payload);
        } catch (Exception e) {
            log.warn("[MetricsCollector] Failed to record LLM call metric: {}", e.getMessage());
        }
    }

    /**
     * 记录端到端延迟
     *
     * @param sessionId 会话 ID
     * @param latencyMs 延迟毫秒数
     * @param phase     阶段标识（如 "e2e", "llm_call"）
     */
    public void recordLatency(String sessionId, long latencyMs, String phase) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("latencyMs", latencyMs);
            payload.put("phase", phase);

            sendMetric(sessionId, null, MetricType.LATENCY.name(), payload);
        } catch (Exception e) {
            log.warn("[MetricsCollector] Failed to record latency metric: {}", e.getMessage());
        }
    }

    /**
     * 记录上下文压缩效果
     *
     * @param sessionId   会话 ID
     * @param beforeTokens 压缩前 Token 数
     * @param afterTokens  压缩后 Token 数
     * @param summary      压缩摘要内容（可为 null）
     */
    public void recordCompression(String sessionId, int beforeTokens, int afterTokens, String summary) {
        try {
            double reductionPercent = beforeTokens > 0
                ? Math.round((1.0 - (double) afterTokens / beforeTokens) * 10000) / 100.0
                : 0;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("beforeTokens", beforeTokens);
            payload.put("afterTokens", afterTokens);
            payload.put("reductionPercent", reductionPercent);
            if (summary != null) {
                payload.put("summary", summary.length() > 200 ? summary.substring(0, 200) + "..." : summary);
            }

            sendMetric(sessionId, null, MetricType.COMPRESSION.name(), payload);
        } catch (Exception e) {
            log.warn("[MetricsCollector] Failed to record compression metric: {}", e.getMessage());
        }
    }

    /**
     * 记录工具调用
     *
     * @param sessionId 会话 ID
     * @param toolName  工具名称
     * @param success   是否成功
     * @param latencyMs 延迟毫秒数
     * @param resultCount 结果数量（可为 null）
     */
    public void recordToolCall(String sessionId, String toolName,
                               boolean success, long latencyMs, Integer resultCount) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("toolName", toolName);
            payload.put("success", success);
            payload.put("latencyMs", latencyMs);
            if (resultCount != null) {
                payload.put("resultCount", resultCount);
            }

            sendMetric(sessionId, null, MetricType.TOOL_CALL.name(), payload);
        } catch (Exception e) {
            log.warn("[MetricsCollector] Failed to record tool call metric: {}", e.getMessage());
        }
    }

    /**
     * 发送指标到 Redis Stream
     */
    private void sendMetric(String sessionId, String traceId, String metricType, Map<String, Object> payload) throws Exception {
        AgentMetricsEntity entity = AgentMetricsEntity.builder()
            .sessionId(sessionId)
            .traceId(traceId)
            .metricType(metricType)
            .payload(objectMapper.writeValueAsString(payload))
            .build();

        metricsStreamProducer.sendMetric(entity);
        log.debug("[MetricsCollector] Recorded {} metric for session={}", metricType, sessionId);
    }
}
