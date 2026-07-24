package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.tool.NvcToolSceneMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StructuredRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;

    /**
     * 步骤到教练 Agent 的映射
     */
    private static final Map<NvcPracticeStep, NvcAgentScene> STEP_COACH_MAP = Map.of(
        NvcPracticeStep.OBSERVE, NvcAgentScene.STEP_OBSERVE_COACH,
        NvcPracticeStep.FEELING, NvcAgentScene.STEP_FEELING_COACH,
        NvcPracticeStep.NEED, NvcAgentScene.STEP_NEED_COACH,
        NvcPracticeStep.REQUEST, NvcAgentScene.STEP_REQUEST_COACH
    );

    @Override
    public AgentDecision route(PracticeContext context) {
        NvcPracticeStep currentStep = context.getSession().getCurrentStep();
        String coveredElements = buildCoveredElements(context.getLastEvaluation());

        // 根据当前步骤选择对应的教练 Agent
        NvcAgentScene coachScene = STEP_COACH_MAP.getOrDefault(
            currentStep, NvcAgentScene.DIALOGUE_GUIDE);

        return new AgentDecision(
            coachScene,
            "结构化四步模式：" + (currentStep != null ? currentStep.name() : "默认"),
            null,
            Map.of("mode", "structured", "covered_elements", coveredElements,
                   "current_step", currentStep != null ? currentStep.name() : ""),
            toolSceneMapping.getDefaultTools(coachScene)
        );
    }

    private String buildCoveredElements(NvcEvaluationEntity eval) {
        if (eval == null) return "";

        StringBuilder sb = new StringBuilder();
        appendIfNotEmpty(sb, "observation", eval.getObservationScore());
        appendIfNotEmpty(sb, "feeling", eval.getFeelingScore());
        appendIfNotEmpty(sb, "need", eval.getNeedScore());
        appendIfNotEmpty(sb, "request", eval.getRequestScore());
        return sb.toString();
    }

    private void appendIfNotEmpty(StringBuilder sb, String element, Integer score) {
        if (score != null && score >= 50) {
            if (!sb.isEmpty()) sb.append(",");
            sb.append(element);
        }
    }
}
