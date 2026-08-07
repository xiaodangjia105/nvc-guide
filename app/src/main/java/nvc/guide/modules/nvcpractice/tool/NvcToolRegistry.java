package nvc.guide.modules.nvcpractice.tool;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * NvcTool → Spring AI FunctionToolCallback 适配
     *
     * <p>注意：inputType 使用 Map.class 而非 String.class。
     * LLM 可能发送 JSON Object（如 {"profile": {...}}），如果用 String.class，
     * Jackson 会尝试将 Object 反序列化为 String 导致 MismatchedInputException。
     * 使用 Map.class 后，再手动序列化为 JSON 字符串传给 NvcTool。
     */
    private ToolCallback toFunctionCallback(NvcTool tool) {
        String schema = tool.inputSchema();
        log.info("[NvcToolRegistry] Registering tool: name={}, description={}, schema={}",
            tool.name(), tool.description(), schema);

        return FunctionToolCallback.builder(tool.name(),
                (Map<String, Object> input, ToolContext aiContext) -> {
                    try {
                        NvcToolContext nvcContext = extractNvcContext(aiContext);
                        String jsonInput = OBJECT_MAPPER.writeValueAsString(input);
                        log.info("[NvcToolRegistry] Executing tool: name={}, input={}", tool.name(), jsonInput);
                        NvcToolResult result = tool.execute(jsonInput, nvcContext);
                        log.info("[NvcToolRegistry] Tool result: name={}, success={}, result={}",
                            tool.name(), result.success(), result.success() ? result.data() : result.errorMessage());
                        return result.success() ? result.data() : "Error: " + result.errorMessage();
                    } catch (Exception e) {
                        log.error("[NvcToolRegistry] Tool execution failed: tool={}", tool.name(), e);
                        return "Error: 工具执行异常: " + e.getMessage();
                    }
                })
            .description(tool.description())
            .inputSchema(schema)
            .inputType(Map.class)
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
            .userId(toLong(map.get("nvc.userId")))
            .sessionId(toLong(map.get("nvc.sessionId")))
            .practiceContext((PracticeContext) map.get("nvc.practiceContext"))
            .build();
    }

    /**
     * 安全地将 Object 转换为 Long
     * 处理 Integer、Long、Number 等类型，避免 ClassCastException
     */
    private Long toLong(Object val) {
        if (val == null) return null;
        if (val instanceof Long l) return l;
        if (val instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(val.toString());
        } catch (NumberFormatException e) {
            log.warn("Failed to convert to Long: {}", val);
            return null;
        }
    }
}
