package nvc.guide.modules.nvcpractice.tool;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 场景-工具映射表 — 定义每个 Agent 场景可用的工具子集
 */
@Component
public class NvcToolSceneMapping {

    private static final Map<NvcAgentScene, List<String>> SCENE_TOOLS = Map.ofEntries(
        // 练习 Agent 场景
        Map.entry(NvcAgentScene.NVC_EXPRESSION_EVALUATOR,
            List.of("evaluate_nvc", "rag_search")),
        Map.entry(NvcAgentScene.DIALOGUE_GUIDE,
            List.of("rag_search", "profile_query")),
        Map.entry(NvcAgentScene.SCENARIO_GENERATOR,
            List.of("scenario_search", "scenario_generate", "profile_query")),
        Map.entry(NvcAgentScene.EMPATHY_COACH,
            List.of("rag_search", "profile_query")),
        Map.entry(NvcAgentScene.NVC_KNOWLEDGE_ADVISOR,
            List.of("rag_search", "wiki_search")),
        // 步骤教练
        Map.entry(NvcAgentScene.STEP_OBSERVE_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_FEELING_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_NEED_COACH, List.of("rag_search")),
        Map.entry(NvcAgentScene.STEP_REQUEST_COACH, List.of("rag_search")),
        // 困难搭档 — 不给工具，纯角色扮演
        Map.entry(NvcAgentScene.DIFFICULT_PARTNER, List.of()),
        // 档案分析 — 只读
        Map.entry(NvcAgentScene.PROFILE_ANALYZER,
            List.of("profile_query", "dashboard_query"))
    );

    /**
     * 获取场景的默认工具列表
     */
    public List<String> getDefaultTools(NvcAgentScene scene) {
        return SCENE_TOOLS.getOrDefault(scene, List.of());
    }

    /**
     * 主 Agent — 全量工具（Phase 2 使用）
     */
    public List<String> getAllTools() {
        return List.of(
            "rag_search", "wiki_search", "wiki_write",
            "profile_query", "profile_update", "evaluate_nvc",
            "scenario_search", "scenario_generate", "dashboard_query",
            "practice_start"
        );
    }
}
