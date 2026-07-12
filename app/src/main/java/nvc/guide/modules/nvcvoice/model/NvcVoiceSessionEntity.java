package nvc.guide.modules.nvcvoice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import nvc.guide.common.model.AsyncTaskStatus;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;

/**
 * NVC 语音练习会话实体
 */
@Entity
@Table(name = "nvc_voice_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcVoiceSessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "practice_mode", length = 30)
  @Enumerated(EnumType.STRING)
  private NvcPracticeMode practiceMode;

  @Column(name = "scenario_id")
  private Long scenarioId;

  @Column(name = "agent_scene", length = 50)
  @Enumerated(EnumType.STRING)
  private NvcAgentScene agentScene;

  @Column(name = "difficulty", length = 16)
  @Enumerated(EnumType.STRING)
  private NvcDifficulty difficulty;

  @Column(name = "current_phase", length = 20)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private NvcVoiceSessionPhase currentPhase = NvcVoiceSessionPhase.INTRO;

  @Column(name = "status", length = 20)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private NvcVoiceSessionStatus status = NvcVoiceSessionStatus.IN_PROGRESS;

  @Column(name = "llm_provider", length = 50)
  @Builder.Default
  private String llmProvider = "dashscope";

  @Column(name = "planned_duration")
  @Builder.Default
  private Integer plannedDuration = 20;

  @Column(name = "actual_duration")
  private Integer actualDuration;

  @Column(name = "start_time")
  private LocalDateTime startTime;

  @Column(name = "end_time")
  private LocalDateTime endTime;

  @Column(name = "paused_at")
  private LocalDateTime pausedAt;

  @Column(name = "resumed_at")
  private LocalDateTime resumedAt;

  @Column(name = "evaluate_status", length = 20)
  @Enumerated(EnumType.STRING)
  private AsyncTaskStatus evaluateStatus;

  @Column(name = "evaluate_error", length = 500)
  private String evaluateError;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = LocalDateTime.now();
  }

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
    this.updatedAt = LocalDateTime.now();
    this.startTime = LocalDateTime.now();
  }
}
