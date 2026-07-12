package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcpractice.model.NvcSummaryEntity;
import nvc.guide.modules.nvcpractice.repository.NvcSummaryRepository;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * NVC 四要素摘要服务
 * 从用户对话中提取观察/感受/需求/请求，更新摘要
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcSummaryService {

  private final NvcSummaryRepository summaryRepository;
  private final NvcPracticeMessageRepository messageRepository;
  private final LlmProviderRegistry llmProviderRegistry;
  private final ObjectMapper objectMapper;

  private static final String SUMMARY_PROMPT = """
      你是 NVC（非暴力沟通）表达分析助手。分析用户的最新消息，提取其中的四要素。

      当前对话历史：
      %s

      用户最新消息：
      %s

      已有摘要：
      %s

      请结合对话历史和已有摘要，给出每个要素的最终完整内容：
      1. 观察：用户描述的客观事实（不含评判）
      2. 感受：用户表达的真实情绪
      3. 需求：用户表达或暗示的深层需求
      4. 请求：用户提出的具体请求

      返回 JSON：
      {
        "observation": "该要素的最终完整内容，或 null",
        "feeling": "该要素的最终完整内容，或 null",
        "need": "该要素的最终完整内容，或 null",
        "request": "该要素的最终完整内容，或 null",
        "hint": "引导提示，建议用户下一步可以表达什么"
      }

      规则：
      - 返回每个要素的最终完整值（综合已有摘要和新内容，不是只返回新增部分）
      - 如果用户没有表达某个要素且已有摘要中也没有，返回 null
      - 如果用户对已有要素描述得更清晰，用更清晰的版本替换
      - hint 应该根据缺失的要素，给出具体的引导建议
      - 只输出 JSON，不要其他内容
      """;

  /**
   * 异步更新摘要
   */
  @Async
  public void updateSummary(Long sessionId, String userMessage) {
    try {
      // 1. 获取对话历史（最近 10 条）
      List<NvcPracticeMessageEntity> messages =
          messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
      String conversation = buildConversationText(messages);

      // 2. 获取已有摘要
      Optional<NvcSummaryEntity> existing = summaryRepository.findBySessionId(sessionId);
      String existingSummary = existing.map(this::formatSummary).orElse("无");

      // 3. 调用 LLM 分析
      String prompt = SUMMARY_PROMPT.formatted(conversation, userMessage, existingSummary);
      ChatClient client = llmProviderRegistry.getPlainChatClient(null);
      String result = client.prompt()
          .user(prompt)
          .call()
          .content();

      // 4. 解析结果
      JsonNode json = parseJson(result);
      if (json == null) {
        log.warn("Failed to parse summary JSON: {}", result);
        return;
      }

      // 5. 更新摘要（增量合并）
      NvcSummaryEntity summary = existing.orElseGet(() ->
          NvcSummaryEntity.builder().sessionId(sessionId).build());

      mergeField(summary::getObservation, summary::setObservation,
          json.path("observation"));
      mergeField(summary::getFeeling, summary::setFeeling,
          json.path("feeling"));
      mergeField(summary::getNeed, summary::setNeed,
          json.path("need"));
      mergeField(summary::getRequest, summary::setRequest,
          json.path("request"));

      if (json.has("hint") && !json.get("hint").isNull()) {
        summary.setHint(json.get("hint").asText());
      }

      summary.setRawAnalysis(result);
      summaryRepository.save(summary);

      log.info("Summary updated: sessionId={}", sessionId);
    } catch (Exception e) {
      log.error("Failed to update summary: sessionId={}", sessionId, e);
    }
  }

  /**
   * 获取当前摘要
   */
  public NvcSummaryEntity getSummary(Long sessionId) {
    return summaryRepository.findBySessionId(sessionId).orElse(null);
  }

  private void mergeField(
      java.util.function.Supplier<String> getter,
      java.util.function.Consumer<String> setter,
      JsonNode node) {
    if (node != null && !node.isNull()) {
      String newValue = node.asText();
      // 始终采用 LLM 返回的最新值（LLM 已收到已有摘要上下文，会做出综合判断）
      setter.accept(newValue);
    }
  }

  private String buildConversationText(List<NvcPracticeMessageEntity> messages) {
    // 只取最近 10 条
    int start = Math.max(0, messages.size() - 10);
    StringBuilder sb = new StringBuilder();
    for (int i = start; i < messages.size(); i++) {
      var msg = messages.get(i);
      if (msg.getRole() == NvcMessageRole.USER) {
        sb.append("用户: ").append(msg.getContent()).append("\n");
      } else if (msg.getRole() == NvcMessageRole.ASSISTANT) {
        sb.append("AI: ").append(truncate(msg.getContent(), 100)).append("\n");
      }
    }
    return sb.toString();
  }

  private String formatSummary(NvcSummaryEntity s) {
    StringBuilder sb = new StringBuilder();
    if (s.getObservation() != null) sb.append("观察: ").append(s.getObservation()).append("\n");
    if (s.getFeeling() != null) sb.append("感受: ").append(s.getFeeling()).append("\n");
    if (s.getNeed() != null) sb.append("需求: ").append(s.getNeed()).append("\n");
    if (s.getRequest() != null) sb.append("请求: ").append(s.getRequest()).append("\n");
    return sb.isEmpty() ? "无" : sb.toString();
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
  }

  private JsonNode parseJson(String text) {
    try {
      // 尝试提取 JSON 部分
      int start = text.indexOf('{');
      int end = text.lastIndexOf('}');
      if (start >= 0 && end > start) {
        return objectMapper.readTree(text.substring(start, end + 1));
      }
      return objectMapper.readTree(text);
    } catch (Exception e) {
      return null;
    }
  }
}
