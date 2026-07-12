package nvc.guide.modules.nvcpractice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * NVC 四要素摘要实体
 * 存储从用户对话中提取的观察/感受/需求/请求
 */
@Entity
@Table(name = "nvc_summary")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcSummaryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false, unique = true)
  private Long sessionId;

  @Column(columnDefinition = "TEXT")
  private String observation;

  @Column(columnDefinition = "TEXT")
  private String feeling;

  @Column(columnDefinition = "TEXT")
  private String need;

  @Column(columnDefinition = "TEXT")
  private String request;

  @Column(columnDefinition = "TEXT")
  private String hint;

  @Column(name = "raw_analysis", columnDefinition = "TEXT")
  private String rawAnalysis;

  @CreationTimestamp
  @Column(name = "created_at")
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;
}
