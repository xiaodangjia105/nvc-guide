package nvc.guide.modules.nvcprofile.dto;

import nvc.guide.modules.nvcscenario.model.NvcScenarioType;
import jakarta.validation.constraints.NotBlank;

public record CommunicationAnalysisRequest(
    String title,

    @NotBlank String rawContent,

    NvcScenarioType scenarioType
) {}
