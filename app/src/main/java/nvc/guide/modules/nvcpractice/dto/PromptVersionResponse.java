package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import java.time.LocalDateTime;

/**
 * Prompt 版本响应
 */
public record PromptVersionResponse(
    Long id,
    NvcAgentScene agentScene,
    Integer version,
    String systemPrompt,
    Boolean isActive,
    Integer trafficPercentage,
    String changeNote,
    Long totalCalls,
    Double avgEvaluationScore,
    Double avgTokenUsage,
    Double avgLatencyMs,
    LocalDateTime createdAt,
    LocalDateTime activatedAt
) {}
