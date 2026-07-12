package nvc.guide.modules.nvcvoice.dto;

import java.time.LocalDateTime;

/**
 * 语音对话消息 DTO
 */
public record VoiceMessageDTO(
    Long id,
    Long sessionId,
    String messageType,
    String agentScene,
    String userRecognizedText,
    String aiGeneratedText,
    Integer sequenceNum,
    LocalDateTime timestamp
) {}
