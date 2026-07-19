package nvc.guide.modules.nvcpractice.dto;

import lombok.Builder;
import lombok.Data;
import nvc.guide.modules.nvcpractice.model.NvcAgentConfigEntity;

import java.util.Map;

/**
 * 统一对话请求对象 — 解耦编排层与 ChatService
 */
@Data
@Builder
public class NvcChatRequest {

    private final NvcAgentConfigEntity agentConfig;
    private final PracticeContext practiceContext;
    private final String userMessage;
    private final Map<String, String> promptVariables;

    /** 工具配置（可选）— 不传则无工具调用 */
    @Builder.Default
    private final NvcToolCallConfig toolConfig = NvcToolCallConfig.disabled();
}
