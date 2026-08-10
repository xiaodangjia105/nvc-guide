package nvc.guide.modules.nvcassistant.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TraceManager 链路追踪管理器")
class TraceManagerTest {

    private TraceStreamProducer traceStreamProducer;
    private TraceSampler traceSampler;
    private TraceManager traceManager;

    @BeforeEach
    void setUp() {
        traceStreamProducer = mock(TraceStreamProducer.class);
        traceSampler = mock(TraceSampler.class);
        // 默认全部采样，避免影响现有测试
        when(traceSampler.shouldSample(anyString(), anyString())).thenReturn(true);
        traceManager = new TraceManager(traceStreamProducer, traceSampler);
    }

    @Nested
    @DisplayName("startTrace - 开启新 Trace")
    class StartTrace {

        @Test
        @DisplayName("应该创建新的 Trace 实体并设置 ThreadLocal")
        void shouldCreateTraceAndSetThreadLocal() {
            AgentTraceEntity trace = traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");

            assertNotNull(trace);
            assertNotNull(trace.getTraceId());
            assertEquals("session-1", trace.getSessionId());
            assertEquals("user-1", trace.getUserId());
            assertEquals("FREE_DIALOG", trace.getMode());
            assertEquals("USER_MESSAGE", trace.getTriggerType());
            assertEquals("SUCCESS", trace.getFinalStatus());

            // 验证 ThreadLocal 已设置
            assertNotNull(traceManager.current());
        }

        @Test
        @DisplayName("采样器返回 false 时应跳过 Trace 上下文设置")
        void shouldSkipContextWhenSamplerRejects() {
            when(traceSampler.shouldSample("user-1", "session-1")).thenReturn(false);

            AgentTraceEntity trace = traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");

            // 实体仍然返回（API 不变）
            assertNotNull(trace);
            assertNotNull(trace.getTraceId());

            // ThreadLocal 未设置
            assertNull(traceManager.current());
        }

        @Test
        @DisplayName("采样器异常时应默认采样（不中断主流程）")
        void shouldDefaultToSampleOnSamplerFailure() {
            when(traceSampler.shouldSample(anyString(), anyString()))
                .thenThrow(new RuntimeException("Redis unavailable"));

            AgentTraceEntity trace = traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");

            assertNotNull(trace);
            // ThreadLocal 仍然设置
            assertNotNull(traceManager.current());
        }
    }

    @Nested
    @DisplayName("startSpan - 创建子 Span")
    class StartSpan {

        @Test
        @DisplayName("应该创建 Span 并关联到当前 Trace")
        void shouldCreateSpanAndAssociateWithTrace() {
            // 先创建 Trace
            AgentTraceEntity trace = traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");

            // 创建 Span
            AgentSpanEntity span = traceManager.startSpan("LLM_CALL", "AgentLoop");

            assertNotNull(span);
            assertNotNull(span.getSpanId());
            assertEquals("LLM_CALL", span.getSpanType());
            assertEquals("AgentLoop", span.getComponentName());
            assertEquals(1, span.getSequence());
            assertEquals(trace, span.getTrace());
        }

        @Test
        @DisplayName("没有活跃 Trace 时应返回临时 Span（不持久化）")
        void shouldReturnTemporarySpanWhenNoActiveTrace() {
            // 清理 ThreadLocal
            traceManager.cleanup();

            AgentSpanEntity span = traceManager.startSpan("TOOL_CALL", "ToolExecutor");

            assertNotNull(span);
            assertNotNull(span.getSpanId());
            assertEquals("TOOL_CALL", span.getSpanType());
            assertNull(span.getTrace()); // 不关联 Trace
        }

        @Test
        @DisplayName("应该支持创建 EVALUATION 类型的 Span")
        void shouldSupportEvaluationSpanType() {
            traceManager.startTrace("session-1", "user-1", "STRUCTURED");

            AgentSpanEntity span = traceManager.startSpan("EVALUATION", "NvcEvaluationService");

            assertNotNull(span);
            assertEquals("EVALUATION", span.getSpanType());
            assertEquals("NvcEvaluationService", span.getComponentName());
        }

        @Test
        @DisplayName("应该支持创建 FALLBACK 类型的 Span")
        void shouldSupportFallbackSpanType() {
            traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");

            AgentSpanEntity span = traceManager.startSpan("FALLBACK", "LlmFallbackHandler");

            assertNotNull(span);
            assertEquals("FALLBACK", span.getSpanType());
            assertEquals("LlmFallbackHandler", span.getComponentName());
        }

