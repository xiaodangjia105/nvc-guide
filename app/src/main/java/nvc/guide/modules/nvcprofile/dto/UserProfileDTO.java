package nvc.guide.modules.nvcprofile.dto;

import nvc.guide.modules.nvcprofile.model.NvcCommunicationStyle;
import nvc.guide.modules.nvcprofile.model.NvcLevel;

import java.time.LocalDateTime;

public record UserProfileDTO(
    Long userId,
    String communicationBackground,
    String personalityTraits,
    NvcCommunicationStyle communicationStyle,
    String emotionalTriggers,
    String commonScenarios,
    String relationshipTypes,
    NvcLevel nvcLevel,
    Integer totalPracticeCount,
    Integer totalPracticeMinutes,
    LocalDateTime lastPracticeAt,
    AbilityRadarDTO abilityRadar
) {}
