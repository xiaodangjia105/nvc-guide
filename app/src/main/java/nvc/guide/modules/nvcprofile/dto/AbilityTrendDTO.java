package nvc.guide.modules.nvcprofile.dto;

import java.time.LocalDateTime;

public record AbilityTrendDTO(
    LocalDateTime scoredAt,
    Integer observation,
    Integer feeling,
    Integer need,
    Integer request,
    Integer empathy,
    String practiceType
) {}