        @Test
        @DisplayName("应该按顺序递增 sequence")
        void shouldIncrementSequence() {
            traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");

            AgentSpanEntity span1 = traceManager.startSpan("INTENT_ROUTING", "IntentRouter");
            AgentSpanEntity span2 = traceManager.startSpan("LLM_CALL", "AgentLoop");
            AgentSpanEntity span3 = traceManager.startSpan("TOOL_CALL", "ToolExecutor");

            assertEquals(1, span1.getSequence());
            assertEquals(2, span2.getSequence());
            assertEquals(3, span3.getSequence());
        }
    }

    @Nested
    @DisplayName("endSpan - 完成 Span")
    class EndSpan {

        @Test
        @DisplayName("应该设置 Span 状态和失败原因")
        void shouldSetStatusAndFailureReason() {
            traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");
            AgentSpanEntity span = traceManager.startSpan("LLM_CALL", "AgentLoop");

            traceManager.endSpan(span, "FAILED", "Connection timeout");

            assertEquals("FAILED", span.getStatus());
            assertEquals("Connection timeout", span.getFailureReason());
        }

        @Test
        @DisplayName("失败状态应更新 Trace 的 finalStatus")
        void failedStatusShouldUpdateTraceFinalStatus() {
            AgentTraceEntity trace = traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");
            AgentSpanEntity span = traceManager.startSpan("LLM_CALL", "AgentLoop");

            traceManager.endSpan(span, "FAILED", "Error");

            assertEquals("FAILED", trace.getFinalStatus());
        }

        @Test
        @DisplayName("DEGRADED 状态不应覆盖 FAILED 状态")
        void degradedShouldNotOverrideFailed() {
            AgentTraceEntity trace = traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");

            AgentSpanEntity span1 = traceManager.startSpan("LLM_CALL", "AgentLoop");
            traceManager.endSpan(span1, "FAILED", "Error");

            AgentSpanEntity span2 = traceManager.startSpan("FALLBACK", "LlmFallbackHandler");
            traceManager.endSpan(span2, "DEGRADED", null);

            assertEquals("FAILED", trace.getFinalStatus());
        }

        @Test
        @DisplayName("应该截断过长的失败原因")
        void shouldTruncateLongFailureReason() {
            traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");
            AgentSpanEntity span = traceManager.startSpan("LLM_CALL", "AgentLoop");

            String longReason = "A".repeat(5000);
            traceManager.endSpan(span, "FAILED", longReason);

            assertTrue(span.getFailureReason().length() <= 4096 + 3); // +3 for "..."
            assertTrue(span.getFailureReason().endsWith("..."));
        }
    }

    @Nested
    @DisplayName("endTrace - 完成 Trace")
    class EndTrace {

        @Test
        @DisplayName("应该汇总统计并异步落库")
        void shouldSummarizeAndPersistAsync() {
            AgentTraceEntity trace = traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");

            // 添加多个 Span
            AgentSpanEntity span1 = traceManager.startSpan("LLM_CALL", "AgentLoop");
            span1.setInputTokens(100);
            span1.setOutputTokens(50);
            span1.setDurationMs(200L);
            traceManager.endSpan(span1, "SUCCESS", null);

            AgentSpanEntity span2 = traceManager.startSpan("TOOL_CALL", "ToolExecutor");
            span2.setDurationMs(100L);
            traceManager.endSpan(span2, "SUCCESS", null);

            // 完成 Trace
            traceManager.endTrace(trace);

            // 验证汇总统计
            assertEquals(2, trace.getTotalSpans());
            assertEquals(300L, trace.getTotalDurationMs());
            assertEquals(100, trace.getTotalInputTokens());
            assertEquals(50, trace.getTotalOutputTokens());

            // 验证异步落库
            verify(traceStreamProducer).sendTrace(eq(trace), anyList());
        }

        @Test
        @DisplayName("应该清理 ThreadLocal")
        void shouldCleanupThreadLocal() {
            AgentTraceEntity trace = traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");
            traceManager.endTrace(trace);

            assertNull(traceManager.current());
        }
    }

    @Nested
    @DisplayName("cleanup - 清理资源")
    class Cleanup {

        @Test
        @DisplayName("应该清理 ThreadLocal")
        void shouldCleanupThreadLocal() {
            traceManager.startTrace("session-1", "user-1", "FREE_DIALOG");
            assertNotNull(traceManager.current());

            traceManager.cleanup();
            assertNull(traceManager.current());
        }
    }
}
