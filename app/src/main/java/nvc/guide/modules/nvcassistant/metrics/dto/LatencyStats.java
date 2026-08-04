package nvc.guide.modules.nvcassistant.metrics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LatencyStats {
    /** P50 延迟（毫秒） */
    private long p50;
    /** P90 延迟（毫秒） */
    private long p90;
    /** P99 延迟（毫秒） */
    private long p99;
    /** 平均延迟（毫秒） */
    private double avgLatencyMs;
    /** 总请求数 */
    private long totalRequests;
}
