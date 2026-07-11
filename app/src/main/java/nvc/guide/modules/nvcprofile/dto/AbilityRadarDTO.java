package nvc.guide.modules.nvcprofile.dto;

public record AbilityRadarDTO(
    Integer observation,
    Integer feeling,
    Integer need,
    Integer request,
    Integer empathy,
    Integer overall,
    String level
) {}
