package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcassistant.fallback.DialogFallbackTemplates;
import nvc.guide.modules.nvcassistant.fallback.LlmFallbackHandler;
import nvc.guide.modules.nvcassistant.metrics.MetricsCollector;
import nvc.guide.modules.nvcassistant.trace.TraceManager;
import nvc.guide.modules.nvcpractice.repository.NvcAgentConfigRepository;
import nvc.guide.modules.nvcpractice.tool.NvcTool;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import nvc.guide.modules.nvcpractice.tool.NvcToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AgentLoop 主循环")
class AgentLoopTest {

    private LlmProviderRegistry llmProviderRegistry;
    private NvcToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;
    private IntentRouter intentRouter;
    private NvcAgentConfigRepository agentConfigRepository;
    private MetricsCollector metricsCollector;
    private TraceManager traceManager;
    private LlmFallbackHandler fallbackHandler;
    private AgentLoop agentLoop;

    @BeforeEach
    void setUp() {
        llmProviderRegistry = mock(LlmProviderRegistry.class);
        toolRegistry = mock(NvcToolRegistry.class);
        toolExecutor = mock(ToolExecutor.class);
        traceManager = mock(TraceManager.class);
        intentRouter = spy(new IntentRouter(traceManager, new ObjectMapper()));
        agentConfigRepository = mock(NvcAgentConfigRepository.class);
        metricsCollector = mock(MetricsCollector.class);
        fallbackHandler = new LlmFallbackHandler();
        DialogFallbackTemplates dialogFallbackTemplates = mock(DialogFallbackTemplates.class);
        agentLoop = new AgentLoop(llmProviderRegistry, toolRegistry, toolExecutor, intentRouter, agentConfigRepository, metricsCollector, traceManager, fallbackHandler, dialogFallbackTemplates);
    }

    private ChatResponse mockTextResponse(String content) {
        AssistantMessage output = new AssistantMessage(content);
        Generation generation = new Generation(output);
        ChatResponse response = mock(ChatResponse.class);
        when(response.getResults()).thenReturn(List.of(generation));
        when(response.getResult()).thenReturn(generation);
        return response;
    }

    private ChatResponse mockToolCallResponse(AssistantMessage.ToolCall... toolCalls) {
        AssistantMessage output = mock(AssistantMessage.class);
        when(output.hasToolCalls()).thenReturn(true);
        when(output.getToolCalls()).thenReturn(List.of(toolCalls));
        Generation generation = new Generation(output);
        ChatResponse response = mock(ChatResponse.class);
        when(response.getResults()).thenReturn(List.of(generation));
        when(response.getResult()).thenReturn(generation);
        return response;
    }

    // ==================== IntentRouter 集成 ====================

    @Nested
    @DisplayName("IntentRouter 预路由")
    class IntentRouterIntegration {

        @Test
        @DisplayName("IntentRouter 命中时直接执行工具，跳过 LLM")
        void executeStream_intentMatch_skipsLlm() {
            IntentRouter.IntentMatch match = new IntentRouter.IntentMatch(
                "profile_update", "{\"field\":\"communicationBackground\",\"value\":\"程序员\"}", "用户描述了个人信息");
            doReturn(match).when(intentRouter).detectIntent(anyString());

            NvcTool tool = mock(NvcTool.class);
            when(toolRegistry.getTool("profile_update")).thenReturn(tool);

            ToolCallResult toolResult = ToolCallResult.success(
                "profile_update", "{}", "档案已更新", 50);
            when(toolExecutor.execute(anyList(), anyLong(), anyLong()))
                .thenReturn(List.of(toolResult));

            List<AgentEvent> events = new CopyOnWriteArrayList<>();
            agentLoop.executeStream(1L, 100L, new ArrayList<>(), "我是程序员，21岁，男")
                .subscribe(events::add);

            // 不应调用 LLM
            verify(llmProviderRegistry, never()).getDefaultChatClient();

            // 应有完整的事件链
            assertTrue(events.stream().anyMatch(e -> e.type() == AgentEvent.AgentEventType.THINKING));
            assertTrue(events.stream().anyMatch(e -> e.type() == AgentEvent.AgentEventType.TOOLCALL_START));
            assertTrue(events.stream().anyMatch(e -> e.type() == AgentEvent.AgentEventType.TOOLCALL_END));
            assertTrue(events.stream().anyMatch(e -> e.type() == AgentEvent.AgentEventType.CONTENT));
            assertTrue(events.stream().anyMatch(e -> e.type() == AgentEvent.AgentEventType.DONE));
        }

        @Test
        @DisplayName("IntentRouter 命中但工具不存在时降级到 LLM")
        void executeStream_intentMatchButToolNotFound_fallsThroughToLlm() {
            IntentRouter.IntentMatch match = new IntentRouter.IntentMatch(
                "profile_update", "{}", "test");
            doReturn(match).when(intentRouter).detectIntent(anyString());
            when(toolRegistry.getTool("profile_update")).thenReturn(null);

            // 降级到 LLM 后需要模拟 LLM 响应，但这里只验证 LLM 被调用
            // 由于 mock LLM 返回 null 会导致 NPE，我们验证调用即可
            when(llmProviderRegistry.getDefaultChatClient()).thenReturn(null);

            List<AgentEvent> events = new CopyOnWriteArrayList<>();
            agentLoop.executeStream(1L, 100L, new ArrayList<>(), "我是程序员")
                .subscribe(events::add);

            // 应降级到 LLM
            verify(llmProviderRegistry).getDefaultChatClient();
            // 由于 LLM mock 返回 null，应该产生错误事件
            assertTrue(events.stream().anyMatch(e -> e.type() == AgentEvent.AgentEventType.ERROR));
        }

        @Test
        @DisplayName("IntentRouter 未命中时走 LLM 循环")
        void executeStream_noIntentMatch_goesToLlm() {
            doReturn(null).when(intentRouter).detectIntent(anyString());
            when(llmProviderRegistry.getDefaultChatClient()).thenReturn(null);

            List<AgentEvent> events = new CopyOnWriteArrayList<>();
            agentLoop.executeStream(1L, 100L, new ArrayList<>(), "什么是NVC？")
                .subscribe(events::add);

            verify(llmProviderRegistry).getDefaultChatClient();
        }
    }
}
