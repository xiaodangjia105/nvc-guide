package nvc.guide.modules.nvcscenario.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nvc_scenario", indexes = {
    @Index(name = "idx_nvc_scenario_type", columnList = "scenario_type, difficulty")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcScenarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", nullable = false, length = 50)
    private NvcScenarioType scenarioType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private nvc.guide.modules.nvcpractice.model.NvcDifficulty difficulty;

    @Column(name = "focus_elements", columnDefinition = "JSONB")
    private String focusElements;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(name = "sample_dialogue", columnDefinition = "TEXT")
    private String sampleDialogue;

    @Column(columnDefinition = "JSONB")
    private String tags;

    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}