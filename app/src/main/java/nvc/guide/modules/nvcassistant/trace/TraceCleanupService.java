package nvc.guide.modules.nvcassistant.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Trace 自动清理服务
 *
 * <p>定期清理超过保留天数的 trace 数据，避免数据库无限增长。
 * 支持配置保留天数、批量大小、cron 表达式。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TraceCleanupService {

    private final AgentTraceRepository traceRepository;
    private final AgentSpanRepository spanRepository;
    private final TraceProperties traceProperties;

    /**
     * 清理旧 Trace
     *
     * @return 删除的 Trace 数量
     */
    public int cleanupOldTraces() {
        TraceProperties.CleanupConfig cleanup = traceProperties.getCleanup();

        if (!cleanup.isEnabled()) {
            log.debug("[TraceCleanup] Cleanup is disabled");
            return 0;
        }

        int retentionDays = cleanup.getRetentionDays();
        int batchSize = cleanup.getBatchSize();
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);

        log.info("[TraceCleanup] Starting cleanup: retentionDays={}, cutoffTime={}", retentionDays, cutoffTime);

        // 查找需要删除的 Trace ID
        List<String> traceIdsToDelete = traceRepository.findTraceIdsOlderThan(cutoffTime);

        if (traceIdsToDelete.isEmpty()) {
            log.info("[TraceCleanup] No traces to delete");
            return 0;
        }

        log.info("[TraceCleanup] Found {} traces to delete", traceIdsToDelete.size());

        // 分批删除
        int totalDeleted = 0;
        for (int i = 0; i < traceIdsToDelete.size(); i += batchSize) {
            int end = Math.min(i + batchSize, traceIdsToDelete.size());
            List<String> batch = traceIdsToDelete.subList(i, end);

            try {
                // 先删除 Span（子表）
                spanRepository.deleteByTraceIdIn(batch);
                // 再删除 Trace（主表）
                traceRepository.deleteByTraceIdIn(batch);
                totalDeleted += batch.size();

                log.debug("[TraceCleanup] Deleted batch: {}/{}", totalDeleted, traceIdsToDelete.size());
            } catch (Exception e) {
                log.error("[TraceCleanup] Failed to delete batch: start={}, end={}, error={}",
                    i, end, e.getMessage(), e);
            }
        }

        log.info("[TraceCleanup] Cleanup completed: deleted={}", totalDeleted);
        return totalDeleted;
    }

    /**
     * 获取待清理的 Trace 数量
     */
    public long getPendingCleanupCount() {
        TraceProperties.CleanupConfig cleanup = traceProperties.getCleanup();

        if (!cleanup.isEnabled()) {
            return 0;
        }

        int retentionDays = cleanup.getRetentionDays();
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);

        return traceRepository.countOlderThan(cutoffTime);
    }

    /**
     * 定时清理任务（默认每天凌晨 3 点执行）
     */
    @Scheduled(cron = "${nvc.trace.cleanup.cron:0 0 3 * * ?}")
    public void scheduledCleanup() {
        log.info("[TraceCleanup] Scheduled cleanup started");
        try {
            int deleted = cleanupOldTraces();
            log.info("[TraceCleanup] Scheduled cleanup completed: deleted={}", deleted);
        } catch (Exception e) {
            log.error("[TraceCleanup] Scheduled cleanup failed: {}", e.getMessage(), e);
        }
    }
}
