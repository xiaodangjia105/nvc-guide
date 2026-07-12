package nvc.guide.modules.nvcpractice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nvc_evaluation", indexes = {
    @Index(name = "idx_nvc_eval_session", columnList = "session_id"),
    @Index(name = "idx_nvc_eval_user", columnList = "user_id, created_at")
})
@Data
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

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
