package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NvcSummaryRepository extends JpaRepository<NvcSummaryEntity, Long> {

  Optional<NvcSummaryEntity> findBySessionId(Long sessionId);
}
