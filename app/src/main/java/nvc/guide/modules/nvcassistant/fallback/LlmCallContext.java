package nvc.guide.modules.nvcassistant.fallback;

import lombok.Builder;
import lombok.Data;

/**
 * LLM 调用上下文
 *
 * <p>用于日志记录和 Trace 关联。
 */
@Data
@Builder
public class LlmCallContext {

    /** 会话 ID */
    private String sessionId;

    /** 组件名称（如 AgentLoop、NvcEvaluationService） */
    private String componentName;

    /** 调用场景（如 dialog、evaluation、scenario_generate） */
    private String scene;
}
