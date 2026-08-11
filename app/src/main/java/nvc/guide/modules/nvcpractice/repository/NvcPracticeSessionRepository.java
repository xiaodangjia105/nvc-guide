package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NvcPracticeSessionRepository extends JpaRepository<NvcPracticeSessionEntity, Long> {

    List<NvcPracticeSessionEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<NvcPracticeSessionEntity> findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
        Long userId, NvcSessionPhase phase);

    Page<NvcPracticeSessionEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<NvcPracticeSessionEntity> findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
        Long userId, NvcSessionPhase phase, Pageable pageable);

    long countByUserId(Long userId);

    long countByUserIdAndCurrentPhase(Long userId, NvcSessionPhase phase);

    @Query("SELECT s FROM NvcPracticeSessionEntity s LEFT JOIN FETCH s.messages WHERE s.userId = :userId ORDER BY s.createdAt DESC")
    List<NvcPracticeSessionEntity> findByUserIdWithMessages(@Param("userId") Long userId);

    @Query("SELECT s FROM NvcPracticeSessionEntity s LEFT JOIN FETCH s.messages WHERE s.userId = :userId AND s.currentPhase = :phase ORDER BY s.createdAt DESC")
    List<NvcPracticeSessionEntity> findByUserIdAndPhaseWithMessages(@Param("userId") Long userId, @Param("phase") NvcSessionPhase phase);
}
