package nvc.guide.modules.nvcpractice.evaluation;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 异常样本（评估偏差 > 阈值）
 */
@Data
@Builder
public class OutlierSample {

    /** 样本 ID */
    private String sampleId;

    /** 用户输入 */
    private String userInput;

    /** 期望评分 */
    private Map<String, Integer> expectedScores;

    /** 实际评分 */
    private Map<String, Integer> actualScores;

    /** 各维度偏差 */
    private Map<String, Integer> deviations;

    /** 最大偏差维度 */
    private String maxDeviationDimension;

    /** 最大偏差值 */
    private int maxDeviation;
}
