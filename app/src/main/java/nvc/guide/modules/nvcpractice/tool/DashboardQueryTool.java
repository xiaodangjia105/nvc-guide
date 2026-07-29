package nvc.guide.modules.nvcpractice.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcprofile.service.NvcDashboardService;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardQueryTool implements NvcTool {

    private final NvcDashboardService dashboardService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() { return "dashboard_query"; }

    @Override
    public String description() {
        return "【查看练习数据】仅当用户说「练习数据」「统计」「我练了多少次」时调用。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(DashboardQueryInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            Long userId = context.getUserId();
            if (userId == null) {
                return NvcToolResult.failure("缺少用户ID");
            }

            Map<String, Object> stats = dashboardService.getUserStats(userId);
            String json = objectMapper.writeValueAsString(stats);
            return NvcToolResult.success(json);
        } catch (Exception e) {
            log.error("[DashboardQueryTool] Execution failed", e);
            return NvcToolResult.failure("统计数据查询失败: " + e.getMessage());
        }
    }

    record DashboardQueryInput(String period) {}
}
