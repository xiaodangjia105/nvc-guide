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
 * NVC 语音对话消息实体
 */
@Entity
@Table(name = "nvc_voice_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcVoiceMessageEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false)
  private Long sessionId;

  @Column(name = "message_type", length = 20)
  @Builder.Default
  private String messageType = "DIALOGUE";

  @Column(name = "agent_scene", length = 50)
  private String agentScene;

  @Column(name = "user_recognized_text", columnDefinition = "TEXT")
  private String userRecognizedText;

  @Column(name = "ai_generated_text", columnDefinition = "TEXT")
  private String aiGeneratedText;

  @Column(name = "sequence_num")
  private Integer sequenceNum;

  @Column(name = "timestamp")
  private LocalDateTime timestamp;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @PrePersist
  protected void onCreate() {
    this.createdAt = LocalDateTime.now();
    if (this.timestamp == null) {
      this.timestamp = LocalDateTime.now();
    }
  }
}
