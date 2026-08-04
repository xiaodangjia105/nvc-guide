package nvc.guide.modules.nvcassistant.metrics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TokenStats {
    /** 总 Token 消耗 */
    private long totalTokens;
    /** 平均每会话 Token */
    private double avgTokensPerSession;
    /** 平均输入 Token */
    private double avgInputTokens;
    /** 平均输出 Token */
    private double avgOutputTokens;
    /** 降级调用次数 */
    private long degradedCallCount;
    /** 总 LLM 调用次数 */
    private long totalLlmCalls;
}
