package nvc.guide.modules.nvcpractice.evaluation;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 评估一致性验证报告
 */
@Data
@Builder
public class ConsistencyReport {

    /** 总样本数 */
    private int totalSamples;

    /** 一致样本数（±1 分内算一致） */
    private int consistentSamples;

    /** 总体一致率 */
    private double overallConsistencyRate;

    /** 各维度一致率 */
    private Map<String, Double> dimensionConsistencyRates;

    /** 各维度平均偏差 */
    private Map<String, Double> dimensionAvgDeviations;

    /** 低分样本列表（偏差 > 阈值） */
    private List<OutlierSample> outliers;
}
