package nvc.guide.modules.nvcpractice.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcprofile.dto.UserProfileUpdateRequest;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileUpdateTool implements NvcTool {

    private final NvcProfileService profileService;

    @Override
    public String name() { return "profile_update"; }

    @Override
    public String description() {
        return "更新用户 NVC 档案字段，如沟通背景、性格特征、沟通风格等。";
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(ProfileUpdateInput.class);
    }

    @Override
    public NvcToolResult execute(String input, NvcToolContext context) {
        try {
            ProfileUpdateInput params = JsonParser.fromJson(input, ProfileUpdateInput.class);
            Long userId = context.getUserId();
            if (userId == null) {
                return NvcToolResult.failure("缺少用户ID");
            }

            UserProfileUpdateRequest request = switch (params.field()) {
                case "communicationBackground" ->
                    new UserProfileUpdateRequest(params.value(), null, null, null, null, null);
                case "personalityTraits" ->
                    new UserProfileUpdateRequest(null, params.value(), null, null, null, null);
                case "communicationStyle" ->
                    new UserProfileUpdateRequest(null, null,
                        nvc.guide.modules.nvcprofile.model.NvcCommunicationStyle.valueOf(params.value()),
                        null, null, null);
                case "emotionTriggers" ->
                    new UserProfileUpdateRequest(null, null, null, params.value(), null, null);
                default -> null;
            };

            if (request == null) {
                return NvcToolResult.failure("不支持的字段: " + params.field()
                    + "，支持: communicationBackground, personalityTraits, communicationStyle, emotionTriggers");
            }

            profileService.updateProfile(userId, request);
            return NvcToolResult.success("档案字段 " + params.field() + " 已更新");
        } catch (Exception e) {
            log.error("[ProfileUpdateTool] Execution failed", e);
            return NvcToolResult.failure("档案更新失败: " + e.getMessage());
        }
    }

    record ProfileUpdateInput(String field, String value) {}
}
