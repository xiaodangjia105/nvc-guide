package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileQueryTool implements NvcTool {

    private final NvcProfileService profileService;

    @Override
    public String name() { return "profile_query"; }

    @Override
    public String description() {
        return "查询用户 NVC 档案，包括等级、沟通背景、性格特征、能力雷达等信息。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(ProfileQueryInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            Long userId = context.getUserId();
            if (userId == null) {
                ProfileQueryInput params = JsonParser.fromJson(input, ProfileQueryInput.class);
                userId = params.userId();
            }
            if (userId == null) {
                return NvcToolResult.failure("缺少用户ID");
            }

            String profilePrompt = profileService.getUserProfilePrompt(userId);
            return NvcToolResult.success(
                profilePrompt != null ? profilePrompt : "用户暂无档案信息");
        } catch (Exception e) {
            log.error("[ProfileQueryTool] Execution failed", e);
            return NvcToolResult.failure("档案查询失败: " + e.getMessage());
        }
    }

    record ProfileQueryInput(Long userId) {}
}
