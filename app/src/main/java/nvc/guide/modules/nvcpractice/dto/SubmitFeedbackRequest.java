package nvc.guide.modules.nvcpractice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcFeedbackSource;

/**
 * 提交反馈请求
 */
public record SubmitFeedbackRequest(
    @NotNull Long sessionId,
    @NotNull Long messageId,
    @NotNull NvcFeedbackSource messageSource,
    NvcAgentScene agentScene,
    @NotNull @Min(1) @Max(5) Integer rating,
    String comment
) {}
