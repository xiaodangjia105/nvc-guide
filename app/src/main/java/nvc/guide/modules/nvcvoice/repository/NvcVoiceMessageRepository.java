package nvc.guide.modules.nvcvoice.repository;

import java.util.List;
import nvc.guide.modules.nvcvoice.model.NvcVoiceMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NvcVoiceMessageRepository extends JpaRepository<NvcVoiceMessageEntity, Long> {

  List<NvcVoiceMessageEntity> findBySessionIdOrderBySequenceNumAsc(Long sessionId);

  List<NvcVoiceMessageEntity> findTop20BySessionIdOrderBySequenceNumDesc(Long sessionId);

  long countBySessionId(Long sessionId);
}
