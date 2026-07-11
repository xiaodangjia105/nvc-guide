package nvc.guide.modules.nvcprofile.dto;

import nvc.guide.modules.nvcprofile.model.NvcCommunicationStyle;

public record UserProfileUpdateRequest(
    String communicationBackground,
    String personalityTraits,
    NvcCommunicationStyle communicationStyle,
    String emotionalTriggers,
    String commonScenarios,
    String relationshipTypes
) {}
