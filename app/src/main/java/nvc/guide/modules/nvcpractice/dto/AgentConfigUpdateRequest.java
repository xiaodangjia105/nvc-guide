package nvc.guide.modules.nvcpractice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Agent 配置更新请求
 * 所有字段可选，非 null 字段才会被更新
 */
public record AgentConfigUpdateRequest(
    String systemPrompt,
    String modelProvider,
    String modelName,

    @DecimalMin("0.0") @DecimalMax("2.0")
    Double temperature,

    @Min(100) @Max(8000)
    Integer maxTokens,

    @DecimalMin("0.0") @DecimalMax("1.0")
    Double topP,

    Boolean isEnabled
) {}
