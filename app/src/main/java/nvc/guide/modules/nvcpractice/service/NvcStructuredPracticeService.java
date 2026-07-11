package nvc.guide.modules.nvcpractice.service;

import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.infrastructure.redis.RedisService;
import nvc.guide.modules.nvcpractice.dto.StepProgressDTO;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationType;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.repository.NvcEvaluationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcStructuredPracticeService {

    private final NvcPracticeSessionService sessionService;
    private final NvcEvaluationRepository evaluationRepository;
    private final NvcAgentConfigService agentConfigService;
    private final RedisService redisService;

    private static final String STEP_PROGRESS_CACHE_PREFIX = "nvc:step:progress:";

    /**
     * 获取步骤进度
     */
    public StepProgressDTO getStepProgress(Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);

        if (session.getPracticeMode() != NvcPracticeMode.STRUCTURED_FOUR_STEP) {
            return null;
        }

        NvcPracticeStep currentStep = session.getCurrentStep();
        if (currentStep == null || currentStep == NvcPracticeStep.COMPLETED) {
            return new StepProgressDTO(
                NvcPracticeStep.COMPLETED, 4, 4, null, false,
                "四步练习全部完成！进入综合练习。",
                null, null, null, null
            );
        }

        NvcEvaluationEntity latestEval = evaluationRepository
            .findBySessionIdAndEvaluationType(sessionId, NvcEvaluationType.REALTIME)
            .orElse(null);

        Integer currentStepScore = null;
        if (latestEval != null) {
            currentStepScore = getStepScore(latestEval, currentStep);
        }

        NvcAgentConfigEntity config = agentConfigService.getConfig(stepToCoach(currentStep));
        Integer threshold = config.getStepAdvanceThreshold() != null
            ? config.getStepAdvanceThreshold() : 70;

        return new StepProgressDTO(
            currentStep,
            getStepIndex(currentStep),
            4,
            currentStepScore,
            currentStepScore != null && currentStepScore >= threshold,
            getStepDescription(currentStep),
            config.getMaxStepAttempts(),
            null, // TODO: track attempts
            null, // TODO: track step start time
            config.getStepTimeoutMinutes()
        );
    }

    /**
     * 推进步骤
     */
    @Transactional
    public StepProgressDTO advanceStep(Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);
        NvcPracticeStep currentStep = session.getCurrentStep();

        if (session.getPracticeMode() != NvcPracticeMode.STRUCTURED_FOUR_STEP) {
            throw new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND,
                "Session is not in structured four-step mode: " + sessionId);
        }

        if (currentStep == null || currentStep == NvcPracticeStep.COMPLETED) {
            throw new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND,
                "Session already completed: " + sessionId);
        }

        NvcPracticeStep nextStep = nextStep(currentStep);
        sessionService.updateStep(sessionId, nextStep);

        log.info("Step advanced: sessionId={}, {} -> {}", sessionId, currentStep, nextStep);

        if (nextStep == NvcPracticeStep.COMPLETED) {
            return new StepProgressDTO(
                NvcPracticeStep.COMPLETED, 4, 4, null, false,
                "四步练习全部完成！进入综合练习。",
                null, null, null, null
            );
        }

        NvcAgentConfigEntity config = agentConfigService.getConfig(stepToCoach(nextStep));
        return new StepProgressDTO(
            nextStep, getStepIndex(nextStep), 4, null, false,
            getStepDescription(nextStep),
            config.getMaxStepAttempts(), null, null, config.getStepTimeoutMinutes()
        );
    }

    /**
     * 检查是否可推进
     */
    public boolean canAdvance(Long sessionId, Integer currentScore) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);
        NvcPracticeStep currentStep = session.getCurrentStep();

        if (currentStep == null || currentStep == NvcPracticeStep.COMPLETED) {
            return false;
        }

        NvcAgentConfigEntity config = agentConfigService.getConfig(stepToCoach(currentStep));
        Integer threshold = config.getStepAdvanceThreshold() != null
            ? config.getStepAdvanceThreshold() : 70;

        return currentScore != null && currentScore >= threshold;
    }

    /**
     * 重置步骤
     */
    @Transactional
    public StepProgressDTO resetStep(Long sessionId) {
        NvcPracticeSessionEntity session = sessionService.getSession(sessionId);

        if (session.getPracticeMode() != NvcPracticeMode.STRUCTURED_FOUR_STEP) {
            throw new BusinessException(
                ErrorCode.NVC_SESSION_NOT_FOUND,
                "Session is not in structured four-step mode: " + sessionId);
        }

        sessionService.updateStep(sessionId, NvcPracticeStep.OBSERVE);
        log.info("Step reset: sessionId={}", sessionId);

        NvcAgentConfigEntity config = agentConfigService.getConfig(NvcAgentScene.STEP_OBSERVE_COACH);
        return new StepProgressDTO(
            NvcPracticeStep.OBSERVE, 0, 4, null, false,
            getStepDescription(NvcPracticeStep.OBSERVE),
            config.getMaxStepAttempts(), null, null, config.getStepTimeoutMinutes()
        );
    }

    private NvcAgentScene stepToCoach(NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> NvcAgentScene.STEP_OBSERVE_COACH;
            case FEELING -> NvcAgentScene.STEP_FEELING_COACH;
            case NEED -> NvcAgentScene.STEP_NEED_COACH;
            case REQUEST -> NvcAgentScene.STEP_REQUEST_COACH;
            default -> NvcAgentScene.DIALOGUE_GUIDE;
        };
    }

    private NvcPracticeStep nextStep(NvcPracticeStep current) {
        return switch (current) {
            case OBSERVE -> NvcPracticeStep.FEELING;
            case FEELING -> NvcPracticeStep.NEED;
            case NEED -> NvcPracticeStep.REQUEST;
            case REQUEST -> NvcPracticeStep.COMPLETED;
            default -> NvcPracticeStep.COMPLETED;
        };
    }

    private int getStepScore(NvcEvaluationEntity eval, NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> eval.getObservationScore() != null ? eval.getObservationScore() : 0;
            case FEELING -> eval.getFeelingScore() != null ? eval.getFeelingScore() : 0;
            case NEED -> eval.getNeedScore() != null ? eval.getNeedScore() : 0;
            case REQUEST -> eval.getRequestScore() != null ? eval.getRequestScore() : 0;
            default -> eval.getOverallScore() != null ? eval.getOverallScore() : 0;
        };
    }

    private int getStepIndex(NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> 0;
            case FEELING -> 1;
            case NEED -> 2;
            case REQUEST -> 3;
            default -> 4;
        };
    }

    private String getStepDescription(NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> "步骤1/4：观察练习 — 学会区分观察（客观事实）和评论（主观判断）";
            case FEELING -> "步骤2/4：感受练习 — 学会识别和表达真实感受，区分感受和想法";
            case NEED -> "步骤3/4：需求练习 — 从感受追溯深层需求，区分需求和策略";
            case REQUEST -> "步骤4/4：请求练习 — 将需求转化为具体、可执行、正向的请求";
            default -> "综合练习 — 将四步串联成完整的NVC表达";
        };
    }
}
