package nvc.guide.modules.nvcpractice.dto;

import java.time.LocalDateTime;

public record MessageResponse(
    Long id,
    Long sessionId,
    String role,
    String agentScene,
    String content,
    Integer sequenceNum,
    String step,
    LocalDateTime createdAt
) {}
