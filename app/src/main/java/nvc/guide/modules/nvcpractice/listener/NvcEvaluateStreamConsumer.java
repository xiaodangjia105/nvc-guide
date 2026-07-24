package nvc.guide.modules.nvcpractice.listener;

import nvc.guide.common.async.AbstractStreamConsumer;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeType;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.service.NvcEvaluationService;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import nvc.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import nvc.guide.modules.nvcwiki.listener.WikiStreamProducer;
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
    private final NvcProfileService profileService;
    private final WikiStreamProducer wikiStreamProducer;
    private final NvcUserProfileRepository profileRepository;

    public NvcEvaluateStreamConsumer(RedisService redisService,
                                      NvcEvaluationService evaluationService,
                                      NvcPracticeMessageRepository messageRepository,
                                      NvcProfileService profileService,
                                      WikiStreamProducer wikiStreamProducer,
                                      NvcUserProfileRepository profileRepository) {
        super(redisService);
        this.evaluationService = evaluationService;
        this.messageRepository = messageRepository;
        this.profileService = profileService;
        this.wikiStreamProducer = wikiStreamProducer;
        this.profileRepository = profileRepository;
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

        NvcEvaluationEntity saved = evaluationService.evaluateFinal(task.sessionId(), task.userId(), messages);

        // 更新用户档案能力分数
        profileService.updateAbilityScore(
            task.userId(),
            task.sessionId(),
            saved,
            NvcPracticeType.TEXT
        );
        log.info("User profile ability score updated: userId={}", task.userId());

        // 检查用户是否开启自动 Wiki 生成偏好
        if (isAutoGenerateWikiEnabled(task.userId())) {
            wikiStreamProducer.sendWikiGenerateTask(task.sessionId(), task.userId());
            log.info("Wiki auto-generate task triggered: sessionId={}", task.sessionId());
        }
    }

    /**
     * 检查用户是否开启自动 Wiki 生成
     */
    private boolean isAutoGenerateWikiEnabled(Long userId) {
        return profileRepository.findByUserId(userId)
                .map(profile -> {
                    Map<String, Object> prefs = profile.getPreferences();
                    return prefs != null && Boolean.TRUE.equals(prefs.get("autoGenerateWiki"));
                })
                .orElse(false);
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
