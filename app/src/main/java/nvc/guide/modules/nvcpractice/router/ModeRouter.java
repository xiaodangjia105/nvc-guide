package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;

/**
 * 模式路由策略接口
 * 每种练习模式有独立的路由实现，互不干扰
 */
public interface ModeRouter {

  /**
   * 根据当前上下文决定下一步使用哪个 Agent
   *
   * @param context 练习上下文
   * @return Agent 调度决策
   */
  AgentDecision route(PracticeContext context);
}
