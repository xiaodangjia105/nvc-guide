package nvc.guide.modules.nvcpractice.dto;

import java.util.List;

/**
 * 反馈统计响应
 */
public record FeedbackStatsResponse(
    /** 总反馈数 */
    long totalFeedbackCount,
    /** 总好评率 (0.0 ~ 1.0) */
    double overallThumbsUpRate,
    /** 按 Agent 场景分组的统计 */
    List<SceneFeedbackStats> perSceneStats
) {
    /**
     * 单个 Agent 场景的反馈统计
     */
    public record SceneFeedbackStats(
        String agentScene,
        long count,
        long thumbsUpCount,
        double thumbsUpRate
    ) {}
}
