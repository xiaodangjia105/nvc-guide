package nvc.guide.modules.nvcassistant.repository;

import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 主 Agent 消息 Repository
 */
public interface NvcAssistantMessageRepository extends JpaRepository<NvcAssistantMessageEntity, Long> {

    List<NvcAssistantMessageEntity> findByConversationIdOrderBySequenceNumAsc(Long conversationId);

    List<NvcAssistantMessageEntity> findTop20ByConversationIdOrderBySequenceNumDesc(Long conversationId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM NvcAssistantMessageEntity m WHERE m.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);

    int countByConversationId(Long conversationId);

    /**
     * 获取对话中最后一条用户消息
     */
    Optional<NvcAssistantMessageEntity> findTopByConversationIdAndRoleOrderBySequenceNumDesc(
        Long conversationId, NvcAssistantMessageRole role);

    /**
     * 获取对话中最大的序列号
     */
    @Query("SELECT MAX(m.sequenceNum) FROM NvcAssistantMessageEntity m WHERE m.conversationId = :conversationId")
    Optional<Integer> findMaxSequenceNumByConversationId(@Param("conversationId") Long conversationId);
}
