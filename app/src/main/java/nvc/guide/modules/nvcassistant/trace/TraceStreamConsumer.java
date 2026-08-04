package nvc.guide.modules.nvcassistant.trace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.async.AbstractStreamConsumer;
import nvc.guide.common.constant.AsyncTaskStreamConstants;
import nvc.guide.infrastructure.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent Trace 异步消费者
 * 从 Redis Stream 消费 Trace 数据，批量写入 PostgreSQL
 */
@Component
@Slf4j
public class TraceStreamConsumer extends AbstractStreamConsumer<TraceStreamProducer.TracePayload> {

    private final AgentTraceRepository traceRepository;
    private final AgentSpanRepository spanRepository;
    private final ObjectMapper objectMapper;

    public TraceStreamConsumer(RedisService redisService,
                               AgentTraceRepository traceRepository,
                               AgentSpanRepository spanRepository,
                               ObjectMapper objectMapper) {
        super(redisService);
        this.traceRepository = traceRepository;
        this.spanRepository = spanRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    protected String taskDisplayName() {
        return "Agent Trace 落库";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.TRACE_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.TRACE_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.TRACE_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "nvc-trace-consumer";
    }

    @Override
    protected TraceStreamProducer.TracePayload parsePayload(StreamMessageId messageId, Map<String, String> data) {
        try {
            AgentTraceEntity trace = AgentTraceEntity.builder()
                .traceId(data.get("traceId"))
                .sessionId(data.get("sessionId"))
                .userId(data.get("userId"))
                .mode(data.get("mode"))
                .triggerType(data.get("triggerType"))
                .totalSpans(parseInt(data.get("totalSpans")))
                .totalDurationMs(parseLong(data.get("totalDurationMs")))
                .totalInputTokens(parseInt(data.get("totalInputTokens")))
                .totalOutputTokens(parseInt(data.get("totalOutputTokens")))
                .finalStatus(data.get("finalStatus"))
                .build();

            List<AgentSpanEntity> spans = objectMapper.readValue(
                data.get("spans"), new TypeReference<>() {});

            return new TraceStreamProducer.TracePayload(trace, spans);
        } catch (Exception e) {
            log.error("Failed to parse trace payload: data={}", data, e);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(TraceStreamProducer.TracePayload payload) {
        return "traceId=" + payload.trace().getTraceId();
    }

    @Override
    protected void markProcessing(TraceStreamProducer.TracePayload payload) {
        log.debug("Agent Trace 开始落库: traceId={}", payload.trace().getTraceId());
    }

    @Override
    protected void processBusiness(TraceStreamProducer.TracePayload payload) {
        AgentTraceEntity trace = payload.trace();
        trace.setCreatedAt(LocalDateTime.now());
        traceRepository.save(trace);

        for (AgentSpanEntity span : payload.spans()) {
            span.setTrace(trace);
            span.setCreatedAt(LocalDateTime.now());
            spanRepository.save(span);
        }

        log.debug("Agent Trace 已落库: traceId={}, spans={}", trace.getTraceId(), payload.spans().size());
    }

    @Override
    protected void markCompleted(TraceStreamProducer.TracePayload payload) {
        log.debug("Agent Trace 落库完成: traceId={}", payload.trace().getTraceId());
    }

    @Override
    protected void markFailed(TraceStreamProducer.TracePayload payload, String error) {
        log.error("Agent Trace 落库失败: traceId={}, error={}", payload.trace().getTraceId(), error);
    }

    @Override
    protected void retryMessage(TraceStreamProducer.TracePayload payload, int retryCount) {
        log.warn("Agent Trace 落库重试: traceId={}, retryCount={}", payload.trace().getTraceId(), retryCount);
        // 重新发送到 Stream（简化处理，实际应重新序列化）
    }

    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }
}
