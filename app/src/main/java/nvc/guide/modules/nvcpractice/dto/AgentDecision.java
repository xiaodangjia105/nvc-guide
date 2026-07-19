package nvc.guide.modules.nvcpractice.dto;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;

import java.util.List;
import java.util.Map;

/**
 * Agent 调度决策结果
 *
 * @param scene            选择的 Agent 角色
 * @param reason           决策原因（日志/调试用）
 * @param action           动作标记（STEP_ADVANCE / LOW_SCORE_GUIDE / DIFFICULT_UPGRADE 等，可为 null）
 * @param promptVariables  注入到 Prompt 模板的变量（mode、covered_elements 等）
 * @param availableTools   该 Agent 可用的工具名称列表（为空则不注入工具）
 */
public record AgentDecision(
    NvcAgentScene scene,
    String reason,
    String action,
    Map<String, String> promptVariables,
    List<String> availableTools
) {

  /**
   * 便捷构造器（无 promptVariables、无工具），兼容现有调用
   */
  public AgentDecision(NvcAgentScene scene, String reason, String action) {
    this(scene, reason, action, Map.of(), List.of());
  }

  /**
   * 便捷构造器（无工具），兼容现有调用
   */
  public AgentDecision(NvcAgentScene scene, String reason, String action,
                       Map<String, String> promptVariables) {
    this(scene, reason, action, promptVariables, List.of());
  }
}
