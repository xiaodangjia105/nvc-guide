package nvc.guide.modules.nvcpractice.evaluation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeStep;
import nvc.guide.modules.nvcpractice.service.NvcEvaluationService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 评估一致性验证器
 * 对比 LLM 评分与 Golden Dataset 标注，计算一致率
 *
 * <p>一致标准：±1 分内算一致
 * <p>用途：验证评估引擎的可信度，为简历提供"评估一致率 XX%"的数据
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EvaluationConsistencyVerifier {

    private final NvcEvaluationService evaluationService;

    /** 一致标准：偏差在此范围内算一致 */
    private static final int CONSISTENCY_THRESHOLD = 1;

    /**
     * 运行一致性验证
     *
     * @param dataset Golden Dataset 样本列表
     * @return 一致性报告
     */
    public ConsistencyReport verify(List<GoldenSample> dataset) {
        if (dataset == null || dataset.isEmpty()) {
            return ConsistencyReport.builder()
                .totalSamples(0).consistentSamples(0)
                .overallConsistencyRate(0)
                .dimensionConsistencyRates(Map.of())
                .dimensionAvgDeviations(Map.of())
                .outliers(List.of())
                .build();
        }

        List<String> dimensions = List.of("observation", "feeling", "need", "request");
        Map<String, Integer> dimensionConsistentCount = new LinkedHashMap<>();
        Map<String, Long> dimensionTotalDeviation = new LinkedHashMap<>();
        for (String dim : dimensions) {
            dimensionConsistentCount.put(dim, 0);
            dimensionTotalDeviation.put(dim, 0L);
        }

        int totalConsistent = 0;
        List<OutlierSample> outliers = new ArrayList<>();

        for (GoldenSample sample : dataset) {
            // 调用评估引擎获取 LLM 评分
            NvcEvaluationEntity actualResult = evaluationService.evaluateRealtime(
                null, null, sample.getUserInput(), sample.getScenario(),
                NvcPracticeStep.valueOf(sample.getStep()));

            if (actualResult == null) {
                log.warn("Evaluation returned null for sample: {}", sample.getId());
                continue;
            }

            Map<String, Integer> actualScores = Map.of(
                "observation", actualResult.getObservationScore() != null ? actualResult.getObservationScore() : 0,
                "feeling", actualResult.getFeelingScore() != null ? actualResult.getFeelingScore() : 0,
                "need", actualResult.getNeedScore() != null ? actualResult.getNeedScore() : 0,
                "request", actualResult.getRequestScore() != null ? actualResult.getRequestScore() : 0
            );

            Map<String, Integer> expectedScores = sample.getExpectedScores();
            Map<String, Integer> deviations = new LinkedHashMap<>();
            boolean sampleConsistent = true;
            String maxDevDim = "";
            int maxDevVal = 0;

            for (String dim : dimensions) {
                int expected = expectedScores.getOrDefault(dim, 0);
                int actual = actualScores.getOrDefault(dim, 0);
                int deviation = Math.abs(expected - actual);
                deviations.put(dim, deviation);
                dimensionTotalDeviation.merge(dim, (long) deviation, Long::sum);

                if (deviation <= CONSISTENCY_THRESHOLD) {
                    dimensionConsistentCount.merge(dim, 1, Integer::sum);
                } else {
                    sampleConsistent = false;
                }

                if (deviation > maxDevVal) {
                    maxDevVal = deviation;
                    maxDevDim = dim;
                }
            }

            if (sampleConsistent) {
                totalConsistent++;
            } else {
                // 偏差超过阈值，标记为异常样本
                if (maxDevVal > CONSISTENCY_THRESHOLD + 1) {
                    outliers.add(OutlierSample.builder()
                        .sampleId(sample.getId())
                        .userInput(sample.getUserInput())
                        .expectedScores(expectedScores)
                        .actualScores(actualScores)
                        .deviations(deviations)
                        .maxDeviationDimension(maxDevDim)
                        .maxDeviation(maxDevVal)
                        .build());
                }
            }

            log.debug("Sample {}: consistent={}, maxDev={}:{}",
                sample.getId(), sampleConsistent, maxDevDim, maxDevVal);
        }

        int size = dataset.size();
        Map<String, Double> dimRates = new LinkedHashMap<>();
        Map<String, Double> dimAvgDevs = new LinkedHashMap<>();
        for (String dim : dimensions) {
            dimRates.put(dim, size > 0
                ? Math.round((double) dimensionConsistentCount.get(dim) / size * 10000) / 100.0
                : 0);
            dimAvgDevs.put(dim, size > 0
                ? Math.round((double) dimensionTotalDeviation.get(dim) / size * 100) / 100.0
                : 0);
        }

        double overallRate = size > 0
            ? Math.round((double) totalConsistent / size * 10000) / 100.0
            : 0;

        log.info("Evaluation consistency verification complete: total={}, consistent={}, rate={}%",
            size, totalConsistent, overallRate);

        return ConsistencyReport.builder()
            .totalSamples(size)
            .consistentSamples(totalConsistent)
            .overallConsistencyRate(overallRate)
            .dimensionConsistencyRates(dimRates)
            .dimensionAvgDeviations(dimAvgDevs)
            .outliers(outliers)
            .build();
    }

    /**
     * 识别异常样本（偏差 > 阈值）
     */
    public List<GoldenSample> identifyOutliers(List<GoldenSample> dataset, double threshold) {
        ConsistencyReport report = verify(dataset);
        Set<String> outlierIds = report.getOutliers().stream()
            .map(OutlierSample::getSampleId)
            .collect(Collectors.toSet());

        return dataset.stream()
            .filter(s -> outlierIds.contains(s.getId()))
            .toList();
    }
}
