package nvc.guide.modules.nvcvoice.service.provider;

/**
 * TTS（语音合成）Provider 接口
 *
 * 抽象语音合成服务，允许不同实现（Qwen、讯飞、Azure 等）。
 * 当前实现：{@link QwenTtsProvider}
 */
public interface TtsProvider {

  /**
   * 文本转语音（使用默认超时）
   *
   * @param text 要合成的文本
   * @return PCM 音频数据
   */
  byte[] synthesize(String text);

  /**
   * 文本转语音（自定义超时）
   *
   * @param text           要合成的文本
   * @param timeoutSeconds 超时秒数
   * @return PCM 音频数据
   */
  byte[] synthesize(String text, int timeoutSeconds);
}
