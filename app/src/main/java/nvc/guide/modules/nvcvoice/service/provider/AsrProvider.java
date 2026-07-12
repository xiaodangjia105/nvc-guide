package nvc.guide.modules.nvcvoice.service.provider;

/**
 * ASR（语音识别）Provider 接口
 *
 * 抽象语音识别服务，允许不同实现（Qwen、讯飞、Azure 等）。
 * 当前实现：{@link QwenAsrProvider}
 */
public interface AsrProvider {

  /**
   * 启动 ASR 会话
   *
   * @param sessionId 会话标识
   * @param callbacks 回调函数（onPartial/onFinal/onReady/onError）
   */
  void startSession(String sessionId, AsrCallbacks callbacks);

  /**
   * 发送音频数据
   *
   * @param sessionId 会话标识
   * @param pcmData   PCM 16kHz 音频数据
   */
  void sendAudio(String sessionId, byte[] pcmData);

  /**
   * 停止 ASR 会话
   *
   * @param sessionId 会话标识
   */
  void stopSession(String sessionId);

  /**
   * ASR 是否就绪
   *
   * @param sessionId 会话标识
   * @return true 如果 ASR 已连接并准备好接收音频
   */
  boolean isReady(String sessionId);

  /**
   * 重启 ASR 会话（自动重连）
   *
   * @param sessionId  会话标识
   * @param callbacks  回调函数
   */
  void restartSession(String sessionId, AsrCallbacks callbacks);

  /**
   * 是否有活跃的 ASR 会话
   *
   * @param sessionId 会话标识
   * @return true 如果有活跃会话
   */
  boolean hasActiveSession(String sessionId);
}
