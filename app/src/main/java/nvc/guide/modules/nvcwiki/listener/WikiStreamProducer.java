package nvc.guide.modules.nvcwiki.listener;

import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.async.AbstractStreamProducer;
import nvc.guide.infrastructure.redis.RedisService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Wiki 自动生成异步任务生产者
 * 练习结束后推送 Wiki 生成任务到 Redis Stream
 */
@Component
@Slf4j
public class WikiStreamProducer extends AbstractStreamProducer<WikiStreamProducer.WikiGenerateTask> {

    public WikiStreamProducer(RedisService redisService) {
        super(redisService);
    }

    public record WikiGenerateTask(Long sessionId, Long userId) {}

    /**
     * 发送 Wiki 生成任务
     */
    public void sendWikiGenerateTask(Long sessionId, Long userId) {
        sendTask(new WikiGenerateTask(sessionId, userId));
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
    protected Map<String, String> buildMessage(WikiGenerateTask task) {
        return Map.of(
                "sessionId", String.valueOf(task.sessionId()),
                "userId", String.valueOf(task.userId())
        );
    }

    @Override
    protected String payloadIdentifier(WikiGenerateTask task) {
        return "sessionId=" + task.sessionId();
    }

    @Override
    protected void onSendFailed(WikiGenerateTask task, String error) {
        log.error("Wiki生成任务发送失败: sessionId={}, error={}", task.sessionId(), error);
    }
}
