package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NvcPracticeMessageRepository extends JpaRepository<NvcPracticeMessageEntity, Long> {

    List<NvcPracticeMessageEntity> findBySessionIdOrderBySequenceNumAsc(Long sessionId);

    int countBySessionId(Long sessionId);
}
