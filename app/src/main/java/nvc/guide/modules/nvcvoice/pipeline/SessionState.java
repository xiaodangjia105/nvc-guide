package nvc.guide.modules.nvcvoice.pipeline;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 会话状态
 *
 * 管理单个 WebSocket 连接的实时状态。
 */
@Slf4j
public class SessionState {

  /** 是否正在处理 LLM 请求（CAS 防并发） */
  public final AtomicBoolean processing = new AtomicBoolean(false);

  /** AI 是否正在说话 */
  public final AtomicBoolean aiSpeaking = new AtomicBoolean(false);

  /** AI 说话结束时间戳（用于回声消除冷却期） */
  public final AtomicLong aiSpeakEndAt = new AtomicLong(0);

  /** ASR 语句合并缓冲区 */
  public final UtteranceMergeBuffer mergeBuffer = new UtteranceMergeBuffer();

  /** 回声消除冷却期（毫秒） */
  private static final long ECHO_COOLDOWN_MS = 800;

  /**
   * AI 是否正在说话或在冷却期内
   */
  public boolean isAiSpeakingOrCooldown() {
    if (aiSpeaking.get()) {
      return true;
    }
    long endAt = aiSpeakEndAt.get();
    return endAt > 0 && System.currentTimeMillis() < endAt;
  }

  /**
   * 标记 AI 开始说话
   */
  public void markAiSpeaking() {
    aiSpeaking.set(true);
    aiSpeakEndAt.set(0);
  }

  /**
   * 标记 AI 停止说话
   */
  public void markAiSilent() {
    aiSpeaking.set(false);
    aiSpeakEndAt.set(System.currentTimeMillis() + ECHO_COOLDOWN_MS);
  }

  /**
   * 重置状态
   */
  public void reset() {
    processing.set(false);
    aiSpeaking.set(false);
    aiSpeakEndAt.set(0);
    mergeBuffer.clear();
  }
}
