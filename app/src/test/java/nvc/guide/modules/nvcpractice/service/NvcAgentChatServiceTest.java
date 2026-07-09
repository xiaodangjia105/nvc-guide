package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcAgentChatService 测试")
class NvcAgentChatServiceTest {

  @Mock private LlmProviderRegistry llmProviderRegistry;

  private NvcAgentChatService service;

  @BeforeEach
  void setUp() {
    service = new NvcAgentChatService(llmProviderRegistry);
  }

  private NvcAgentConfigEntity buildConfig() {
    return NvcAgentConfigEntity.builder()
        .id(1L)
        .agentScene(NvcAgentScene.DIALOGUE_GUIDE)
        .displayName("对话引导官")
        .systemPrompt("你是 NVC 对话引导官")
        .modelProvider("mimo")
        .temperature(0.7)
        .maxTokens(2000)
        .topP(0.9)
        .isEnabled(true)
        .build();
  }

  private PracticeContext buildContext() {
    NvcPracticeSessionEntity session = NvcPracticeSessionEntity.builder()
        .id(1L)
        .userId(100L)
        .practiceMode(NvcPracticeMode.FREE_DIALOG)
        .currentPhase(NvcSessionPhase.IN_PROGRESS)
        .build();
    return PracticeContext.builder()
        .session(session)
        .roundCount(2)
        .build();
  }

  @Test
  @DisplayName("chat() 使用 getChatClientOrDefault 获取 ChatClient")
  void chat_usesChatClientOrDefault() {
    NvcAgentConfigEntity config = buildConfig();
    PracticeContext context = buildContext();

    ChatClient mockClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(llmProviderRegistry.getChatClientOrDefault("mimo")).thenReturn(mockClient);
    when(mockClient.prompt().options(any()).messages(anyList()).call().content())
        .thenReturn("AI 回复");

    String result = service.chat(config, context, "你好");

    assertEquals("AI 回复", result);
    verify(llmProviderRegistry).getChatClientOrDefault("mimo");
    verify(llmProviderRegistry, never()).getPlainChatClient(any());
  }

  @Test
  @DisplayName("chatPlain() 使用 getPlainChatClient 获取 ChatClient")
  void chatPlain_usesPlainChatClient() {
    NvcAgentConfigEntity config = buildConfig();

    ChatClient mockClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(llmProviderRegistry.getPlainChatClient("mimo")).thenReturn(mockClient);
    when(mockClient.prompt().options(any()).system(anyString()).user(anyString())
        .call().content()).thenReturn("评估结果");

    String result = service.chatPlain(config, "系统提示词", "用户提示词");

    assertEquals("评估结果", result);
    verify(llmProviderRegistry).getPlainChatClient("mimo");
    verify(llmProviderRegistry, never()).getChatClientOrDefault(any());
  }

  @Test
  @DisplayName("chatStream() 使用 getChatClientOrDefault 获取 ChatClient")
  void chatStream_usesChatClientOrDefault() {
    NvcAgentConfigEntity config = buildConfig();
    PracticeContext context = buildContext();

    ChatClient mockClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    when(llmProviderRegistry.getChatClientOrDefault("mimo")).thenReturn(mockClient);

    // chatStream 返回 Flux，此处验证方法可调用且使用正确的 ChatClient
    assertDoesNotThrow(() -> service.chatStream(config, context, "你好"));
    verify(llmProviderRegistry).getChatClientOrDefault("mimo");
  }
}
