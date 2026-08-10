package nvc.guide.modules.nvcpractice.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.event.PracticeCompletedEvent;
import nvc.guide.infrastructure.redis.RedisService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 练习完成事件监听器
 * 异步处理练习完成后的后续工作，失败不影响主流程
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PracticeCompletedEventListener {

    private final RedisService redisService;

    private static final String ACTIVE_SESSION_KEY = "practice:active:session:";

    /**
     * 练习完成后的异步处理
     * <p>
     * 1. 记录完成日志
     * 2. 清理活跃会话缓存
     */
    @Async
    @EventListener
    public void onPracticeCompleted(PracticeCompletedEvent event) {
        try {
            log.info("Practice completed event received: sessionId={}, userId={}, evaluationFailed={}",
                    event.getSessionId(), event.getUserId(), event.isEvaluationFailed());

            // 清理活跃会话缓存
            String cacheKey = ACTIVE_SESSION_KEY + event.getSessionId();
            try {
                redisService.delete(cacheKey);
                log.debug("Cleared active session cache: sessionId={}", event.getSessionId());
            } catch (Exception e) {
                log.warn("Failed to clear active session cache: sessionId={}, error={}",
                        event.getSessionId(), e.getMessage());
            }

            log.info("Practice completed event processed successfully: sessionId={}",
                    event.getSessionId());
        } catch (Exception e) {
            // 监听器失败不影响主流程
            log.error("Failed to process practice completed event: sessionId={}, userId={}, error={}",
                    event.getSessionId(), event.getUserId(), e.getMessage(), e);
        }
    }
}
