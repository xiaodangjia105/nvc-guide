package nvc.guide.modules.nvcpractice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "nvc_practice_message", indexes = {
    @Index(name = "idx_nvc_message_session", columnList = "session_id, sequence_num")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcPracticeMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NvcMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_scene", length = 50)
    private NvcAgentScene agentScene;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NvcPracticeStep step;

    @Column(name = "sequence_num", nullable = false)
    private Integer sequenceNum;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
