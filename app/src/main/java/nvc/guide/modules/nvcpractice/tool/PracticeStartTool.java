package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.dto.CreatePracticeSessionRequest;
import nvc.guide.modules.nvcpractice.model.NvcDifficulty;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMode;
import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.service.NvcPracticeSessionService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PracticeStartTool implements NvcTool {

    private final NvcPracticeSessionService sessionService;

    @Override
    public String name() { return "practice_start"; }

    @Override
    public String description() {
        return "启动一个新的 NVC 练习会话。需要指定场景ID和练习模式（SCENARIO/FREE_DIALOG/STRUCTURED_FOUR_STEP）。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(PracticeStartInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            PracticeStartInput params = JsonParser.fromJson(input, PracticeStartInput.class);
            Long userId = context.getUserId();
            if (userId == null) {
                return NvcToolResult.failure("缺少用户ID");
            }

            NvcPracticeMode practiceMode = NvcPracticeMode.valueOf(params.mode());
            NvcDifficulty difficulty = params.difficulty() != null
                ? NvcDifficulty.valueOf(params.difficulty()) : null;

            CreatePracticeSessionRequest request = new CreatePracticeSessionRequest(
                practiceMode, params.scenarioId(), difficulty);

            NvcPracticeSessionEntity session = sessionService.createSession(userId, request);
            return NvcToolResult.success("练习会话已创建，sessionId=" + session.getId());
        } catch (IllegalArgumentException e) {
            log.warn("[PracticeStartTool] Invalid enum value: {}", e.getMessage());
            return NvcToolResult.failure("参数值无效: " + e.getMessage());
        } catch (Exception e) {
            log.error("[PracticeStartTool] Execution failed", e);
            return NvcToolResult.failure("启动练习失败: " + e.getMessage());
        }
    }

    record PracticeStartInput(Long scenarioId, String mode, String difficulty) {}
}
