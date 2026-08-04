package nvc.guide.modules.nvcassistant.trace.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TraceStats {
    /** 总 Trace 数 */
    private long totalTraces;
    /** 平均耗时（毫秒） */
    private double avgDurationMs;
    /** 平均每 Trace Token 消耗 */
    private double avgTokensPerTrace;
    /** 成功率 */
    private double successRate;
    /** 各状态计数 */
    private Map<String, Long> statusCounts;
    /** 各模式计数 */
    private Map<String, Long> modeCounts;
    /** 失败原因 Top N */
    private List<FailureReason> topFailureReasons;

    @Data
    @Builder
    public static class FailureReason {
        private String reason;
        private long count;
    }
}
