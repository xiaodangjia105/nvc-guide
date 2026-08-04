package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcassistant.metrics.MetricsCollector;
import nvc.guide.modules.nvcpractice.tool.NvcTool;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import nvc.guide.modules.nvcpractice.tool.NvcToolResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行器 — 支持 Hook 链、并行执行、超时控制
 *
 * <p>直接调用 NvcTool.execute()，绕过 Spring AI 的 ToolCallingManager。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ToolExecutor {

    private final NvcToolRegistry toolRegistry;
    private final List<NvcToolHook> hooks;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;

    /** 单个工具超时（毫秒） */
    private static final long TOOL_TIMEOUT_MS = 30_000;

    /**
     * 并行执行工具调用列表
     *
     * @param toolCalls  LLM 返回的工具调用
     * @param userId     用户 ID
     * @param sessionId  会话 ID
     * @return 每个工具调用的结果（顺序与 toolCalls 一致）
     */
    public List<ToolCallResult> execute(List<AssistantMessage.ToolCall> toolCalls, Long userId, Long sessionId) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }

        // 并行执行所有工具调用（每个调用独立的 context，避免属性冲突）
        List<CompletableFuture<ToolCallResult>> futures = toolCalls.stream()
            .map(tc -> CompletableFuture.supplyAsync(() -> {
                NvcToolContext ctx = NvcToolContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .build();
                return executeSingle(tc, ctx);
            }))
            .toList();

        // 等待所有完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // 收集结果（保持顺序）并记录指标
        List<ToolCallResult> results = futures.stream()
            .map(f -> {
                try {
                    return f.get(1, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return ToolCallResult.failure("unknown", "{}", "执行超时: " + e.getMessage(), 0);
                }
            })
            .toList();

        // 采集工具调用指标
        for (ToolCallResult result : results) {
            try {
                metricsCollector.recordToolCall(
                    String.valueOf(sessionId),
                    result.toolName(),
                    result.success(),
                    result.durationMs(),
                    null);
            } catch (Exception e) {
                log.debug("[ToolExecutor] Failed to record tool metric: {}", e.getMessage());
            }
        }

        return results;
    }

    /**
     * 执行单个工具调用（含 Hook 链）
     */
    private ToolCallResult executeSingle(AssistantMessage.ToolCall toolCall, NvcToolContext context) {
        String toolName = toolCall.name();
        String arguments = toolCall.arguments();
        long startTime = System.currentTimeMillis();

        try {
            JsonNode argsNode = parseArguments(arguments);

            // 1. beforeToolCall Hook 链（按 @Order 排序）
            List<NvcToolHook> sortedHooks = getSortedHooks();
            for (NvcToolHook hook : sortedHooks) {
                try {
                    NvcToolHook.ToolCallDecision decision = hook.beforeToolCall(toolName, argsNode, context)
                        .get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (decision == NvcToolHook.ToolCallDecision.SKIP) {
                        long duration = System.currentTimeMillis() - startTime;
                        // 检查是否有缓存结果（CacheToolHook 命中时设置）
                        String cachedResult = context.getAttribute("cachedResult");
                        if (cachedResult != null) {
                            log.info("[ToolExecutor] SKIPPED (cached): tool={}, duration={}ms", toolName, duration);
                            return ToolCallResult.success(toolName, arguments, cachedResult, duration);
                        }
                        String skipReason = context.hasAttribute("skipReason")
                            ? context.getAttribute("skipReason").toString()
                            : "Hook 跳过";
                        log.info("[ToolExecutor] SKIPPED: tool={}, reason={}, duration={}ms",
                            toolName, skipReason, duration);
                        return ToolCallResult.skipped(toolName, arguments, skipReason);
                    }
                } catch (Exception e) {
                    log.warn("[ToolExecutor] beforeToolCall hook error: tool={}, hook={}",
                        toolName, hook.getClass().getSimpleName(), e);
                    // Hook 异常不阻断执行
                }
            }

            // 2. 执行工具
            NvcTool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                long duration = System.currentTimeMillis() - startTime;
                return ToolCallResult.failure(toolName, arguments, "工具不存在: " + toolName, duration);
            }

            NvcToolResult result = tool.execute(arguments, context);
            String resultData = result.success() ? result.data() : "Error: " + result.errorMessage();

            // 3. afterToolCall Hook 链（逆序）
            String processedResult = resultData;
            for (int i = sortedHooks.size() - 1; i >= 0; i--) {
                try {
                    String hookResult = sortedHooks.get(i).afterToolCall(toolName, processedResult, context)
                        .get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (hookResult != null) {
                        processedResult = hookResult;
                    }
                } catch (Exception e) {
                    log.warn("[ToolExecutor] afterToolCall hook error: tool={}, hook={}",
                        toolName, sortedHooks.get(i).getClass().getSimpleName(), e);
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[ToolExecutor] Executed: tool={}, success={}, duration={}ms",
                toolName, result.success(), duration);

            return result.success()
                ? ToolCallResult.success(toolName, arguments, processedResult, duration)
                : ToolCallResult.failure(toolName, arguments, processedResult, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[ToolExecutor] Execution failed: tool={}, duration={}ms", toolName, duration, e);
            return ToolCallResult.failure(toolName, arguments, "工具执行异常: " + e.getMessage(), duration);
        }
    }

    private JsonNode parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(arguments);
        } catch (Exception e) {
            log.warn("[ToolExecutor] Failed to parse tool arguments: {}", arguments, e);
            return objectMapper.createObjectNode();
        }
    }

    /**
     * 获取按 @Order 排序的 Hook 列表
     */
    private List<NvcToolHook> getSortedHooks() {
        // Spring 注入时已按 @Order 排序，直接返回
        return hooks;
    }
}
