package nvc.guide.modules.nvcassistant.metrics;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentMetricsRepository extends JpaRepository<AgentMetricsEntity, Long> {

    /**
     * @deprecated 使用分页版本 {@link #findBySessionIdOrderByCreatedAtAsc(String, Pageable)}
     */
    @Deprecated
    List<AgentMetricsEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    Page<AgentMetricsEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId, Pageable pageable);

    /**
     * @deprecated 使用分页版本
     */
    @Deprecated
    List<AgentMetricsEntity> findByMetricTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
        String metricType, LocalDateTime from, LocalDateTime to);

    Page<AgentMetricsEntity> findByMetricTypeAndCreatedAtBetweenOrderByCreatedAtAsc(
        String metricType, LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * @deprecated 使用分页版本 {@link #findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime, LocalDateTime, Pageable)}
     */
    @Deprecated
    List<AgentMetricsEntity> findByCreatedAtBetweenOrderByCreatedAtAsc(
        LocalDateTime from, LocalDateTime to);

    Page<AgentMetricsEntity> findByCreatedAtBetweenOrderByCreatedAtAsc(
        LocalDateTime from, LocalDateTime to, Pageable pageable);

    /**
     * @deprecated 使用分页版本
     */
    @Deprecated
    @Query("SELECT m FROM AgentMetricsEntity m WHERE m.metricType = :type AND m.createdAt BETWEEN :from AND :to")
    List<AgentMetricsEntity> findByTypeAndTimeRange(
        @Param("type") String type,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    @Query("SELECT m FROM AgentMetricsEntity m WHERE m.metricType = :type AND m.createdAt BETWEEN :from AND :to")
    Page<AgentMetricsEntity> findByTypeAndTimeRange(
        @Param("type") String type,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable);
}
