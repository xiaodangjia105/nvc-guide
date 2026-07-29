package nvc.guide.modules.nvcassistant.repository;

import nvc.guide.modules.nvcassistant.model.NvcToolCallRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 工具调用记录仓库
 */
public interface NvcToolCallRecordRepository extends JpaRepository<NvcToolCallRecordEntity, Long> {

    List<NvcToolCallRecordEntity> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<NvcToolCallRecordEntity> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);
}
