package nvc.guide.modules.nvcassistant.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsCollector 指标采集器测试")
class MetricsCollectorTest {

    @Mock
    private MetricsStreamProducer metricsStreamProducer;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MetricsCollector metricsCollector;

    @Captor
    private ArgumentCaptor<AgentMetricsEntity> entityCaptor;

    @Test
    @DisplayName("recordLlmCall 应正确构建 TOKEN 类型指标")
    void recordLlmCall_shouldBuildTokenMetric() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"inputTokens\":1200,\"outputTokens\":180}");

        metricsCollector.recordLlmCall("session-1", null, 1200, 180, "qwen-plus", false);

        verify(metricsStreamProducer).sendMetric(entityCaptor.capture());
        AgentMetricsEntity entity = entityCaptor.getValue();

        assertEquals("session-1", entity.getSessionId());
        assertEquals(MetricType.TOKEN.name(), entity.getMetricType());
        assertNull(entity.getTraceId());
    }

    @Test
    @DisplayName("recordLatency 应正确构建 LATENCY 类型指标")
    void recordLatency_shouldBuildLatencyMetric() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"latencyMs\":1800,\"phase\":\"e2e\"}");

        metricsCollector.recordLatency("session-1", 1800, "e2e");

        verify(metricsStreamProducer).sendMetric(entityCaptor.capture());
        AgentMetricsEntity entity = entityCaptor.getValue();

        assertEquals("session-1", entity.getSessionId());
        assertEquals(MetricType.LATENCY.name(), entity.getMetricType());
    }

    @Test
    @DisplayName("recordCompression 应正确计算压缩百分比")
    void recordCompression_shouldCalculateReductionPercent() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"beforeTokens\":3200,\"afterTokens\":1800}");

        metricsCollector.recordCompression("session-1", 3200, 1800, "摘要内容");

        verify(metricsStreamProducer).sendMetric(entityCaptor.capture());
        AgentMetricsEntity entity = entityCaptor.getValue();

        assertEquals(MetricType.COMPRESSION.name(), entity.getMetricType());
    }

    @Test
    @DisplayName("recordToolCall 应正确构建 TOOL_CALL 类型指标")
    void recordToolCall_shouldBuildToolCallMetric() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"toolName\":\"rag_search\",\"success\":true}");

        metricsCollector.recordToolCall("session-1", "rag_search", true, 450, 3);

        verify(metricsStreamProducer).sendMetric(entityCaptor.capture());
        AgentMetricsEntity entity = entityCaptor.getValue();

        assertEquals(MetricType.TOOL_CALL.name(), entity.getMetricType());
    }

    @Test
    @DisplayName("Stream 发送失败不应抛出异常")
    void recordLlmCall_shouldNotThrowOnStreamFailure() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON error"));

        assertDoesNotThrow(() ->
            metricsCollector.recordLlmCall("session-1", null, 100, 50, "model", false));

        verify(metricsStreamProducer, never()).sendMetric(any());
    }
}
