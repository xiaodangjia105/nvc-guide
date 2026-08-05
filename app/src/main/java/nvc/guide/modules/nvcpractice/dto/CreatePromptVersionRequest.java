package nvc.guide.modules.nvcpractice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建 Prompt 版本请求
 */
public record CreatePromptVersionRequest(
    @NotBlank String systemPrompt,
    String changeNote,
    /** 初始流量百分比（默认 0，激活后可调整） */
    Integer trafficPercentage
) {}
