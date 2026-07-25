package nvc.guide.modules.nvcassistant.dto;

import lombok.Data;

/**
 * 主 Agent 对话请求
 */
@Data
public class AssistantRequest {

    /** 对话 ID，null 表示新建对话 */
    private Long conversationId;

    /** 用户消息 */
    private String message;
}
