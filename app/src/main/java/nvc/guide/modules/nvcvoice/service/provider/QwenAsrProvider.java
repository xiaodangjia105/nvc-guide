package nvc.guide.modules.nvcvoice.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import nvc.guide.modules.voiceinterview.service.QwenAsrService;

/**
 * Qwen ASR Provider 实现
 *
 * 包装现有的 {@link QwenAsrService}，实现 {@link AsrProvider} 接口。
 * 零改动复用原有 ASR 服务能力。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class QwenAsrProvider implements AsrProvider {

  private final QwenAsrService asrService;

  @Override
  public void startSession(String sessionId, AsrCallbacks callbacks) {
    log.info("[ASR] Starting session: {}", sessionId);
    asrService.startTranscription(
        sessionId,
        callbacks.onFinal(),
        callbacks.onPartial(),
        callbacks.onReady(),
        callbacks.onError()
    );
  }

  @Override
  public void sendAudio(String sessionId, byte[] pcmData) {
    asrService.sendAudio(sessionId, pcmData);
  }

  @Override
  public void stopSession(String sessionId) {
    log.info("[ASR] Stopping session: {}", sessionId);
    asrService.stopTranscription(sessionId);
  }

  @Override
  public boolean isReady(String sessionId) {
    return asrService.isReady(sessionId);
  }

  @Override
  public void restartSession(String sessionId, AsrCallbacks callbacks) {
    log.info("[ASR] Restarting session: {}", sessionId);
    asrService.restartTranscription(
        sessionId,
        callbacks.onFinal(),
        callbacks.onPartial(),
        callbacks.onReady(),
        callbacks.onError()
    );
  }

  @Override
  public boolean hasActiveSession(String sessionId) {
    return asrService.hasActiveSession(sessionId);
  }
}
