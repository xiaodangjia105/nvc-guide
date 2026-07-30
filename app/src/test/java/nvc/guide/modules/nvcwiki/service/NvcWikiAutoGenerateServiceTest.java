package nvc.guide.modules.nvcwiki.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.StructuredOutputInvoker;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcwiki.dto.WikiCreateRequest;
import nvc.guide.modules.nvcwiki.dto.WikiResponse;
import nvc.guide.modules.nvcwiki.model.NvcWikiCategory;
import nvc.guide.modules.nvcwiki.model.NvcWikiSourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcWikiAutoGenerateService 测试")
class NvcWikiAutoGenerateServiceTest {

    @Mock
    private NvcWikiService wikiService;
    @Mock
    private StructuredOutputInvoker structuredOutputInvoker;
    @Mock
    private LlmProviderRegistry llmProviderRegistry;
    @Mock
    private ChatClient chatClient;

    private NvcWikiAutoGenerateService service;

    @BeforeEach
    void setUp() {
        service = new NvcWikiAutoGenerateService(wikiService, structuredOutputInvoker, llmProviderRegistry);
    }

    private NvcPracticeMessageEntity buildMessage(Long id, NvcMessageRole role, String content) {
        return NvcPracticeMessageEntity.builder()
                .id(id)
                .sessionId(100L)
                .role(role)
                .content(content)
                .sequenceNum(1)
                .build();
    }

    @Nested
    @DisplayName("generateFromSession()")
    class GenerateFromSessionTests {

        @Test
        @DisplayName("LLM 成功生成笔记")
        void generatesWikiFromLlm() {
            List<NvcPracticeMessageEntity> messages = List.of(
                    buildMessage(1L, NvcMessageRole.USER, "我观察到你迟到了"),
                    buildMessage(2L, NvcMessageRole.ASSISTANT, "你感到失望对吗？")
            );

            NvcWikiAutoGenerateService.WikiGenerateResult llmResult =
                    new NvcWikiAutoGenerateService.WikiGenerateResult(
                            "NVC 练习笔记",
                            "## 观察\n\n用户使用了观察性语言",
                            List.of("观察", "练习")
                    );

            when(llmProviderRegistry.getDefaultChatClient()).thenReturn(chatClient);
            when(structuredOutputInvoker.invoke(
                    eq(chatClient), anyString(), anyString(),
                    any(BeanOutputConverter.class), any(), anyString(), anyString(), any()
            )).thenReturn(llmResult);

            WikiResponse expectedResponse = new WikiResponse(
                    1L, "NVC 练习笔记", NvcWikiCategory.CONVERSATION_CASE,
                    NvcWikiSourceType.AUTO_GENERATED, "## 观察\n\n用户使用了观察性语言",
                    List.of("观察", "练习"), 100L,
                    LocalDateTime.now(), LocalDateTime.now()
            );
            when(wikiService.createWiki(eq(1L), any(WikiCreateRequest.class)))
                    .thenReturn(expectedResponse);

            WikiResponse result = service.generateFromSession(1L, 100L, messages);

            assertNotNull(result);
            assertEquals("NVC 练习笔记", result.title());

            // 验证传给 wikiService 的请求参数
            ArgumentCaptor<WikiCreateRequest> captor = ArgumentCaptor.forClass(WikiCreateRequest.class);
            verify(wikiService).createWiki(eq(1L), captor.capture());
            WikiCreateRequest captured = captor.getValue();
            assertEquals("NVC 练习笔记", captured.title());
            assertEquals(NvcWikiCategory.CONVERSATION_CASE, captured.category());
            assertEquals(NvcWikiSourceType.AUTO_GENERATED, captured.sourceType());
            assertEquals(100L, captured.sessionId());
        }

