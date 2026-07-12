package nvc.guide.modules.nvcvoice.service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.common.ai.PromptSanitizer;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;

/**
 * NVC 语音练习 LLM 服务
 *
 * 负责 LLM 流式调用，包含：
 * - 流式句子分割
 * - 语音输出优化
 * - 错误处理与用户友好提示
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcVoiceLlmService {

  private final LlmProviderRegistry llmProviderRegistry;
  private final NvcVoicePromptService promptService;
  private final NvcVoiceProperties properties;
  private final PromptSanitizer promptSanitizer;

  /**
   * 非流式调用 LLM
   *
   * @param systemPrompt 系统 Prompt
   * @param userPrompt   用户 Prompt
   * @param provider     LLM 提供商
   * @return 优化后的回复文本
   */
  public String chat(String systemPrompt, String userPrompt, String provider) {
    try {
      ChatClient chatClient = llmProviderRegistry.getVoiceChatClient(provider);

      ChatClient.CallResponseSpec response = chatClient.prompt()
          .system(systemPrompt)
          .user(userPrompt)
          .call();

      String content = response.chatResponse().getResult().getOutput().getText();
      return promptService.optimizeForVoice(content);

    } catch (Exception e) {
      log.error("LLM chat error: {}", e.getMessage(), e);
      return mapLlmErrorToUserMessage(e);
    }
  }

  /**
   * 流式调用 LLM，每检测到一个完整句子就回调 onSentence
   *
   * @param systemPrompt 系统 Prompt
   * @param userPrompt   用户 Prompt
   * @param onToken      实时文本回调（可为 null）
   * @param onSentence   句子级别回调（可为 null）
   * @param provider     LLM 提供商
   * @return 完整优化后的文本
   */
  public String chatStreamSentences(
      String systemPrompt,
      String userPrompt,
      Consumer<String> onToken,
      Consumer<String> onSentence,
      String provider) {

    try {
      ChatClient chatClient = llmProviderRegistry.getVoiceChatClient(provider);
      StringBuilder raw = new StringBuilder();
      AtomicLong lastEmitNanos = new AtomicLong(System.nanoTime());
      AtomicInteger lastEmitLength = new AtomicInteger(0);
      AtomicInteger lastSentenceEnd = new AtomicInteger(0);
      int emitIntervalMs = Math.max(80, properties.getAiStreamPushIntervalMs());
      int minCharsDelta = Math.max(4, properties.getAiStreamMinCharsDelta());

      chatClient.prompt()
          .system(systemPrompt)
          .user(userPrompt)
          .stream()
          .content()
          .doOnNext(token -> {
            if (token == null || token.isEmpty()) {
              return;
            }
            raw.append(token);

            // 检测句子边界，回调 onSentence
            if (onSentence != null && promptService.hasTerminalPunctuation(token)) {
              String normalized = promptService.normalizeRealtimeText(raw.toString());
              int currentEnd = normalized.length();
              if (currentEnd > lastSentenceEnd.get()) {
                String sentence = normalized.substring(lastSentenceEnd.get()).trim();
                if (!sentence.isEmpty()) {
                  onSentence.accept(sentence);
                }
                lastSentenceEnd.set(currentEnd);
              }
            }

            // 实时文本推送
            if (onToken == null) {
              return;
            }
            long now = System.nanoTime();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(now - lastEmitNanos.get());
            int currentLength = raw.length();
            boolean shouldEmit =
                elapsedMs >= emitIntervalMs
                    && currentLength - lastEmitLength.get() >= minCharsDelta;
            if (!shouldEmit) {
              return;
            }
            String normalized = promptService.normalizeRealtimeText(raw.toString());
            if (normalized.isBlank()) {
              return;
            }
            onToken.accept(normalized);
            lastEmitNanos.set(now);
            lastEmitLength.set(normalized.length());
          })
          .blockLast();

      // 发送最后一段（可能不以终止标点结尾）
      if (onSentence != null) {
        String normalized = promptService.normalizeRealtimeText(raw.toString());
        if (normalized.length() > lastSentenceEnd.get()) {
          String remaining = normalized.substring(lastSentenceEnd.get()).trim();
          if (!remaining.isEmpty()) {
            onSentence.accept(remaining);
          }
        }
      }

      String optimized = promptService.optimizeForVoice(raw.toString());
      if (onToken != null && !optimized.isBlank()) {
        onToken.accept(optimized);
      }

      return optimized;

    } catch (Exception e) {
      log.error("LLM sentence stream error: {}", e.getMessage(), e);
      return mapLlmErrorToUserMessage(e);
    }
  }

  /**
   * 构建用户 Prompt（含对话历史）
   *
   * @param userText            用户输入
   * @param conversationHistory 对话历史（可为 null）
   * @return 用户 Prompt
   */
  public String buildUserPrompt(String userText, List<String> conversationHistory) {
    StringBuilder promptBuilder = new StringBuilder();
    if (conversationHistory != null && !conversationHistory.isEmpty()) {
      promptBuilder.append("【之前的对话】\n");
      for (String message : conversationHistory) {
        promptBuilder.append(promptSanitizer.sanitize(message)).append("\n");
      }
      promptBuilder.append("\n【当前对话】\n");
    }
    promptBuilder.append("用户：")
        .append(promptSanitizer.wrapWithDelimiters("input", promptSanitizer.sanitize(userText)));
    return promptBuilder.toString();
  }

  /**
   * 将 LLM 错误映射为用户友好消息
   */
  public String mapLlmErrorToUserMessage(Exception e) {
    String errorMessage = e.getMessage();
    if (errorMessage != null) {
      if (errorMessage.contains("403")
          || errorMessage.contains("ACCESS_DENIED")
          || errorMessage.contains("Authentication")) {
        return "AI 服务认证失败，请检查 API Key 配置";
      } else if (errorMessage.contains("timeout") || errorMessage.contains("Timeout")) {
        return "AI 服务响应超时，请稍后重试";
      } else if (errorMessage.contains("429")
          || errorMessage.contains("rate limit")
          || errorMessage.contains("quota")) {
        return "AI 服务调用频率超限，请稍后重试";
      } else if (errorMessage.contains("connection") || errorMessage.contains("network")) {
        return "AI 服务网络连接失败，请检查网络";
      }
    }
    return "抱歉，AI 服务暂时不可用，请稍后重试";
  }
}
