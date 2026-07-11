package nvc.guide.modules.nvcscenario.dto;

import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcscenario.model.NvcScenarioType;

public record ScenarioDTO(
    Long id,
    String title,
    String description,
    NvcScenarioType scenarioType,
    NvcDifficulty difficulty,
    String focusElements,
    String context,
    String sampleDialogue,
    String tags,
    Boolean isSystem,
    Integer usageCount
) {}
