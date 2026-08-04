package nvc.guide.modules.nvcassistant.metrics;

/**
 * 指标类型枚举
 */
public enum MetricType {

    /** Token 消耗（inputTokens, outputTokens, model, degraded） */
    TOKEN,

    /** 端到端延迟（latencyMs, phase） */
    LATENCY,

    /** 上下文压缩效果（beforeTokens, afterTokens, reductionPercent） */
    COMPRESSION,

    /** 工具调用（toolName, success, latencyMs） */
    TOOL_CALL
}
