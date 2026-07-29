package nvc.guide.modules.nvcassistant.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 工具调用记录实体 — 持久化每次工具调用
 */
@Entity
@Table(name = "nvc_tool_call_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcToolCallRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long sessionId;

    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String arguments;

    @Column(columnDefinition = "TEXT")
    private String result;

    private Boolean success;

    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String skipReason;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
