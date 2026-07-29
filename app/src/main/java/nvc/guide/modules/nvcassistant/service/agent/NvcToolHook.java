package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;

import java.util.concurrent.CompletableFuture;

/**
 * 工具调用钩子接口 — 异步责任链模式
 *
 * <p>Spring 自动注入所有实现，按 @Order 注解排序执行。
 * beforeToolCall 返回 SKIP 可中断链（跳过工具执行）。
 */
public interface NvcToolHook {

    /**
     * 工具调用前钩子
     *
     * @param toolName  工具名称
     * @param arguments 工具参数（JSON）
     * @param context   工具上下文
     * @return PROCEED 继续执行，SKIP 跳过此工具
     */
    default CompletableFuture<ToolCallDecision> beforeToolCall(String toolName, JsonNode arguments, NvcToolContext context) {
        return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
    }

    /**
     * 工具调用后钩子
     *
     * @param toolName 工具名称
     * @param result   工具执行结果
     * @param context  工具上下文
     * @return 处理后的结果（可修改）
     */
    default CompletableFuture<String> afterToolCall(String toolName, String result, NvcToolContext context) {
        return CompletableFuture.completedFuture(result);
    }

    /**
     * 工具调用决策
     */
    enum ToolCallDecision {
        /** 继续执行 */
        PROCEED,
        /** 跳过此工具 */
        SKIP
    }
}
