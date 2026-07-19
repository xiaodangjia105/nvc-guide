package nvc.guide.modules.nvcpractice.tool;

import lombok.Builder;
import lombok.Data;
import nvc.guide.modules.nvcpractice.dto.PracticeContext;

/**
 * 工具执行上下文 — 从 Spring AI ToolContext Map 中提取
 */
@Data
@Builder
public class NvcToolContext {
    private Long userId;
    private Long sessionId;
    private PracticeContext practiceContext;
}
