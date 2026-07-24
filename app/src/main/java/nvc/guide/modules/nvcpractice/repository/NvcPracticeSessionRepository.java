package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NvcPracticeSessionRepository extends JpaRepository<NvcPracticeSessionEntity, Long> {

    List<NvcPracticeSessionEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<NvcPracticeSessionEntity> findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
        Long userId, NvcSessionPhase phase);

    long countByUserId(Long userId);

    long countByUserIdAndCurrentPhase(Long userId, NvcSessionPhase phase);
}
