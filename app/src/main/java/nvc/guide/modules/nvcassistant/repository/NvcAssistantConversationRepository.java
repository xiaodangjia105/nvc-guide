package nvc.guide.modules.nvcassistant.repository;

import nvc.guide.modules.nvcassistant.model.NvcAssistantConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 主 Agent 对话会话 Repository
 */
public interface NvcAssistantConversationRepository extends JpaRepository<NvcAssistantConversationEntity, Long> {

    List<NvcAssistantConversationEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<NvcAssistantConversationEntity> findByIdAndUserId(Long id, Long userId);
}
