package nvc.guide.modules.nvcassistant.repository;

import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 主 Agent 消息 Repository
 */
public interface NvcAssistantMessageRepository extends JpaRepository<NvcAssistantMessageEntity, Long> {

    List<NvcAssistantMessageEntity> findByConversationIdOrderBySequenceNumAsc(Long conversationId);

    List<NvcAssistantMessageEntity> findTop20ByConversationIdOrderBySequenceNumDesc(Long conversationId);

    void deleteByConversationId(Long conversationId);

    int countByConversationId(Long conversationId);
}
