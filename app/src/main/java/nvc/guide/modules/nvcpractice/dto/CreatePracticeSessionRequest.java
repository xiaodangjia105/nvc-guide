package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import jakarta.validation.constraints.NotNull;

public record CreatePracticeSessionRequest(
    @NotNull NvcPracticeMode practiceMode,
    Long scenarioId,
    NvcDifficulty difficulty
) {}
