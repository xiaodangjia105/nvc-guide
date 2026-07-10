package nvc.guide.modules.nvcpractice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nvc_practice_session", indexes = {
    @Index(name = "idx_nvc_session_user", columnList = "user_id"),
    @Index(name = "idx_nvc_session_phase", columnList = "current_phase")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcPracticeSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "practice_mode", nullable = false, length = 20)
    private NvcPracticeMode practiceMode;

    @Column(name = "scenario_id")
    private Long scenarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", length = 30)
    @Builder.Default
    private NvcSessionPhase currentPhase = NvcSessionPhase.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 20)
    private NvcPracticeStep currentStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_scene", length = 50)
    private NvcAgentScene agentScene;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private NvcDifficulty difficulty = NvcDifficulty.MEDIUM;

    @Column(name = "context_summary", columnDefinition = "TEXT")
    private String contextSummary;

    @Column(name = "session_config", columnDefinition = "JSONB")
    private String sessionConfig;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
