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
 * 用户反馈实体
 * 记录用户对 AI 回复的 👍/👎 反馈 + 可选文字评论
 */
@Entity
@Table(name = "nvc_feedback", indexes = {
    @Index(name = "idx_feedback_user_time", columnList = "user_id, created_at"),
    @Index(name = "idx_feedback_session", columnList = "session_id"),
    @Index(name = "idx_feedback_scene", columnList = "agent_scene")
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    /**
     * 消息来源：PRACTICE（练习对话）/ ASSISTANT（主 Agent 对话）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "message_source", nullable = false, length = 20)
    private NvcFeedbackSource messageSource;

    /**
     * Agent 场景（记录是哪个 Agent 的回复）
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_scene", length = 50)
    private NvcAgentScene agentScene;

    /**
     * 评分：1 = 踩, 5 = 赞（预留未来扩展为 5 星）
     */
    @Column(nullable = false)
    private Integer rating;

    /**
     * 可选文字反馈
     */
    @Column(columnDefinition = "TEXT")
    private String comment;

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
