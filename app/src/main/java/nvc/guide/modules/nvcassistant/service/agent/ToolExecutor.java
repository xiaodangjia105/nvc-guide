package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcassistant.metrics.MetricsCollector;
import nvc.guide.modules.nvcassistant.trace.AgentSpanEntity;
import nvc.guide.modules.nvcassistant.trace.PayloadTruncator;
import nvc.guide.modules.nvcassistant.trace.TraceManager;
import nvc.guide.modules.nvcassistant.trace.TraceProperties;
import nvc.guide.modules.nvcpractice.tool.NvcTool;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import nvc.guide.modules.nvcpractice.tool.NvcToolResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    private final TraceManager traceManager;
    private final TraceProperties traceProperties;
    private final PayloadTruncator payloadTruncator;

    /** 单个工具超时（毫秒） */
    private static final long TOOL_TIMEOUT_MS = 30_000;

    /** 工具执行专用线程池（避免使用 ForkJoinPool.commonPool） */
    private final ExecutorService toolExecutorPool = new ThreadPoolExecutor(
        4, 16, 60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(100),
        r -> {
            Thread t = new Thread(r, "tool-executor");
            t.setDaemon(true);
            return t;
        },
        new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @PreDestroy
    public void shutdown() {
        toolExecutorPool.shutdown();
        try {
            if (!toolExecutorPool.awaitTermination(10, TimeUnit.SECONDS)) {
                toolExecutorPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            toolExecutorPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

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

        // 并行执行所有工具调用（使用专用线程池，避免占用 ForkJoinPool）
        List<CompletableFuture<ToolCallResult>> futures = toolCalls.stream()
            .map(tc -> CompletableFuture.supplyAsync(() -> {
                NvcToolContext ctx = NvcToolContext.builder()
                    .userId(userId)
                    .sessionId(sessionId)
                    .build();
                return executeSingle(tc, ctx);
            }, toolExecutorPool))
            .toList();

        // 收集结果（保持顺序），单个失败不影响其他结果
        List<ToolCallResult> results = futures.stream()
            .map(f -> {
                try {
                    return f.get(TOOL_TIMEOUT_MS + 5000, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    return ToolCallResult.failure("unknown", "{}", "执行超时或失败: " + e.getMessage(), 0);
                }
            })
            .toList();

        // 采集工具调用指标 + Trace 埋点
        for (ToolCallResult result : results) {
            try {
                metricsCollector.recordToolCall(
                    String.valueOf(sessionId),
                    result.toolName(),
                    result.success(),
                    result.durationMs(),
                    null);

                // Trace 埋点（详细信息）
                AgentSpanEntity toolSpan = traceManager.startSpan("TOOL_CALL", "ToolExecutor");
                toolSpan.setDurationMs(result.durationMs());

                // 记录输入 payload（工具名 + 参数）
                boolean detailedTrace = traceProperties.shouldRecordDetailed("TOOL_CALL", userId, sessionId);
                TraceProperties.SpanConfig spanConfig = traceProperties.getSpanConfig("TOOL_CALL");

                if (detailedTrace) {
                    // 构建详细输入 payload
                    Map<String, Object> inputPayload = new HashMap<>();
                    inputPayload.put("toolName", result.toolName());
                    inputPayload.put("arguments", result.arguments());
                    if (result.hookRecords() != null && !result.hookRecords().isEmpty()) {
                        inputPayload.put("hookChain", result.hookRecords());
                    }
                    String inputJson = objectMapper.writeValueAsString(inputPayload);
                    toolSpan.setInputPayload(payloadTruncator.truncate(inputJson, spanConfig.getPayloadMaxLength(), "TOOL_CALL"));

                    // 记录输出 payload（结果）
                    Map<String, Object> outputPayload = new HashMap<>();
                    outputPayload.put("success", result.success());
                    outputPayload.put("result", result.result());
                    if (result.skipReason() != null) {
                        outputPayload.put("skipReason", result.skipReason());
                    }
                    String outputJson = objectMapper.writeValueAsString(outputPayload);
                    toolSpan.setOutputPayload(payloadTruncator.truncate(outputJson, spanConfig.getPayloadMaxLength(), "TOOL_CALL"));

                    // 记录 metadata
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("hookCount", result.hookRecords() != null ? result.hookRecords().size() : 0);
                    metadata.put("skipped", result.skipped());
                    metadata.put("userId", userId);
                    metadata.put("sessionId", sessionId);
                    toolSpan.setMetadata(objectMapper.writeValueAsString(metadata));
                } else {
                    // 基础级别：只记录工具名
                    Map<String, Object> basicInput = new HashMap<>();
                    basicInput.put("toolName", result.toolName());
                    toolSpan.setInputPayload(objectMapper.writeValueAsString(basicInput));
                }

                traceManager.endSpan(toolSpan,
                    result.success() ? "SUCCESS" : "FAILED",
                    result.success() ? null : result.result());
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

        // Hook 执行记录（用于 trace）
        List<Map<String, Object>> hookRecords = new ArrayList<>();
        boolean detailedTrace = traceProperties.shouldRecordDetailed("TOOL_CALL", context.getUserId(), context.getSessionId());

        try {
            JsonNode argsNode = parseArguments(arguments);

            // 1. beforeToolCall Hook 链（按 @Order 排序）
            List<NvcToolHook> sortedHooks = getSortedHooks();
            for (NvcToolHook hook : sortedHooks) {
                String hookName = hook.getClass().getSimpleName();
                long hookStart = System.currentTimeMillis();

                try {
                    NvcToolHook.ToolCallDecision decision = hook.beforeToolCall(toolName, argsNode, context)
                        .get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    long hookDuration = System.currentTimeMillis() - hookStart;

                    // 记录 Hook 执行
                    if (detailedTrace) {
                        Map<String, Object> hookRecord = new HashMap<>();
                        hookRecord.put("hook", hookName);
                        hookRecord.put("phase", "before");
                        hookRecord.put("decision", decision.name());
                        hookRecord.put("durationMs", hookDuration);
                        hookRecords.add(hookRecord);
                    }

                    if (decision == NvcToolHook.ToolCallDecision.SKIP) {
                        long duration = System.currentTimeMillis() - startTime;
                        // 检查是否有缓存结果（CacheToolHook 命中时设置）
                        String cachedResult = context.getAttribute("cachedResult");
                        if (cachedResult != null) {
                            log.info("[ToolExecutor] SKIPPED (cached): tool={}, duration={}ms", toolName, duration);
                            return ToolCallResult.success(toolName, arguments, cachedResult, duration, hookRecords);
                        }
                        String skipReason = context.hasAttribute("skipReason")
                            ? context.getAttribute("skipReason").toString()
                            : "Hook 跳过";
                        log.info("[ToolExecutor] SKIPPED: tool={}, reason={}, duration={}ms",
                            toolName, skipReason, duration);
                        return ToolCallResult.skipped(toolName, arguments, skipReason, hookRecords);
                    }
                } catch (Exception e) {
                    long hookDuration = System.currentTimeMillis() - hookStart;
                    log.warn("[ToolExecutor] beforeToolCall hook error: tool={}, hook={}",
                        toolName, hookName, e);
                    // Hook 异常不阻断执行
                    if (detailedTrace) {
                        Map<String, Object> hookRecord = new HashMap<>();
                        hookRecord.put("hook", hookName);
                        hookRecord.put("phase", "before");
                        hookRecord.put("decision", "ERROR");
                        hookRecord.put("durationMs", hookDuration);
                        hookRecord.put("error", e.getMessage());
                        hookRecords.add(hookRecord);
                    }
                }
            }

            // 2. 执行工具
            NvcTool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                long duration = System.currentTimeMillis() - startTime;
                return ToolCallResult.failure(toolName, arguments, "工具不存在: " + toolName, duration, hookRecords);
            }

            NvcToolResult result = tool.execute(arguments, context);
            String resultData = result.success() ? result.data() : "Error: " + result.errorMessage();

            // 3. afterToolCall Hook 链（逆序）
            String processedResult = resultData;
            for (int i = sortedHooks.size() - 1; i >= 0; i--) {
                NvcToolHook hook = sortedHooks.get(i);
                String hookName = hook.getClass().getSimpleName();
                long hookStart = System.currentTimeMillis();

                try {
                    String hookResult = hook.afterToolCall(toolName, processedResult, context)
                        .get(TOOL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    long hookDuration = System.currentTimeMillis() - hookStart;

                    // 记录 Hook 执行
                    if (detailedTrace) {
                        Map<String, Object> hookRecord = new HashMap<>();
                        hookRecord.put("hook", hookName);
                        hookRecord.put("phase", "after");
                        hookRecord.put("decision", hookResult != null ? "MODIFIED" : "PASSTHROUGH");
                        hookRecord.put("durationMs", hookDuration);
                        hookRecords.add(hookRecord);
                    }

                    if (hookResult != null) {
                        processedResult = hookResult;
                    }
                } catch (Exception e) {
                    long hookDuration = System.currentTimeMillis() - hookStart;
                    log.warn("[ToolExecutor] afterToolCall hook error: tool={}, hook={}",
                        toolName, hookName, e);
                    if (detailedTrace) {
                        Map<String, Object> hookRecord = new HashMap<>();
                        hookRecord.put("hook", hookName);
                        hookRecord.put("phase", "after");
                        hookRecord.put("decision", "ERROR");
                        hookRecord.put("durationMs", hookDuration);
                        hookRecord.put("error", e.getMessage());
                        hookRecords.add(hookRecord);
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("[ToolExecutor] Executed: tool={}, success={}, duration={}ms",
                toolName, result.success(), duration);

            return result.success()
                ? ToolCallResult.success(toolName, arguments, processedResult, duration, hookRecords)
                : ToolCallResult.failure(toolName, arguments, processedResult, duration, hookRecords);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[ToolExecutor] Execution failed: tool={}, duration={}ms", toolName, duration, e);
            return ToolCallResult.failure(toolName, arguments, "工具执行异常: " + e.getMessage(), duration, hookRecords);
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
