package nvc.guide.modules.nvcvoice.dto;

import nvc.guide.common.model.AsyncTaskStatus;

/**
 * 语音评估状态 DTO
 */
public record VoiceEvaluationStatusDTO(
    AsyncTaskStatus status,
    String error,
    VoiceEvaluationDetailDTO evaluation
) {}
