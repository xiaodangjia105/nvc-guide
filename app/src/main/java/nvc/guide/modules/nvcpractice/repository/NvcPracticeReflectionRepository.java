package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcPracticeReflectionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NvcPracticeReflectionRepository extends JpaRepository<NvcPracticeReflectionEntity, Long> {

    /**
     * 获取用户最近 N 次反思（用于上下文记忆）
     */
    @Query("SELECT r FROM NvcPracticeReflectionEntity r " +
           "WHERE r.userId = :userId " +
           "ORDER BY r.createdAt DESC")
    List<NvcPracticeReflectionEntity> findRecentByUserId(
        @Param("userId") Long userId);

    /**
     * 获取用户最新一次反思（用于自适应难度）
     */
    Optional<NvcPracticeReflectionEntity> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * 获取某会话的反思
     */
    Optional<NvcPracticeReflectionEntity> findBySessionId(Long sessionId);
}
