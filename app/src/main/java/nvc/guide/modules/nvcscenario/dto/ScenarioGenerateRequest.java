package nvc.guide.modules.nvcscenario.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;

import java.util.List;

public record ScenarioGenerateRequest(
    NvcScenarioType scenarioType,
    NvcDifficulty difficulty,
    List<String> focusElements,
    String description
) {}
