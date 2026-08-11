package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class NvcPracticeSessionValidator {

  private static final Map<NvcSessionPhase, Set<NvcSessionPhase>> VALID_TRANSITIONS = Map.of(
      NvcSessionPhase.CREATED, Set.of(NvcSessionPhase.IN_PROGRESS, NvcSessionPhase.COMPLETED),
      NvcSessionPhase.IN_PROGRESS, Set.of(NvcSessionPhase.PAUSED, NvcSessionPhase.COMPLETED),
      NvcSessionPhase.PAUSED, Set.of(NvcSessionPhase.IN_PROGRESS, NvcSessionPhase.COMPLETED),
      NvcSessionPhase.COMPLETED, Set.of(NvcSessionPhase.EVALUATED),
      NvcSessionPhase.EVALUATED, Set.of()
  );

  /**
   * 启动时校验 VALID_TRANSITIONS 覆盖所有枚举值
   */
  @PostConstruct
  public void validateTransitions() {
    for (NvcSessionPhase phase : NvcSessionPhase.values()) {
      if (!VALID_TRANSITIONS.containsKey(phase)) {
        throw new IllegalStateException(
            "VALID_TRANSITIONS 缺少枚举值: " + phase + "，请补充状态转换规则");
      }
    }
    log.info("VALID_TRANSITIONS 校验通过，覆盖所有 {} 个枚举值", NvcSessionPhase.values().length);
  }

  public void validatePhaseTransition(NvcSessionPhase currentPhase, NvcSessionPhase newPhase, Long sessionId) {
    Set<NvcSessionPhase> allowed = VALID_TRANSITIONS.getOrDefault(currentPhase, Set.of());
    if (!allowed.contains(newPhase)) {
      log.warn("Invalid phase transition attempted: sessionId={}, {} -> {}",
          sessionId, currentPhase, newPhase);
      throw new BusinessException(
          ErrorCode.INVALID_OPERATION,
          "不允许从 " + currentPhase + " 转换到 " + newPhase);
    }
  }
}
