package nvc.guide.modules.nvcvoice.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import nvc.guide.modules.nvcpractice.dto.AgentDecision;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;
import nvc.guide.modules.nvcvoice.dto.WebSocketSubtitleMessage;
import nvc.guide.modules.nvcvoice.service.NvcVoiceLlmService;
import nvc.guide.modules.nvcvoice.service.NvcVoicePromptService;
import nvc.guide.modules.nvcvoice.service.NvcVoiceService;
import nvc.guide.modules.nvcvoice.service.provider.AsrCallbacks;
import nvc.guide.modules.nvcvoice.service.provider.AsrProvider;
import nvc.guide.modules.nvcvoice.service.provider.TtsProvider;

/**
 * 语音管线编排器
 *
 * 编排 ASR → 合并 → LLM → TTS 的完整语音处理流程。
 * 从 WebSocket Handler 提取业务逻辑，Handler 只做协议层路由。
 */
@Service
@Slf4j
public class VoicePipelineCoordinator {

  private final AsrProvider asrProvider;
  private final TtsProvider ttsProvider;
  private final NvcVoiceLlmService llmService;
  private final NvcVoicePromptService promptService;
  private final NvcVoiceService voiceService;
  private final NvcVoiceProperties properties;
  private final ObjectMapper objectMapper;

  /** 阻塞 LLM/TTS 工作的虚拟线程池 */
  private final ExecutorService pipelineExecutor = Executors.newVirtualThreadPerTaskExecutor();

