package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;

import java.time.LocalDateTime;

/**
 * NVC 练习报告 — 包含会话信息、五维度评分、详细分析
 */
public record NvcPracticeReport(
    Long sessionId,
    NvcPracticeMode practiceMode,
    NvcDifficulty difficulty,
    long totalRounds,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    // 五维度评分
    Integer observationScore,
    Integer feelingScore,
    Integer needScore,
    Integer requestScore,
    Integer empathyScore,
    Integer overallScore,
    // 五维度详细分析
    String observationDetail,
    String feelingDetail,
    String needDetail,
    String requestDetail,
    String empathyDetail,
    // 综合
    String strengths,
    String improvements,
    String referenceExpressions,
    String summary
) {}
