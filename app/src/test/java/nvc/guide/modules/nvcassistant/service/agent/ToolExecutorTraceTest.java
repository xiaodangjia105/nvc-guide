package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcassistant.metrics.MetricsCollector;
import nvc.guide.modules.nvcassistant.trace.*;
import nvc.guide.modules.nvcpractice.tool.NvcTool;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import nvc.guide.modules.nvcpractice.tool.NvcToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ToolExecutor Trace 埋点")
class ToolExecutorTraceTest {

    private NvcToolRegistry toolRegistry;
    private List<NvcToolHook> hooks;
    private ObjectMapper objectMapper;
    private MetricsCollector metricsCollector;
    private TraceManager traceManager;
    private TraceProperties traceProperties;
    private PayloadTruncator payloadTruncator;
    private ToolExecutor toolExecutor;

    @BeforeEach
    void setUp() {
        toolRegistry = mock(NvcToolRegistry.class);
        hooks = List.of();
        objectMapper = new ObjectMapper();
        metricsCollector = mock(MetricsCollector.class);
        traceManager = mock(TraceManager.class);
        traceProperties = new TraceProperties();
        payloadTruncator = new PayloadTruncator(objectMapper);

        // 默认配置为 DETAILED 级别
        TraceProperties.SpanConfig spanConfig = new TraceProperties.SpanConfig();
        spanConfig.setLevel("DETAILED");
        spanConfig.setHookDetailEnabled(true);
        spanConfig.setPayloadMaxLength(4096);
        traceProperties.getSpans().put("TOOL_CALL", spanConfig);

        toolExecutor = new ToolExecutor(toolRegistry, hooks, objectMapper, metricsCollector,
            traceManager, traceProperties, payloadTruncator);
    }

    private AssistantMessage.ToolCall createToolCall(String id, String name, String arguments) {
        return new AssistantMessage.ToolCall(id, "function", name, arguments);
    }

    @Nested
    @DisplayName("TOOL_CALL Trace 埋点")
    class ToolCallTrace {

        @Test
        @DisplayName("应该记录工具名和参数到 inputPayload")
        void shouldRecordToolNameAndArgumentsToInputPayload() {
            // 准备
            NvcTool tool = mock(NvcTool.class);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("更新成功"));
            when(toolRegistry.getTool("profile_update")).thenReturn(tool);

            AgentSpanEntity span = AgentSpanEntity.builder()
                .spanId("test-span")
                .spanType("TOOL_CALL")
                .componentName("ToolExecutor")
                .build();
            when(traceManager.startSpan("TOOL_CALL", "ToolExecutor")).thenReturn(span);

            AssistantMessage.ToolCall toolCall = createToolCall("call-1", "profile_update",
                "{\"field\":\"occupation\",\"value\":\"程序员\"}");

            // 执行
            List<ToolCallResult> results = toolExecutor.execute(List.of(toolCall), 1L, 100L);

            // 验证
            assertFalse(results.isEmpty());
            assertTrue(results.get(0).success());

            // 验证 span 的 inputPayload 包含工具名和参数
            ArgumentCaptor<AgentSpanEntity> spanCaptor = ArgumentCaptor.forClass(AgentSpanEntity.class);
            verify(traceManager).endSpan(spanCaptor.capture(), eq("SUCCESS"), isNull());

            AgentSpanEntity capturedSpan = spanCaptor.getValue();
            assertNotNull(capturedSpan.getInputPayload());
            assertTrue(capturedSpan.getInputPayload().contains("profile_update"));
            assertTrue(capturedSpan.getInputPayload().contains("occupation"));
        }

        @Test
        @DisplayName("应该记录执行结果到 outputPayload")
        void shouldRecordResultToOutputPayload() {
            // 准备
            NvcTool tool = mock(NvcTool.class);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("{\"updated\":true}"));
            when(toolRegistry.getTool("profile_update")).thenReturn(tool);

            AgentSpanEntity span = AgentSpanEntity.builder()
                .spanId("test-span")
                .spanType("TOOL_CALL")
                .componentName("ToolExecutor")
                .build();
            when(traceManager.startSpan("TOOL_CALL", "ToolExecutor")).thenReturn(span);

            AssistantMessage.ToolCall toolCall = createToolCall("call-1", "profile_update", "{}");

            // 执行
            toolExecutor.execute(List.of(toolCall), 1L, 100L);

