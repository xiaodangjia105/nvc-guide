package nvc.guide.modules.nvcassistant.metrics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompressionStats {
    /** 压缩触发次数 */
    private long compressionTriggerCount;
    /** 平均 Token 减少数 */
    private double avgTokenReduction;
    /** 平均压缩百分比 */
    private double avgReductionPercent;
}
