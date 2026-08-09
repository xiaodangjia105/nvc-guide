package nvc.guide.modules.nvcassistant.trace;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AgentTraceRepository extends JpaRepository<AgentTraceEntity, String> {

    List<AgentTraceEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId);

    Page<AgentTraceEntity> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);

    Page<AgentTraceEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT t FROM AgentTraceEntity t WHERE t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    List<AgentTraceEntity> findByCreatedAtBetween(
        @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT t FROM AgentTraceEntity t WHERE t.finalStatus = :status AND t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    List<AgentTraceEntity> findByStatusAndTimeRange(
        @Param("status") String status, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT t FROM AgentTraceEntity t WHERE t.mode = :mode AND t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt DESC")
    List<AgentTraceEntity> findByModeAndTimeRange(
        @Param("mode") String mode, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
