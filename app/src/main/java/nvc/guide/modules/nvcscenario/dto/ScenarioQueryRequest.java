package nvc.guide.modules.nvcscenario.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;

public record ScenarioQueryRequest(
    NvcScenarioType scenarioType,
    NvcDifficulty difficulty
) {}
