package nvc.guide.modules.nvcprofile.dto;

import nvc.guide.modules.nvcscenario.model.NvcScenarioType;

import java.time.LocalDateTime;

public record CommunicationRecordDTO(
    Long id,
    String title,
    NvcScenarioType scenarioType,
    String rawContent,
    String analysisResult,
    String nvcSuggestion,
    LocalDateTime createdAt
) {}
