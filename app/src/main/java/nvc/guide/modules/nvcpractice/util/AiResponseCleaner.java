package nvc.guide.modules.nvcpractice.util;

import java.util.regex.Pattern;

/**
 * AI 回复清理工具
 * 去除 AI 输出中混杂的 JSON、代码块、Markdown 格式标记
 * 只保留纯自然语言内容
 */
public final class AiResponseCleaner {

  private AiResponseCleaner() {}

  // 匹配 ```json ... ``` 或 ```...``` 代码块
  private static final Pattern CODE_BLOCK = Pattern.compile(
      "```[a-zA-Z]*\\s*\\n[\\s\\S]*?```",
      Pattern.MULTILINE);

  // 匹配独立的 JSON 对象段落（以 { 开头，以 } 结尾，占一整段）
  private static final Pattern JSON_OBJECT = Pattern.compile(
      "^\\s*\\{[\\s\\S]*?\\}\\s*$",
      Pattern.MULTILINE);

  // 匹配 JSON 数组段落
  private static final Pattern JSON_ARRAY = Pattern.compile(
      "^\\s*\\[[\\s\\S]*?]\\s*$",
      Pattern.MULTILINE);

  // 匹配混合内容中以 { 开头的 JSON 块（如 "是啊\n{ ... }"）
  private static final Pattern JSON_OBJECT_INLINE = Pattern.compile(
      "\\{\\s*\"[\\s\\S]*?\"\\s*:\\s*[\\s\\S]*?\\}",
      Pattern.MULTILINE);

  // 匹配连续 3 行以上空行
  private static final Pattern EXCESS_NEWLINES = Pattern.compile(
      "\\n{3,}");

  /**
   * 清理 AI 回复
   *
   * @param raw AI 原始回复
   * @return 清理后的纯文本
   */
  public static String clean(String raw) {
    if (raw == null || raw.isBlank()) {
      return raw;
    }

    String result = raw;

    // 1. 去除代码块
    result = CODE_BLOCK.matcher(result).replaceAll("");

    // 2. 去除独立 JSON 对象段落
    result = JSON_OBJECT.matcher(result).replaceAll("");

    // 3. 去除独立 JSON 数组段落
    result = JSON_ARRAY.matcher(result).replaceAll("");

    // 4. 去除混合内容中的 JSON 块（用大括号计数处理嵌套）
    result = removeInlineJson(result);

    // 5. 压缩多余空行
    result = EXCESS_NEWLINES.matcher(result).replaceAll("\n\n");

    // 6. trim
    return result.trim();
  }

  /**
   * 移除混合内容中的 JSON 块，使用大括号计数正确处理嵌套
   */
  private static String removeInlineJson(String text) {
    StringBuilder sb = new StringBuilder();
    int i = 0;
    while (i < text.length()) {
      if (text.charAt(i) == '{') {
        // 检查是否像 JSON（下一个非空字符是 "）
        int j = i + 1;
        while (j < text.length() && Character.isWhitespace(text.charAt(j))) j++;
        if (j < text.length() && text.charAt(j) == '"') {
          // 用大括号计数找到匹配的闭合
          int depth = 0;
          int start = i;
          boolean valid = true;
          for (int k = i; k < text.length(); k++) {
            char c = text.charAt(k);
            if (c == '{') depth++;
            else if (c == '}') {
              depth--;
              if (depth == 0) {
                // 跳过整个 JSON 块
                i = k + 1;
                break;
              }
            }
            // 如果在字符串内，跳过转义字符
            if (k == text.length() - 1 && depth > 0) {
              valid = false;
            }
          }
          if (valid && i > start) {
            continue;
          }
        }
      }
      sb.append(text.charAt(i));
      i++;
    }
    return sb.toString();
  }
}
