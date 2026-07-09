package nvc.guide.modules.llmprovider.repository;

import nvc.guide.modules.llmprovider.model.LlmProviderEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmProviderRepository extends JpaRepository<LlmProviderEntity, String> {

  List<LlmProviderEntity> findByEnabledTrueOrderByIdAsc();
}
