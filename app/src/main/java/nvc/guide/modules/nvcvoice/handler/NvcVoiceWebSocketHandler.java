package nvc.guide.modules.nvcvoice.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import nvc.guide.modules.nvcvoice.config.NvcVoiceProperties;
import nvc.guide.modules.nvcvoice.dto.WebSocketSubtitleMessage;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionEntity;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionStatus;
import nvc.guide.modules.nvcvoice.pipeline.SessionState;
import nvc.guide.modules.nvcvoice.pipeline.VoicePipelineCoordinator;
import nvc.guide.modules.nvcvoice.service.NvcVoiceService;

/**
 * NVC 语音练习 WebSocket 处理器（瘦 Handler）
 *
 * 只负责：
 * - WebSocket 连接生命周期管理
 * - 消息类型路由（audio/control → Pipeline）
 * - 并发安全（ConcurrentWebSocketSessionDecorator）
 * - 回声消除（AI 说话时丢弃麦克风输入）
 *
 * 业务逻辑全部委托给 {@link VoicePipelineCoordinator}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NvcVoiceWebSocketHandler extends TextWebSocketHandler implements DisposableBean {

  private final ObjectMapper objectMapper;
  private final VoicePipelineCoordinator pipeline;
  private final NvcVoiceService voiceService;
  private final NvcVoiceProperties properties;

  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final Map<String, SessionState> sessionStates = new ConcurrentHashMap<>();

  private static final int WS_SEND_TIME_LIMIT_MS = 10_000;
  private static final int WS_SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

  // ==================== 连接生命周期 ====================

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String sessionId = extractSessionId(session);
    if (sessionId == null) {
      session.close(CloseStatus.BAD_DATA);
      return;
    }

    // 验证会话存在
    Long sessionIdLong = parseSessionId(sessionId);
    NvcVoiceSessionEntity voiceSession = voiceService.getSession(sessionIdLong);
    if (voiceSession == null) {
      log.warn("[Handler] Session not found: {}", sessionId);
      session.close(CloseStatus.BAD_DATA);
      return;
    }

    // 验证会话状态
    if (voiceSession.getStatus() != NvcVoiceSessionStatus.IN_PROGRESS) {
      log.warn("[Handler] Session not in progress: {}, status: {}", sessionId,
          voiceSession.getStatus());
      session.close(CloseStatus.BAD_DATA);
      return;
    }

    // 包装为线程安全的 Session
    ConcurrentWebSocketSessionDecorator safeSession =
        new ConcurrentWebSocketSessionDecorator(session, WS_SEND_TIME_LIMIT_MS,
            WS_SEND_BUFFER_LIMIT_BYTES);

    sessions.put(sessionId, safeSession);
    sessionStates.put(sessionId, new SessionState());

    log.info("[Handler] WebSocket connected: {}", sessionId);

    // 启动 ASR
    pipeline.startAsr(sessionId, safeSession);

    // 发送欢迎消息
    sendWelcomeMessage(safeSession, voiceSession);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String sessionId = extractSessionId(session);
    if (sessionId == null) {
      return;
    }

    log.info("[Handler] WebSocket closed: {}, status: {}", sessionId, status);

    // 清理
    cleanupSession(sessionId);

    // 自动结束进行中的会话
    Long sessionIdLong = parseSessionId(sessionId);
    voiceService.endSessionIfInProgress(sessionIdLong);
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) {
    String sessionId = extractSessionId(session);
    log.error("[Handler] Transport error for session {}: {}", sessionId, exception.getMessage());
  }

  // ==================== 消息路由 ====================

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    String sessionId = extractSessionId(session);
    if (sessionId == null) {
      return;
    }

    SessionState state = sessionStates.get(sessionId);
    if (state == null) {
      log.warn("[Handler] No state for session: {}", sessionId);
      return;
    }

    try {
      JsonNode root = objectMapper.readTree(message.getPayload());
      String type = root.path("type").asText();

      switch (type) {
        case "audio" -> handleAudioMessage(sessionId, root, state);
        case "control" -> handleControlMessage(sessionId, root, state, session);
        default -> log.warn("[Handler] Unknown message type: {}", type);
      }
    } catch (Exception e) {
      log.error("[Handler] Error processing message for session {}: {}", sessionId,
          e.getMessage(), e);
    }
  }

  @Override
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
    // 二进制消息暂不支持
    log.warn("[Handler] Binary message not supported");
  }

  // ==================== 内部处理 ====================

  private void handleAudioMessage(String sessionId, JsonNode root, SessionState state) {
    String data = root.path("data").asText();
    if (data == null || data.isBlank()) {
      return;
    }

    byte[] pcmData = Base64.getDecoder().decode(data);
    pipeline.handleAudioData(sessionId, pcmData, state);
  }

  private void handleControlMessage(String sessionId, JsonNode root, SessionState state,
      WebSocketSession session) {
    String action = root.path("action").asText();
    if (action == null || action.isBlank()) {
      return;
    }

    pipeline.handleControl(sessionId, action, state, session);
  }

  // ==================== 辅助方法 ====================

  private void sendWelcomeMessage(WebSocketSession session, NvcVoiceSessionEntity voiceSession) {
    try {
      String welcomeText = buildWelcomeText(voiceSession);
      String json = objectMapper.writeValueAsString(
          new WelcomeMessage("text", welcomeText, "system"));
      session.sendMessage(new TextMessage(json));

      // 发送字幕
      String subtitleJson = objectMapper.writeValueAsString(
          WebSocketSubtitleMessage.aiFinal(welcomeText));
      session.sendMessage(new TextMessage(subtitleJson));

    } catch (IOException e) {
      log.error("[Handler] Failed to send welcome message: {}", e.getMessage());
    }
  }

  private String buildWelcomeText(NvcVoiceSessionEntity session) {
    return switch (session.getPracticeMode()) {
      case SCENARIO -> "你好！欢迎来到 NVC 语音练习。让我们开始场景练习吧。请描述你遇到的情况。";
      case FREE_DIALOG -> "你好！欢迎来到 NVC 语音练习。请自由表达你想练习的沟通场景。";
      case STRUCTURED_FOUR_STEP ->
          "你好！欢迎来到 NVC 结构化四步语音练习。让我们从第一步「观察」开始。请描述一个你想练习的场景。";
    };
  }

  private void cleanupSession(String sessionId) {
    sessions.remove(sessionId);
    sessionStates.remove(sessionId);
    pipeline.stopAsr(sessionId);
  }

  private String extractSessionId(WebSocketSession session) {
    String uri = session.getUri() != null ? session.getUri().toString() : null;
    if (uri == null) {
      return null;
    }

    // 从路径中提取 sessionId: /ws/nvc-voice/{sessionId}
    String[] parts = uri.split("/");
    if (parts.length >= 4) {
      return parts[parts.length - 1];
    }
    return null;
  }

  private Long parseSessionId(String sessionId) {
    try {
      return Long.parseLong(sessionId);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  @Override
  public void destroy() {
    // 清理所有会话
    for (String sessionId : sessions.keySet()) {
      cleanupSession(sessionId);
    }
  }

  // ==================== 内部 DTO ====================

  private record WelcomeMessage(String type, String text, String source) {}
}
