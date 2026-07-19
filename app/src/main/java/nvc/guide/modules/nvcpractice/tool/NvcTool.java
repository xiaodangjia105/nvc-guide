package nvc.guide.modules.nvcpractice.tool;

/**
 * NVC 工具接口 — Agent 可调用的外部能力。
 * 实现此接口并加上 @Component 注解即可自动注册到 NvcToolRegistry。
 */
public interface NvcTool {

    /** 工具名称，用于 Function Calling */
    String name();

    /** 工具描述，告诉 LLM 这个工具能做什么 */
    String description();

    /** 输入参数的 JSON Schema（字符串格式，与 Spring AI ToolDefinition 一致） */
    String inputSchema();

    /** 执行工具 */
    NvcToolResult execute(String input, NvcToolContext context);
}
