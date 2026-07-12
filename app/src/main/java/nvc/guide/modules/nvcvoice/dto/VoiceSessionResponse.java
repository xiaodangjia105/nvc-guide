package nvc.guide.modules.nvcvoice.dto;

import java.time.LocalDateTime;
import nvc.guide.common.model.AsyncTaskStatus;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionPhase;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionStatus;

/**
 * 语音练习会话响应
 */
public record VoiceSessionResponse(
    Long id,
    Long userId,
    NvcPracticeMode practiceMode,
    Long scenarioId,
    NvcAgentScene agentScene,
    NvcDifficulty difficulty,
    NvcVoiceSessionPhase currentPhase,
    NvcVoiceSessionStatus status,
    String llmProvider,
    Integer plannedDuration,
    Integer actualDuration,
    LocalDateTime startTime,
    LocalDateTime endTime,
    LocalDateTime pausedAt,
    LocalDateTime resumedAt,
    AsyncTaskStatus evaluateStatus,
    String webSocketUrl
) {}
