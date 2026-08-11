package nvc.guide.modules.nvcassistant.trace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentTraceRepository extends JpaRepository<AgentTraceEntity, String> {

    Page<AgentTraceEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    Page<AgentTraceEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 按时间范围分页查询 Trace
     */
    @Query("SELECT t FROM AgentTraceEntity t WHERE t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    Page<AgentTraceEntity> findByCreatedAtBetween(
        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    /**
     * 按状态 + 时间范围分页查询 Trace
     */
    @Query("SELECT t FROM AgentTraceEntity t WHERE t.finalStatus = :status AND t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    Page<AgentTraceEntity> findByStatusAndTimeRange(
        @Param("status") String status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
        Pageable pageable);

    /**
     * 按模式 + 时间范围分页查询 Trace
     */
    @Query("SELECT t FROM AgentTraceEntity t WHERE t.mode = :mode AND t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    Page<AgentTraceEntity> findByModeAndTimeRange(
        @Param("mode") String mode, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
        Pageable pageable);

    /**
     * 查找超过指定时间的 Trace ID 列表（带分页，避免大量数据一次性加载）
     */
    @Query("SELECT t.traceId FROM AgentTraceEntity t WHERE t.createdAt < :cutoffTime")
    Page<String> findTraceIdsOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime, Pageable pageable);

    /**
     * 统计超过指定时间的 Trace 数量
     */
    @Query("SELECT COUNT(t) FROM AgentTraceEntity t WHERE t.createdAt < :cutoffTime")
    long countOlderThan(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * 批量删除指定 Trace ID 的记录
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AgentTraceEntity t WHERE t.traceId IN :traceIds")
    void deleteByTraceIdIn(@Param("traceIds") List<String> traceIds);
}
