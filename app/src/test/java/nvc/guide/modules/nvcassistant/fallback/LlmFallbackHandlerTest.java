package nvc.guide.modules.nvcassistant.fallback;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmFallbackHandler 降级处理器测试")
class LlmFallbackHandlerTest {

    private final LlmFallbackHandler handler = new LlmFallbackHandler();

    private LlmCallContext testContext = LlmCallContext.builder()
        .sessionId("test-session")
        .componentName("TestComponent")
        .scene("test")
        .build();

    @Test
    @DisplayName("正常调用成功时直接返回结果")
    void executeWithFallback_successOnFirstAttempt_returnsResult() {
        String result = handler.executeWithFallback(
            () -> "success",
            () -> "fallback",
            testContext);

        assertEquals("success", result);
    }

    @Test
    @DisplayName("第一次失败第二次成功时返回正常结果")
    void executeWithFallback_successOnRetry_returnsResult() {
        int[] callCount = {0};
        String result = handler.executeWithFallback(
            () -> {
                callCount[0]++;
                if (callCount[0] == 1) throw new RuntimeException("timeout");
                return "success";
            },
            () -> "fallback",
            testContext);

        assertEquals("success", result);
        assertEquals(2, callCount[0]);
    }

    @Test
    @DisplayName("三次都失败时触发降级")
    void executeWithFallback_allFailures_triggersFallback() {
        String result = handler.executeWithFallback(
            () -> { throw new RuntimeException("timeout"); },
            () -> "fallback-result",
            testContext);

        assertEquals("fallback-result", result);
    }

    @Test
    @DisplayName("降级也失败时抛出异常")
    void executeWithFallback_fallbackFails_throwsException() {
        assertThrows(RuntimeException.class, () ->
            handler.executeWithFallback(
                () -> { throw new RuntimeException("llm error"); },
                () -> { throw new RuntimeException("fallback error"); },
                testContext));
    }
}
