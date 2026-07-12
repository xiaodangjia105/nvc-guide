package nvc.guide.modules.nvcvoice.dto;

import jakarta.validation.constraints.NotNull;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;

/**
 * 创建语音练习会话请求
 */
public record CreateVoiceSessionRequest(

    @NotNull(message = "用户ID不能为空")
    Long userId,

    @NotNull(message = "练习模式不能为空")
    NvcPracticeMode practiceMode,

    Long scenarioId,

    NvcDifficulty difficulty,

    String llmProvider
) {

  public CreateVoiceSessionRequest {
    if (difficulty == null) {
      difficulty = NvcDifficulty.MEDIUM;
    }
  }
}
