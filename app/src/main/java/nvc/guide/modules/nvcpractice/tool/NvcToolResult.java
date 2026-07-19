package nvc.guide.modules.nvcpractice.tool;

/**
 * 工具执行结果
 */
public record NvcToolResult(boolean success, String data, String errorMessage) {

    public static NvcToolResult success(String data) {
        return new NvcToolResult(true, data, null);
    }

    public static NvcToolResult failure(String errorMessage) {
        return new NvcToolResult(false, null, errorMessage);
    }
}
