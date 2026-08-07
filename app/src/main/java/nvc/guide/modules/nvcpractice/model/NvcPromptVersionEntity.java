package nvc.guide.modules.nvcpractice.model;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Prompt 版本实体
 * 支持版本管理和 A/B 测试
 */
@Entity
@Table(name = "nvc_prompt_version", indexes = {
    @Index(name = "idx_prompt_version_scene", columnList = "agent_scene, is_active")
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcPromptVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_scene", nullable = false, length = 50)
    private NvcAgentScene agentScene;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    /**
     * 流量百分比（用于 A/B 测试，所有活跃版本的百分比之和应为 100）
     */
    @Column(name = "traffic_percentage")
    @Builder.Default
    private Integer trafficPercentage = 100;

    @Column(name = "change_note", columnDefinition = "TEXT")
    private String changeNote;

    // ===== 聚合指标（定期从 agent_metrics 聚合） =====

    @Column(name = "total_calls")
    @Builder.Default
    private Long totalCalls = 0L;

    @Column(name = "avg_evaluation_score")
    private Double avgEvaluationScore;

    @Column(name = "avg_token_usage")
    private Double avgTokenUsage;

    @Column(name = "avg_latency_ms")
    private Double avgLatencyMs;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
