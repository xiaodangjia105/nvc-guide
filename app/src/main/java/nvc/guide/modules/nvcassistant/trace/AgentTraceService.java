package nvc.guide.modules.nvcassistant.trace;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Agent Trace 查询 Service
 *
 * <p>封装 Trace 数据的查询逻辑，Controller 不应直接调用 Repository。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentTraceService {

    private final AgentTraceRepository traceRepository;

    /**
     * 查询 Trace 列表（sessionId 可选，不传则返回所有）
     */
    public Page<AgentTraceEntity> listTraces(String sessionId, Pageable pageable) {
        if (sessionId != null && !sessionId.isBlank()) {
            return traceRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable);
        }
        return traceRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /**
     * 查询单个 Trace 详情（含 Spans，显式触发懒加载）
     */
    @Transactional(readOnly = true)
    public Optional<AgentTraceEntity> getTraceDetail(String traceId) {
        return traceRepository.findById(traceId)
            .map(trace -> {
                // 显式触发懒加载，确保 spans 被加载
                trace.getSpans().size();
                return trace;
            });
    }

    /**
     * 按状态和时间范围分页查询 Trace
     */
    public Page<AgentTraceEntity> searchByStatus(String status, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return traceRepository.findByStatusAndTimeRange(status, from, to, pageable);
    }

    /**
     * 按模式和时间范围分页查询 Trace
     */
    public Page<AgentTraceEntity> searchByMode(String mode, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return traceRepository.findByModeAndTimeRange(mode, from, to, pageable);
    }

    /**
     * 按时间范围分页查询 Trace
     */
    public Page<AgentTraceEntity> searchByTimeRange(LocalDateTime from, LocalDateTime to, Pageable pageable) {
        return traceRepository.findByCreatedAtBetween(from, to, pageable);
    }
}
