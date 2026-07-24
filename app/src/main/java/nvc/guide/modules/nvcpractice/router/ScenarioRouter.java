package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.tool.NvcToolSceneMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScenarioRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;

    /** 每隔多少轮检查一次低分 */
    private static final int EVALUATION_CHECK_INTERVAL = 5;
    /** 低分阈值 */
    private static final int LOW_SCORE_THRESHOLD = 50;

    @Override
    public AgentDecision route(PracticeContext context) {
        NvcSessionPhase phase = context.getSession().getCurrentPhase();

        // CREATED 阶段 → 场景生成官引入场景
        if (phase == NvcSessionPhase.CREATED) {
            return new AgentDecision(
                NvcAgentScene.SCENARIO_GENERATOR,
                "场景驱动模式：生成场景",
                null,
                Map.of("mode", "scenario"),
                toolSceneMapping.getDefaultTools(NvcAgentScene.SCENARIO_GENERATOR)
            );
        }

        // 每 5 轮检查评估分数，无评估记录或低分时触发评估教练
        int roundCount = context.getRoundCount();
        if (roundCount > 0 && roundCount % EVALUATION_CHECK_INTERVAL == 0) {
            NvcEvaluationEntity lastEval = context.getLastEvaluation();
            if (lastEval == null || isLowScore(lastEval)) {
                return new AgentDecision(
                    NvcAgentScene.NVC_EXPRESSION_EVALUATOR,
                    "场景驱动模式：第 " + roundCount + " 轮，"
                        + (lastEval == null ? "首次评估" : "检测到低分"),
                    null,
                    Map.of("mode", "scenario", "trigger",
                        lastEval == null ? "first_evaluation" : "low_score_evaluation"),
                    toolSceneMapping.getDefaultTools(NvcAgentScene.NVC_EXPRESSION_EVALUATOR)
                );
            }
        }

        // 默认 → 困难搭档（角色扮演）
        return new AgentDecision(
            NvcAgentScene.DIFFICULT_PARTNER,
            "场景驱动模式：困难搭档角色扮演",
            null,
            Map.of("mode", "scenario"),
            toolSceneMapping.getDefaultTools(NvcAgentScene.DIFFICULT_PARTNER)
        );
    }

    /**
     * 检查评估分数是否低于阈值
     */
    private boolean isLowScore(NvcEvaluationEntity eval) {
        if (eval.getOverallScore() != null) {
            return eval.getOverallScore() < LOW_SCORE_THRESHOLD;
        }
        // 如果没有总分，检查各维度
        int count = 0;
        int sum = 0;
        if (eval.getObservationScore() != null) { sum += eval.getObservationScore(); count++; }
        if (eval.getFeelingScore() != null) { sum += eval.getFeelingScore(); count++; }
        if (eval.getNeedScore() != null) { sum += eval.getNeedScore(); count++; }
        if (eval.getRequestScore() != null) { sum += eval.getRequestScore(); count++; }
        return count > 0 && (sum / count) < LOW_SCORE_THRESHOLD;
    }
}
