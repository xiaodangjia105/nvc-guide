package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;

/**
 * Agent 调度决策结果
 *
 * @param scene  选择的 Agent 角色
 * @param reason 决策原因（日志/调试用）
 * @param action 动作标记（STEP_ADVANCE / LOW_SCORE_GUIDE / DIFFICULT_UPGRADE 等，可为 null）
 */
public record AgentDecision(
    NvcAgentScene scene,
    String reason,
    String action
) {}
