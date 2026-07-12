package nvc.guide.modules.nvcvoice.dto;

import java.time.LocalDateTime;

/**
 * 语音评估详情 DTO
 */
public record VoiceEvaluationDetailDTO(
    Long id,
    Long sessionId,
    Integer observationScore,
    Integer feelingScore,
    Integer needScore,
    Integer requestScore,
    Integer empathyScore,
    Integer overallScore,
    Integer fluencyScore,
    String observationDetail,
    String feelingDetail,
    String needDetail,
    String requestDetail,
    String overallFeedback,
    String strengthsJson,
    String improvementsJson,
    LocalDateTime createdAt
) {}
