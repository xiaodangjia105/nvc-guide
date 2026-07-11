package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import java.time.LocalDateTime;

/**
 * 步骤进度 DTO
 */
public record StepProgressDTO(
    NvcPracticeStep currentStep,
    int stepIndex,
    int totalSteps,
    Integer currentScore,
    boolean canAdvance,
    String stepDescription,
    Integer maxAttempts,
    Integer attemptsUsed,
    LocalDateTime stepStartedAt,
    Integer timeoutMinutes
) {}
