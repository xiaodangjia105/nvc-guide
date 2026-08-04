package nvc.guide.modules.nvcassistant.metrics.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ToolCallStats {
    /** 每工具成功率（toolName → successRate） */
    private Map<String, Double> perToolSuccessRate;
    /** 每工具平均延迟（toolName → avgLatencyMs） */
    private Map<String, Double> avgToolLatency;
    /** 每工具调用次数（toolName → count） */
    private Map<String, Long> perToolCallCount;
    /** 总调用次数 */
    private long totalCalls;
    /** 总成功率 */
    private double overallSuccessRate;
}
