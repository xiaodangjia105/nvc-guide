package nvc.guide.modules.nvcassistant.service.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcassistant.dto.ToolCallRecord;
import nvc.guide.modules.nvcpractice.tool.NvcToolRegistry;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final NvcProfileService profileService;

    @Value("classpath:prompts/nvc-assistant-system.st")
    private Resource systemPromptResource;

    /** 缓存的系统 Prompt 模板 */
    private volatile String cachedSystemPromptTemplate;

    /** 最大工具调用轮数 */
    private static final int MAX_TOOL_CALL_TURNS = 10;
    /** 总超时（毫秒） */
    private static final long TOTAL_TIMEOUT_MS = 120_000;

    /**
     * 执行 Agent 循环（返回 SSE 事件流）
     *
     * @param userId         用户 ID
     * @param conversationId 对话 ID
     * @param contextMessages 上下文消息列表（系统 Prompt + 历史，不含当前用户消息）
     * @param userMessage    当前用户消息
     * @return SSE 事件流
     */
    public Flux<AgentEvent> executeStream(Long userId, Long conversationId,
                                           List<Message> contextMessages, String userMessage) {
        return Flux.create(sink -> {
            long startTime = System.currentTimeMillis();
            try {
                // 1. 构建完整消息列表
                List<Message> messages = new ArrayList<>(contextMessages);
                messages.add(new UserMessage(userMessage));

                // 2. 发送 thinking 事件
                sink.next(AgentEvent.thinking("正在思考..."));

                // 3. 循环调用 LLM
                int turn = 0;
                List<ToolCallRecord> allToolCalls = new ArrayList<>();

                while (turn < MAX_TOOL_CALL_TURNS) {
                    // 检查总超时
                    if (System.currentTimeMillis() - startTime > TOTAL_TIMEOUT_MS) {
                        log.warn("[AgentLoop] Total timeout: userId={}, turns={}, elapsed={}ms",
                            userId, turn, System.currentTimeMillis() - startTime);
                        sink.next(AgentEvent.error("对话超时，请重试"));
                        break;
                    }

                    // 调用 LLM
                    ChatResponse response = callLlm(messages);

                    // 检查是否有工具调用
                    AssistantMessage assistantMessage = getAssistantMessage(response);
                    if (assistantMessage != null && assistantMessage.hasToolCalls()) {
                        List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();

                        log.info("[AgentLoop] Turn {}: LLM requested {} tool calls: {}",
                            turn, toolCalls.size(),
                            toolCalls.stream().map(AssistantMessage.ToolCall::name).toList());

                        // 发送工具调用开始事件
                        for (AssistantMessage.ToolCall tc : toolCalls) {
                            sink.next(AgentEvent.toolcallStart(tc.name(), tc.arguments()));
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
                                result.toolName(), result.success(), truncateResult(result.result()), result.durationMs()));

                            // 记录
                            allToolCalls.add(ToolCallRecord.builder()
                                .toolName(result.toolName())
                                .arguments(result.arguments())
                                .result(truncateResult(result.result()))
                                .success(result.success())
                                .durationMs(result.durationMs())
                                .build());

                            // 工具结果消息
                            String responseText = result.skipped()
                                ? "跳过: " + result.skipReason()
                                : (result.success() ? result.result() : "Error: " + result.result());
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
                }

                sink.complete();
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - startTime;
                log.error("[AgentLoop] Failed: userId={}, elapsed={}ms", userId, elapsed, e);
                sink.next(AgentEvent.error("对话出错: " + e.getMessage()));
                sink.complete();
            }
        });
    }

    /**
     * 调用 LLM（不自动执行工具）
     */
    private ChatResponse callLlm(List<Message> messages) {
        ChatClient client = llmProviderRegistry.getDefaultChatClient();

        return client.prompt()
            .messages(messages)
            .options(OpenAiChatOptions.builder()
                .temperature(0.7)
                .maxTokens(2000)
                .topP(0.9)
                // 提供工具定义（让 LLM 知道有哪些工具可用）
                .toolCallbacks(toolRegistry.toFunctionCallbacks())
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
        Generation generation = response.getResult();
        if (generation == null) {
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
}
