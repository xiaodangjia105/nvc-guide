package nvc.guide.modules.nvcpractice.tool;

import lombok.Builder;
import lombok.Data;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具执行上下文
 *
 * <p>attributes 用于 Hook 之间传递数据（缓存结果、跳过原因等）。
 */
@Data
@Builder
public class NvcToolContext {
    private Long userId;
    private Long sessionId;
    private PracticeContext practiceContext;

    /** 通用属性存储 — Hook 间数据传递 */
    @Builder.Default
    private Map<String, Object> attributes = new ConcurrentHashMap<>();

    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        return (T) attributes.get(key);
    }

    public boolean hasAttribute(String key) {
        return attributes.containsKey(key);
    }
}
