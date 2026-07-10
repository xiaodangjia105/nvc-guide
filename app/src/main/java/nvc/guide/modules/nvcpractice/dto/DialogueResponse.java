package nvc.guide.modules.nvcpractice.dto;

public record DialogueResponse(
    Long sessionId,
    String aiReply,
    String agentScene,
    String decisionReason,
    String action,
    String currentStep
) {}
