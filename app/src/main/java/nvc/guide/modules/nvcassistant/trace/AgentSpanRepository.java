package nvc.guide.modules.nvcassistant.trace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentSpanRepository extends JpaRepository<AgentSpanEntity, String> {

    List<AgentSpanEntity> findByTraceIdOrderBySequenceAsc(String traceId);

    List<AgentSpanEntity> findByTraceIdAndSpanTypeOrderBySequenceAsc(String traceId, String spanType);
}
