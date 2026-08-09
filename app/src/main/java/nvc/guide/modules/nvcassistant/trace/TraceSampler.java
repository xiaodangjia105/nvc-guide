package nvc.guide.modules.nvcassistant.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Trace 采样器
 *
 * <p>控制生产环境的 trace 采样率，避免过多的 trace 数据影响性能。
 * 调试用户和调试会话总是会被采样。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TraceSampler {

    private final TraceProperties traceProperties;

    /**
     * 判断是否应该采样
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @return true 表示应该采样，false 表示跳过
     */
    public boolean shouldSample(String userId, String sessionId) {
        TraceProperties.SamplingConfig sampling = traceProperties.getSampling();

        // 禁用采样时总是采样
        if (!sampling.isEnabled()) {
            return true;
        }

        // 检查调试用户
        if (userId != null && isDebugUser(userId)) {
            log.debug("[TraceSampler] Debug user, always sample: userId={}", userId);
            return true;
        }

        // 检查调试会话
        if (sessionId != null && isDebugSession(sessionId)) {
            log.debug("[TraceSampler] Debug session, always sample: sessionId={}", sessionId);
            return true;
        }

        // 按采样率随机决定
        double rate = sampling.getRate();
        boolean sampled = ThreadLocalRandom.current().nextDouble() < rate;

        if (sampled) {
            log.debug("[TraceSampler] Sampled: userId={}, sessionId={}, rate={}", userId, sessionId, rate);
        }

        return sampled;
    }

    /**
     * 获取当前采样率
     */
    public double getSampledRate() {
        if (!traceProperties.getSampling().isEnabled()) {
            return 1.0;
        }
        return traceProperties.getSampling().getRate();
    }

    /**
     * 检查是否是调试用户
     */
    private boolean isDebugUser(String userId) {
        try {
            Long userIdLong = Long.parseLong(userId);
            return traceProperties.getRuntime().getDebugUsers().contains(userIdLong);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 检查是否是调试会话
     */
    private boolean isDebugSession(String sessionId) {
        try {
            Long sessionIdLong = Long.parseLong(sessionId);
            return traceProperties.getRuntime().getDebugSessions().contains(sessionIdLong);
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
