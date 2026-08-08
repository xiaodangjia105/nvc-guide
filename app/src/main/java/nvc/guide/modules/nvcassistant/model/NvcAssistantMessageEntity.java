package nvc.guide.modules.nvcassistant.model;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.EqualsAndHashCode;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 主 Agent 消息实体
 */
@Entity
@Table(name = "nvc_assistant_messages", indexes = {
    @Index(name = "idx_assistant_message_conv_seq", columnList = "conversation_id, sequence_num")
})
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcAssistantMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "对话ID不能为空")
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @NotNull(message = "用户ID不能为空")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @NotNull(message = "消息角色不能为空")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NvcAssistantMessageRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    private String toolCallsJson;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @Column(name = "tool_call_id", length = 500)
    private String toolCallId;

    @Column(name = "sequence_num")
    private Integer sequenceNum;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
