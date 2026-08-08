package nvc.guide.modules.nvcpractice.model;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.EqualsAndHashCode;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;

@Entity
@Table(name = "nvc_practice_message", indexes = {
    @Index(name = "idx_nvc_message_session", columnList = "session_id, sequence_num")
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcPracticeMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "会话ID不能为空")
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @NotNull(message = "消息角色不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NvcMessageRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_scene", length = 50)
    private NvcAgentScene agentScene;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 50000, message = "消息内容不能超过50000字符")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NvcPracticeStep step;

    @NotNull(message = "序列号不能为空")
    @Min(value = 0, message = "序列号不能为负数")
    @Column(name = "sequence_num", nullable = false)
    private Integer sequenceNum;

    @Size(max = 10000, message = "元数据不能超过10000字符")
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
