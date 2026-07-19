package nvc.guide.modules.nvcpractice.router;

import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.tool.NvcToolSceneMapping;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ScenarioRouter implements ModeRouter {

    private final NvcToolSceneMapping toolSceneMapping;

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

        // 后续阶段 → 困难搭档（角色扮演）
        return new AgentDecision(
            NvcAgentScene.DIFFICULT_PARTNER,
            "场景驱动模式：困难搭档角色扮演",
            null,
            Map.of("mode", "scenario"),
            toolSceneMapping.getDefaultTools(NvcAgentScene.DIFFICULT_PARTNER)
        );
    }
}
