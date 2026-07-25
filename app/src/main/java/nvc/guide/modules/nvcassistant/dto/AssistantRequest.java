package nvc.guide.modules.nvcassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 主 Agent 对话请求
 */
@Data
public class AssistantRequest {

    /** 对话 ID，null 表示新建对话 */
    private Long conversationId;

    /** 用户消息 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 5000, message = "消息内容不能超过 5000 字")
    private String message;
}
