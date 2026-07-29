package nvc.guide.modules.nvcassistant.service.agent;

import java.util.Map;

/**
 * Agent 事件 — 用于 SSE 流式传输
 *
 * <p>事件类型覆盖 Agent Loop 全生命周期：
 * THINKING → TOOLCALL_START → TOOLCALL_END → CONTENT → DONE/ERROR
 */
public record AgentEvent(
    AgentEventType type,
    String data,
    Map<String, Object> metadata
) {

    public enum AgentEventType {
        THINKING,        // 思考中
        TOOLCALL_START,  // 工具调用开始
        TOOLCALL_END,    // 工具调用结束
        CONTENT,         // 回复内容
        DONE,            // 完成
        ERROR            // 错误
    }

    // ==================== 工厂方法 ====================

    public static AgentEvent thinking(String message) {
        return new AgentEvent(AgentEventType.THINKING, message, null);
    }

    public static AgentEvent toolcallStart(String toolName, String arguments) {
        return new AgentEvent(AgentEventType.TOOLCALL_START, toolName,
            Map.of("arguments", arguments));
    }

    public static AgentEvent toolcallEnd(String toolName, boolean success, String result, long durationMs) {
        return new AgentEvent(AgentEventType.TOOLCALL_END, toolName,
            Map.of("success", success, "result", result, "durationMs", durationMs));
    }

    public static AgentEvent content(String text) {
        return new AgentEvent(AgentEventType.CONTENT, text, null);
    }

    public static AgentEvent done(Long conversationId) {
        return new AgentEvent(AgentEventType.DONE, conversationId.toString(), null);
    }

    public static AgentEvent error(String message) {
        return new AgentEvent(AgentEventType.ERROR, message, null);
    }
}
