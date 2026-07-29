package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 日志钩子 — 记录工具调用详情
 *
 * <p>Order=7（最后执行，只记录不干预）
 */
@Component
@Slf4j
@Order(7)
public class LoggingToolHook implements NvcToolHook {

    @Override
    public CompletableFuture<ToolCallDecision> beforeToolCall(String toolName, JsonNode arguments, NvcToolContext context) {
        log.info("[ToolHook] BEFORE: tool={}, userId={}, args={}", toolName, context.getUserId(), arguments);
        return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
    }

    @Override
    public CompletableFuture<String> afterToolCall(String toolName, String result, NvcToolContext context) {
        int resultLen = result != null ? result.length() : 0;
        log.info("[ToolHook] AFTER: tool={}, userId={}, resultLength={}", toolName, context.getUserId(), resultLen);
        return CompletableFuture.completedFuture(result);
    }
}
