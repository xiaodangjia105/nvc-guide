package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 场景练习模式路由
 * CREATED 阶段 → SCENARIO_GENERATOR（引入场景）
 * 后续 → DIFFICULT_PARTNER（扮演对方角色）
 */
@Component
public class ScenarioRouter implements ModeRouter {

  @Override
  public AgentDecision route(PracticeContext context) {
    var session = context.getSession();
    Map<String, String> vars = new HashMap<>();
    vars.put("mode", "scenario");

    // 注入场景详情
    if (context.getScenarioDescription() != null) {
      vars.put("scenario_context", context.getScenarioDescription());
    }

    // CREATED 阶段：使用场景生成官引入场景
    if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
      return new AgentDecision(
          NvcAgentScene.SCENARIO_GENERATOR,
          "场景模式首轮，使用场景生成官引入场景",
          null,
          vars
      );
    }

    // 后续轮次：使用困难搭档扮演对方角色
    return new AgentDecision(
        NvcAgentScene.DIFFICULT_PARTNER,
        "场景模式，使用困难搭档扮演对方",
        null,
        vars
    );
  }
}
