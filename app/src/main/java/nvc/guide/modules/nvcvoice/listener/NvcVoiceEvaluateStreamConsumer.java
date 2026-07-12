package nvc.guide.modules.nvcvoice.listener;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.stream.StreamMessageId;
import org.springframework.stereotype.Component;
import nvc.guide.common.async.AbstractStreamConsumer;
import nvc.guide.common.constant.AsyncTaskStreamConstants;
import nvc.guide.common.model.AsyncTaskStatus;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcvoice.service.NvcVoiceEvaluationService;
import nvc.guide.modules.nvcvoice.service.NvcVoiceService;

/**
 * NVC 语音评估 Stream 消费者
 */
@Slf4j
@Component
public class NvcVoiceEvaluateStreamConsumer
    extends AbstractStreamConsumer<NvcVoiceEvaluateStreamConsumer.NvcVoiceEvaluatePayload> {

  private final NvcVoiceService voiceService;
  private final NvcVoiceEvaluationService evaluationService;

  public NvcVoiceEvaluateStreamConsumer(
      RedisService redisService,
      NvcVoiceService voiceService,
      NvcVoiceEvaluationService evaluationService) {
    super(redisService);
    this.voiceService = voiceService;
    this.evaluationService = evaluationService;
  }

  record NvcVoiceEvaluatePayload(String sessionId) {}

  @Override
  protected String taskDisplayName() {
    return "NVC 语音评估";
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.NVC_VOICE_EVALUATE_STREAM_KEY;
  }

  @Override
  protected String groupName() {
    return AsyncTaskStreamConstants.NVC_VOICE_EVALUATE_GROUP_NAME;
  }

  @Override
  protected String consumerPrefix() {
    return AsyncTaskStreamConstants.NVC_VOICE_EVALUATE_CONSUMER_PREFIX;
  }

  @Override
  protected String threadName() {
    return "nvc-voice-evaluate-consumer";
  }

  @Override
  protected NvcVoiceEvaluatePayload parsePayload(StreamMessageId messageId,
      Map<String, String> data) {
    String sessionId = data.get(AsyncTaskStreamConstants.FIELD_NVC_VOICE_SESSION_ID);
    if (sessionId == null) {
      log.warn("消息格式错误，跳过: messageId={}", messageId);
      return null;
    }
    return new NvcVoiceEvaluatePayload(sessionId);
  }

  @Override
  protected String payloadIdentifier(NvcVoiceEvaluatePayload payload) {
    return "nvcVoiceSessionId=" + payload.sessionId();
  }

  @Override
  protected void markProcessing(NvcVoiceEvaluatePayload payload) {
    voiceService.updateEvaluateStatus(
        Long.parseLong(payload.sessionId()), AsyncTaskStatus.PROCESSING, null);
  }

  @Override
  protected void processBusiness(NvcVoiceEvaluatePayload payload) {
    evaluationService.generateEvaluation(Long.parseLong(payload.sessionId()));
    log.info("NVC 语音评估完成: sessionId={}", payload.sessionId());
  }

  @Override
  protected void markCompleted(NvcVoiceEvaluatePayload payload) {
    voiceService.updateEvaluateStatus(
        Long.parseLong(payload.sessionId()), AsyncTaskStatus.COMPLETED, null);
  }

  @Override
  protected void markFailed(NvcVoiceEvaluatePayload payload, String error) {
    voiceService.updateEvaluateStatus(
        Long.parseLong(payload.sessionId()), AsyncTaskStatus.FAILED, error);
  }

  @Override
  protected void retryMessage(NvcVoiceEvaluatePayload payload, int retryCount) {
    String sessionId = payload.sessionId();
    try {
      Map<String, String> message = Map.of(
          AsyncTaskStreamConstants.FIELD_NVC_VOICE_SESSION_ID, sessionId,
          AsyncTaskStreamConstants.FIELD_RETRY_COUNT, String.valueOf(retryCount)
      );

      redisService().streamAdd(
          AsyncTaskStreamConstants.NVC_VOICE_EVALUATE_STREAM_KEY,
          message,
          AsyncTaskStreamConstants.STREAM_MAX_LEN
      );
      log.info("NVC 语音评估任务已重新入队: sessionId={}, retryCount={}", sessionId, retryCount);
    } catch (Exception e) {
      log.error("重试入队失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
      voiceService.updateEvaluateStatus(
          Long.parseLong(sessionId), AsyncTaskStatus.FAILED,
          truncateError("重试入队失败: " + e.getMessage()));
    }
  }
}
