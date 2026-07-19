package nvc.guide.modules.nvcpractice.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NvcToolRegistry 测试")
class NvcToolRegistryTest {

    private NvcTool createDummyTool(String name) {
        return new NvcTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "desc-" + name; }
            @Override public String inputSchema() { return "{}"; }
            @Override public NvcToolResult execute(String input, NvcToolContext context) {
                return NvcToolResult.success("ok");
            }
        };
    }

    @Test
    @DisplayName("自动注册所有 NvcTool 实现")
    void registerAllTools() {
        NvcTool t1 = createDummyTool("tool_a");
        NvcTool t2 = createDummyTool("tool_b");

        NvcToolRegistry registry = new NvcToolRegistry(List.of(t1, t2));

        assertEquals(2, registry.getAllTools().size());
        assertNotNull(registry.getTool("tool_a"));
        assertNotNull(registry.getTool("tool_b"));
        assertNull(registry.getTool("nonexistent"));
    }

    @Test
    @DisplayName("toFunctionCallbacks() 返回全量工具")
    void toFunctionCallbacks_all() {
        NvcTool t1 = createDummyTool("tool_a");
        NvcToolRegistry registry = new NvcToolRegistry(List.of(t1));

        List<ToolCallback> callbacks = registry.toFunctionCallbacks();
        assertEquals(1, callbacks.size());
    }

    @Test
    @DisplayName("toFunctionCallbacks(List) 按名称过滤")
    void toFunctionCallbacks_filtered() {
        NvcTool t1 = createDummyTool("tool_a");
        NvcTool t2 = createDummyTool("tool_b");
        NvcToolRegistry registry = new NvcToolRegistry(List.of(t1, t2));

        List<ToolCallback> callbacks = registry.toFunctionCallbacks(List.of("tool_a"));
        assertEquals(1, callbacks.size());
    }

    @Test
    @DisplayName("toFunctionCallbacks(List) 忽略不存在的工具名")
    void toFunctionCallbacks_ignoreUnknown() {
        NvcTool t1 = createDummyTool("tool_a");
        NvcToolRegistry registry = new NvcToolRegistry(List.of(t1));

        List<ToolCallback> callbacks = registry.toFunctionCallbacks(List.of("tool_a", "nonexistent"));
        assertEquals(1, callbacks.size());
    }

    @Test
    @DisplayName("toFunctionCallbacks 空列表返回空")
    void toFunctionCallbacks_empty() {
        NvcToolRegistry registry = new NvcToolRegistry(List.of());
        assertTrue(registry.toFunctionCallbacks().isEmpty());
        assertTrue(registry.toFunctionCallbacks(List.of("x")).isEmpty());
    }
}
