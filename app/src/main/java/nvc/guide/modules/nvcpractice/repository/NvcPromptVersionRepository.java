package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcPromptVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NvcPromptVersionRepository extends JpaRepository<NvcPromptVersionEntity, Long> {

    /**
     * 查找某个场景的所有版本（按版本号降序）
     */
    List<NvcPromptVersionEntity> findByAgentSceneOrderByVersionDesc(NvcAgentScene agentScene);

    /**
     * 查找某个场景的所有活跃版本（用于 A/B 路由）
     */
    List<NvcPromptVersionEntity> findByAgentSceneAndIsActiveTrue(NvcAgentScene agentScene);

    /**
     * 查找某个场景的当前活跃主版本（流量最大的）
     */
    @Query("SELECT p FROM NvcPromptVersionEntity p " +
           "WHERE p.agentScene = :scene AND p.isActive = true " +
           "ORDER BY p.trafficPercentage DESC")
    List<NvcPromptVersionEntity> findActiveVersions(@Param("scene") NvcAgentScene scene);

    /**
     * 获取某个场景的最大版本号
     */
    @Query("SELECT MAX(p.version) FROM NvcPromptVersionEntity p WHERE p.agentScene = :scene")
    Integer findMaxVersion(@Param("scene") NvcAgentScene scene);

    /**
     * 查找某个场景某个版本
     */
    Optional<NvcPromptVersionEntity> findByAgentSceneAndVersion(NvcAgentScene agentScene, Integer version);
}
