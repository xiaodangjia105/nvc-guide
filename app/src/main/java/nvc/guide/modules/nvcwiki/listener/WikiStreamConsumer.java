package nvc.guide.modules.nvcwiki.listener;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.async.AbstractStreamConsumer;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcwiki.service.NvcWikiAutoGenerateService;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Wiki 自动生成异步任务消费者
 * 从 Redis Stream 消费 Wiki 生成任务
 */
@Component
@Slf4j
public class WikiStreamConsumer extends AbstractStreamConsumer<WikiStreamProducer.WikiGenerateTask> {

    private final NvcWikiAutoGenerateService wikiAutoGenerateService;
    private final NvcPracticeMessageRepository messageRepository;

    public WikiStreamConsumer(RedisService redisService,
                               NvcWikiAutoGenerateService wikiAutoGenerateService,
                               NvcPracticeMessageRepository messageRepository) {
        super(redisService);
        this.wikiAutoGenerateService = wikiAutoGenerateService;
        this.messageRepository = messageRepository;
    }

    @Override
    protected String taskDisplayName() {
        return "Wiki自动生成";
    }

    @Override
    protected String streamKey() {
        return "nvc:wiki:generate:stream";
    }

    @Override
    protected String groupName() {
        return "nvc-wiki-generate-group";
    }

    @Override
    protected String consumerPrefix() {
        return "nvc-wiki-";
    }

    @Override
    protected String threadName() {
        return "nvc-wiki-generate-consumer";
    }

    @Override
    protected WikiStreamProducer.WikiGenerateTask parsePayload(
            StreamMessageId messageId, Map<String, String> data) {
        try {
            return new WikiStreamProducer.WikiGenerateTask(
                    Long.parseLong(data.get("sessionId")),
                    Long.parseLong(data.get("userId"))
            );
        } catch (Exception e) {
            log.error("Failed to parse wiki generate task: data={}", data, e);
            return null;
        }
    }

    @Override
    protected String payloadIdentifier(WikiStreamProducer.WikiGenerateTask task) {
        return "sessionId=" + task.sessionId();
    }

    @Override
    protected void markProcessing(WikiStreamProducer.WikiGenerateTask task) {
        log.info("Wiki自动生成开始处理: sessionId={}", task.sessionId());
    }

    @Override
    protected void processBusiness(WikiStreamProducer.WikiGenerateTask task) {
        List<NvcPracticeMessageEntity> messages =
                messageRepository.findBySessionIdOrderBySequenceNumAsc(task.sessionId());

        if (messages.isEmpty()) {
            log.warn("No messages found for session: {}", task.sessionId());
            return;
        }

        wikiAutoGenerateService.generateFromSession(task.userId(), task.sessionId(), messages);
    }

    @Override
    protected void markCompleted(WikiStreamProducer.WikiGenerateTask task) {
        log.info("Wiki自动生成完成: sessionId={}", task.sessionId());
    }

    @Override
    protected void markFailed(WikiStreamProducer.WikiGenerateTask task, String error) {
        log.error("Wiki自动生成失败: sessionId={}, error={}", task.sessionId(), error);
    }

    @Override
    protected void retryMessage(WikiStreamProducer.WikiGenerateTask task, int retryCount) {
        log.warn("Wiki自动生成重试: sessionId={}, retryCount={}", task.sessionId(), retryCount);
        redisService().streamAdd(streamKey(), Map.of(
                "sessionId", String.valueOf(task.sessionId()),
                "userId", String.valueOf(task.userId()),
                "retryCount", String.valueOf(retryCount)
        ));
    }
}
