package nvc.guide.modules.nvcassistant.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcassistant.metrics.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MetricsStatsService 指标统计服务测试")
class MetricsStatsServiceTest {

    @Mock
    private AgentMetricsRepository metricsRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private MetricsStatsService metricsStatsService;

    private final LocalDateTime from = LocalDateTime.of(2026, 8, 1, 0, 0);
    private final LocalDateTime to = LocalDateTime.of(2026, 8, 4, 23, 59);

    @Test
    @DisplayName("Token 统计：空数据应返回零值")
    void getTokenStats_emptyData_shouldReturnZeros() {
        when(metricsRepository.findByTypeAndTimeRange(eq("TOKEN"), any(), any()))
            .thenReturn(List.of());

        TokenStats stats = metricsStatsService.getTokenStats(from, to);

        assertEquals(0, stats.getTotalTokens());
        assertEquals(0, stats.getAvgTokensPerSession());
        assertEquals(0, stats.getTotalLlmCalls());
    }

    @Test
    @DisplayName("Token 统计：应正确聚合 Token 数据")
    void getTokenStats_withData_shouldAggregateCorrectly() {
        List<AgentMetricsEntity> metrics = List.of(
            buildMetric("session-1", "TOKEN", "{\"inputTokens\":1200,\"outputTokens\":180,\"degraded\":false}"),
            buildMetric("session-1", "TOKEN", "{\"inputTokens\":800,\"outputTokens\":120,\"degraded\":false}"),
            buildMetric("session-2", "TOKEN", "{\"inputTokens\":1000,\"outputTokens\":150,\"degraded\":true}")
        );
        when(metricsRepository.findByTypeAndTimeRange(eq("TOKEN"), any(), any()))
            .thenReturn(metrics);

        TokenStats stats = metricsStatsService.getTokenStats(from, to);

        assertEquals(3450, stats.getTotalTokens()); // 1200+180+800+120+1000+150
        assertEquals(3, stats.getTotalLlmCalls()); // 3 LLM calls total
        assertEquals(1, stats.getDegradedCallCount());
    }

    @Test
    @DisplayName("延迟统计：应正确计算 P50/P90/P99")
    void getLatencyStats_shouldCalculatePercentiles() {
        // 10 个延迟数据：100, 200, 300, ..., 1000
        List<AgentMetricsEntity> metrics = new java.util.ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            metrics.add(buildMetric("session-1", "LATENCY",
                "{\"latencyMs\":" + (i * 100) + ",\"phase\":\"e2e\"}"));
        }
        when(metricsRepository.findByTypeAndTimeRange(eq("LATENCY"), any(), any()))
            .thenReturn(metrics);

        LatencyStats stats = metricsStatsService.getLatencyStats(from, to);

        // 10 items, indices 0-9
        // P50 = index 10*50/100 = 5 → value 600
        // P90 = index min(10*90/100, 9) = min(9, 9) = 9 → value 1000
        // P99 = index min(10*99/100, 9) = min(9, 9) = 9 → value 1000
        assertEquals(600, stats.getP50());
        assertEquals(1000, stats.getP90());
        assertEquals(1000, stats.getP99());
        assertEquals(10, stats.getTotalRequests());
    }

    @Test
    @DisplayName("压缩统计：空数据应返回零值")
    void getCompressionStats_emptyData_shouldReturnZeros() {
        when(metricsRepository.findByTypeAndTimeRange(eq("COMPRESSION"), any(), any()))
            .thenReturn(List.of());

        CompressionStats stats = metricsStatsService.getCompressionStats(from, to);

        assertEquals(0, stats.getCompressionTriggerCount());
        assertEquals(0, stats.getAvgReductionPercent());
    }

    @Test
    @DisplayName("工具调用统计：应按工具分组统计")
    void getToolCallStats_shouldGroupByTool() {
        List<AgentMetricsEntity> metrics = List.of(
            buildMetric("session-1", "TOOL_CALL", "{\"toolName\":\"rag_search\",\"success\":true,\"latencyMs\":450}"),
            buildMetric("session-1", "TOOL_CALL", "{\"toolName\":\"rag_search\",\"success\":true,\"latencyMs\":500}"),
            buildMetric("session-1", "TOOL_CALL", "{\"toolName\":\"wiki_search\",\"success\":false,\"latencyMs\":200}")
        );
        when(metricsRepository.findByTypeAndTimeRange(eq("TOOL_CALL"), any(), any()))
            .thenReturn(metrics);

        ToolCallStats stats = metricsStatsService.getToolCallStats(from, to);

        assertEquals(3, stats.getTotalCalls());
        assertEquals(2, stats.getPerToolSuccessRate().size());
        assertTrue(stats.getPerToolSuccessRate().containsKey("rag_search"));
        assertTrue(stats.getPerToolSuccessRate().containsKey("wiki_search"));
        // rag_search: 2/2 = 100%
        assertEquals(100.0, stats.getPerToolSuccessRate().get("rag_search"));
        // wiki_search: 0/1 = 0%
        assertEquals(0.0, stats.getPerToolSuccessRate().get("wiki_search"));
    }

    @Test
    @DisplayName("综合概览：应包含所有子统计")
    void getOverview_shouldContainAllSubStats() {
        when(metricsRepository.findByTypeAndTimeRange(anyString(), any(), any()))
            .thenReturn(List.of());

        MetricsOverview overview = metricsStatsService.getOverview(from, to);

        assertNotNull(overview.getTokenStats());
        assertNotNull(overview.getLatencyStats());
        assertNotNull(overview.getCompressionStats());
        assertNotNull(overview.getToolCallStats());
    }

    // ==================== 工具方法 ====================

    private AgentMetricsEntity buildMetric(String sessionId, String metricType, String payload) {
        return AgentMetricsEntity.builder()
            .sessionId(sessionId)
            .metricType(metricType)
            .payload(payload)
            .build();
    }
}
