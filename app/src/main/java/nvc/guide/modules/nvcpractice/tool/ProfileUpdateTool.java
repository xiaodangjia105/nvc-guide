package nvc.guide.modules.nvcpractice.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcprofile.dto.UserProfileUpdateRequest;
import nvc.guide.modules.nvcprofile.model.NvcCommunicationStyle;
import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileUpdateTool implements NvcTool {

    private final NvcProfileService profileService;
    private final ObjectMapper objectMapper;

    @Override
    public String name() { return "profile_update"; }

    @Override
    public String description() {
        return "【更新用户档案】当用户说「我是XXX」「帮我记录」「更新档案」「修改个人信息」时必须调用此工具。" +
            "支持字段：communicationBackground(职业/年龄/性别等个人信息，自由文本), " +
            "personalityTraits(性格特征，JSON数组如[\"内向\",\"敏感\"]), " +
            "communicationStyle(沟通风格：ASSERTIVE/PASSIVE/AGGRESSIVE/PASSIVE_AGGRESSIVE), " +
            "emotionalTriggers(情绪触发点，自由文本), " +
            "commonScenarios(常见沟通场景，JSON数组), " +
            "relationshipTypes(重要关系类型，JSON数组), " +
            "preferences(其他偏好，JSON对象如{\"age\":21,\"gender\":\"男\"})。";
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

            String field = params.field();
            String value = params.value();

            // 构建请求——只设置目标字段，其余为 null
            UserProfileUpdateRequest request = switch (field) {
                case "communicationBackground" ->
                    new UserProfileUpdateRequest(value, null, null, null, null, null);
                case "personalityTraits" ->
                    new UserProfileUpdateRequest(null, value, null, null, null, null);
                case "communicationStyle" ->
                    new UserProfileUpdateRequest(null, null, NvcCommunicationStyle.valueOf(value), null, null, null);
                case "emotionalTriggers" ->
                    new UserProfileUpdateRequest(null, null, null, value, null, null);
                case "commonScenarios" ->
                    new UserProfileUpdateRequest(null, null, null, null, value, null);
                case "relationshipTypes" ->
                    new UserProfileUpdateRequest(null, null, null, null, null, value);
                case "preferences" -> {
                    // preferences 存到 JSONB 字段，使用原子更新方法防止并发丢失
                    @SuppressWarnings("unchecked")
                    Map<String, Object> incoming = objectMapper.readValue(value, Map.class);
                    profileService.updatePreferences(userId, incoming);
                    yield null; // 已单独处理，下面会返回成功
                }
                default -> null;
            };

            if ("preferences".equals(field)) {
                return NvcToolResult.success("偏好信息已更新");
            }

            if (request == null) {
                return NvcToolResult.failure("不支持的字段: " + field
                    + "，支持: communicationBackground, personalityTraits, communicationStyle, "
                    + "emotionalTriggers, commonScenarios, relationshipTypes, preferences");
            }

            profileService.updateProfile(userId, request);
            return NvcToolResult.success("档案字段 " + field + " 已更新");
        } catch (Exception e) {
            log.error("[ProfileUpdateTool] Execution failed", e);
            return NvcToolResult.failure("档案更新失败: " + e.getMessage());
        }
    }

    record ProfileUpdateInput(String field, String value) {}
}
