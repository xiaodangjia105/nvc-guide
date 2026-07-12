package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import java.time.LocalDateTime;

public record PracticeSessionResponse(
    Long id,
    Long userId,
    NvcPracticeMode practiceMode,
    NvcSessionPhase currentPhase,
    NvcPracticeStep currentStep,
    String agentScene,
    NvcDifficulty difficulty,
    Long scenarioId,
    String scenarioTitle,
    String scenarioDescription,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    LocalDateTime createdAt
) {}
