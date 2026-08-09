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

    @NotNull(message = "会话ID不能为空")
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @NotNull(message = "用户ID不能为空")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Min(value = 0, message = "观察评分最低为0")
    @Max(value = 100, message = "观察评分最高为100")
    @Column(name = "observation_score")
    private Integer observationScore;

    @Min(value = 0, message = "感受评分最低为0")
    @Max(value = 100, message = "感受评分最高为100")
    @Column(name = "feeling_score")
    private Integer feelingScore;

    @Min(value = 0, message = "需求评分最低为0")
    @Max(value = 100, message = "需求评分最高为100")
    @Column(name = "need_score")
    private Integer needScore;

    @Min(value = 0, message = "请求评分最低为0")
    @Max(value = 100, message = "请求评分最高为100")
    @Column(name = "request_score")
    private Integer requestScore;

    @Min(value = 0, message = "总体评分最低为0")
    @Max(value = 100, message = "总体评分最高为100")
    @Column(name = "overall_score")
    private Integer overallScore;

    @Min(value = 0, message = "共情评分最低为0")
    @Max(value = 100, message = "共情评分最高为100")
    @Column(name = "empathy_score")
    private Integer empathyScore;

    @Size(max = 5000, message = "观察详情不能超过5000字符")
    @Column(name = "observation_detail", columnDefinition = "TEXT")
    private String observationDetail;

    @Size(max = 5000, message = "感受详情不能超过5000字符")
    @Column(name = "feeling_detail", columnDefinition = "TEXT")
    private String feelingDetail;

    @Size(max = 5000, message = "需求详情不能超过5000字符")
    @Column(name = "need_detail", columnDefinition = "TEXT")
    private String needDetail;

    @Size(max = 5000, message = "请求详情不能超过5000字符")
    @Column(name = "request_detail", columnDefinition = "TEXT")
    private String requestDetail;

    @Size(max = 5000, message = "共情详情不能超过5000字符")
    @Column(name = "empathy_detail", columnDefinition = "TEXT")
    private String empathyDetail;

    @Size(max = 5000, message = "优势描述不能超过5000字符")
    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Size(max = 5000, message = "改进描述不能超过5000字符")
    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Size(max = 5000, message = "参考表达不能超过5000字符")
    @Column(name = "reference_expressions", columnDefinition = "TEXT")
    private String referenceExpressions;

    @Size(max = 5000, message = "摘要不能超过5000字符")
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
