package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.tool.NvcToolSceneMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class StructuredRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;

    @Override
    public AgentDecision route(PracticeContext context) {
        String coveredElements = buildCoveredElements(context.getLastEvaluation());

        return new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE,
            "结构化四步模式",
            null,
            Map.of("mode", "structured", "covered_elements", coveredElements),
            toolSceneMapping.getDefaultTools(NvcAgentScene.DIALOGUE_GUIDE)
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
