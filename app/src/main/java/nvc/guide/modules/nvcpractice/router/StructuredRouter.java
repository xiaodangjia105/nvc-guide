package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 结构化练习模式路由
 * 始终返回 DIALOGUE_GUIDE，通过 promptVariables 切换为"主动引导四要素"风格
 * 不强制顺序，灵活引导
 */
@Component
public class StructuredRouter implements ModeRouter {

  @Override
  public AgentDecision route(PracticeContext context) {
    Map<String, String> vars = new HashMap<>();
    vars.put("mode", "structured");

    // 从评估结果推导已覆盖的要素
    String covered = buildCoveredElements(context.getLastEvaluation());
    vars.put("covered_elements", covered);

    return new AgentDecision(
        NvcAgentScene.DIALOGUE_GUIDE,
        "结构化练习模式，教练主动引导四要素",
        null,
        vars
    );
  }

  /**
   * 根据评估结果推导哪些要素已被覆盖（得分 >= 50 视为已覆盖）
   */
  private String buildCoveredElements(NvcEvaluationEntity eval) {
    if (eval == null) {
      return "无";
    }

    StringBuilder sb = new StringBuilder();
    if (eval.getObservationScore() != null && eval.getObservationScore() >= 50) {
      appendIfNotEmpty(sb, "观察");
    }
    if (eval.getFeelingScore() != null && eval.getFeelingScore() >= 50) {
      appendIfNotEmpty(sb, "感受");
    }
    if (eval.getNeedScore() != null && eval.getNeedScore() >= 50) {
      appendIfNotEmpty(sb, "需求");
    }
    if (eval.getRequestScore() != null && eval.getRequestScore() >= 50) {
      appendIfNotEmpty(sb, "请求");
    }

    return sb.isEmpty() ? "无" : sb.toString();
  }

  private void appendIfNotEmpty(StringBuilder sb, String element) {
    if (!sb.isEmpty()) {
      sb.append("、");
    }
    sb.append(element);
  }
}
