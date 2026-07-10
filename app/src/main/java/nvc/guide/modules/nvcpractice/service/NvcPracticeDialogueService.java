package nvc.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.DialogueResponse;
import nvc.guide.modules.nvcpractice.dto.MessageResponse;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcpractice.dto.StreamMetadata;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcMessageRole;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
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

  /**
   * 发送消息并获取 AI 回复（非流式）
   */
  public DialogueResponse sendMessage(Long sessionId,
      String userMessage) {
    // 1. 获取会话
    NvcPracticeSessionEntity session =
        sessionService.getSession(sessionId);

    // 2. 如果会话是 CREATED 状态，自动切换到 IN_PROGRESS
    if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
      session = sessionService.updatePhase(
          sessionId, NvcSessionPhase.IN_PROGRESS);
    }

    // 3. 保存用户消息
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

    // 4. 构建练习上下文
    PracticeContext context = orchestrator.buildPracticeContext(
        sessionId, session.getUserId());

    // 5. Agent 调度决策
    AgentDecision decision =
        orchestrator.decideNextAgent(context);
    log.info("Agent decision: session={}, scene={}, reason={}",
        sessionId, decision.scene(), decision.reason());

    // 6. 更新会话的当前 Agent 场景
    sessionService.updateAgentScene(sessionId, decision.scene());

    // 7. 执行 Agent 对话
    String aiReply = orchestrator.executeAgent(
        decision.scene(), context, userMessage);

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

    // 9. 异步触发实时评估（不阻塞对话）
    try {
      evaluationService.evaluateRealtime(
          sessionId,
          session.getUserId(),
          userMessage,
          aiReply,
          session.getCurrentStep());
    } catch (Exception e) {
      log.warn("Realtime evaluation failed (non-blocking): sessionId={}",
          sessionId, e);
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
   *   <li>event: message   -> 纯文本 token</li>
   *   <li>event: done      -> {"length": N}</li>
   * </ul>
   */
  public Flux<String> sendMessageStream(Long sessionId,
      String userMessage) {
    // 1. 获取会话 + 自动切换状态
    NvcPracticeSessionEntity session =
        sessionService.getSession(sessionId);
    if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
      session = sessionService.updatePhase(
          sessionId, NvcSessionPhase.IN_PROGRESS);
    }

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

    // 3. 构建上下文 + Agent 调度
    var currentStep = session.getCurrentStep();
    Long userId = session.getUserId();
    PracticeContext context = orchestrator.buildPracticeContext(
        sessionId, userId);
    AgentDecision decision =
        orchestrator.decideNextAgent(context);
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
        decision.scene(), context, userMessage);

    // 6. 组装 SSE 事件流
    StringBuilder fullReply = new StringBuilder();
    Flux<String> metadataEvent = Flux.just(
        "event: metadata\ndata: " + metadataJson + "\n\n");
    Flux<String> messageEvents = tokenStream
        .doOnNext(fullReply::append)
        .map(token ->
            "event: message\ndata: " + token + "\n\n");
    Flux<String> doneEvent = Flux.defer(() -> Flux.just(
        "event: done\ndata: {\"length\":"
        + fullReply.length() + "}\n\n"));

    return Flux.concat(metadataEvent, messageEvents, doneEvent)
        .doOnComplete(() -> {
          NvcPracticeMessageEntity aiMsg =
              NvcPracticeMessageEntity.builder()
                  .sessionId(sessionId)
                  .role(NvcMessageRole.ASSISTANT)
                  .agentScene(decision.scene())
                  .content(fullReply.toString())
                  .sequenceNum(nextSeq + 1)
                  .step(currentStep)
                  .build();
          messageRepository.save(aiMsg);
          log.info("Stream reply saved: sessionId={}, length={}",
              sessionId, fullReply.length());

          // 实时评估（不阻塞流式输出）
          try {
            evaluationService.evaluateRealtime(
                sessionId,
                userId,
                userMessage,
                fullReply.toString(),
                currentStep);
          } catch (Exception e) {
            log.warn("Realtime evaluation failed in stream (non-blocking): sessionId={}",
                sessionId, e);
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
