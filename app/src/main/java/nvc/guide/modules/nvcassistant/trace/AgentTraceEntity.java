package nvc.guide.modules.nvcassistant.trace;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent Trace 主实体
 *
 * <p>记录一次完整用户交互的全链路信息，包含多个 Span 子实体。
 * 通过 Redis Stream 异步落库，不影响对话主链路性能。
 */
@Entity
@Table(name = "agent_trace", indexes = {
    @Index(name = "idx_trace_session", columnList = "session_id"),
    @Index(name = "idx_trace_user", columnList = "user_id"),
    @Index(name = "idx_trace_created", columnList = "created_at"),
    @Index(name = "idx_trace_status", columnList = "final_status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTraceEntity {

    @Id
    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /**
     * 练习模式：FREE_DIALOG / SCENARIO / STRUCTURED
     */
    @Column(nullable = false, length = 32)
    private String mode;

    /**
     * 触发类型：USER_MESSAGE / TOOL_CALL / AUTO
     */
    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(name = "total_spans", nullable = false)
    @Builder.Default
    private Integer totalSpans = 0;

    @Column(name = "total_duration_ms", nullable = false)
    @Builder.Default
    private Long totalDurationMs = 0L;

    @Column(name = "total_input_tokens", nullable = false)
    @Builder.Default
    private Integer totalInputTokens = 0;

    @Column(name = "total_output_tokens", nullable = false)
    @Builder.Default
    private Integer totalOutputTokens = 0;

    /**
     * 最终状态：SUCCESS / DEGRADED / FAILED
     */
    @Column(name = "final_status", nullable = false, length = 16)
    private String finalStatus;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "trace", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("sequence ASC")
    @Builder.Default
    private List<AgentSpanEntity> spans = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
