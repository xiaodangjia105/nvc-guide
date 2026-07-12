package nvc.guide.modules.nvcvoice.listener;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import nvc.guide.common.async.AbstractStreamProducer;
import nvc.guide.common.constant.AsyncTaskStreamConstants;
import nvc.guide.common.model.AsyncTaskStatus;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcvoice.service.NvcVoiceService;

/**
 * NVC 语音评估任务生产者
 */
@Slf4j
@Component
public class NvcVoiceEvaluateStreamProducer extends AbstractStreamProducer<String> {

  private final NvcVoiceService voiceService;

  public NvcVoiceEvaluateStreamProducer(
      RedisService redisService,
      @Lazy NvcVoiceService voiceService) {
    super(redisService);
    this.voiceService = voiceService;
  }

  public void sendEvaluateTask(String sessionId) {
    sendTask(sessionId);
  }

  @Override
  protected String taskDisplayName() {
    return "NVC 语音评估";
  }

  @Override
  protected String streamKey() {
    return AsyncTaskStreamConstants.NVC_VOICE_EVALUATE_STREAM_KEY;
  }

  @Override
  protected Map<String, String> buildMessage(String sessionId) {
    return Map.of(
        AsyncTaskStreamConstants.FIELD_NVC_VOICE_SESSION_ID, sessionId,
        AsyncTaskStreamConstants.FIELD_RETRY_COUNT, "0"
    );
  }

  @Override
  protected String payloadIdentifier(String sessionId) {
    return "nvcVoiceSessionId=" + sessionId;
  }

  @Override
  protected void onSendFailed(String sessionId, String error) {
    voiceService.updateEvaluateStatus(
        Long.parseLong(sessionId), AsyncTaskStatus.FAILED, truncateError(error));
  }
}
