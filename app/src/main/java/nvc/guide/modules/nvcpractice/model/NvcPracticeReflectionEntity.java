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
 * 练习反思实体
 * 存储每次练习完成后的反思结果，用于自适应难度和上下文记忆
 */
@Entity
@Table(name = "nvc_practice_reflection", indexes = {
    @Index(name = "idx_reflection_user_time", columnList = "user_id, created_at"),
    @Index(name = "idx_reflection_session", columnList = "session_id")
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcPracticeReflectionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /**
     * 薄弱要素列表（JSON 数组）
     * 例如：["observation", "request"]
     */
    @Column(name = "weak_elements", columnDefinition = "TEXT")
    private String weakElements;

    /**
     * 建议难度（EASY/MEDIUM/HARD）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "suggested_difficulty", length = 20)
    private NvcDifficulty suggestedDifficulty;

    /**
     * 建议场景类型
     */
    @Column(name = "suggested_scenario_type", length = 50)
    private String suggestedScenarioType;

    /**
     * 策略建议说明
     */
    @Column(name = "strategy_note", columnDefinition = "TEXT")
    private String strategyNote;

    /**
     * 反思时的评估分数快照
     */
    @Column(name = "observation_score")
    private Integer observationScore;

    @Column(name = "feeling_score")
    private Integer feelingScore;

    @Column(name = "need_score")
    private Integer needScore;

    @Column(name = "request_score")
    private Integer requestScore;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
