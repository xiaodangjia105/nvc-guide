package nvc.guide.modules.nvcassistant.fallback;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 统一 LLM 降级处理器
 *
 * <p>所有 LLM 调用入口通过此 Handler 包装，异常时自动重试 + 降级。
 *
 * <p>执行流程：
 * <pre>
 * executeWithFallback(llmCall, fallback, context)
 *   ├── 第 1 次调用 llmCall → 成功返回 / 失败等待重试
 *   ├── 第 2 次调用 llmCall → 成功返回 / 失败等待重试
 *   ├── 第 3 次调用 llmCall → 成功返回 / 失败进入降级
 *   └── 降级路径 → 调用 fallback.get() → 标记 degraded=true → 返回
 * </pre>
 */
@Component
@Slf4j
public class LlmFallbackHandler {

    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    /**
     * 执行 LLM 调用，失败时自动重试 + 降级
     *
     * @param llmCall  正常 LLM 调用逻辑
     * @param fallback 降级逻辑
     * @param context  调用上下文
     * @return 正常结果或降级结果
     */
    public <T> T executeWithFallback(
            Supplier<T> llmCall,
            Supplier<T> fallback,
            LlmCallContext context) {

        Exception lastException = null;
        LlmFailureType lastFailureType = LlmFailureType.UNKNOWN;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                T result = llmCall.get();
                if (attempt > 1) {
                    log.info("[Fallback] LLM call succeeded on attempt {}: scene={}, component={}",
                        attempt, context.getScene(), context.getComponentName());
                }
                return result;
            } catch (Exception e) {
                lastException = e;
                lastFailureType = classifyException(e);

                log.warn("[Fallback] LLM call failed on attempt {}/{}: scene={}, type={}, error={}",
                    attempt, MAX_RETRIES, context.getScene(), lastFailureType, e.getMessage());

                // 如果不可重试或已达最大次数，跳出
                if (!lastFailureType.isRetryable() || attempt >= MAX_RETRIES) {
                    break;
                }

                // 等待重试
                try {
                    Thread.sleep(lastFailureType.getRetryDelayMs());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // 进入降级路径
        log.warn("[Fallback] All {} attempts failed, falling back: scene={}, type={}, lastError={}",
            MAX_RETRIES, context.getScene(), lastFailureType,
            lastException != null ? lastException.getMessage() : "unknown");

        try {
            T fallbackResult = fallback.get();
            log.info("[Fallback] Degraded response generated: scene={}", context.getScene());
            return fallbackResult;
        } catch (Exception fallbackException) {
            log.error("[Fallback] Fallback also failed: scene={}", context.getScene(), fallbackException);
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                "LLM 调用失败且降级也失败: " + context.getScene());
        }
    }

    /**
     * 分类异常类型
     */
    private LlmFailureType classifyException(Exception e) {
        String message = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String exceptionType = e.getClass().getSimpleName().toLowerCase();

        if (exceptionType.contains("timeout") || message.contains("timeout")) {
            return LlmFailureType.TIMEOUT;
        }
        if (exceptionType.contains("ratelimit") || message.contains("rate limit")
            || message.contains("429") || message.contains("too many requests")) {
            return LlmFailureType.RATE_LIMITED;
        }
        if (message.contains("invalid") || message.contains("format") || message.contains("json")
            || exceptionType.contains("jsonparse")) {
            return LlmFailureType.INVALID_RESPONSE;
        }
        if (message.contains("500") || message.contains("502") || message.contains("503")
            || exceptionType.contains("servererror") || exceptionType.contains("httpstatus")) {
            return LlmFailureType.PROVIDER_ERROR;
        }

        return LlmFailureType.UNKNOWN;
    }
}
