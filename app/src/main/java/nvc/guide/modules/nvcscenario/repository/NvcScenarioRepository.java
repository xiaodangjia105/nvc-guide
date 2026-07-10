package nvc.guide.modules.nvcscenario.repository;

import nvc.guide.modules.nvcscenario.model.NvcScenarioEntity;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NvcScenarioRepository extends JpaRepository<NvcScenarioEntity, Long> {

    List<NvcScenarioEntity> findByScenarioTypeAndDifficulty(
        NvcScenarioType type, NvcDifficulty difficulty);

    List<NvcScenarioEntity> findByIsSystemTrueOrderByUsageCountDesc();

    List<NvcScenarioEntity> findByScenarioType(NvcScenarioType type);

    List<NvcScenarioEntity> findByDifficulty(NvcDifficulty difficulty);
}
