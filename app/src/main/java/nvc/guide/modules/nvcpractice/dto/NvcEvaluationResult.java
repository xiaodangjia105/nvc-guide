package nvc.guide.modules.nvcpractice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * NVC 评估结果 - 由 LLM 结构化输出
 */
public record NvcEvaluationResult(
    @JsonProperty("observation_score") Integer observationScore,
    @JsonProperty("feeling_score") Integer feelingScore,
    @JsonProperty("need_score") Integer needScore,
    @JsonProperty("request_score") Integer requestScore,
    @JsonProperty("empathy_score") Integer empathyScore,
    @JsonProperty("overall_score") Integer overallScore,
    @JsonProperty("observation_detail") String observationDetail,
    @JsonProperty("feeling_detail") String feelingDetail,
    @JsonProperty("need_detail") String needDetail,
    @JsonProperty("request_detail") String requestDetail,
    @JsonProperty("empathy_detail") String empathyDetail,
    String strengths,
    String improvements,
    @JsonProperty("reference_expressions") String referenceExpressions,
    String summary
) {}
