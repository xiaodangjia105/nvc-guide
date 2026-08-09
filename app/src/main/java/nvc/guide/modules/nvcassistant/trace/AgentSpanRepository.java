package nvc.guide.modules.nvcassistant.trace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface AgentSpanRepository extends JpaRepository<AgentSpanEntity, String> {

    List<AgentSpanEntity> findByTraceIdOrderBySequenceAsc(String traceId);

    List<AgentSpanEntity> findByTraceIdAndSpanTypeOrderBySequenceAsc(String traceId, String spanType);

    /**
     * 批量删除指定 Trace ID 的 Span 记录
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM AgentSpanEntity s WHERE s.traceId IN :traceIds")
    void deleteByTraceIdIn(@Param("traceIds") List<String> traceIds);
}
