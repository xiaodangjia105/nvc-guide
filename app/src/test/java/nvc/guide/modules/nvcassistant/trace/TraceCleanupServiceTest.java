package nvc.guide.modules.nvcassistant.trace;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TraceCleanupService 自动清理")
class TraceCleanupServiceTest {

    private AgentTraceRepository traceRepository;
    private AgentSpanRepository spanRepository;
    private TraceProperties traceProperties;
    private TraceCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        traceRepository = mock(AgentTraceRepository.class);
        spanRepository = mock(AgentSpanRepository.class);
        traceProperties = new TraceProperties();
        cleanupService = new TraceCleanupService(traceRepository, spanRepository, traceProperties);
    }

    @Nested
    @DisplayName("cleanupOldTraces - 清理旧 Trace")
    class CleanupOldTraces {

        @Test
        @DisplayName("应该删除超过保留天数的 Trace")
        void shouldDeleteTracesOlderThanRetentionDays() {
            // 配置保留 30 天
            traceProperties.getCleanup().setEnabled(true);
            traceProperties.getCleanup().setRetentionDays(30);
            traceProperties.getCleanup().setBatchSize(100);

            // 模拟需要删除的 Trace ID（分页返回）
            List<String> oldTraceIds = List.of("trace-1", "trace-2", "trace-3");
            Page<String> page = new PageImpl<>(oldTraceIds);
            when(traceRepository.findTraceIdsOlderThan(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page);

            // 执行清理
            int deletedCount = cleanupService.cleanupOldTraces();

            // 验证
            assertEquals(3, deletedCount);
            verify(traceRepository).findTraceIdsOlderThan(any(LocalDateTime.class), any(Pageable.class));
            verify(spanRepository).deleteByTraceIdIn(oldTraceIds);
            verify(traceRepository).deleteByTraceIdIn(oldTraceIds);
        }

        @Test
        @DisplayName("没有旧 Trace 时应该返回 0")
        void shouldReturn0WhenNoOldTraces() {
            traceProperties.getCleanup().setEnabled(true);
            traceProperties.getCleanup().setRetentionDays(30);
            traceProperties.getCleanup().setBatchSize(100);

            Page<String> emptyPage = new PageImpl<>(List.of());
            when(traceRepository.findTraceIdsOlderThan(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(emptyPage);

            int deletedCount = cleanupService.cleanupOldTraces();

            assertEquals(0, deletedCount);
            verify(spanRepository, never()).deleteByTraceIdIn(anyList());
            verify(traceRepository, never()).deleteByTraceIdIn(anyList());
        }

        @Test
        @DisplayName("禁用清理时应该不执行任何操作")
        void shouldDoNothingWhenDisabled() {
            traceProperties.getCleanup().setEnabled(false);

            int deletedCount = cleanupService.cleanupOldTraces();

            assertEquals(0, deletedCount);
            verify(traceRepository, never()).findTraceIdsOlderThan(any(), any());
        }

        @Test
        @DisplayName("应该分页删除避免内存溢出")
        void shouldDeleteInPages() {
            traceProperties.getCleanup().setEnabled(true);
            traceProperties.getCleanup().setRetentionDays(30);
            traceProperties.getCleanup().setBatchSize(2);

            // 模拟分页返回：第一页 2 条，第二页 2 条，第三页 1 条
            // 使用 PageRequest 和 total 参数确保 hasNext() 正确工作
            Page<String> page1 = new PageImpl<>(List.of("trace-1", "trace-2"), PageRequest.of(0, 2), 5);
            Page<String> page2 = new PageImpl<>(List.of("trace-3", "trace-4"), PageRequest.of(1, 2), 5);
            Page<String> page3 = new PageImpl<>(List.of("trace-5"), PageRequest.of(2, 2), 5);

            when(traceRepository.findTraceIdsOlderThan(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page1, page2, page3);

            int deletedCount = cleanupService.cleanupOldTraces();

            assertEquals(5, deletedCount);
            // 验证分页删除（3 页）
            verify(spanRepository, times(3)).deleteByTraceIdIn(anyList());
            verify(traceRepository, times(3)).deleteByTraceIdIn(anyList());
        }
    }

    @Nested
    @DisplayName("getCleanupStats - 获取清理统计")
    class GetCleanupStats {

        @Test
        @DisplayName("应该返回待清理的 Trace 数量")
        void shouldReturnPendingCleanupCount() {
            traceProperties.getCleanup().setEnabled(true);
            traceProperties.getCleanup().setRetentionDays(30);

            when(traceRepository.countOlderThan(any(LocalDateTime.class)))
                .thenReturn(42L);

            long count = cleanupService.getPendingCleanupCount();

            assertEquals(42L, count);
        }

        @Test
        @DisplayName("禁用时应该返回 0")
        void shouldReturn0WhenDisabled() {
            traceProperties.getCleanup().setEnabled(false);

            long count = cleanupService.getPendingCleanupCount();

            assertEquals(0L, count);
        }
    }
}
