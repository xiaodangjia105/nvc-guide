package nvc.guide.modules.nvcvoice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.PromptSanitizer;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@DisplayName("NvcVoiceLlmService 语音 LLM 服务")
class NvcVoiceLlmServiceTest {

    private NvcVoiceLlmService service;

    @BeforeEach
    void setUp() {
        LlmProviderRegistry llmProviderRegistry = mock(LlmProviderRegistry.class);
        NvcVoicePromptService promptService = new NvcVoicePromptService(new NvcVoiceProperties());
        NvcVoiceProperties properties = new NvcVoiceProperties();
        properties.setAiStreamPushIntervalMs(100);
        properties.setAiStreamMinCharsDelta(4);
        PromptSanitizer promptSanitizer = mock(PromptSanitizer.class);
        when(promptSanitizer.sanitize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(promptSanitizer.wrapWithDelimiters(anyString(), anyString()))
            .thenAnswer(inv -> "<" + inv.getArgument(0) + ">" + inv.getArgument(1) + "</" + inv.getArgument(0) + ">");
        service = new NvcVoiceLlmService(llmProviderRegistry, promptService, properties, promptSanitizer);
    }

    // ==================== buildUserPrompt ====================

    @Nested
    @DisplayName("buildUserPrompt 用户 Prompt 构建")
    class BuildUserPromptTests {

        @Test
        @DisplayName("无历史时只包含当前输入")
        void buildUserPrompt_noHistory_containsOnlyInput() {
            String result = service.buildUserPrompt("你好", null);
            assertTrue(result.contains("用户："));
            assertTrue(result.contains("你好"));
            assertFalse(result.contains("之前的对话"));
        }

        @Test
        @DisplayName("空历史时只包含当前输入")
        void buildUserPrompt_emptyHistory_containsOnlyInput() {
            String result = service.buildUserPrompt("你好", List.of());
            assertTrue(result.contains("用户："));
            assertFalse(result.contains("之前的对话"));
        }

        @Test
        @DisplayName("有历史时包含历史和当前输入")
        void buildUserPrompt_withHistory_containsBoth() {
            String result = service.buildUserPrompt("第二句", List.of("用户：第一句", "AI：回复"));
            assertTrue(result.contains("之前的对话"));
            assertTrue(result.contains("第一句"));
            assertTrue(result.contains("第二句"));
        }
    }

    // ==================== mapLlmErrorToUserMessage ====================

    @Nested
    @DisplayName("mapLlmErrorToUserMessage 错误映射")
    class MapErrorTests {

        @Test
        @DisplayName("403 错误 → 认证失败")
        void mapError_403_returnsAuthFailed() {
            String result = service.mapLlmErrorToUserMessage(new Exception("403 Forbidden"));
            assertTrue(result.contains("认证失败"));
        }

        @Test
        @DisplayName("ACCESS_DENIED → 认证失败")
        void mapError_accessDenied_returnsAuthFailed() {
            String result = service.mapLlmErrorToUserMessage(new Exception("ACCESS_DENIED"));
            assertTrue(result.contains("认证失败"));
        }

        @Test
        @DisplayName("timeout → 超时")
        void mapError_timeout_returnsTimeout() {
            String result = service.mapLlmErrorToUserMessage(new Exception("connection timeout"));
            assertTrue(result.contains("超时"));
        }

        @Test
        @DisplayName("429 → 频率超限")
        void mapError_429_returnsRateLimit() {
            String result = service.mapLlmErrorToUserMessage(new Exception("429 Too Many Requests"));
            assertTrue(result.contains("频率超限"));
        }

        @Test
        @DisplayName("rate limit → 频率超限")
        void mapError_rateLimit_returnsRateLimit() {
            String result = service.mapLlmErrorToUserMessage(new Exception("rate limit exceeded"));
            assertTrue(result.contains("频率超限"));
        }

        @Test
        @DisplayName("connection → 网络连接")
        void mapError_connection_returnsNetwork() {
            String result = service.mapLlmErrorToUserMessage(new Exception("connection refused"));
            assertTrue(result.contains("网络连接"));
        }

        @Test
        @DisplayName("未知错误 → 通用提示")
        void mapError_unknown_returnsGeneric() {
            String result = service.mapLlmErrorToUserMessage(new Exception("something weird"));
            assertTrue(result.contains("暂时不可用"));
        }

        @Test
        @DisplayName("null message → 通用提示")
        void mapError_nullMessage_returnsGeneric() {
            String result = service.mapLlmErrorToUserMessage(new Exception((String) null));
            assertTrue(result.contains("暂时不可用"));
        }
    }
}