            // 验证
            ArgumentCaptor<AgentSpanEntity> spanCaptor = ArgumentCaptor.forClass(AgentSpanEntity.class);
            verify(traceManager).endSpan(spanCaptor.capture(), eq("SUCCESS"), isNull());

            AgentSpanEntity capturedSpan = spanCaptor.getValue();
            assertNotNull(capturedSpan.getOutputPayload());
            assertTrue(capturedSpan.getOutputPayload().contains("updated"));
        }

        @Test
        @DisplayName("应该记录 metadata（hookCount、skipped 等）")
        void shouldRecordMetadata() {
            // 准备
            NvcTool tool = mock(NvcTool.class);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("成功"));
            when(toolRegistry.getTool("test_tool")).thenReturn(tool);

            AgentSpanEntity span = AgentSpanEntity.builder()
                .spanId("test-span")
                .spanType("TOOL_CALL")
                .componentName("ToolExecutor")
                .build();
            when(traceManager.startSpan("TOOL_CALL", "ToolExecutor")).thenReturn(span);

            AssistantMessage.ToolCall toolCall = createToolCall("call-1", "test_tool", "{}");

            // 执行
            toolExecutor.execute(List.of(toolCall), 1L, 100L);

            // 验证
            ArgumentCaptor<AgentSpanEntity> spanCaptor = ArgumentCaptor.forClass(AgentSpanEntity.class);
            verify(traceManager).endSpan(spanCaptor.capture(), eq("SUCCESS"), isNull());

            AgentSpanEntity capturedSpan = spanCaptor.getValue();
            assertNotNull(capturedSpan.getMetadata());
            assertTrue(capturedSpan.getMetadata().contains("hookCount"));
            assertTrue(capturedSpan.getMetadata().contains("skipped"));
        }

        @Test
        @DisplayName("失败时应该设置 FAILED 状态和失败原因")
        void shouldSetFailedStatusOnFailure() {
            // 准备
            NvcTool tool = mock(NvcTool.class);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.failure("工具执行失败"));
            when(toolRegistry.getTool("test_tool")).thenReturn(tool);

            AgentSpanEntity span = AgentSpanEntity.builder()
                .spanId("test-span")
                .spanType("TOOL_CALL")
                .componentName("ToolExecutor")
                .build();
            when(traceManager.startSpan("TOOL_CALL", "ToolExecutor")).thenReturn(span);

            AssistantMessage.ToolCall toolCall = createToolCall("call-1", "test_tool", "{}");

            // 执行
            toolExecutor.execute(List.of(toolCall), 1L, 100L);

            // 验证
            ArgumentCaptor<AgentSpanEntity> spanCaptor = ArgumentCaptor.forClass(AgentSpanEntity.class);
            ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
            verify(traceManager).endSpan(spanCaptor.capture(), eq("FAILED"), reasonCaptor.capture());

            AgentSpanEntity capturedSpan = spanCaptor.getValue();
            assertNotNull(capturedSpan.getOutputPayload());
            assertTrue(capturedSpan.getOutputPayload().contains("工具执行失败"));
        }

        @Test
        @DisplayName("BASIC 级别应该只记录工具名")
        void basicLevelShouldOnlyRecordToolName() {
            // 设置为 BASIC 级别
            TraceProperties.SpanConfig basicConfig = new TraceProperties.SpanConfig();
            basicConfig.setLevel("BASIC");
            traceProperties.getSpans().put("TOOL_CALL", basicConfig);

            // 准备
            NvcTool tool = mock(NvcTool.class);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("成功"));
            when(toolRegistry.getTool("test_tool")).thenReturn(tool);

            AgentSpanEntity span = AgentSpanEntity.builder()
                .spanId("test-span")
                .spanType("TOOL_CALL")
                .componentName("ToolExecutor")
                .build();
            when(traceManager.startSpan("TOOL_CALL", "ToolExecutor")).thenReturn(span);

            AssistantMessage.ToolCall toolCall = createToolCall("call-1", "test_tool",
                "{\"param\":\"value\"}");

            // 执行
            toolExecutor.execute(List.of(toolCall), 1L, 100L);

            // 验证 - 只记录工具名，不记录参数
            ArgumentCaptor<AgentSpanEntity> spanCaptor = ArgumentCaptor.forClass(AgentSpanEntity.class);
            verify(traceManager).endSpan(spanCaptor.capture(), eq("SUCCESS"), isNull());

            AgentSpanEntity capturedSpan = spanCaptor.getValue();
            assertNotNull(capturedSpan.getInputPayload());
            assertTrue(capturedSpan.getInputPayload().contains("test_tool"));
            // BASIC 级别不记录详细参数
            assertNull(capturedSpan.getOutputPayload());
            assertNull(capturedSpan.getMetadata());
        }
    }

    @Nested
    @DisplayName("Hook 链追踪")
    class HookChainTrace {

        @Test
        @DisplayName("应该记录 Hook 执行链路")
        void shouldRecordHookChain() {
            // 准备 Hook
            NvcToolHook hook1 = mock(NvcToolHook.class);
            when(hook1.beforeToolCall(anyString(), any(), any(NvcToolContext.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(NvcToolHook.ToolCallDecision.PROCEED));

            NvcToolHook hook2 = mock(NvcToolHook.class);
            when(hook2.beforeToolCall(anyString(), any(), any(NvcToolContext.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(NvcToolHook.ToolCallDecision.PROCEED));
            when(hook2.afterToolCall(anyString(), anyString(), any(NvcToolContext.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture("modified result"));

            // 重新创建 toolExecutor 带 Hook
            toolExecutor = new ToolExecutor(toolRegistry, List.of(hook1, hook2), objectMapper,
                metricsCollector, traceManager, traceProperties, payloadTruncator);

            NvcTool tool = mock(NvcTool.class);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("原始结果"));
            when(toolRegistry.getTool("test_tool")).thenReturn(tool);

            AgentSpanEntity span = AgentSpanEntity.builder()
                .spanId("test-span")
                .spanType("TOOL_CALL")
                .componentName("ToolExecutor")
                .build();
            when(traceManager.startSpan("TOOL_CALL", "ToolExecutor")).thenReturn(span);

            AssistantMessage.ToolCall toolCall = createToolCall("call-1", "test_tool", "{}");

            // 执行
            List<ToolCallResult> results = toolExecutor.execute(List.of(toolCall), 1L, 100L);

            // 验证
            assertFalse(results.isEmpty());
            ToolCallResult result = results.get(0);
            assertNotNull(result.hookRecords());
            // 至少有 2 个 before hook 和 1 个 after hook
            assertTrue(result.hookRecords().size() >= 3, "Expected at least 3 hook records, got: " + result.hookRecords().size());

            // 验证 before hook 记录
            long beforeCount = result.hookRecords().stream()
                .filter(h -> "before".equals(h.get("phase")))
                .count();
            assertEquals(2, beforeCount, "Expected 2 before hooks");

            // 验证 after hook 记录
            long afterCount = result.hookRecords().stream()
                .filter(h -> "after".equals(h.get("phase")))
                .count();
            assertTrue(afterCount >= 1, "Expected at least 1 after hook");

            // 验证有 MODIFIED 决策
            boolean hasModified = result.hookRecords().stream()
                .anyMatch(h -> "MODIFIED".equals(h.get("decision")));
            assertTrue(hasModified, "Expected MODIFIED decision in hook records");
        }

        @Test
        @DisplayName("Hook 跳过时应该记录 SKIP 决策")
        void shouldRecordSkipDecisionWhenHookSkips() {
            // 准备 Hook - 跳过
            NvcToolHook hook = mock(NvcToolHook.class);
            when(hook.beforeToolCall(anyString(), any(), any(NvcToolContext.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(NvcToolHook.ToolCallDecision.SKIP));

            toolExecutor = new ToolExecutor(toolRegistry, List.of(hook), objectMapper,
                metricsCollector, traceManager, traceProperties, payloadTruncator);

            AgentSpanEntity span = AgentSpanEntity.builder()
                .spanId("test-span")
                .spanType("TOOL_CALL")
                .componentName("ToolExecutor")
                .build();
            when(traceManager.startSpan("TOOL_CALL", "ToolExecutor")).thenReturn(span);

            AssistantMessage.ToolCall toolCall = createToolCall("call-1", "test_tool", "{}");

            // 执行
            List<ToolCallResult> results = toolExecutor.execute(List.of(toolCall), 1L, 100L);

            // 验证
            assertFalse(results.isEmpty());
            ToolCallResult result = results.get(0);
            assertTrue(result.skipped());
            assertNotNull(result.hookRecords());
            assertFalse(result.hookRecords().isEmpty());

            Map<String, Object> hookRecord = result.hookRecords().get(0);
            assertEquals("SKIP", hookRecord.get("decision"));
        }
    }
}
