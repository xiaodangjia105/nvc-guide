package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.ai.LlmProviderRegistry;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentChatService {

  private final LlmProviderRegistry llmProviderRegistry;

  /**
   * 执行 Agent 对话（非流式）
   */
  public String chat(
      NvcAgentConfigEntity config, PracticeContext context,
      String userMessage, Map<String, String> promptVariables) {
    ChatClient client = getChatClient(config);
    List<Message> messages = buildMessages(config, context, userMessage, promptVariables);

    return client.prompt()
        .options(buildChatOptions(config))
        .messages(messages)
        .call()
        .content();
  }

  /**
   * 执行 Agent 对话（流式）
   */
  public Flux<String> chatStream(
      NvcAgentConfigEntity config, PracticeContext context,
      String userMessage, Map<String, String> promptVariables) {
    ChatClient client = getChatClient(config);
    List<Message> messages = buildMessages(config, context, userMessage, promptVariables);

    return client.prompt()
        .options(buildChatOptions(config))
        .messages(messages)
        .stream()
        .content();
  }

  /**
   * 使用 Plain ChatClient（无工具、无记忆）执行简单对话
   * 用于评估、反思等需要结构化输出的场景
   */
  public String chatPlain(
      NvcAgentConfigEntity config, String systemPrompt, String userPrompt) {
    ChatClient client = llmProviderRegistry.getPlainChatClient(
        config.getModelProvider());

    return client.prompt()
        .options(buildChatOptions(config))
        .system(systemPrompt)
        .user(userPrompt)
        .call()
        .content();
  }

  /**
   * 根据 Agent 配置获取 ChatClient
   */
  private ChatClient getChatClient(NvcAgentConfigEntity config) {
    return llmProviderRegistry.getChatClientOrDefault(config.getModelProvider());
  }

  /**
   * 构建 per-call ChatOptions，覆盖 Agent 级别的模型参数
   */
  private OpenAiChatOptions buildChatOptions(NvcAgentConfigEntity config) {
    return OpenAiChatOptions.builder()
        .temperature(config.getTemperature())
        .maxTokens(config.getMaxTokens())
        .topP(config.getTopP())
        .build();
  }

  /**
   * 组装对话消息列表
   * 包含：系统提示词 + 用户档案摘要 + 场景信息 + RAG 知识 + promptVariables + 对话历史 + 当前消息
   */
  private List<Message> buildMessages(
      NvcAgentConfigEntity config, PracticeContext context,
      String userMessage, Map<String, String> promptVariables) {
    List<Message> messages = new ArrayList<>();

    // 1. 系统提示词
    String systemPrompt = config.getSystemPrompt();

    // 2. 注入用户档案信息（如有）
    if (context.getUserProfileSummary() != null) {
      systemPrompt += "\n\n[用户档案]\n" + context.getUserProfileSummary();
    }

    // 3. 注入场景信息（如有）
    if (context.getScenarioDescription() != null) {
      systemPrompt += "\n\n[练习场景]\n" + context.getScenarioDescription();
    }

    // 4. 注入 RAG 知识（如有）
    if (context.getRagContext() != null) {
      systemPrompt += "\n\n[参考资料]\n" + context.getRagContext();
    }

    // 5. 注入路由变量（mode、covered_elements 等）
    if (promptVariables != null && !promptVariables.isEmpty()) {
      StringBuilder sb = new StringBuilder("\n\n[当前配置]");
      promptVariables.forEach((key, value) ->
          sb.append("\n").append(key).append("=").append(value));
      systemPrompt += sb;
    }

    messages.add(new SystemMessage(systemPrompt));

    // 6. 对话历史
    if (context.getRecentMessages() != null) {
      for (var msg : context.getRecentMessages()) {
        if (msg.getRole() == NvcMessageRole.USER) {
          messages.add(new UserMessage(msg.getContent()));
        } else if (msg.getRole() == NvcMessageRole.ASSISTANT) {
          messages.add(new AssistantMessage(msg.getContent()));
        }
      }
    }

    // 7. 当前用户消息（防御性去重：recentMessages 已在 DB 保存后构建，可能已包含当前消息）
    boolean alreadyIncluded = context.getRecentMessages() != null
        && !context.getRecentMessages().isEmpty()
        && context.getRecentMessages().getLast().getRole() == NvcMessageRole.USER
        && context.getRecentMessages().getLast().getContent().equals(userMessage);
    if (!alreadyIncluded) {
      messages.add(new UserMessage(userMessage));
    }

    return messages;
  }
}
