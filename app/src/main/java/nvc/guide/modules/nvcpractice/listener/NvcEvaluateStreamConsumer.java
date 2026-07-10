package nvc.guide.modules.nvcpractice.listener;

import nvc.guide.common.async.AbstractStreamConsumer;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.service.NvcEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * NVC 练习评估异步任务消费者
 * 从 Redis Stream 消费最终评估任务
 */
@Component
@Slf4j
public class NvcEvaluateStreamConsumer extends AbstractStreamConsumer<NvcEvaluateStreamProducer.NvcEvaluateTask> {

    private final NvcEvaluationService evaluationService;
    private final NvcPracticeMessageRepository messageRepository;

    public NvcEvaluateStreamConsumer(RedisService redisService,
                                      NvcEvaluationService evaluationService,
                                      NvcPracticeMessageRepository messageRepository) {
        super(redisService);
        this.evaluationService = evaluationService;
        this.messageRepository = messageRepository;
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
    protected String groupName() {
        return "nvc-evaluate-group";
    }

    @Override
    protected String consumerPrefix() {
        return "nvc-evaluate-";
    }

    @Override
    protected String threadName() {
        return "nvc-evaluate-consumer";
    }

    @Override
    protected NvcEvaluateStreamProducer.NvcEvaluateTask parsePayload(
            StreamMessageId messageId, Map<String, String> data) {
        try {
            return new NvcEvaluateStreamProducer.NvcEvaluateTask(
                Long.parseLong(data.get("sessionId")),
                Long.parseLong(data.get("userId"))
            );
        } catch (Exception e) {
            log.error("Failed to parse evaluate task: data={}", data, e);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(NvcEvaluateStreamProducer.NvcEvaluateTask task) {
        return "sessionId=" + task.sessionId();
    }

    @Override
    protected void markProcessing(NvcEvaluateStreamProducer.NvcEvaluateTask task) {
        log.info("NVC最终评估开始处理: sessionId={}", task.sessionId());
    }

    @Override
    protected void processBusiness(NvcEvaluateStreamProducer.NvcEvaluateTask task) {
        List<NvcPracticeMessageEntity> messages =
            messageRepository.findBySessionIdOrderBySequenceNumAsc(task.sessionId());

        if (messages.isEmpty()) {
            log.warn("No messages found for session: {}", task.sessionId());
            return;
        }

        evaluationService.evaluateFinal(task.sessionId(), task.userId(), messages);
    }

    @Override
    protected void markCompleted(NvcEvaluateStreamProducer.NvcEvaluateTask task) {
        log.info("NVC最终评估完成: sessionId={}", task.sessionId());
    }

    @Override
    protected void markFailed(NvcEvaluateStreamProducer.NvcEvaluateTask task, String error) {
        log.error("NVC最终评估失败: sessionId={}, error={}", task.sessionId(), error);
    }

    @Override
    protected void retryMessage(NvcEvaluateStreamProducer.NvcEvaluateTask task, int retryCount) {
        log.warn("NVC最终评估重试: sessionId={}, retryCount={}", task.sessionId(), retryCount);
        // 重新发送到 Stream
        redisService().streamAdd(streamKey(), Map.of(
            "sessionId", String.valueOf(task.sessionId()),
            "userId", String.valueOf(task.userId()),
            "retryCount", String.valueOf(retryCount)
        ));
    }
}
