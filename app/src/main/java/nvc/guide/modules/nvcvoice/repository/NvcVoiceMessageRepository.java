package nvc.guide.modules.nvcvoice.repository;

import java.util.List;
import java.util.Optional;
import nvc.guide.modules.nvcvoice.model.NvcVoiceMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NvcVoiceMessageRepository extends JpaRepository<NvcVoiceMessageEntity, Long> {

  List<NvcVoiceMessageEntity> findBySessionIdOrderBySequenceNumAsc(Long sessionId);

  Page<NvcVoiceMessageEntity> findBySessionIdOrderBySequenceNumAsc(Long sessionId, Pageable pageable);

  List<NvcVoiceMessageEntity> findTop20BySessionIdOrderBySequenceNumDesc(Long sessionId);

  long countBySessionId(Long sessionId);

  @Query("SELECT MAX(m.sequenceNum) FROM NvcVoiceMessageEntity m WHERE m.sessionId = :sessionId")
  Optional<Integer> findMaxSequenceNumBySessionId(@Param("sessionId") Long sessionId);
}
