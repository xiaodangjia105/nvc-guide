package nvc.guide.common.evaluation;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 评估配置属性（保留，后续改造为 NVC 评估配置）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.evaluation")
public class InterviewEvaluationProperties {

    /**
     * 评估系统提示词路径
     */
    private String systemPromptPath = "prompts/nvc-evaluation-system.st";

    /**
     * 评估用户提示词路径
     */
    private String userPromptPath = "prompts/nvc-evaluation-user.st";

    /**
     * 汇总系统提示词路径
     */
    private String summarySystemPromptPath = "prompts/nvc-evaluation-summary-system.st";

    /**
     * 汇总用户提示词路径
     */
    private String summaryUserPromptPath = "prompts/nvc-evaluation-summary-user.st";

    /**
     * 评估批次大小
     */
    private int batchSize = 8;
}
