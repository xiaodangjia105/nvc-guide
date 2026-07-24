package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.tool.NvcToolSceneMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class FreeDialogRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;

    /** 每隔多少轮触发一次评估 */
    private static final int EVALUATION_INTERVAL = 5;

    @Override
    public AgentDecision route(PracticeContext context) {
        int roundCount = context.getRoundCount();

        // 每 5 轮触发一次 NVC 表达评估
        if (roundCount > 0 && roundCount % EVALUATION_INTERVAL == 0) {
            return new AgentDecision(
                NvcAgentScene.NVC_EXPRESSION_EVALUATOR,
                "自由对话模式：第 " + roundCount + " 轮，触发 NVC 表达评估",
                null,
                Map.of("mode", "free_dialog", "trigger", "periodic_evaluation"),
                toolSceneMapping.getDefaultTools(NvcAgentScene.NVC_EXPRESSION_EVALUATOR)
            );
        }

        return new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE,
            "自由对话模式",
            null,
            Map.of("mode", "free_dialog"),
            toolSceneMapping.getDefaultTools(NvcAgentScene.DIALOGUE_GUIDE)
        );
    }
}
