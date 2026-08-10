package nvc.guide.modules.nvcassistant.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.trace.TraceSpan;
import nvc.guide.common.trace.TraceSpanManager;
import org.springframework.stereotype.Component;
import reactor.util.context.Context;

import java.util.UUID;

/**
 * Trace 管理器
 *
 * <p>负责 Trace/Span 的创建、关联、完成、异步落库。
 * 使用 ThreadLocal 持有当前 Trace 上下文，组件通过 current() 获取。
 *
 * <p>典型使用流程：
 * <pre>
 * // 1. 开启 Trace（在对话开始时）
 * AgentTraceEntity trace = traceManager.startTrace(sessionId, userId, mode);
 *
 * // 2. 创建 Span（在各组件中）
 * AgentSpanEntity span = traceManager.startSpan("LLM_CALL", "AgentLoop");
 * span.setInputPayload(prompt);
 * // ... 执行操作 ...
 * span.setOutputPayload(response);
 * span.setInputTokens(1200);
 * span.setOutputTokens(180);
 * traceManager.endSpan(span, "SUCCESS", null);
 *
 * // 3. 完成 Trace（在对话结束时）
 * traceManager.endTrace(trace);
 * </pre>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TraceManager implements TraceSpanManager {

    private final TraceStreamProducer traceStreamProducer;
    private final TraceSampler traceSampler;

    private static final ThreadLocal<TraceContext> CURRENT_TRACE = new ThreadLocal<>();

    /** payload 最大长度（超过截断） */
    private static final int PAYLOAD_MAX_LENGTH = 4096;

    /** Reactor Context 中的 trace 上下文 key */
    public static final String TRACE_CONTEXT_KEY = "traceContext";

    /**
     * 开启新 Trace
     *
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param mode      练习模式（FREE_DIALOG / SCENARIO / STRUCTURED）
     * @return 新创建的 Trace 实体
     */
    public AgentTraceEntity startTrace(String sessionId, String userId, String mode) {
        AgentTraceEntity trace = AgentTraceEntity.builder()
            .traceId(UUID.randomUUID().toString())
            .sessionId(sessionId)
            .userId(userId)
            .mode(mode)
            .triggerType("USER_MESSAGE")
            .finalStatus("SUCCESS")
            .build();

        // 采样决策：未命中采样时跳过上下文设置，下游操作自动降级为 no-op
        if (!shouldSample(userId, sessionId)) {
            log.debug("[Trace] Skipped by sampler: traceId={}, session={}, mode={}", trace.getTraceId(), sessionId, mode);
            return trace;
        }

        TraceContext context = new TraceContext(trace);
        CURRENT_TRACE.set(context);

        log.debug("[Trace] Started: traceId={}, session={}, mode={}", trace.getTraceId(), sessionId, mode);
        return trace;
    }

    /**
     * 判断是否应该采样（采样器异常时默认采样，不影响主流程）
     */
    private boolean shouldSample(String userId, String sessionId) {
        try {
            return traceSampler.shouldSample(userId, sessionId);
        } catch (Exception e) {
            log.warn("[Trace] TraceSampler failed, defaulting to sample: userId={}, sessionId={}", userId, sessionId, e);
            return true;
        }
    }

    /**
     * 清理 ThreadLocal，防止内存泄漏
     * 应在请求结束时调用（如 Filter/Interceptor 的 finally 块中）
     */
    public void cleanup() {
        CURRENT_TRACE.remove();
        log.debug("[Trace] ThreadLocal cleaned up");
    }

    /**
     * 获取当前 Trace 上下文（可能为 null）
     * 用于在异步场景下保存上下文
     */
    public TraceContext getTraceContext() {
        return CURRENT_TRACE.get();
    }

    /**
     * 设置 Trace 上下文
     * 用于在异步场景下恢复上下文
     */
    public void setTraceContext(TraceContext context) {
        if (context != null) {
            CURRENT_TRACE.set(context);
        } else {
            CURRENT_TRACE.remove();
        }
    }

    /**
     * 在指定的 Trace 上下文中执行操作
     * 用于在异步场景下恢复上下文执行操作
     */
    public void runWithContext(TraceContext context, Runnable action) {
        TraceContext previous = CURRENT_TRACE.get();
        try {
            CURRENT_TRACE.set(context);
            action.run();
        } finally {
            if (previous != null) {
                CURRENT_TRACE.set(previous);
            } else {
                CURRENT_TRACE.remove();
            }
        }
    }

    /**
     * 创建子 Span
     *
     * @param spanType      Span 类型（INTENT_ROUTING / LLM_CALL / TOOL_CALL / COMPRESSION / EVALUATION）
     * @param componentName 组件名称（IntentRouter / AgentLoop / ToolExecutor 等）
     * @return 新创建的 Span 实体（尚未持久化）
     */
    @Override
    public AgentSpanEntity startSpan(String spanType, String componentName) {
        TraceContext context = CURRENT_TRACE.get();
        if (context == null) {
            log.debug("[Trace] No active trace, skipping span creation: type={}, component={}", spanType, componentName);
            // 返回一个临时 Span，不会被持久化
            return AgentSpanEntity.builder()
                .spanId(UUID.randomUUID().toString())
                .spanType(spanType)
                .componentName(componentName)
                .status("SUCCESS")
                .build();
        }

        AgentSpanEntity span = AgentSpanEntity.builder()
            .spanId(UUID.randomUUID().toString())
            .trace(context.getTrace())
            .sequence(context.nextSequence())
            .spanType(spanType)
            .componentName(componentName)
            .status("SUCCESS")
            .build();

        context.getSpans().add(span);
        log.debug("[Trace] Started span: type={}, component={}, seq={}", spanType, componentName, span.getSequence());
        return span;
    }

    /**
     * 完成 Span
     *
     * @param span   要完成的 Span
     * @param status 状态（SUCCESS / DEGRADED / FAILED）
     * @param failureReason 失败原因（可为 null）
     */
    public void endSpan(AgentSpanEntity span, String status, String failureReason) {
        span.setStatus(status);
        if (failureReason != null) {
            span.setFailureReason(truncate(failureReason));
        }

        // 更新 Trace 的最终状态
        TraceContext context = CURRENT_TRACE.get();
        if (context != null) {
            if ("FAILED".equals(status)) {
                context.getTrace().setFinalStatus("FAILED");
            } else if ("DEGRADED".equals(status) && !"FAILED".equals(context.getTrace().getFinalStatus())) {
                context.getTrace().setFinalStatus("DEGRADED");
            }
        }

        log.debug("[Trace] Ended span: type={}, status={}, duration={}ms",
            span.getSpanType(), status, span.getDurationMs());
    }

    /**
     * 完成 Span（TraceSpanManager 接口实现）
     * 委托给具体的 endSpan(AgentSpanEntity, ...) 方法
     */
    @Override
    public void endSpan(TraceSpan span, String status, String failureReason) {
        if (span instanceof AgentSpanEntity entity) {
            endSpan(entity, status, failureReason);
        } else {
            log.warn("[Trace] endSpan called with non-AgentSpanEntity type: {}", span.getClass().getName());
        }
    }

    /**
     * 完成 Trace，异步落库
     *
     * @param trace 要完成的 Trace
     */
    public void endTrace(AgentTraceEntity trace) {
        TraceContext context = CURRENT_TRACE.get();
        if (context == null) {
            log.warn("[Trace] No active trace context for traceId={}", trace.getTraceId());
            return;
        }

        // 汇总统计
        int totalInputTokens = 0, totalOutputTokens = 0;
        long totalDuration = 0;
        for (AgentSpanEntity span : context.getSpans()) {
            if (span.getInputTokens() != null) totalInputTokens += span.getInputTokens();
            if (span.getOutputTokens() != null) totalOutputTokens += span.getOutputTokens();
            totalDuration += span.getDurationMs();
        }

        trace.setTotalSpans(context.getSpans().size());
        trace.setTotalDurationMs(totalDuration);
        trace.setTotalInputTokens(totalInputTokens);
        trace.setTotalOutputTokens(totalOutputTokens);

        // 异步落库
        try {
            traceStreamProducer.sendTrace(trace, context.getSpans());
            log.info("[Trace] Ended and queued: traceId={}, spans={}, duration={}ms, status={}",
                trace.getTraceId(), trace.getTotalSpans(), trace.getTotalDurationMs(), trace.getFinalStatus());
        } catch (Exception e) {
            log.error("[Trace] Failed to queue trace for persistence: traceId={}", trace.getTraceId(), e);
        } finally {
            CURRENT_TRACE.remove();
        }
    }

    /**
     * 获取当前 Trace 上下文
     *
     * @return 当前上下文，如果没有活跃 Trace 则返回 null
     */
    public TraceContext current() {
        return CURRENT_TRACE.get();
    }

    /**
     * 截断过长的字符串
     */
    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > PAYLOAD_MAX_LENGTH ? s.substring(0, PAYLOAD_MAX_LENGTH) + "..." : s;
    }
}