        @Test
        @DisplayName("LLM 返回 null 时使用降级结果")
        void usesFallbackWhenLlmReturnsNull() {
            List<NvcPracticeMessageEntity> messages = List.of(
                    buildMessage(1L, NvcMessageRole.USER, "我感到生气"),
                    buildMessage(2L, NvcMessageRole.ASSISTANT, "你的需求没有被满足？")
            );

            when(llmProviderRegistry.getDefaultChatClient()).thenReturn(chatClient);
            when(structuredOutputInvoker.invoke(
                    eq(chatClient), anyString(), anyString(),
                    any(BeanOutputConverter.class), any(), anyString(), anyString(), any()
            )).thenReturn(null);

            WikiResponse expectedResponse = new WikiResponse(
                    1L, "练习笔记", NvcWikiCategory.CONVERSATION_CASE,
                    NvcWikiSourceType.AUTO_GENERATED, "## 练习摘要\n\n我感到生气",
                    List.of("练习笔记"), 100L,
                    LocalDateTime.now(), LocalDateTime.now()
            );
            when(wikiService.createWiki(eq(1L), any(WikiCreateRequest.class)))
                    .thenReturn(expectedResponse);

            WikiResponse result = service.generateFromSession(1L, 100L, messages);

            assertNotNull(result);
            ArgumentCaptor<WikiCreateRequest> captor = ArgumentCaptor.forClass(WikiCreateRequest.class);
            verify(wikiService).createWiki(eq(1L), captor.capture());
            assertEquals("练习笔记", captor.getValue().title());
        }

        @Test
        @DisplayName("LLM 调用异常时降级生成简化笔记")
        void fallsBackWhenLlmThrowsException() {
            List<NvcPracticeMessageEntity> messages = List.of(
                    buildMessage(1L, NvcMessageRole.USER, "观察内容"),
                    buildMessage(2L, NvcMessageRole.ASSISTANT, "感受内容")
            );

            when(llmProviderRegistry.getDefaultChatClient()).thenReturn(chatClient);
            when(structuredOutputInvoker.invoke(
                    eq(chatClient), anyString(), anyString(),
                    any(BeanOutputConverter.class), any(), anyString(), anyString(), any()
            )).thenThrow(new RuntimeException("LLM 服务不可用"));

            WikiResponse fallbackResponse = new WikiResponse(
                    1L, "练习笔记 - 100", NvcWikiCategory.CONVERSATION_CASE,
                    NvcWikiSourceType.AUTO_GENERATED, "## 练习对话摘要\n\n观察内容 感受内容",
                    List.of("练习笔记", "自动降级"), 100L,
                    LocalDateTime.now(), LocalDateTime.now()
            );
            when(wikiService.createWiki(eq(1L), any(WikiCreateRequest.class)))
                    .thenReturn(fallbackResponse);

            WikiResponse result = service.generateFromSession(1L, 100L, messages);

            assertNotNull(result);
            assertEquals("练习笔记 - 100", result.title());
            verify(wikiService).createWiki(eq(1L), any(WikiCreateRequest.class));
        }

        @Test
        @DisplayName("LLM 和降级都失败时抛出异常")
        void throwsWhenBothLlmAndFallbackFail() {
            List<NvcPracticeMessageEntity> messages = List.of(
                    buildMessage(1L, NvcMessageRole.USER, "content")
            );

            when(llmProviderRegistry.getDefaultChatClient()).thenReturn(chatClient);
            when(structuredOutputInvoker.invoke(
                    eq(chatClient), anyString(), anyString(),
                    any(BeanOutputConverter.class), any(), anyString(), anyString(), any()
            )).thenThrow(new RuntimeException("LLM 服务不可用"));

            // wikiService.createWiki 也抛异常，降级也会失败
            when(wikiService.createWiki(anyLong(), any(WikiCreateRequest.class)))
                    .thenThrow(new RuntimeException("Wiki 服务不可用"));

            assertThrows(RuntimeException.class,
                    () -> service.generateFromSession(1L, 100L, messages));
        }
    }
}
