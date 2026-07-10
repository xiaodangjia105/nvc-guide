package nvc.guide.modules.nvcpractice.listener;

import nvc.guide.common.async.AbstractStreamProducer;
import nvc.guide.infrastructure.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * NVC 练习评估异步任务生产者
 * 练习结束后推送最终评估任务到 Redis Stream
 */
@Component
@Slf4j
public class NvcEvaluateStreamProducer extends AbstractStreamProducer<NvcEvaluateStreamProducer.NvcEvaluateTask> {

    public NvcEvaluateStreamProducer(RedisService redisService) {
        super(redisService);
    }

    public record NvcEvaluateTask(Long sessionId, Long userId) {}

    /**
     * 发送最终评估任务
     */
    public void sendEvaluateTask(Long sessionId, Long userId) {
        sendTask(new NvcEvaluateTask(sessionId, userId));
    }

    @Override
    protected String taskDisplayName() {
        return "NVC最终评估";
    }

    @Override
    protected String streamKey() {
        return "nvc:evaluate:stream";
    }

    @Override
    protected Map<String, String> buildMessage(NvcEvaluateTask task) {
        return Map.of(
            "sessionId", String.valueOf(task.sessionId()),
            "userId", String.valueOf(task.userId())
        );
    }

    @Override
    protected String payloadIdentifier(NvcEvaluateTask task) {
        return "sessionId=" + task.sessionId();
    }

    @Override
    protected void onSendFailed(NvcEvaluateTask task, String error) {
        log.error("NVC评估任务发送失败: sessionId={}, error={}", task.sessionId(), error);
    }
}
