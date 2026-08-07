package nvc.guide.modules.nvcpractice.model;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nvc_evaluation", indexes = {
    @Index(name = "idx_nvc_eval_session", columnList = "session_id"),
    @Index(name = "idx_nvc_eval_user", columnList = "user_id, created_at")
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

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

    @Column(name = "empathy_score")
    private Integer empathyScore;

    @Column(name = "observation_detail", columnDefinition = "TEXT")
    private String observationDetail;

    @Column(name = "feeling_detail", columnDefinition = "TEXT")
    private String feelingDetail;

    @Column(name = "need_detail", columnDefinition = "TEXT")
    private String needDetail;

    @Column(name = "request_detail", columnDefinition = "TEXT")
    private String requestDetail;

    @Column(name = "empathy_detail", columnDefinition = "TEXT")
    private String empathyDetail;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Column(name = "reference_expressions", columnDefinition = "TEXT")
    private String referenceExpressions;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", length = 20)
    @Builder.Default
    private NvcEvaluationType evaluationType = NvcEvaluationType.REALTIME;

    /**
     * 是否为降级评估（关键词匹配评分）
     * 降级评估结果仅供参考，服务恢复后可触发 LLM 重新评估
     */
    @Column(name = "degraded")
    @Builder.Default
    private Boolean degraded = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
