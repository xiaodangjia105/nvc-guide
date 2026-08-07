package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NvcPracticeMessageRepository extends JpaRepository<NvcPracticeMessageEntity, Long> {

    List<NvcPracticeMessageEntity> findBySessionIdOrderBySequenceNumAsc(Long sessionId);

    int countBySessionId(Long sessionId);

    /**
     * 获取会话中最大的序列号
     */
    @Query("SELECT MAX(m.sequenceNum) FROM NvcPracticeMessageEntity m WHERE m.sessionId = :sessionId")
    Optional<Integer> findMaxSequenceNumBySessionId(@Param("sessionId") Long sessionId);
}
