package nvc.guide.modules.nvcpractice.evaluation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Golden Dataset 样本结构
 * 用于评估一致性验证和离线评估
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoldenSample {

    /** 样本 ID（如 "GS-001"） */
    private String id;

    /** 场景描述 */
    private String scenario;

    /** 当前练习步骤：OBSERVE / FEELING / NEED / REQUEST */
    private String step;

    /** 难度：BEGINNER / INTERMEDIATE / ADVANCED */
    private String difficulty;

    /** 用户输入 */
    private String userInput;

    /** 期望评分（四要素 1-10 分） */
    private Map<String, Integer> expectedScores;

    /** 评分理由 */
    private String reasoning;

    /** 场景类别（如 workplace_conflict） */
    private String category;

    /** 标签列表 */
    private java.util.List<String> tags;
}
