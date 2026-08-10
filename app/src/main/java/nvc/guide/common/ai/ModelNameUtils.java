package nvc.guide.common.ai;

/**
 * 模型名称相关的工具方法。
 */
public final class ModelNameUtils {

    private ModelNameUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 判断给定模型名是否"看起来像"聊天模型（而非 Embedding 模型）。
     * <p>
     * 基于常见厂商聊天模型名称前缀进行启发式判断。
     *
     * @param model 模型名称
     * @return 如果匹配已知聊天模型前缀则返回 true
     */
    public static boolean looksLikeChatModel(String model) {
        String lower = model.toLowerCase();
        return lower.startsWith("glm-")
            || lower.startsWith("deepseek")
            || lower.startsWith("kimi")
            || lower.startsWith("moonshot")
            || lower.startsWith("qwen")
            || lower.startsWith("ernie");
    }
}
