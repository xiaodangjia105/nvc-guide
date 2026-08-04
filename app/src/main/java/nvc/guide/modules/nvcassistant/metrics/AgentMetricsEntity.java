package nvc.guide.modules.nvcassistant.metrics;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Agent 指标采集实体
 *
 * <p>存储 Agent 运行时的各项量化指标，通过 Redis Stream 异步落库。
 * 支持 4 种指标类型：TOKEN / LATENCY / COMPRESSION / TOOL_CALL
 */
@Entity
@Table(name = "agent_metrics", indexes = {
    @Index(name = "idx_metrics_session", columnList = "session_id"),
    @Index(name = "idx_metrics_type", columnList = "metric_type"),
    @Index(name = "idx_metrics_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetricsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    /**
     * 指标类型：TOKEN / LATENCY / COMPRESSION / TOOL_CALL
     */
    @Column(name = "metric_type", nullable = false, length = 32)
    private String metricType;

    /**
     * 指标数据（JSONB）
     *
     * <p>TOKEN 类型示例：
     * <pre>{"inputTokens": 1200, "outputTokens": 180, "model": "qwen-plus", "degraded": false}</pre>
     *
     * <p>LATENCY 类型示例：
     * <pre>{"latencyMs": 1800, "phase": "e2e"}</pre>
     *
     * <p>COMPRESSION 类型示例：
     * <pre>{"beforeTokens": 3200, "afterTokens": 1800, "reductionPercent": 43.75}</pre>
     *
     * <p>TOOL_CALL 类型示例：
     * <pre>{"toolName": "rag_search", "success": true, "latencyMs": 450}</pre>
     */
    @Column(nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
