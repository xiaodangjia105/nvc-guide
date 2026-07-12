package nvc.guide.modules.nvcvoice.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import nvc.guide.common.result.Result;
import nvc.guide.modules.nvcvoice.dto.CreateVoiceSessionRequest;
import nvc.guide.modules.nvcvoice.dto.VoiceEvaluationStatusDTO;
import nvc.guide.modules.nvcvoice.dto.VoiceMessageDTO;
import nvc.guide.modules.nvcvoice.dto.VoiceSessionResponse;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionStatus;
import nvc.guide.modules.nvcvoice.service.NvcVoiceService;

/**
 * NVC 语音练习 REST API
 */
@RestController
@RequestMapping("/api/nvc/voice")
@RequiredArgsConstructor
@Slf4j
public class NvcVoiceController {

  private final NvcVoiceService voiceService;

  /**
   * 创建语音练习会话
   */
  @PostMapping("/sessions")
  public Result<VoiceSessionResponse> createSession(
      @RequestBody CreateVoiceSessionRequest request) {
    log.info("Creating NVC voice session: mode={}, scenario={}",
        request.practiceMode(), request.scenarioId());
    return Result.success(voiceService.createSession(request));
  }

  /**
   * 获取会话详情
   */
  @GetMapping("/sessions/{sessionId}")
  public Result<VoiceSessionResponse> getSession(@PathVariable Long sessionId) {
    return Result.success(voiceService.getSessionResponse(sessionId));
  }

  /**
   * 结束会话
   */
  @PostMapping("/sessions/{sessionId}/end")
  public Result<VoiceSessionResponse> endSession(@PathVariable Long sessionId) {
    log.info("Ending NVC voice session: {}", sessionId);
    return Result.success(voiceService.endSession(sessionId));
  }

  /**
   * 暂停会话
   */
  @PutMapping("/sessions/{sessionId}/pause")
  public Result<VoiceSessionResponse> pauseSession(@PathVariable Long sessionId) {
    log.info("Pausing NVC voice session: {}", sessionId);
    return Result.success(voiceService.pauseSession(sessionId, "user"));
  }

  /**
   * 恢复会话
   */
  @PutMapping("/sessions/{sessionId}/resume")
  public Result<VoiceSessionResponse> resumeSession(@PathVariable Long sessionId) {
    log.info("Resuming NVC voice session: {}", sessionId);
    return Result.success(voiceService.resumeSession(sessionId));
  }

  /**
   * 列出会话
   */
  @GetMapping("/sessions")
  public Result<List<VoiceSessionResponse>> listSessions(
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) NvcVoiceSessionStatus status) {
    return Result.success(voiceService.listSessions(userId, status));
  }

  /**
   * 删除会话
   */
  @DeleteMapping("/sessions/{sessionId}")
  public Result<Void> deleteSession(@PathVariable Long sessionId) {
    log.info("Deleting NVC voice session: {}", sessionId);
    voiceService.deleteSession(sessionId);
    return Result.success();
  }

  /**
   * 获取对话历史
   */
  @GetMapping("/sessions/{sessionId}/messages")
  public Result<List<VoiceMessageDTO>> getMessages(@PathVariable Long sessionId) {
    return Result.success(voiceService.getMessages(sessionId));
  }

  /**
   * 获取评估状态/结果
   */
  @GetMapping("/sessions/{sessionId}/evaluation")
  public Result<VoiceEvaluationStatusDTO> getEvaluation(@PathVariable Long sessionId) {
    return Result.success(voiceService.getEvaluationStatus(sessionId));
  }

  /**
   * 手动触发评估
   */
  @PostMapping("/sessions/{sessionId}/evaluation")
  public Result<Void> triggerEvaluation(@PathVariable Long sessionId) {
    log.info("Triggering evaluation for NVC voice session: {}", sessionId);
    voiceService.triggerEvaluation(sessionId);
    return Result.success();
  }
}
