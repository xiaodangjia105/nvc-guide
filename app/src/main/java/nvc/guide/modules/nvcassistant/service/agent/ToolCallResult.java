package nvc.guide.modules.nvcassistant.service.agent;

import java.util.List;
import java.util.Map;

/**
 * 单个工具调用的执行结果
 *
 * @param toolName    工具名称
 * @param arguments   原始参数 JSON
 * @param result      执行结果
 * @param success     是否成功
 * @param durationMs  执行耗时（毫秒）
 * @param skipped     是否被 Hook 跳过
 * @param skipReason  跳过原因（skipped=true 时有值）
 * @param hookRecords Hook 执行记录（用于 trace）
 */
public record ToolCallResult(
    String toolName,
    String arguments,
    String result,
    boolean success,
    long durationMs,
    boolean skipped,
    String skipReason,
    List<Map<String, Object>> hookRecords
) {

    public static ToolCallResult success(String toolName, String arguments, String result, long durationMs) {
        return new ToolCallResult(toolName, arguments, result, true, durationMs, false, null, null);
    }

    public static ToolCallResult success(String toolName, String arguments, String result, long durationMs,
                                          List<Map<String, Object>> hookRecords) {
        return new ToolCallResult(toolName, arguments, result, true, durationMs, false, null, hookRecords);
    }

    public static ToolCallResult failure(String toolName, String arguments, String errorMessage, long durationMs) {
        return new ToolCallResult(toolName, arguments, errorMessage, false, durationMs, false, null, null);
    }

    public static ToolCallResult failure(String toolName, String arguments, String errorMessage, long durationMs,
                                          List<Map<String, Object>> hookRecords) {
        return new ToolCallResult(toolName, arguments, errorMessage, false, durationMs, false, null, hookRecords);
    }

    public static ToolCallResult skipped(String toolName, String arguments, String reason) {
        return new ToolCallResult(toolName, arguments, reason, false, 0, true, reason, null);
    }

    public static ToolCallResult skipped(String toolName, String arguments, String reason,
                                          List<Map<String, Object>> hookRecords) {
        return new ToolCallResult(toolName, arguments, reason, false, 0, true, reason, hookRecords);
    }
}
