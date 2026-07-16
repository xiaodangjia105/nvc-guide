package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.DialogueResponse;
import nvc.guide.modules.nvcpractice.dto.MessageResponse;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.dto.StreamMetadata;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import org.springframework.http.codec.ServerSentEvent;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.util.AiResponseCleaner;
import nvc.guide.modules.nvcscenario.service.NvcScenarioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcPracticeDialogueService {

  private final NvcPracticeSessionService sessionService;
  private final NvcPracticeMessageRepository messageRepository;
  private final NvcAgentOrchestrator orchestrator;
  private final ObjectMapper objectMapper;
  private final NvcEvaluationService evaluationService;
  private final NvcSummaryService summaryService;
  private final NvcScenarioService scenarioService;
  private final NvcStructuredPracticeService structuredPracticeService;

  /**
   * 发送消息并获取 AI 回复（非流式）
   */
  public DialogueResponse sendMessage(Long sessionId,
      String userMessage) {
    // 1. 获取会话
    NvcPracticeSessionEntity session =
        sessionService.getSession(sessionId);

    // 2. 保存用户消息
    int nextSeq = getNextSequenceNum(sessionId);
    NvcPracticeMessageEntity userMsg =
        NvcPracticeMessageEntity.builder()
            .sessionId(sessionId)
            .role(NvcMessageRole.USER)
            .content(userMessage)
            .sequenceNum(nextSeq)
            .step(session.getCurrentStep())
            .build();
    messageRepository.save(userMsg);

    // 2.5 场景驱动模式：增加场景使用次数
    if (session.getScenarioId() != null) {
      scenarioService.incrementUsage(session.getScenarioId());
    }

    // 3. 构建练习上下文
    PracticeContext context = orchestrator.buildPracticeContext(
        sessionId, session.getUserId());

    // 4. Agent 调度决策（在状态转换之前，确保 SCENARIO_GENERATOR 可以被选中）
    AgentDecision decision =
        orchestrator.decideNextAgent(context);

    // 5. 如果会话是 CREATED 状态，自动切换到 IN_PROGRESS
    if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
      session = sessionService.updatePhase(
          sessionId, NvcSessionPhase.IN_PROGRESS);
    }
    log.info("Agent decision: session={}, scene={}, reason={}",
        sessionId, decision.scene(), decision.reason());

    // 6. 更新会话的当前 Agent 场景
    sessionService.updateAgentScene(sessionId, decision.scene());

    // 7. 执行 Agent 对话
    String aiReply = orchestrator.executeAgent(
        decision, context, userMessage);

    // 7.5 清理 AI 回复中的 JSON/代码块
    aiReply = AiResponseCleaner.clean(aiReply);

    // 8. 保存 AI 回复
    NvcPracticeMessageEntity aiMsg =
        NvcPracticeMessageEntity.builder()
            .sessionId(sessionId)
            .role(NvcMessageRole.ASSISTANT)
            .agentScene(decision.scene())
            .content(aiReply)
            .sequenceNum(nextSeq + 1)
            .step(session.getCurrentStep())
            .build();
    messageRepository.save(aiMsg);

    // 9. 异步触发摘要更新（不阻塞对话）
    try {
      summaryService.updateSummary(sessionId, userMessage);
    } catch (Exception e) {
      log.warn("Summary update failed (non-blocking): sessionId={}",
          sessionId, e);
    }

    // 9.5 结构化四步模式：检查是否需要推进步骤
    if (session.getPracticeMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP
        && decision.action() != null
        && decision.action().equals("STEP_ADVANCE")) {
      structuredPracticeService.advanceStep(sessionId);
      session = sessionService.getSession(sessionId);
      log.info("Step advanced: sessionId={}, newStep={}",
          sessionId, session.getCurrentStep());
    }

    // 10. 返回结果
    return new DialogueResponse(
        sessionId,
        aiReply,
        decision.scene().name(),
        decision.reason(),
        decision.action(),
        session.getCurrentStep() != null
            ? session.getCurrentStep().name() : null
    );
  }

  /**
   * 发送消息并获取 AI 回复（流式 SSE）
   *
   * <p>事件格式：
   * <ul>
   *   <li>event: metadata  -> StreamMetadata JSON</li>
   *   <li>event: message   -> 纯文本 token（换行符转义为 \\n）</li>
   *   <li>event: done      -> {"length": N}</li>
   * </ul>
   */
  public Flux<ServerSentEvent<String>> sendMessageStream(Long sessionId,
      String userMessage) {
    // 1. 获取会话
    NvcPracticeSessionEntity session =
        sessionService.getSession(sessionId);

    // 2. 保存用户消息
    int nextSeq = getNextSequenceNum(sessionId);
    NvcPracticeMessageEntity userMsg =
        NvcPracticeMessageEntity.builder()
            .sessionId(sessionId)
            .role(NvcMessageRole.USER)
            .content(userMessage)
            .sequenceNum(nextSeq)
            .step(session.getCurrentStep())
            .build();
    messageRepository.save(userMsg);

    // 2.5 场景驱动模式：增加场景使用次数
    if (session.getScenarioId() != null) {
      scenarioService.incrementUsage(session.getScenarioId());
    }

    // 3. 构建上下文 + Agent 调度（在状态转换之前，确保 SCENARIO_GENERATOR 可以被选中）
    var currentStep = session.getCurrentStep();
    var practiceMode = session.getPracticeMode();
    Long userId = session.getUserId();
    PracticeContext context = orchestrator.buildPracticeContext(
        sessionId, userId);
    AgentDecision decision =
        orchestrator.decideNextAgent(context);

    // 3.5 状态转换：CREATED → IN_PROGRESS
    if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
      session = sessionService.updatePhase(
          sessionId, NvcSessionPhase.IN_PROGRESS);
    }

    sessionService.updateAgentScene(sessionId, decision.scene());

    // 4. 构建 metadata 事件
    StreamMetadata metadata = new StreamMetadata(
        decision.scene().name(),
        decision.reason(),
        decision.action(),
        currentStep != null ? currentStep.name() : null
    );
    String metadataJson;
    try {
      metadataJson = objectMapper.writeValueAsString(metadata);
    } catch (Exception e) {
      log.warn("Failed to serialize StreamMetadata", e);
      metadataJson = "{}";
    }

    // 5. 流式获取 AI 回复
    Flux<String> tokenStream = orchestrator.executeAgentStream(
        decision, context, userMessage);

    // 6. 组装 SSE 事件流（使用 ServerSentEvent 正确设置 event 类型）
    AiResponseCleaner.StreamingCleaner streamingCleaner =
        new AiResponseCleaner.StreamingCleaner();

    Flux<ServerSentEvent<String>> metadataEvent = Flux.just(
        ServerSentEvent.<String>builder()
            .event("metadata")
            .data(metadataJson)
            .build());

    Flux<ServerSentEvent<String>> messageEvents = tokenStream
        .mapNotNull(token -> {
          String cleaned = streamingCleaner.appendAndClean(token);
          if (cleaned.isEmpty()) {
            return null;
          }
          return ServerSentEvent.<String>builder()
              .event("message")
              .data(cleaned.replace("\n", "\\n").replace("\r", "\\r"))
              .build();
        });

    Flux<ServerSentEvent<String>> doneEvent = Flux.defer(() -> Flux.just(
        ServerSentEvent.<String>builder()
            .event("done")
            .data("{\"length\":" + streamingCleaner.getFinalCleaned().length() + "}")
            .build()));

    return Flux.concat(metadataEvent, messageEvents, doneEvent)
        .doOnComplete(() -> {
          // 流式清理已完成，直接获取最终结果
          String cleanedReply = streamingCleaner.getFinalCleaned();

          NvcPracticeMessageEntity aiMsg =
              NvcPracticeMessageEntity.builder()
                  .sessionId(sessionId)
                  .role(NvcMessageRole.ASSISTANT)
                  .agentScene(decision.scene())
                  .content(cleanedReply)
                  .sequenceNum(nextSeq + 1)
                  .step(currentStep)
                  .build();
          messageRepository.save(aiMsg);
          log.info("Stream reply saved: sessionId={}, length={}",
              sessionId, cleanedReply.length());

          // 异步触发摘要更新（不阻塞流式输出）
          try {
            summaryService.updateSummary(sessionId, userMessage);
          } catch (Exception e) {
            log.warn("Summary update failed in stream (non-blocking): sessionId={}",
                sessionId, e);
          }

          // 结构化四步模式：检查是否需要推进步骤
          try {
            if (practiceMode == NvcPracticeMode.STRUCTURED_FOUR_STEP
                && decision.action() != null
                && decision.action().equals("STEP_ADVANCE")) {
              structuredPracticeService.advanceStep(sessionId);
              log.info("Step advanced in stream: sessionId={}", sessionId);
            }
          } catch (Exception e) {
            log.error("Step advancement failed in stream: sessionId={}", sessionId, e);
          }
        });
  }

  /**
   * 获取对话历史
   */
  public List<MessageResponse> getMessages(Long sessionId) {
    return messageRepository
        .findBySessionIdOrderBySequenceNumAsc(sessionId)
        .stream()
        .map(this::toMessageResponse)
        .toList();
  }

  private int getNextSequenceNum(Long sessionId) {
    return messageRepository.countBySessionId(sessionId);
  }

  private MessageResponse toMessageResponse(
      NvcPracticeMessageEntity msg) {
    return new MessageResponse(
        msg.getId(),
        msg.getSessionId(),
        msg.getRole().name(),
        msg.getAgentScene() != null
            ? msg.getAgentScene().name() : null,
        msg.getContent(),
        msg.getSequenceNum(),
        msg.getStep() != null ? msg.getStep().name() : null,
        msg.getCreatedAt()
    );
  }
}
