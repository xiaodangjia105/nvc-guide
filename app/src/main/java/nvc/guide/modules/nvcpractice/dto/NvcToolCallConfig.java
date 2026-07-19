package nvc.guide.modules.nvcpractice.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工具调用配置 — 独立于编排层的可选扩展点
 */
@Data
@Builder
public class NvcToolCallConfig {

    /** 可用工具名称列表 */
    private final List<String> toolNames;

    /** 额外上下文（传递给 Spring AI ToolContext） */
    @Builder.Default
    private final Map<String, Object> extraContext = Map.of();

    public static NvcToolCallConfig disabled() {
        return new NvcToolCallConfig(List.of(), Map.of());
    }

    public boolean isEnabled() {
        return toolNames != null && !toolNames.isEmpty();
    }
}
