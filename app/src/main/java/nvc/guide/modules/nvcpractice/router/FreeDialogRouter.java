package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自由对话模式路由
 * 始终返回 DIALOGUE_GUIDE，不触发场景生成或步骤教练
 */
@Component
public class FreeDialogRouter implements ModeRouter {

  @Override
  public AgentDecision route(PracticeContext context) {
    return new AgentDecision(
        NvcAgentScene.DIALOGUE_GUIDE,
        "自由对话模式，使用教练引导",
        null,
        Map.of("mode", "free_dialog")
    );
  }
}
