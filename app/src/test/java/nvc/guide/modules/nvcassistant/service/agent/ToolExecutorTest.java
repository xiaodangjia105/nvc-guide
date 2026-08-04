package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcassistant.metrics.MetricsCollector;
import nvc.guide.modules.nvcassistant.trace.TraceManager;
import nvc.guide.modules.nvcpractice.tool.NvcTool;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import nvc.guide.modules.nvcpractice.tool.NvcToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ToolExecutor 工具执行器")
class ToolExecutorTest {

    private NvcToolRegistry toolRegistry;
    private ToolExecutor executor;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MetricsCollector metricsCollector = mock(MetricsCollector.class);
    private final TraceManager traceManager = mock(TraceManager.class);

    @BeforeEach
    void setUp() {
        toolRegistry = mock(NvcToolRegistry.class);
        // 默认不注入 hooks（空列表）
        executor = new ToolExecutor(toolRegistry, List.of(), objectMapper, metricsCollector, traceManager);
    }

    private AssistantMessage.ToolCall toolCall(String name, String args) {
        return new AssistantMessage.ToolCall("call-1", "function", name, args);
    }

    // ==================== 基本执行 ====================

    @Nested
    @DisplayName("基本工具执行")
    class BasicExecution {

        @Test
        @DisplayName("正常执行工具并返回成功结果")
        void execute_singleTool_returnsSuccess() {
            NvcTool tool = mock(NvcTool.class);
            when(toolRegistry.getTool("test_tool")).thenReturn(tool);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("result-data"));

            List<ToolCallResult> results = executor.execute(
                List.of(toolCall("test_tool", "{}")), 1L, 100L);

            assertEquals(1, results.size());
            assertTrue(results.get(0).success());
            assertEquals("result-data", results.get(0).result());
        }

        @Test
        @DisplayName("工具不存在时返回失败")
        void execute_toolNotFound_returnsFailure() {
            when(toolRegistry.getTool("nonexistent")).thenReturn(null);

            List<ToolCallResult> results = executor.execute(
                List.of(toolCall("nonexistent", "{}")), 1L, 100L);

            assertEquals(1, results.size());
            assertFalse(results.get(0).success());
            assertTrue(results.get(0).result().contains("工具不存在"));
        }

        @Test
        @DisplayName("工具执行异常时返回失败")
        void execute_toolThrows_returnsFailure() {
            NvcTool tool = mock(NvcTool.class);
            when(toolRegistry.getTool("failing_tool")).thenReturn(tool);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenThrow(new RuntimeException("boom"));

            List<ToolCallResult> results = executor.execute(
                List.of(toolCall("failing_tool", "{}")), 1L, 100L);

            assertEquals(1, results.size());
            assertFalse(results.get(0).success());
            assertTrue(results.get(0).result().contains("boom"));
        }

        @Test
        @DisplayName("空工具调用列表返回空结果")
        void execute_emptyList_returnsEmpty() {
            List<ToolCallResult> results = executor.execute(List.of(), 1L, 100L);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("null 工具调用列表返回空结果")
        void execute_nullList_returnsEmpty() {
            List<ToolCallResult> results = executor.execute(null, 1L, 100L);
            assertTrue(results.isEmpty());
        }
    }

    // ==================== Hook 链 ====================

    @Nested
    @DisplayName("Hook 链执行")
    class HookChainTests {

        @Test
        @DisplayName("beforeToolCall 返回 PROCEED 时正常执行工具")
        void execute_hookProceed_executesTool() {
            NvcToolHook hook = mock(NvcToolHook.class);
            when(hook.beforeToolCall(anyString(), any(JsonNode.class), any(NvcToolContext.class)))
                .thenReturn(CompletableFuture.completedFuture(NvcToolHook.ToolCallDecision.PROCEED));

            NvcTool tool = mock(NvcTool.class);
            when(toolRegistry.getTool("my_tool")).thenReturn(tool);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("ok"));

            ToolExecutor executorWithHook = new ToolExecutor(toolRegistry, List.of(hook), objectMapper, metricsCollector, traceManager);
            List<ToolCallResult> results = executorWithHook.execute(
                List.of(toolCall("my_tool", "{}")), 1L, 100L);

            assertEquals(1, results.size());
            assertTrue(results.get(0).success());
            verify(tool).execute(anyString(), any(NvcToolContext.class));
        }

