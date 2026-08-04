package nvc.guide.modules.nvcassistant.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.async.AbstractStreamProducer;
import nvc.guide.common.constant.AsyncTaskStreamConstants;
import nvc.guide.infrastructure.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent Trace 异步生产者
 * 将 Trace + Span 数据推送到 Redis Stream，由 TraceStreamConsumer 批量落库
 */
@Component
@Slf4j
public class TraceStreamProducer extends AbstractStreamProducer<TraceStreamProducer.TracePayload> {

    private final ObjectMapper objectMapper;

    public TraceStreamProducer(RedisService redisService, ObjectMapper objectMapper) {
        super(redisService);
        this.objectMapper = objectMapper;
    }

    /**
     * Trace 载荷（包含 Trace 和 Span 列表）
     */
    public record TracePayload(AgentTraceEntity trace, List<AgentSpanEntity> spans) {}

    /**
     * 发送 Trace 数据到 Redis Stream
     */
    public void sendTrace(AgentTraceEntity trace, List<AgentSpanEntity> spans) {
        sendTask(new TracePayload(trace, spans));
    }

    @Override
    protected String taskDisplayName() {
        return "Agent Trace";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.TRACE_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(TracePayload payload) {
        try {
            Map<String, String> message = new LinkedHashMap<>();
            message.put("traceId", payload.trace().getTraceId());
            message.put("sessionId", payload.trace().getSessionId());
            message.put("userId", payload.trace().getUserId());
            message.put("mode", payload.trace().getMode());
            message.put("triggerType", payload.trace().getTriggerType());
            message.put("totalSpans", String.valueOf(payload.trace().getTotalSpans()));
            message.put("totalDurationMs", String.valueOf(payload.trace().getTotalDurationMs()));
            message.put("totalInputTokens", String.valueOf(payload.trace().getTotalInputTokens()));
            message.put("totalOutputTokens", String.valueOf(payload.trace().getTotalOutputTokens()));
            message.put("finalStatus", payload.trace().getFinalStatus());
            // Span 列表序列化为 JSON
            message.put("spans", objectMapper.writeValueAsString(payload.spans()));
            return message;
        } catch (Exception e) {
            log.error("Failed to serialize trace payload: traceId={}", payload.trace().getTraceId(), e);
            return Map.of("traceId", payload.trace().getTraceId(), "error", "serialization_failed");
        }
    }

    @Override
    protected String payloadIdentifier(TracePayload payload) {
        return "traceId=" + payload.trace().getTraceId();
    }

    @Override
    protected void onSendFailed(TracePayload payload, String error) {
        log.error("Agent Trace 发送失败: traceId={}, error={}", payload.trace().getTraceId(), error);
    }
}
