package nvc.guide.modules.nvcprofile.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;

@Entity
@Table(name = "nvc_communication_record", indexes = {
    @Index(name = "idx_nvc_comm_user", columnList = "user_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcCommunicationRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", length = 50)
    private NvcScenarioType scenarioType;

    @Column(name = "raw_content", nullable = false, columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "analysis_result", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String analysisResult;

    @Column(name = "nvc_suggestion", columnDefinition = "TEXT")
    private String nvcSuggestion;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}