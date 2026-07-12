package nvc.guide.modules.nvcvoice.repository;

import java.util.Optional;
import nvc.guide.modules.nvcvoice.model.NvcVoiceEvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NvcVoiceEvaluationRepository extends JpaRepository<NvcVoiceEvaluationEntity, Long> {

  Optional<NvcVoiceEvaluationEntity> findBySessionId(Long sessionId);

  boolean existsBySessionId(Long sessionId);
}