  public VoicePipelineCoordinator(
      AsrProvider asrProvider,
      TtsProvider ttsProvider,
      NvcVoiceLlmService llmService,
      NvcVoicePromptService promptService,
      NvcVoiceService voiceService,
      NvcVoiceProperties properties,
      ObjectMapper objectMapper) {
    this.asrProvider = asrProvider;
    this.ttsProvider = ttsProvider;
    this.llmService = llmService;
    this.promptService = promptService;
    this.voiceService = voiceService;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  // ==================== ASR 阶段 ====================

  /**
   * 启动 ASR 会话
   */
  public void startAsr(String sessionId, WebSocketSession wsSession) {
    log.info("[Pipeline] Starting ASR for session: {}", sessionId);

    asrProvider.startSession(sessionId, new AsrCallbacks(
        // onPartial: 实时字幕
        text -> sendSubtitle(wsSession, WebSocketSubtitleMessage.userPartial(text)),
        // onFinal: 合并到缓冲区
        text -> {
          log.debug("[Pipeline] ASR final: {}", text);
          // 缓冲区在 Handler 的 SessionState 中管理
        },
        // onReady
        () -> log.info("[Pipeline] ASR ready for session: {}", sessionId),
        // onError
        error -> {
          log.error("[Pipeline] ASR error for session {}: {}", sessionId, error.getMessage());
          handleAsrError(sessionId, wsSession, error);
        }
    ));
  }

  /**
   * 处理用户音频数据
   */
  public void handleAudioData(String sessionId, byte[] pcmData, SessionState state) {
    // 回声消除：AI 说话时丢弃
    if (state.isAiSpeakingOrCooldown()) {
      return;
    }
    asrProvider.sendAudio(sessionId, pcmData);
  }

  /**
   * 处理 ASR 最终结果
   */
  public void handleSttFinal(String text, SessionState state) {
    if (text != null && !text.isBlank()) {
      state.mergeBuffer.addSegment(text);
    }
  }

  /**
   * 停止 ASR 会话
   */
  public void stopAsr(String sessionId) {
    log.info("[Pipeline] Stopping ASR for session: {}", sessionId);
    asrProvider.stopSession(sessionId);
  }

  // ==================== 提交阶段 ====================

  /**
   * 处理用户提交（VAD 检测静音或手动提交）
   */
  public void handleSubmit(String sessionId, SessionState state, WebSocketSession wsSession) {
    String userText = state.mergeBuffer.flush();
    if (userText == null || userText.isBlank()) {
      log.debug("[Pipeline] Empty submit, ignoring");
      return;
    }

    log.info("[Pipeline] Submit from session {}: {}", sessionId, userText);

    pipelineExecutor.submit(() -> {
      // CAS 防并发
      if (!state.processing.compareAndSet(false, true)) {
        log.warn("[Pipeline] Already processing, skipping submit");
        return;
      }

      state.markAiSpeaking();

      try {
        // 1. 构建上下文 + Agent 调度
        PracticeContext context = voiceService.buildVoiceContext(parseSessionId(sessionId));
        AgentDecision decision = voiceService.decideNextAgent(context);

        log.info("[Pipeline] Agent decision: {}, reason: {}", decision.scene(), decision.reason());

        // 2. 构建 Prompt
        String systemPrompt = promptService.buildSystemPrompt(
            decision.scene().name(), null); // TODO: 从 AgentConfig 获取 systemPrompt
        String userPrompt = llmService.buildUserPrompt(userText, null);

        // 3. LLM 流式调用
        String aiText;
        if (properties.isLlmStreamingEnabled()) {
          aiText = llmService.chatStreamSentences(
              systemPrompt,
              userPrompt,
              // onToken: 实时文本
              token -> sendSubtitle(wsSession, WebSocketSubtitleMessage.aiPartial(token)),
              // onSentence: 触发 TTS
              sentence -> {
                if (properties.isChunkedAudioEnabled()) {
                  // 分片模式：每句触发 TTS
                  triggerSentenceTts(sessionId, sentence, wsSession);
                }
              },
              voiceService.getSession(parseSessionId(sessionId)).getLlmProvider()
          );
        } else {
          aiText = llmService.chat(systemPrompt, userPrompt,
              voiceService.getSession(parseSessionId(sessionId)).getLlmProvider());
        }

        // 4. TTS 合成（非分片模式或兜底）
        if (!properties.isChunkedAudioEnabled()) {
          byte[] audio = ttsProvider.synthesize(aiText);
          sendAudioResponse(wsSession, audio);
        }

        // 5. 保存消息
        voiceService.saveMessage(
            parseSessionId(sessionId), userText, aiText, decision.scene().name());

        // 6. 发送最终文本
        sendTextResponse(wsSession, aiText, decision.scene().name());

        // 7. 触发实时评估
        voiceService.triggerEvaluation(parseSessionId(sessionId));

      } catch (Exception e) {
        log.error("[Pipeline] Error processing submit for session {}: {}",
            sessionId, e.getMessage(), e);
        sendError(wsSession, "处理失败，请重试");
      } finally {
        state.markAiSilent();
        state.processing.set(false);
      }
    });
  }

  // ==================== 控制消息 ====================

  /**
   * 处理控制消息
   */
  public void handleControl(String sessionId, String action, SessionState state,
      WebSocketSession wsSession) {
    log.info("[Pipeline] Control action: {}, session: {}", action, sessionId);

    switch (action) {
      case "submit" -> handleSubmit(sessionId, state, wsSession);
      case "pause" -> {
        voiceService.pauseSession(parseSessionId(sessionId), "user");
        sendMessage(wsSession, createControlMessage("paused"));
      }
      case "resume" -> {
        voiceService.resumeSession(parseSessionId(sessionId));
        sendMessage(wsSession, createControlMessage("resumed"));
      }
      case "end" -> {
        voiceService.endSession(parseSessionId(sessionId));
        sendMessage(wsSession, createControlMessage("ended"));
      }
      default -> log.warn("[Pipeline] Unknown control action: {}", action);
    }
  }

  // ==================== 内部方法 ====================

  private void triggerSentenceTts(String sessionId, String sentence, WebSocketSession wsSession) {
    CompletableFuture<byte[]> ttsFuture = CompletableFuture.supplyAsync(
        () -> ttsProvider.synthesize(sentence), pipelineExecutor);

    ttsFuture.thenAccept(audio -> {
      if (audio != null && audio.length > 0) {
        sendAudioChunk(wsSession, audio);
      }
    }).exceptionally(error -> {
      log.warn("[Pipeline] TTS failed for sentence: {}, error: {}", sentence, error.getMessage());
      return null;
    });
  }

  private void handleAsrError(String sessionId, WebSocketSession wsSession, Throwable error) {
    // 尝试重启 ASR
    try {
      Thread.sleep(1000);
      asrProvider.restartSession(sessionId, new AsrCallbacks(
          text -> sendSubtitle(wsSession, WebSocketSubtitleMessage.userPartial(text)),
          text -> {
            // 重新连接后继续合并
          },
          () -> log.info("[Pipeline] ASR reconnected for session: {}", sessionId),
          retryError -> {
            log.error("[Pipeline] ASR reconnect failed for session {}: {}",
                sessionId, retryError.getMessage());
            sendError(wsSession, "语音识别连接失败，请刷新页面重试");
          }
      ));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void sendSubtitle(WebSocketSession session, WebSocketSubtitleMessage message) {
    sendMessage(session, message);
  }

  private void sendTextResponse(WebSocketSession session, String text, String agentScene) {
    sendMessage(session, new TextMessageData("text", text, agentScene));
  }

  private void sendAudioResponse(WebSocketSession session, byte[] audio) {
    if (audio == null || audio.length == 0) {
      return;
    }
    String base64Audio = Base64.getEncoder().encodeToString(audio);
    sendMessage(session, new AudioMessageData("audio", base64Audio, "wav"));
  }

  private void sendAudioChunk(WebSocketSession session, byte[] audio) {
    if (audio == null || audio.length == 0) {
      return;
    }
    String base64Audio = Base64.getEncoder().encodeToString(audio);
    sendMessage(session, new AudioChunkMessageData("audio_chunk", base64Audio));
  }

  private void sendError(WebSocketSession session, String errorMessage) {
    sendMessage(session, createControlMessage("error", errorMessage));
  }

  private void sendMessage(WebSocketSession session, Object message) {
    try {
      if (session != null && session.isOpen()) {
        String json = objectMapper.writeValueAsString(message);
        session.sendMessage(new TextMessage(json));
      }
    } catch (IOException e) {
      log.error("[Pipeline] Failed to send WebSocket message: {}", e.getMessage());
    }
  }

  private Object createControlMessage(String action) {
    return createControlMessage(action, null);
  }

  private Object createControlMessage(String action, String data) {
    return new ControlMessageData("control", action, data);
  }

  private Long parseSessionId(String sessionId) {
    return Long.parseLong(sessionId);
  }

  // ==================== 内部 DTO ====================

  private record TextMessageData(String type, String text, String agentScene) {}

  private record AudioMessageData(String type, String data, String format) {}

  private record AudioChunkMessageData(String type, String data) {}

  private record ControlMessageData(String type, String action, String data) {}
}
