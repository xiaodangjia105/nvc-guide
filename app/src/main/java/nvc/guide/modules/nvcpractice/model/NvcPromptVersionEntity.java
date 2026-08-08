package nvc.guide.modules.nvcpractice.model;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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

    @NotNull(message = "Agent场景不能为空")
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_scene", nullable = false, length = 50)
    private NvcAgentScene agentScene;

    @NotNull(message = "版本号不能为空")
    @Min(value = 1, message = "版本号最小为1")
    @Column(nullable = false)
    private Integer version;

    @NotBlank(message = "系统提示词不能为空")
    @Size(max = 50000, message = "系统提示词不能超过50000字符")
    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    /**
     * 流量百分比（用于 A/B 测试，所有活跃版本的百分比之和应为 100）
     */
    @Min(value = 0, message = "流量百分比最小为0")
    @Max(value = 100, message = "流量百分比最大为100")
    @Column(name = "traffic_percentage")
    @Builder.Default
    private Integer trafficPercentage = 100;

    @Size(max = 2000, message = "变更说明不能超过2000字符")
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