        @Test
        @DisplayName("beforeToolCall 返回 SKIP 时跳过工具执行")
        void execute_hookSkip_skipsTool() {
            NvcToolHook hook = mock(NvcToolHook.class);
            when(hook.beforeToolCall(anyString(), any(JsonNode.class), any(NvcToolContext.class)))
                .thenReturn(CompletableFuture.completedFuture(NvcToolHook.ToolCallDecision.SKIP));

            NvcTool tool = mock(NvcTool.class);
            when(toolRegistry.getTool("my_tool")).thenReturn(tool);

            ToolExecutor executorWithHook = new ToolExecutor(toolRegistry, List.of(hook), objectMapper, metricsCollector, traceManager);
            List<ToolCallResult> results = executorWithHook.execute(
                List.of(toolCall("my_tool", "{}")), 1L, 100L);

            assertEquals(1, results.size());
            assertTrue(results.get(0).skipped());
            verify(tool, never()).execute(anyString(), any(NvcToolContext.class));
        }

        @Test
        @DisplayName("Hook 异常不阻断工具执行")
        void execute_hookThrowsContinues_execution() {
            NvcToolHook hook = mock(NvcToolHook.class);
            when(hook.beforeToolCall(anyString(), any(JsonNode.class), any(NvcToolContext.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("hook error")));

            NvcTool tool = mock(NvcTool.class);
            when(toolRegistry.getTool("my_tool")).thenReturn(tool);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("ok"));

            ToolExecutor executorWithHook = new ToolExecutor(toolRegistry, List.of(hook), objectMapper, metricsCollector, traceManager);
            List<ToolCallResult> results = executorWithHook.execute(
                List.of(toolCall("my_tool", "{}")), 1L, 100L);

            assertEquals(1, results.size());
            assertTrue(results.get(0).success());
        }

        @Test
        @DisplayName("afterToolCall 可修改结果")
        void execute_afterHookModifiesResult() {
            NvcToolHook hook = mock(NvcToolHook.class);
            when(hook.beforeToolCall(anyString(), any(JsonNode.class), any(NvcToolContext.class)))
                .thenReturn(CompletableFuture.completedFuture(NvcToolHook.ToolCallDecision.PROCEED));
            when(hook.afterToolCall(anyString(), anyString(), any(NvcToolContext.class)))
                .thenReturn(CompletableFuture.completedFuture("modified-result"));

            NvcTool tool = mock(NvcTool.class);
            when(toolRegistry.getTool("my_tool")).thenReturn(tool);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("original"));

            ToolExecutor executorWithHook = new ToolExecutor(toolRegistry, List.of(hook), objectMapper, metricsCollector, traceManager);
            List<ToolCallResult> results = executorWithHook.execute(
                List.of(toolCall("my_tool", "{}")), 1L, 100L);

            assertEquals(1, results.size());
            assertEquals("modified-result", results.get(0).result());
        }
    }

    // ==================== 参数解析 ====================

    @Nested
    @DisplayName("参数解析")
    class ArgumentParsing {

        @Test
        @DisplayName("空参数字符串可正常执行")
        void execute_emptyArgs_executesSuccessfully() {
            NvcTool tool = mock(NvcTool.class);
            when(toolRegistry.getTool("my_tool")).thenReturn(tool);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("ok"));

            List<ToolCallResult> results = executor.execute(List.of(toolCall("my_tool", "")), 1L, 100L);

            assertEquals(1, results.size());
            assertTrue(results.get(0).success());
            verify(tool).execute(anyString(), any(NvcToolContext.class));
        }

        @Test
        @DisplayName("无效 JSON 参数可正常执行（ToolExecutor 内部容错）")
        void execute_invalidJson_executesSuccessfully() {
            NvcTool tool = mock(NvcTool.class);
            when(toolRegistry.getTool("my_tool")).thenReturn(tool);
            when(tool.execute(anyString(), any(NvcToolContext.class)))
                .thenReturn(NvcToolResult.success("ok"));

            List<ToolCallResult> results = executor.execute(List.of(toolCall("my_tool", "not-json")), 1L, 100L);

            assertEquals(1, results.size());
            assertTrue(results.get(0).success());
            verify(tool).execute(anyString(), any(NvcToolContext.class));
        }
    }
}
