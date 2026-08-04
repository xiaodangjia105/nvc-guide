package nvc.guide.modules.nvcassistant.fallback;

import lombok.Getter;

/**
 * LLM 调用失败类型
 *
 * <p>每种类型有独立的重试延迟和是否可重试标记。
 */
@Getter
public enum LlmFailureType {

    /** 调用超时 */
    TIMEOUT(3000, true),

    /** 限流 */
    RATE_LIMITED(5000, true),

    /** 返回格式异常 */
    INVALID_RESPONSE(0, true),

    /** 供应商错误（5xx） */
    PROVIDER_ERROR(2000, true),

    /** 未知异常 */
    UNKNOWN(1000, false);

    /** 重试延迟（毫秒） */
    private final long retryDelayMs;

    /** 是否可重试 */
    private final boolean retryable;

    LlmFailureType(long retryDelayMs, boolean retryable) {
        this.retryDelayMs = retryDelayMs;
        this.retryable = retryable;
    }
}
