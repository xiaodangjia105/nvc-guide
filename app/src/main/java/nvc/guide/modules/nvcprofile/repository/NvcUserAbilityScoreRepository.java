package nvc.guide.modules.nvcprofile.repository;

import nvc.guide.modules.nvcprofile.model.NvcUserAbilityScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NvcUserAbilityScoreRepository extends JpaRepository<NvcUserAbilityScoreEntity, Long> {

    List<NvcUserAbilityScoreEntity> findByUserIdOrderByScoredAtDesc(Long userId);

    List<NvcUserAbilityScoreEntity> findTop30ByUserIdOrderByScoredAtDesc(Long userId);
}
