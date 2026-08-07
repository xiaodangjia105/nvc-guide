package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限流钩子 — 基于滑动窗口的内存限流
 *
 * <p>限流配置：
 * <ul>
 *   <li>scenario_generate: 每小时 5 次</li>
 *   <li>evaluate_nvc: 每小时 20 次</li>
 *   <li>其他工具: 每分钟 30 次（默认）</li>
 * </ul>
 *
 * <p>Order=1（最先执行，拦截高频滥用）
 */
@Component
@Slf4j
@Order(1)
public class RateLimitToolHook implements NvcToolHook {

    /**
     * 限流窗口配置
     */
    private record RateLimitConfig(long windowMs, int maxCalls) {}

    private static final Map<String, RateLimitConfig> TOOL_LIMITS = Map.of(
        "scenario_generate", new RateLimitConfig(3_600_000L, 5),   // 每小时 5 次
        "evaluate_nvc",      new RateLimitConfig(3_600_000L, 20)   // 每小时 20 次
    );

    /** 默认限流: 每分钟 30 次 */
    private static final RateLimitConfig DEFAULT_LIMIT = new RateLimitConfig(60_000L, 30);

    /**
     * 滑动窗口计数器
     * key: rate_limit:{toolName}:{userId}
     * value: 窗口信息（窗口开始时间 + 计数）
     */
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    /** 定期清理过期计数器的调度器 */
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "rate-limit-cleanup");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void init() {
        // 每 10 分钟清理一次过期计数器
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredCounters, 10, 10, TimeUnit.MINUTES);
        log.info("[RateLimitToolHook] Initialized with periodic cleanup every 10 minutes");
    }

    @PreDestroy
    public void destroy() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[RateLimitToolHook] Shutdown complete, counters cleared");
    }

    /**
     * 清理过期计数器（窗口已过期的条目）
     */
    private void cleanupExpiredCounters() {
        int before = counters.size();
        long now = System.currentTimeMillis();
        counters.entrySet().removeIf(entry -> {
            WindowCounter counter = entry.getValue();
            // 使用最大窗口时间（1 小时）作为清理阈值
            return now - counter.windowStart > 3_600_000L;
        });
        int after = counters.size();
        if (before != after) {
            log.debug("[RateLimitToolHook] Cleaned up {} expired counters: {} -> {}", before - after, before, after);
        }
    }

    @Override
    public CompletableFuture<ToolCallDecision> beforeToolCall(String toolName, JsonNode arguments, NvcToolContext context) {
        Long userId = context.getUserId();
        if (userId == null) {
            return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
        }

        String key = "rate_limit:" + toolName + ":" + userId;
        RateLimitConfig config = TOOL_LIMITS.getOrDefault(toolName, DEFAULT_LIMIT);

        WindowCounter counter = counters.compute(key, (k, existing) -> {
            long now = System.currentTimeMillis();
            if (existing == null || now - existing.windowStart >= config.windowMs()) {
                // 新窗口或窗口过期，重置
                return new WindowCounter(now, new AtomicInteger(1));
            }
            // 同一窗口内，递增计数
            existing.count.incrementAndGet();
            return existing;
        });

        int currentCount = counter.count.get();
        if (currentCount > config.maxCalls()) {
            long elapsed = System.currentTimeMillis() - counter.windowStart;
            long remainingMs = config.windowMs() - elapsed;
            long remainingSec = Math.max(1, remainingMs / 1000);

            String reason = "调用过于频繁，请 " + remainingSec + " 秒后再试";
            context.setAttribute("skipReason", reason);
            log.warn("[RateLimitToolHook] Rate limited: tool={}, userId={}, count={}/{}, waitSec={}",
                toolName, userId, currentCount, config.maxCalls(), remainingSec);
            return CompletableFuture.completedFuture(ToolCallDecision.SKIP);
        }

        log.debug("[RateLimitToolHook] Passed: tool={}, userId={}, count={}/{}",
            toolName, userId, currentCount, config.maxCalls());
        return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
    }

    /**
     * 滑动窗口计数器
     */
    private static class WindowCounter {
        final long windowStart;
        final AtomicInteger count;

        WindowCounter(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
