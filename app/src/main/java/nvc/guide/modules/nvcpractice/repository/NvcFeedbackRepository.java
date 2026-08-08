package nvc.guide.modules.nvcpractice.repository;

import nvc.guide.modules.nvcpractice.model.NvcAgentScene;
import nvc.guide.modules.nvcpractice.model.NvcFeedbackEntity;
import nvc.guide.modules.nvcpractice.model.NvcFeedbackSource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NvcFeedbackRepository extends JpaRepository<NvcFeedbackEntity, Long> {

    /**
     * 查找同一消息的已有反馈（防重复）
     */
    Optional<NvcFeedbackEntity> findBySessionIdAndMessageIdAndMessageSource(
        Long sessionId, Long messageId, NvcFeedbackSource messageSource);

    /**
     * 按 Agent 场景统计反馈
     */
    @Query("SELECT f.agentScene, COUNT(f), SUM(CASE WHEN f.rating = 5 THEN 1 ELSE 0 END) " +
           "FROM NvcFeedbackEntity f " +
           "WHERE f.createdAt BETWEEN :from AND :to " +
           "GROUP BY f.agentScene")
    List<Object[]> countByAgentScene(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    /**
     * 统计时间范围内的总反馈数和好评数
     */
    @Query("SELECT COUNT(f), SUM(CASE WHEN f.rating = 5 THEN 1 ELSE 0 END) " +
           "FROM NvcFeedbackEntity f " +
           "WHERE f.createdAt BETWEEN :from AND :to")
    Object[] countTotalAndThumbsUp(
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to);

    /**
     * 获取最近的差评反馈
     */
    List<NvcFeedbackEntity> findByRatingOrderByCreatedAtDesc(Integer rating);

    /**
     * 获取最近的差评反馈（带分页）
     */
    List<NvcFeedbackEntity> findByRatingOrderByCreatedAtDesc(Integer rating, Pageable pageable);

    /**
     * 获取用户对某会话的所有反馈
     */
    List<NvcFeedbackEntity> findByUserIdAndSessionId(Long userId, Long sessionId);
}
