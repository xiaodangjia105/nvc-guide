package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 工具注册中心 — Spring 自动注入所有 NvcTool 实现，转换为 Spring AI FunctionCallback
 */
@Service
@Slf4j
public class NvcToolRegistry {

    private final Map<String, NvcTool> tools;

    /**
     * Spring 自动注入所有 NvcTool 实现
     */
    public NvcToolRegistry(List<NvcTool> toolList) {
        this.tools = toolList.stream()
            .collect(Collectors.toMap(NvcTool::name, t -> t));
        log.info("[NvcToolRegistry] Registered {} tools: {}", tools.size(), tools.keySet());
    }

    public NvcTool getTool(String name) {
        return tools.get(name);
    }

    public List<NvcTool> getAllTools() {
        return List.copyOf(tools.values());
    }

    /**
     * 全量转换 — 主 Agent 使用
     */
    public List<ToolCallback> toFunctionCallbacks() {
        return tools.values().stream().map(this::toFunctionCallback).toList();
    }

    /**
     * 按名称子集 — 练习 Agent 按场景过滤
     */
    public List<ToolCallback> toFunctionCallbacks(List<String> toolNames) {
        if (toolNames == null || toolNames.isEmpty()) {
            return List.of();
        }
        return toolNames.stream()
            .map(tools::get)
            .filter(Objects::nonNull)
            .map(this::toFunctionCallback)
            .toList();
    }

    /**
     * NvcTool → Spring AI FunctionToolCallback 适配
     */
    private ToolCallback toFunctionCallback(NvcTool tool) {
        return FunctionToolCallback.builder(tool.name(),
                (String input, ToolContext aiContext) -> {
                    try {
                        NvcToolContext nvcContext = extractNvcContext(aiContext);
                        NvcToolResult result = tool.execute(input, nvcContext);
                        return result.success() ? result.data() : "Error: " + result.errorMessage();
                    } catch (Exception e) {
                        log.error("[NvcToolRegistry] Tool execution failed: tool={}", tool.name(), e);
                        return "Error: 工具执行异常: " + e.getMessage();
                    }
                })
            .description(tool.description())
            .inputSchema(tool.inputSchema())
            .inputType(String.class)
            .build();
    }

    /**
     * 从 Spring AI ToolContext Map 中提取 NvcToolContext
     */
    private NvcToolContext extractNvcContext(ToolContext aiContext) {
        if (aiContext == null || aiContext.getContext().isEmpty()) {
            return NvcToolContext.builder().build();
        }
        Map<String, Object> map = aiContext.getContext();
        return NvcToolContext.builder()
            .userId((Long) map.get("nvc.userId"))
            .sessionId((Long) map.get("nvc.sessionId"))
            .practiceContext((PracticeContext) map.get("nvc.practiceContext"))
            .build();
    }
}
