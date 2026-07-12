package nvc.guide.modules.nvcvoice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import nvc.guide.common.ai.PromptSecurityConstants;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;

/**
 * NVC 语音练习 Prompt 构建服务
 *
 * 负责构建语音练习的系统 Prompt，包含：
 * - Agent 角色设定
 * - NVC 场景描述
 * - 语音输出约束
 * - 反注入指令
 */
@Service
@Slf4j
public class NvcVoicePromptService {

  private final NvcVoiceProperties properties;

  /** 终止标点 */
  private static final String TERMINAL_PUNCTUATION = "。！？；!?;.";

  /** 语音输出约束 */
  private static final String VOICE_RESPONSE_CONSTRAINTS = """

      【语音输出要求】
      - 回复控制在 120 字以内
      - 不要使用 markdown 格式（如 **加粗**、- 列表）
      - 不要使用特殊符号（如 #、*、[]）
      - 使用口语化表达，适合语音朗读
      - 句子简短，避免长句
      - 适当使用语气词，让对话更自然
      - 不要重复对方刚说的话
      - 每轮只表达一个核心观点或问题
      """;

  public NvcVoicePromptService(NvcVoiceProperties properties) {
    this.properties = properties;
  }

  /**
   * 构建语音练习系统 Prompt
   *
   * @param agentSystemPrompt   Agent 的系统提示词（来自 NvcAgentConfigEntity）
   * @param scenarioDescription 场景描述（可为 null）
   * @return 完整的系统 Prompt
   */
  public String buildSystemPrompt(String agentSystemPrompt, String scenarioDescription) {
    StringBuilder prompt = new StringBuilder();

    // 1. Agent 角色设定
    if (agentSystemPrompt != null && !agentSystemPrompt.isBlank()) {
      prompt.append(agentSystemPrompt);
    }

    // 2. 场景描述
    if (scenarioDescription != null && !scenarioDescription.isBlank()) {
      prompt.append("\n\n【练习场景】\n").append(scenarioDescription);
    }

    // 3. 语音输出约束
    prompt.append(VOICE_RESPONSE_CONSTRAINTS);

    // 4. 反注入指令
    prompt.append(PromptSecurityConstants.ANTI_INJECTION_INSTRUCTION);

    return prompt.toString();
  }

  /**
   * 优化 LLM 输出为语音友好格式
   *
   * @param content 原始 LLM 输出
   * @return 优化后的文本
   */
  public String optimizeForVoice(String content) {
    String normalized = normalizeRealtimeText(content);
    if (normalized.isBlank()) {
      return "请继续。";
    }

    int maxChars = Math.max(80, properties.getAiQuestionMaxChars());
    if (normalized.length() <= maxChars) {
      return normalized;
    }

    // 在句子边界截断
    String truncated = normalized.substring(0, maxChars);
    int lastTerminal = -1;
    for (int i = truncated.length() - 1; i >= 0; i--) {
      if (TERMINAL_PUNCTUATION.indexOf(truncated.charAt(i)) >= 0) {
        lastTerminal = i;
        break;
      }
    }
    if (lastTerminal >= maxChars / 2) {
      return truncated.substring(0, lastTerminal + 1);
    }

    return truncated + "…";
  }

  /**
   * 标准化实时文本（去除 markdown、特殊符号）
   */
  public String normalizeRealtimeText(String content) {
    if (content == null || content.isBlank()) {
      return "";
    }
    return content
        .replace("**", "")
        .replace("```", "")
        .replace("`", "")
        .replaceAll("(?m)^\\s*[-*+]\\s*", "")
        .replaceAll("\\s+", " ")
        .trim();
  }

  /**
   * 检测 token 中是否包含终止标点
   */
  public boolean hasTerminalPunctuation(String token) {
    for (int i = 0; i < token.length(); i++) {
      if (TERMINAL_PUNCTUATION.indexOf(token.charAt(i)) >= 0) {
        return true;
      }
    }
    return false;
  }
}
