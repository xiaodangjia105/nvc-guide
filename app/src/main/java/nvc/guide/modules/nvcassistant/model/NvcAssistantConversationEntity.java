package nvc.guide.modules.nvcassistant.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 主 Agent 对话会话实体
 */
@Entity
@Table(name = "nvc_assistant_conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcAssistantConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 200)
    private String title;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
