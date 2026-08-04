package nvc.guide.modules.nvcassistant.metrics;

import nvc.guide.common.async.AbstractStreamProducer;
import nvc.guide.common.constant.AsyncTaskStreamConstants;
import nvc.guide.infrastructure.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 指标异步生产者
 * 将指标数据推送到 Redis Stream，由 MetricsStreamConsumer 批量落库
 */
@Component
@Slf4j
public class MetricsStreamProducer extends AbstractStreamProducer<AgentMetricsEntity> {

    public MetricsStreamProducer(RedisService redisService) {
        super(redisService);
    }

    /**
     * 发送指标数据到 Redis Stream
     */
    public void sendMetric(AgentMetricsEntity entity) {
        sendTask(entity);
    }

    @Override
    protected String taskDisplayName() {
        return "Agent指标采集";
    }

    @Override
    protected String streamKey() {
        return AsyncTaskStreamConstants.METRICS_STREAM_KEY;
    }

    @Override
    protected Map<String, String> buildMessage(AgentMetricsEntity entity) {
        Map<String, String> message = new LinkedHashMap<>();
        message.put("sessionId", entity.getSessionId());
        message.put("metricType", entity.getMetricType());
        message.put("payload", entity.getPayload());
        if (entity.getTraceId() != null) {
            message.put("traceId", entity.getTraceId());
        }
        return message;
    }

    @Override
    protected String payloadIdentifier(AgentMetricsEntity entity) {
        return "session=" + entity.getSessionId() + ",type=" + entity.getMetricType();
    }

    @Override
    protected void onSendFailed(AgentMetricsEntity entity, String error) {
        log.error("Agent指标发送失败: session={}, type={}, error={}",
            entity.getSessionId(), entity.getMetricType(), error);
    }
}
