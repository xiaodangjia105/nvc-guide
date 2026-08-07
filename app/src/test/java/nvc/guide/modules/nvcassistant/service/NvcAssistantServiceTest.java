package nvc.guide.modules.nvcassistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcassistant.dto.AssistantRequest;
import nvc.guide.modules.nvcassistant.model.NvcAssistantConversationEntity;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import nvc.guide.modules.nvcassistant.service.agent.AgentEvent;
import nvc.guide.modules.nvcassistant.service.agent.AgentLoop;
import nvc.guide.modules.nvcassistant.service.agent.ContextManager;
import nvc.guide.modules.nvcassistant.service.agent.PromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("NvcAssistantService 主 Agent 对话服务")
class NvcAssistantServiceTest {

    private NvcAssistantMessageService messageService;
    private AgentLoop agentLoop;
    private ContextManager contextManager;
    private PromptBuilder promptBuilder;
    private ObjectMapper objectMapper;
    private NvcAssistantService service;

    @BeforeEach
    void setUp() {
        messageService = mock(NvcAssistantMessageService.class);
        agentLoop = mock(AgentLoop.class);
        contextManager = mock(ContextManager.class);
        promptBuilder = mock(PromptBuilder.class);
        objectMapper = new ObjectMapper();
        service = new NvcAssistantService(messageService, agentLoop, contextManager, promptBuilder, objectMapper);
    }

    private AssistantRequest buildRequest(String message, Long conversationId) {
        AssistantRequest request = new AssistantRequest();
        request.setMessage(message);
        request.setConversationId(conversationId);
        return request;
    }

    private void setupConversation(long convId, Long userId, String message, int existingMsgCount) {
        NvcAssistantConversationEntity conversation = new NvcAssistantConversationEntity();
        conversation.setId(convId);

        if (existingMsgCount == 0) {
            when(messageService.createConversation(userId)).thenReturn(conversation);
        } else {
            when(messageService.getConversationOrThrow(convId, userId)).thenReturn(conversation);
        }
        when(messageService.getNextSequenceNum(convId)).thenReturn(existingMsgCount);
        when(messageService.buildUserMessage(eq(convId), eq(userId), eq(message), eq(existingMsgCount)))
            .thenReturn(NvcAssistantMessageEntity.builder().build());

        ContextManager.ContextResult contextResult = new ContextManager.ContextResult(List.of(), null);
        when(contextManager.buildContext(convId, userId)).thenReturn(contextResult);
        when(promptBuilder.buildSystemPrompt(eq(userId), isNull())).thenReturn("系统提示词");
    }

    // ==================== chatStreamRaw ====================

    @Nested
    @DisplayName("chatStreamRaw 流式对话")
    class ChatStreamRawTests {

        @Test
        @DisplayName("新对话：创建对话 + 保存用户消息 + 返回事件流")
        void chatStreamRaw_newConversation_createsAndStreams() {
            setupConversation(100L, 1L, "你好", 0);

            when(agentLoop.executeStream(eq(1L), eq(100L), anyList(), eq("你好")))
                .thenReturn(Flux.just(
                    AgentEvent.thinking("思考中..."),
                    AgentEvent.content("你好！有什么可以帮你的吗？"),
                    AgentEvent.done(100L)
                ));

            NvcAssistantService.ChatStreamResult result = service.chatStreamRaw(1L, buildRequest("你好", null));

            assertEquals(100L, result.conversationId());
            verify(messageService).saveMessage(any(NvcAssistantMessageEntity.class));

            // 收集事件验证 - 使用 collectList().block() 确保 Flux 完成
            List<AgentEvent> events = result.eventStream().collectList().block();

            assertNotNull(events);
            assertEquals(3, events.size());
            assertEquals(AgentEvent.AgentEventType.THINKING, events.get(0).type());
            assertEquals(AgentEvent.AgentEventType.CONTENT, events.get(1).type());
            assertEquals(AgentEvent.AgentEventType.DONE, events.get(2).type());
        }

        @Test
        @DisplayName("已有对话：复用对话 ID")
        void chatStreamRaw_existingConversation_reusesId() {
            setupConversation(50L, 1L, "继续聊", 3);

            when(agentLoop.executeStream(eq(1L), eq(50L), anyList(), eq("继续聊")))
                .thenReturn(Flux.just(AgentEvent.done(50L)));

            NvcAssistantService.ChatStreamResult result = service.chatStreamRaw(1L, buildRequest("继续聊", 50L));

            assertEquals(50L, result.conversationId());
            verify(messageService, never()).createConversation(anyLong());
        }

        @Test
        @DisplayName("第一轮对话自动生成标题")
        void chatStreamRaw_firstRound_generatesTitle() {
            setupConversation(200L, 1L, "什么是NVC？", 0);

            when(agentLoop.executeStream(eq(1L), eq(200L), anyList(), eq("什么是NVC？")))
                .thenReturn(Flux.just(AgentEvent.done(200L)));

            NvcAssistantService.ChatStreamResult result = service.chatStreamRaw(1L, buildRequest("什么是NVC？", null));

            // 触发流完成 - 使用 collectList().block() 确保 Flux 完成
            List<AgentEvent> events = result.eventStream().collectList().block();
            assertNotNull(events);

            // 验证标题被更新（包含用户消息前50字符）
            verify(messageService).updateConversationTitle(eq(200L), contains("什么是NVC"));
        }

        @Test
        @DisplayName("非第一轮对话不更新标题")
        void chatStreamRaw_notFirstRound_noTitleUpdate() {
            setupConversation(50L, 1L, "继续聊", 3);

            when(agentLoop.executeStream(eq(1L), eq(50L), anyList(), eq("继续聊")))
                .thenReturn(Flux.just(AgentEvent.content("好的"), AgentEvent.done(50L)));

            NvcAssistantService.ChatStreamResult result = service.chatStreamRaw(1L, buildRequest("继续聊", 50L));

            List<AgentEvent> events = result.eventStream().collectList().block();
            assertNotNull(events);

            verify(messageService, never()).updateConversationTitle(anyLong(), anyString());
        }

        @Test
        @DisplayName("长消息标题截取前50字符 + ...")
        void chatStreamRaw_longMessage_truncatesTitle() {
            String longMessage = "这是一段很长的消息".repeat(10); // 90字符
            setupConversation(300L, 1L, longMessage, 0);

            when(agentLoop.executeStream(eq(1L), eq(300L), anyList(), eq(longMessage)))
                .thenReturn(Flux.just(AgentEvent.done(300L)));

            NvcAssistantService.ChatStreamResult result = service.chatStreamRaw(1L, buildRequest(longMessage, null));

            List<AgentEvent> events = result.eventStream().collectList().block();
            assertNotNull(events);

            verify(messageService).updateConversationTitle(eq(300L), argThat(title ->
                title.length() <= 53 && (title.length() <= 50 || title.endsWith("..."))
            ));
        }
    }
}
