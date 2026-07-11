package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;

/**
 * Agent 配置响应 DTO
 */
public record AgentConfigDTO(
    NvcAgentScene agentScene,
    String displayName,
    String description,
    String systemPrompt,
    String modelProvider,
    String modelName,
    Double temperature,
    Integer maxTokens,
    Double topP,
    Boolean isEnabled,
    Integer stepAdvanceThreshold,
    Integer maxStepAttempts,
    Integer stepTimeoutMinutes
) {}
