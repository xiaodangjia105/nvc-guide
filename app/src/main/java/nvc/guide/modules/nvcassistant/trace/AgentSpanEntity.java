package nvc.guide.modules.nvcassistant.trace;

import jakarta.persistence.*;
import lombok.*;
import nvc.guide.common.trace.TraceSpan;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Agent Span 子实体
 *
 * <p>记录 Trace 中一个步骤的详细信息（一次 LLM 调用、一次工具调用等）。
 */
@Entity
@Table(name = "agent_span", indexes = {
    @Index(name = "idx_span_trace", columnList = "trace_id"),
    @Index(name = "idx_span_type", columnList = "span_type"),
    @Index(name = "idx_span_status", columnList = "status"),
    @Index(name = "idx_span_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentSpanEntity implements TraceSpan {

    @Id
    @Column(name = "span_id", length = 64)
    private String spanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trace_id", nullable = false)
    private AgentTraceEntity trace;

    @Column(name = "trace_id", insertable = false, updatable = false, length = 64)
    private String traceId;

    /**
     * 在 Trace 中的顺序
     */
    @Column(nullable = false)
    private Integer sequence;

    /**
     * Span 类型：INTENT_ROUTING / LLM_CALL / TOOL_CALL / COMPRESSION / EVALUATION / FALLBACK / METRICS
     */
    @Column(name = "span_type", nullable = false, length = 32)
    private String spanType;

    /**
     * 组件名称：IntentRouter / AgentLoop / ToolExecutor / ContextManager / NvcEvaluationService 等
     */
    @Column(name = "component_name", nullable = false, length = 64)
    private String componentName;

    /**
     * 输入数据（JSON，截断到 payload-max-length）
     */
    @Column(name = "input_payload", columnDefinition = "TEXT")
    private String inputPayload;

    /**
     * 输出数据（JSON，截断到 payload-max-length）
     */
    @Column(name = "output_payload", columnDefinition = "TEXT")
    private String outputPayload;

    @Column(name = "duration_ms", nullable = false)
    @Builder.Default
    private Long durationMs = 0L;

    /**
     * 状态：SUCCESS / DEGRADED / FAILED
     */
    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    /**
     * 扩展字段（JSONB）
     */
    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
