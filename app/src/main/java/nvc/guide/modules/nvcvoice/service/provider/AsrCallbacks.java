package nvc.guide.modules.nvcvoice.service.provider;

import java.util.function.Consumer;

/**
 * ASR 回调函数集合
 *
 * @param onPartial 部分识别结果（实时字幕）
 * @param onFinal   最终识别结果（完整语句）
 * @param onReady   ASR 就绪回调
 * @param onError   错误回调
 */
public record AsrCallbacks(
    Consumer<String> onPartial,
    Consumer<String> onFinal,
    Runnable onReady,
    Consumer<Throwable> onError
) {

  public AsrCallbacks {
    if (onFinal == null) {
      throw new IllegalArgumentException("onFinal cannot be null");
    }
    if (onError == null) {
      throw new IllegalArgumentException("onError cannot be null");
    }
  }

  /**
   * 创建只有 onFinal 和 onError 的简化回调
   */
  public static AsrCallbacks of(Consumer<String> onFinal, Consumer<Throwable> onError) {
    return new AsrCallbacks(null, onFinal, null, onError);
  }

  /**
   * 创建完整回调
   */
  public static AsrCallbacks of(
      Consumer<String> onPartial,
      Consumer<String> onFinal,
      Runnable onReady,
      Consumer<Throwable> onError) {
    return new AsrCallbacks(onPartial, onFinal, onReady, onError);
  }
}
