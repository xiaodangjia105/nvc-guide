package nvc.guide.modules.nvcassistant.service.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcassistant.dto.ToolCallRecord;
import nvc.guide.modules.nvcassistant.fallback.LlmCallContext;
import nvc.guide.modules.nvcassistant.fallback.LlmFallbackHandler;
import nvc.guide.modules.nvcassistant.metrics.MetricsCollector;
import nvc.guide.modules.nvcassistant.trace.AgentSpanEntity;
import nvc.guide.modules.nvcassistant.trace.TraceManager;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.repository.NvcAgentConfigRepository;
import nvc.guide.modules.nvcpractice.tool.NvcTool;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 主循环 — 控制多轮工具调用
 *
 * <p>核心逻辑：
 * <ol>
 *   <li>构建上下文（系统 Prompt + 历史 + 用户消息）</li>
 *   <li>调用 LLM（internalToolExecutionEnabled=false，不自动执行工具）</li>
 *   <li>如果 LLM 返回 toolCalls → 通过 ToolExecutor 执行 → 结果加入上下文 → 回到步骤 2</li>
 *   <li>如果 LLM 返回 content → 结束循环</li>
 * </ol>
 *
 * <p>参考：Pi Agent Loop（agent-loop.ts）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentLoop {

    private final LlmProviderRegistry llmProviderRegistry;
    private final NvcToolRegistry toolRegistry;
    private final ToolExecutor toolExecutor;
    private final IntentRouter intentRouter;
    private final NvcAgentConfigRepository agentConfigRepository;
    private final MetricsCollector metricsCollector;
    private final TraceManager traceManager;
    private final LlmFallbackHandler fallbackHandler;

    /** 最大工具调用轮数 */
    private static final int MAX_TOOL_CALL_TURNS = 10;
    /** 总超时（毫秒） */
    private static final long TOTAL_TIMEOUT_MS = 120_000;

    /**
     * 执行 Agent 循环（返回 SSE 事件流）
     *
     * @param userId         用户 ID
     * @param conversationId 对话 ID
     * @param contextMessages 上下文消息列表（由 NvcAssistantService 组装：SystemPrompt + 历史，不含当前用户消息）
     * @param userMessage    当前用户消息
     * @return SSE 事件流
     */
    public Flux<AgentEvent> executeStream(Long userId, Long conversationId,
                                           List<Message> contextMessages, String userMessage) {
        return Flux.<AgentEvent>deferContextual(ctx -> {
            // 从 Reactor Context 中恢复 trace 上下文到 ThreadLocal
            var traceContextOpt = ctx.getOrEmpty("traceContext");
            if (traceContextOpt.isPresent()) {
                traceManager.setTraceContext((nvc.guide.modules.nvcassistant.trace.TraceContext) traceContextOpt.get());
            } else {
                // 清理可能存在的脏数据
                traceManager.cleanup();
            }
            return Flux.<AgentEvent>create(sink -> {
                long startTime = System.currentTimeMillis();
                try {
                    // 1. 构建完整消息列表
                    List<Message> messages = new ArrayList<>(contextMessages);
                    messages.add(new UserMessage(userMessage));

                // 2. 意图预路由：高置信度意图直接执行工具，跳过 LLM
                IntentRouter.IntentMatch intentMatch = intentRouter.detectIntent(userMessage);
                if (intentMatch != null) {
                    NvcTool directTool = toolRegistry.getTool(intentMatch.toolName());
                    if (directTool != null) {
                        log.info("[AgentLoop] IntentRouter matched: tool={}, reason={}",
                            intentMatch.toolName(), intentMatch.reason());
                        String intentCallId = "intent-" + System.currentTimeMillis();
                        sink.next(AgentEvent.thinking("正在处理..."));
                        sink.next(AgentEvent.toolcallStart(intentCallId, intentMatch.toolName(), intentMatch.arguments()));

                        // 通过 ToolExecutor 执行（保留 Hook 链：缓存、限流、日志等）
                        AssistantMessage.ToolCall syntheticTc = new AssistantMessage.ToolCall(
                            intentCallId, "function",
                            intentMatch.toolName(), intentMatch.arguments());
                        List<ToolCallResult> results = toolExecutor.execute(
                            List.of(syntheticTc), userId, conversationId);

                        ToolCallResult result = results.get(0);
                        sink.next(AgentEvent.toolcallEnd(
                            intentCallId, result.toolName(), result.success(),
                            truncateResult(result.result()), result.durationMs()));

                        String responseText = result.success()
                            ? result.result()
                            : "操作失败: " + result.result();
                        sink.next(AgentEvent.content(responseText));
                        sink.next(AgentEvent.done(conversationId));
                        sink.complete();
                        return;
                    }
                    // 工具不存在，降级到 LLM 循环
                    log.warn("[AgentLoop] IntentRouter matched tool '{}' but not found in registry, falling through to LLM",
                        intentMatch.toolName());
                }

                log.info("[AgentLoop] Sending {} messages to LLM: userId={}, conversationId={}",
                    messages.size(), userId, conversationId);

                // 构建练习上下文标识（供 callLlm 使用）
                nvc.guide.common.PracticeContext agentCtx =
                    new nvc.guide.common.PracticeContext(conversationId, userId, "dialog");

                // 3. 发送 thinking 事件
                sink.next(AgentEvent.thinking("正在思考..."));

                // 4. 循环调用 LLM
                int turn = 0;
                List<ToolCallRecord> allToolCalls = new ArrayList<>();

                while (turn < MAX_TOOL_CALL_TURNS) {
                    // 检查客户端是否已断开
                    if (sink.isCancelled()) {
                        log.info("[AgentLoop] Sink cancelled, stopping: userId={}, turn={}", userId, turn);
                        break;
                    }

                    // 检查总超时
                    if (System.currentTimeMillis() - startTime > TOTAL_TIMEOUT_MS) {
                        log.warn("[AgentLoop] Total timeout: userId={}, turns={}, elapsed={}ms",
                            userId, turn, System.currentTimeMillis() - startTime);
                        sink.next(AgentEvent.error("对话超时，请重试"));
                        break;
                    }

                    // 调用 LLM（含 Trace 埋点 + Fallback 降级）
                    long llmStartTime = System.currentTimeMillis();
                    AgentSpanEntity llmSpan = traceManager.startSpan("LLM_CALL", "AgentLoop");
                    ChatResponse response;
                    try {
                        LlmCallContext fallbackCtx = LlmCallContext.builder()
                            .sessionId(String.valueOf(conversationId))
                            .componentName("AgentLoop")
                            .scene("dialog")
                            .build();
                        response = fallbackHandler.executeWithFallback(
                            () -> callLlm(messages, agentCtx),
                            () -> buildFallbackResponse(),
                            fallbackCtx);
                        llmSpan.setDurationMs(System.currentTimeMillis() - llmStartTime);
                        // 采集 Token 消耗
                        if (response != null && response.getMetadata() != null
                            && response.getMetadata().getUsage() != null) {
                            var usage = response.getMetadata().getUsage();
                            int inputTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
                            int outputTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
                            llmSpan.setInputTokens(inputTokens);
                            llmSpan.setOutputTokens(outputTokens);
                            metricsCollector.recordLlmCall(
                                String.valueOf(conversationId), null,
                                inputTokens, outputTokens, "default", false);
                        }
                        traceManager.endSpan(llmSpan, "SUCCESS", null);
                    } catch (Exception e) {
                        llmSpan.setDurationMs(System.currentTimeMillis() - llmStartTime);
                        traceManager.endSpan(llmSpan, "FAILED", e.getMessage());
                        throw e;
                    }

                    // 检查是否有工具调用
                    AssistantMessage assistantMessage = getAssistantMessage(response);
                    if (assistantMessage != null && assistantMessage.hasToolCalls()) {
                        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

                        log.info("[AgentLoop] Turn {}: LLM requested {} tool calls: {}",
                            turn, toolCalls.size(),
                            toolCalls.stream().map(tc -> tc.name() + "(" + tc.arguments() + ")").toList());

                        // 发送工具调用开始事件
                        for (AssistantMessage.ToolCall tc : toolCalls) {
                            sink.next(AgentEvent.toolcallStart(tc.id(), tc.name(), tc.arguments()));
                        }

                        // 执行工具
                        List<ToolCallResult> results = toolExecutor.execute(toolCalls, userId, conversationId);

                        // 发送工具调用结束事件 + 构建工具结果消息
                        List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                        for (int i = 0; i < results.size(); i++) {
                            ToolCallResult result = results.get(i);
                            AssistantMessage.ToolCall tc = toolCalls.get(i);

                            // SSE 事件
                            sink.next(AgentEvent.toolcallEnd(
                                tc.id(), result.toolName(), result.success(), truncateResult(result.result()), result.durationMs()));

                            // 记录
                            allToolCalls.add(ToolCallRecord.builder()
                                .toolName(result.toolName())
                                .arguments(result.arguments())
                                .result(truncateResult(result.result()))
                                .success(result.success())
                                .durationMs(result.durationMs())
                                .build());

                            // 工具结果消息（注入明确指令，防止 LLM 幻觉）
                            // 注意：ToolExecutor 已经为失败结果添加了 "Error: " 前缀，此处不再重复
                            String responseText = result.skipped()
                                ? "跳过: " + result.skipReason()
                                : result.result();

                            // 当工具返回空结果或"没有找到"时，注入明确指令
                            if (result.success() && isEmptyResult(result.result())) {
                                responseText = result.result()
                                    + "\n\n[系统指令] 搜索结果为空。请如实告知用户未找到相关内容，"
                                    + "并询问是否要尝试其他搜索词。不要调用其他无关工具，不要编造内容。";
                            }

                            toolResponses.add(new ToolResponseMessage.ToolResponse(
                                tc.id(), tc.name(), responseText));
                        }

                        // 更新上下文：助手消息 + 工具结果消息
                        messages.add(assistantMessage);
                        messages.add(ToolResponseMessage.builder().responses(toolResponses).build());

                        turn++;
                    } else {
                        // 无工具调用，提取文本内容并结束
                        String content = extractContent(response);
                        log.info("[AgentLoop] Turn {}: LLM returned content (length={}): {}",
                            turn, content != null ? content.length() : 0,
                            content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content);
                        if (content != null && !content.isEmpty()) {
                            sink.next(AgentEvent.content(content));
                        }
                        sink.next(AgentEvent.done(conversationId));
                        break;
                    }
                }

                // 检查是否达到最大轮数
                if (turn >= MAX_TOOL_CALL_TURNS) {
                    log.warn("[AgentLoop] Max turns reached: userId={}, turns={}", userId, turn);
                    sink.next(AgentEvent.error("工具调用次数过多，请简化请求"));
                    sink.complete();
                    return;
                }

                // 记录端到端延迟
                long e2eLatency = System.currentTimeMillis() - startTime;
                try {
                    metricsCollector.recordLatency(String.valueOf(conversationId), e2eLatency, "e2e");
                } catch (Exception e) {
                    log.debug("[AgentLoop] Failed to record latency metric: {}", e.getMessage());
                }

                sink.complete();
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.error("[AgentLoop] Failed: userId={}, elapsed={}ms", userId, elapsed, e);
                // 不泄露内部异常消息给客户端
                sink.next(AgentEvent.error("对话出错，请稍后重试"));
                sink.complete();
            }
        }).doFinally(signal -> {
            // 清理 trace 上下文，防止内存泄漏
            traceManager.cleanup();
        });
        });
    }

    /**
     * 调用 LLM（不自动执行工具）
     */
    private ChatResponse callLlm(List<Message> messages, nvc.guide.common.PracticeContext ctx) {
        Long userId = ctx.userId();
        Long conversationId = ctx.sessionId();
        ChatClient client = llmProviderRegistry.getDefaultChatClient();

        // 调试日志：显示可用工具（降级为 debug，避免热路径过度日志）
        List<org.springframework.ai.tool.ToolCallback> toolCallbacks = toolRegistry.toFunctionCallbacks();
        log.debug("[AgentLoop] Available tools: {}", toolCallbacks.stream()
            .map(tc -> tc.getToolDefinition().name())
            .toList());

        // 从数据库读取主 Agent 配置，若无则使用默认值
        double temperature = 0.3;
        int maxTokens = 2000;
        double topP = 0.9;
        try {
            NvcAgentConfigEntity config = agentConfigRepository
                .findByAgentScene(NvcAgentScene.MAIN_ASSISTANT)
                .orElse(null);
            if (config != null && Boolean.TRUE.equals(config.getIsEnabled())) {
                temperature = config.getTemperature() != null ? config.getTemperature() : temperature;
                maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : maxTokens;
                topP = config.getTopP() != null ? config.getTopP() : topP;
                log.debug("[AgentLoop] Using DB config: temperature={}, maxTokens={}, topP={}",
                    temperature, maxTokens, topP);
            }
        } catch (Exception e) {
            log.warn("[AgentLoop] Failed to load agent config, using defaults", e);
        }

        // 构建 ToolContext，注入 userId/sessionId（确保工具执行时能获取用户信息）
        java.util.Map<String, Object> toolContextMap = new java.util.HashMap<>();
        toolContextMap.put("nvc.userId", userId);
        toolContextMap.put("nvc.sessionId", conversationId);

        return client.prompt()
            .messages(messages)
            .toolContext(toolContextMap)
            .options(OpenAiChatOptions.builder()
                .temperature(temperature)
                .maxTokens(maxTokens)
                .topP(topP)
                // 提供工具定义（让 LLM 知道有哪些工具可用）
                .toolCallbacks(toolCallbacks)
                // 禁用自动工具执行（我们自己处理工具调用）
                .internalToolExecutionEnabled(false)
                .build())
            .call()
            .chatResponse();
    }

    /**
     * 从 ChatResponse 中提取 AssistantMessage
     */
    private AssistantMessage getAssistantMessage(ChatResponse response) {
        if (response == null || response.getResults().isEmpty()) {
            return null;
        }
        return response.getResults().get(0).getOutput();
    }

    /**
     * 从 ChatResponse 中提取文本内容
     */
    private String extractContent(ChatResponse response) {
        if (response == null) {
            return null;
        }
        Generation generation = response.getResult();
        if (generation == null || generation.getOutput() == null) {
            return null;
        }
        String content = generation.getOutput().getText();
        return content != null ? content.trim() : null;
    }

    /**
     * 截断过长的结果
     */
    private String truncateResult(String result) {
        if (result == null) return "";
        return result.length() > 2000 ? result.substring(0, 2000) + "..." : result;
    }

    /**
     * 构建降级响应（LLM 调用失败时使用）
     */
    private ChatResponse buildFallbackResponse() {
        // 返回一个简单的降级响应，让循环正常结束
        AssistantMessage output = new AssistantMessage(
            "【降级模式】AI 服务暂时不可用，以下是 NVC 引导提示：\n\n"
            + "让我们先停下来，客观描述一下刚才发生了什么？注意区分事实和评价哦。");
        Generation generation = new Generation(output);
        return org.springframework.ai.chat.model.ChatResponse.builder()
            .generations(List.of(generation))
            .build();
    }

    /**
     * 判断工具结果是否为空/未找到
     */
    private boolean isEmptyResult(String result) {
        if (result == null || result.isBlank()) return true;
        String lower = result.toLowerCase();
        return lower.contains("没有找到")
            || lower.contains("未找到")
            || lower.contains("no results")
            || lower.contains("not found")
            || lower.equals("[]")
            || lower.equals("{}");
    }
}
