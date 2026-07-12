package nvc.guide.modules.nvcvoice.repository;

import java.time.LocalDateTime;
import java.util.List;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionEntity;
import nvc.guide.modules.nvcvoice.model.NvcVoiceSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NvcVoiceSessionRepository extends JpaRepository<NvcVoiceSessionEntity, Long> {

  List<NvcVoiceSessionEntity> findByUserId(Long userId);

  List<NvcVoiceSessionEntity> findByStatus(NvcVoiceSessionStatus status);

  List<NvcVoiceSessionEntity> findByUserIdAndStatus(Long userId, NvcVoiceSessionStatus status);

  List<NvcVoiceSessionEntity> findByStatusInAndUpdatedAtBefore(
      List<NvcVoiceSessionStatus> statuses, LocalDateTime threshold);

  List<NvcVoiceSessionEntity> findByEvaluateStatusAndUpdatedAtBefore(
      nvc.guide.common.model.AsyncTaskStatus evaluateStatus, LocalDateTime threshold);
}
