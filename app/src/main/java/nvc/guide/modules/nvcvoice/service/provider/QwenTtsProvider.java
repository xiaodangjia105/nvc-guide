package nvc.guide.modules.nvcvoice.service.provider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import nvc.guide.modules.voiceinterview.service.QwenTtsService;

/**
 * Qwen TTS Provider 实现
 *
 * 包装现有的 {@link QwenTtsService}，实现 {@link TtsProvider} 接口。
 * 零改动复用原有 TTS 服务能力。
 */
@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class QwenTtsProvider implements TtsProvider {

  private final QwenTtsService ttsService;

  @Override
  public byte[] synthesize(String text) {
    if (text == null || text.isBlank()) {
      log.warn("[TTS] Empty text, skipping synthesis");
      return new byte[0];
    }
    return ttsService.synthesize(text);
  }

  @Override
  public byte[] synthesize(String text, int timeoutSeconds) {
    if (text == null || text.isBlank()) {
      log.warn("[TTS] Empty text, skipping synthesis");
      return new byte[0];
    }
    // QwenTtsService 的 synthesize 方法内部已有超时控制
    // 这里直接委托，未来可扩展为传递超时参数
    return ttsService.synthesize(text);
  }
}
