package nvc.guide.modules.nvcassistant.metrics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MetricsOverview {
    private TokenStats tokenStats;
    private LatencyStats latencyStats;
    private CompressionStats compressionStats;
    private ToolCallStats toolCallStats;
}
