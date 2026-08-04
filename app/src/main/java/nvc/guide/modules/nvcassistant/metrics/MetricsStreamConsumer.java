package nvc.guide.modules.nvcassistant.metrics;

import nvc.guide.common.async.AbstractStreamConsumer;
import nvc.guide.common.constant.AsyncTaskStreamConstants;
import nvc.guide.infrastructure.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 指标异步消费者
 * 从 Redis Stream 消费指标数据，批量写入 PostgreSQL
 */
@Component
@Slf4j
public class MetricsStreamConsumer extends AbstractStreamConsumer<AgentMetricsEntity> {

    private final AgentMetricsRepository metricsRepository;

    public MetricsStreamConsumer(RedisService redisService,
                                 AgentMetricsRepository metricsRepository) {
        super(redisService);
        this.metricsRepository = metricsRepository;
    }

    @Override
    protected String taskDisplayName() {
        return "Agent指标落库";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.METRICS_STREAM_KEY;
    }

    @Override
    protected String groupName() {
        return AsyncTaskStreamConstants.METRICS_GROUP_NAME;
    }

    @Override
    protected String consumerPrefix() {
        return AsyncTaskStreamConstants.METRICS_CONSUMER_PREFIX;
    }

    @Override
    protected String threadName() {
        return "nvc-metrics-consumer";
    }

    @Override
    protected AgentMetricsEntity parsePayload(StreamMessageId messageId, Map<String, String> data) {
        try {
            return AgentMetricsEntity.builder()
                .sessionId(data.get("sessionId"))
                .traceId(data.get("traceId"))
                .metricType(data.get("metricType"))
                .payload(data.get("payload"))
                .build();
        } catch (Exception e) {
            log.error("Failed to parse metrics task: data={}", data, e);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(AgentMetricsEntity entity) {
        return "session=" + entity.getSessionId() + ",type=" + entity.getMetricType();
    }

    @Override
    protected void markProcessing(AgentMetricsEntity entity) {
        log.debug("Agent指标开始落库: session={}, type={}", entity.getSessionId(), entity.getMetricType());
    }

    @Override
    protected void processBusiness(AgentMetricsEntity entity) {
        metricsRepository.save(entity);
        log.debug("Agent指标已落库: session={}, type={}", entity.getSessionId(), entity.getMetricType());
    }

    @Override
    protected void markCompleted(AgentMetricsEntity entity) {
        log.debug("Agent指标落库完成: session={}, type={}", entity.getSessionId(), entity.getMetricType());
    }

    @Override
    protected void markFailed(AgentMetricsEntity entity, String error) {
        log.error("Agent指标落库失败: session={}, type={}, error={}",
            entity.getSessionId(), entity.getMetricType(), error);
    }

    @Override
    protected void retryMessage(AgentMetricsEntity entity, int retryCount) {
        log.warn("Agent指标落库重试: session={}, type={}, retryCount={}",
            entity.getSessionId(), entity.getMetricType(), retryCount);
        redisService().streamAdd(streamKey(), Map.of(
            "sessionId", entity.getSessionId(),
            "metricType", entity.getMetricType(),
            "payload", entity.getPayload(),
            "retryCount", String.valueOf(retryCount)
        ));
    }
}
