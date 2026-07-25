package nvc.guide.modules.nvcassistant.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 主 Agent 对话响应
 */
@Data
@Builder
public class AssistantResponse {

    private Long conversationId;
    private Long messageId;
    private String content;
    private List<ToolCallRecord> toolCalls;
    private boolean done;
}
