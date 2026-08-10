package nvc.guide.modules.nvcassistant.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
     * 清理旧 Trace（分页查询 + 分批删除，避免一次性加载过多数据）
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

        int totalDeleted = 0;
        int pageNum = 0;

        // 分页查询待删除的 Trace ID，每页 batchSize 条
        while (true) {
            Page<String> traceIdPage = traceRepository.findTraceIdsOlderThan(
                cutoffTime, PageRequest.of(pageNum, batchSize));
            List<String> traceIdsToDelete = traceIdPage.getContent();

            if (traceIdsToDelete.isEmpty()) {
                break;
            }

            log.info("[TraceCleanup] Deleting page {}: {} traces", pageNum, traceIdsToDelete.size());

            try {
                // 先删除 Span（子表）
                spanRepository.deleteByTraceIdIn(traceIdsToDelete);
                // 再删除 Trace（主表）
                traceRepository.deleteByTraceIdIn(traceIdsToDelete);
                totalDeleted += traceIdsToDelete.size();

                log.debug("[TraceCleanup] Deleted batch: {}", totalDeleted);
            } catch (Exception e) {
                log.error("[TraceCleanup] Failed to delete batch: page={}, size={}, error={}",
                    pageNum, traceIdsToDelete.size(), e.getMessage(), e);
            }

            // 如果是最后一页，退出循环
            if (!traceIdPage.hasNext()) {
                break;
            }
            pageNum++;
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
