package nvc.guide.modules.nvcvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * NVC 语音评估结果实体
 */
@Entity
@Table(name = "nvc_voice_evaluations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcVoiceEvaluationEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", unique = true)
  private Long sessionId;

  // === NVC 四维度评分（0-100）===

  @Column(name = "observation_score")
  private Integer observationScore;

  @Column(name = "feeling_score")
  private Integer feelingScore;

  @Column(name = "need_score")
  private Integer needScore;

  @Column(name = "request_score")
  private Integer requestScore;

  @Column(name = "empathy_score")
  private Integer empathyScore;

  @Column(name = "overall_score")
  private Integer overallScore;

  // === 语音特有评分 ===

  @Column(name = "fluency_score")
  private Integer fluencyScore;

  // === 详细评语 ===

  @Column(name = "observation_detail", columnDefinition = "TEXT")
  private String observationDetail;

  @Column(name = "feeling_detail", columnDefinition = "TEXT")
  private String feelingDetail;

  @Column(name = "need_detail", columnDefinition = "TEXT")
  private String needDetail;

  @Column(name = "request_detail", columnDefinition = "TEXT")
  private String requestDetail;

  @Column(name = "overall_feedback", columnDefinition = "TEXT")
  private String overallFeedback;

  // === JSON 数组 ===

  @Column(name = "strengths_json", columnDefinition = "TEXT")
  private String strengthsJson;

  @Column(name = "improvements_json", columnDefinition = "TEXT")
  private String improvementsJson;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
  }
}
