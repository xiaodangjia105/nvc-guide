package nvc.guide.modules.nvcassistant.service.agent;

/**
 * 单个工具调用的执行结果
 *
 * @param toolName   工具名称
 * @param arguments  原始参数 JSON
 * @param result     执行结果
 * @param success    是否成功
 * @param durationMs 执行耗时（毫秒）
 * @param skipped    是否被 Hook 跳过
 * @param skipReason 跳过原因（skipped=true 时有值）
 */
public record ToolCallResult(
    String toolName,
    String arguments,
    String result,
    boolean success,
    long durationMs,
    boolean skipped,
    String skipReason
) {

    public static ToolCallResult success(String toolName, String arguments, String result, long durationMs) {
        return new ToolCallResult(toolName, arguments, result, true, durationMs, false, null);
    }

    public static ToolCallResult failure(String toolName, String arguments, String errorMessage, long durationMs) {
        return new ToolCallResult(toolName, arguments, errorMessage, false, durationMs, false, null);
    }

    public static ToolCallResult skipped(String toolName, String arguments, String reason) {
        return new ToolCallResult(toolName, arguments, reason, false, 0, true, reason);
    }
}
