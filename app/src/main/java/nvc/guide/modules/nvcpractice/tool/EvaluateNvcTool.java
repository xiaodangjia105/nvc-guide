package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.service.NvcEvaluationService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class EvaluateNvcTool implements NvcTool {

    private final NvcEvaluationService evaluationService;

    @Override
    public String name() { return "evaluate_nvc"; }

    @Override
    public String description() {
        return "评估用户的 NVC 表达质量，返回观察、感受、需求、请求四个维度的评分和反馈。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(EvaluateNvcInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            EvaluateNvcInput params = JsonParser.fromJson(input, EvaluateNvcInput.class);
            Long sessionId = context.getSessionId();
            Long userId = context.getUserId();

            if (sessionId == null || userId == null) {
                return NvcToolResult.failure("缺少会话ID或用户ID");
            }

            NvcEvaluationEntity eval = evaluationService.evaluateRealtime(
                sessionId, userId, params.expression(), params.scenario(), null);

            String result = String.format(
                "观察: %d/100, 感受: %d/100, 需求: %d/100, 请求: %d/100\n反馈: %s",
                eval.getObservationScore() != null ? eval.getObservationScore() : 0,
                eval.getFeelingScore() != null ? eval.getFeelingScore() : 0,
                eval.getNeedScore() != null ? eval.getNeedScore() : 0,
                eval.getRequestScore() != null ? eval.getRequestScore() : 0,
                eval.getSummary() != null ? eval.getSummary() : "暂无反馈"
            );

            return NvcToolResult.success(result);
        } catch (Exception e) {
            log.error("[EvaluateNvcTool] Execution failed", e);
            return NvcToolResult.failure("评估失败: " + e.getMessage());
        }
    }

    record EvaluateNvcInput(String expression, String scenario) {}
}
