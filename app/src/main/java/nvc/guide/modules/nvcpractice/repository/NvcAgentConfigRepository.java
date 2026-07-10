package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NvcAgentConfigRepository extends JpaRepository<NvcAgentConfigEntity, Long> {

    Optional<NvcAgentConfigEntity> findByAgentScene(NvcAgentScene agentScene);

    List<NvcAgentConfigEntity> findByIsEnabledTrue();
}
