package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcassistant.model.NvcToolCallRecordEntity;
import nvc.guide.modules.nvcassistant.repository.NvcToolCallRecordRepository;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 持久化钩子 — 保存工具调用记录到数据库
 *
 * <p>Order=6（在日志钩子之前）
 */
@Component
@Slf4j
@Order(6)
@RequiredArgsConstructor
public class PersistToolHook implements NvcToolHook {

    private final NvcToolCallRecordRepository recordRepository;

    @Override
    public CompletableFuture<ToolCallDecision> beforeToolCall(String toolName, JsonNode arguments, NvcToolContext context) {
        context.setAttribute("toolCallStartTime", System.currentTimeMillis());
        context.setAttribute("toolCallArguments", arguments != null ? arguments.toString() : "{}");
        return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
    }

    @Override
    public CompletableFuture<String> afterToolCall(String toolName, String result, NvcToolContext context) {
        try {
            Long startTime = context.getAttribute("toolCallStartTime");
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
            String arguments = context.getAttribute("toolCallArguments");

            boolean success = result != null && !result.startsWith("Error:");

            NvcToolCallRecordEntity record = NvcToolCallRecordEntity.builder()
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .toolName(toolName)
                .arguments(arguments)
                .result(truncate(result, 4000))
                .success(success)
                .durationMs(duration)
                .build();

            recordRepository.save(record);
            log.debug("[PersistToolHook] Saved: tool={}, success={}, duration={}ms", toolName, success, duration);
        } catch (Exception e) {
            log.error("[PersistToolHook] Failed to save tool call record: tool={}", toolName, e);
        }
        return CompletableFuture.completedFuture(result);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
