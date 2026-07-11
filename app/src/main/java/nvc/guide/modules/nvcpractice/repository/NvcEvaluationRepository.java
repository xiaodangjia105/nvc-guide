package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NvcEvaluationRepository extends JpaRepository<NvcEvaluationEntity, Long> {

    List<NvcEvaluationEntity> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<NvcEvaluationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<NvcEvaluationEntity> findFirstBySessionIdAndEvaluationTypeOrderByCreatedAtDesc(
        Long sessionId, NvcEvaluationType type);
}
