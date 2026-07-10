package nvc.guide.modules.nvcprofile.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nvc_user_profile")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcUserProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "communication_background", columnDefinition = "TEXT")
    private String communicationBackground;

    @Column(name = "personality_traits", columnDefinition = "JSONB")
    private String personalityTraits;

    @Enumerated(EnumType.STRING)
    @Column(name = "communication_style", length = 50)
    private NvcCommunicationStyle communicationStyle;

    @Column(name = "emotional_triggers", columnDefinition = "TEXT")
    private String emotionalTriggers;

    @Column(name = "common_scenarios", columnDefinition = "JSONB")
    private String commonScenarios;

    @Column(name = "relationship_types", columnDefinition = "JSONB")
    private String relationshipTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "nvc_level", length = 20)
    @Builder.Default
    private NvcLevel nvcLevel = NvcLevel.BEGINNER;

    @Column(name = "total_practice_count")
    @Builder.Default
    private Integer totalPracticeCount = 0;

    @Column(name = "total_practice_minutes")
    @Builder.Default
    private Integer totalPracticeMinutes = 0;

    @Column(name = "last_practice_at")
    private LocalDateTime lastPracticeAt;

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