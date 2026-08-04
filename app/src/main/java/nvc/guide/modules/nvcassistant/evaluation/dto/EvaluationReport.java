package nvc.guide.modules.nvcassistant.evaluation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 离线评估报告
 */
@Data
@Builder
public class EvaluationReport {
    private String reportId;
    private LocalDateTime evaluatedAt;
    private int totalTraces;

    // 意图路由准确率
    private double intentRoutingAccuracy;
    private int totalIntentRoutes;
    private int correctIntentRoutes;

    // 工具调用稳定性
    private Map<String, Double> toolSuccessRates;
    private double overallToolSuccessRate;
    private int totalToolCalls;

    // 性能指标
    private long latencyP50;
    private long latencyP90;
    private long latencyP99;
    private double avgLatencyMs;

    // 成本指标
    private double avgTokensPerSession;
    private long totalTokens;

    // 综合评语
    private String summary;
}
