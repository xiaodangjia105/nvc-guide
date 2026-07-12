package nvc.guide.modules.nvcvoice.pipeline;

import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;

/**
 * ASR 语句合并缓冲区
 *
 * 将多个 ASR 最终识别片段智能合并为完整语句。
 * 支持去重、前后缀重叠检测、标点感知拼接。
 */
@Slf4j
public class UtteranceMergeBuffer {

  private final AtomicReference<String> buffer = new AtomicReference<>("");

  /**
   * 添加 ASR 识别片段
   *
   * @param segment ASR 最终识别文本
   */
  public void addSegment(String segment) {
    if (segment == null || segment.isBlank()) {
      return;
    }

    buffer.updateAndGet(current -> {
      if (current.isEmpty()) {
        return segment.trim();
      }
      return mergeText(current, segment.trim());
    });

    log.debug("[MergeBuffer] Added segment: {}, buffer: {}", segment, buffer.get());
  }

  /**
   * 取出并清空缓冲区
   *
   * @return 合并后的完整语句
   */
  public String flush() {
    String result = buffer.getAndSet("");
    log.debug("[MergeBuffer] Flushed: {}", result);
    return result;
  }

  /**
   * 查看当前缓冲区内容（不清空）
   */
  public String peek() {
    return buffer.get();
  }

  /**
   * 清空缓冲区
   */
  public void clear() {
    buffer.set("");
  }

  /**
   * 是否为空
   */
  public boolean isEmpty() {
    return buffer.get().isEmpty();
  }

  /**
   * 智能合并文本
   *
   * 处理以下情况：
   * 1. 完全重复：忽略新片段
   * 2. 前后缀重叠：去重后拼接
   * 3. 正常拼接：用适当分隔符连接
   */
  private String mergeText(String current, String newText) {
    // 完全重复
    if (current.equals(newText)) {
      return current;
    }

    // 当前文本包含新片段
    if (current.contains(newText)) {
      return current;
    }

    // 新文本包含当前文本
    if (newText.contains(current)) {
      return newText;
    }

    // 检测前后缀重叠
    int overlap = findOverlap(current, newText);
    if (overlap > 0) {
      return current + newText.substring(overlap);
    }

    // 检查是否以标点结尾，决定分隔符
    char lastChar = current.charAt(current.length() - 1);
    if (isPunctuation(lastChar)) {
      return current + newText;
    }

    // 默认用逗号连接
    return current + "，" + newText;
  }

  /**
   * 查找 current 尾部与 newText 头部的重叠长度
   */
  private int findOverlap(String current, String newText) {
    int maxOverlap = Math.min(current.length(), newText.length());
    for (int len = maxOverlap; len > 0; len--) {
      if (current.endsWith(newText.substring(0, len))) {
        return len;
      }
    }
    return 0;
  }

  private boolean isPunctuation(char c) {
    return "。！？，、；：.!?;,:".indexOf(c) >= 0;
  }
}
