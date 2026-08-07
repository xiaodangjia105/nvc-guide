package nvc.guide.modules.nvcassistant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.common.exception.BusinessException;
import nvc.guide.common.exception.ErrorCode;
import nvc.guide.modules.nvcassistant.dto.ConversationResponse;
import nvc.guide.modules.nvcassistant.model.NvcAssistantConversationEntity;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageEntity;
import nvc.guide.modules.nvcassistant.model.NvcAssistantMessageRole;
import nvc.guide.modules.nvcassistant.repository.NvcAssistantConversationRepository;
import nvc.guide.modules.nvcassistant.repository.NvcAssistantMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 主 Agent 消息管理服务
 * 提供对话和消息的 CRUD 操作
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAssistantMessageService {

    private final NvcAssistantMessageRepository messageRepository;
    private final NvcAssistantConversationRepository conversationRepository;

    /**
     * 创建新对话
     */
    @Transactional
    public NvcAssistantConversationEntity createConversation(Long userId) {
        NvcAssistantConversationEntity conversation = NvcAssistantConversationEntity.builder()
            .userId(userId)
            .title("新对话")
            .build();
        NvcAssistantConversationEntity saved = conversationRepository.save(conversation);
        log.info("Assistant conversation created: id={}, userId={}", saved.getId(), userId);
        return saved;
    }

    /**
     * 获取对话（校验所有权）
     */
    @Transactional(readOnly = true)
    public NvcAssistantConversationEntity getConversationOrThrow(Long conversationId, Long userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ASSISTANT_CONVERSATION_NOT_FOUND,
                "对话会话不存在: " + conversationId));
    }

    /**
     * 更新对话标题
     */
    @Transactional
    public void updateConversationTitle(Long conversationId, String title) {
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            conv.setTitle(title);
            conversationRepository.save(conv);
        });
    }

    /**
     * 获取用户对话列表
     */
    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
            .stream()
            .map(this::toConversationResponse)
            .toList();
    }

    /**
     * 保存消息
     */
    @Transactional
    public NvcAssistantMessageEntity saveMessage(NvcAssistantMessageEntity message) {
        return messageRepository.save(message);
    }

    /**
     * 获取对话消息列表（按序号升序）
     */
    @Transactional(readOnly = true)
    public List<NvcAssistantMessageEntity> getMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderBySequenceNumAsc(conversationId);
    }

    /**
     * 获取最近 20 条消息（用于构建上下文）
     */
    @Transactional(readOnly = true)
    public List<NvcAssistantMessageEntity> getRecentMessages(Long conversationId) {
        List<NvcAssistantMessageEntity> messages =
            messageRepository.findTop20ByConversationIdOrderBySequenceNumDesc(conversationId);
        // 反转为时间正序
        return messages.reversed();
    }

    /**
     * 获取对话当前消息数
     */
    @Transactional(readOnly = true)
    public int getMessageCount(Long conversationId) {
        return messageRepository.countByConversationId(conversationId);
    }

    /**
     * 获取最后一条用户消息内容
     */
    @Transactional(readOnly = true)
    public Optional<String> getLastUserMessageContent(Long conversationId) {
        return messageRepository.findTopByConversationIdAndRoleOrderBySequenceNumDesc(
                conversationId, NvcAssistantMessageRole.USER)
            .map(NvcAssistantMessageEntity::getContent);
    }

    /**
     * 删除对话及其所有消息
     */
    @Transactional
    public void deleteConversation(Long conversationId, Long userId) {
        NvcAssistantConversationEntity conversation = getConversationOrThrow(conversationId, userId);
        messageRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conversation);
        log.info("Assistant conversation deleted: id={}", conversationId);
    }

    /**
     * 构建用户消息实体
     */
    public NvcAssistantMessageEntity buildUserMessage(Long conversationId, Long userId,
                                                       String content, int sequenceNum) {
        return NvcAssistantMessageEntity.builder()
            .conversationId(conversationId)
            .userId(userId)
            .role(NvcAssistantMessageRole.USER)
            .content(content)
            .sequenceNum(sequenceNum)
            .build();
    }

    /**
     * 构建助手消息实体
     */
    public NvcAssistantMessageEntity buildAssistantMessage(Long conversationId, Long userId,
                                                            String content, String toolCallsJson,
                                                            int sequenceNum) {
        return NvcAssistantMessageEntity.builder()
            .conversationId(conversationId)
            .userId(userId)
            .role(NvcAssistantMessageRole.ASSISTANT)
            .content(content)
            .toolCallsJson(toolCallsJson)
            .sequenceNum(sequenceNum)
            .build();
    }

    private ConversationResponse toConversationResponse(NvcAssistantConversationEntity entity) {
        return ConversationResponse.builder()
            .id(entity.getId())
            .title(entity.getTitle())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
