package nvc.guide.modules.nvcpractice.dto;

public record StreamMetadata(
    String agentScene,
    String decisionReason,
    String action,
    String currentStep
) {}
