package nvc.guide.modules.nvcprofile.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import nvc.guide.modules.nvcpractice.model.NvcPracticeType;

@Entity
@Table(name = "nvc_user_ability_score", indexes = {
    @Index(name = "idx_nvc_ability_user", columnList = "user_id, scored_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcUserAbilityScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false)
    private Integer observation;

    @Column(nullable = false)
    private Integer feeling;

    @Column(nullable = false)
    private Integer need;

    @Column(nullable = false)
    private Integer request;

    private Integer empathy;

    @Enumerated(EnumType.STRING)
    @Column(name = "practice_type", length = 20)
    private NvcPracticeType practiceType;

    @Column(name = "scored_at", updatable = false)
    private LocalDateTime scoredAt;

    @PrePersist
    protected void onCreate() {
        scoredAt = LocalDateTime.now();
    }
}