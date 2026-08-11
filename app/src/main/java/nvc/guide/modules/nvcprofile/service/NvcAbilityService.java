package nvc.guide.modules.nvcprofile.service;

import nvc.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import nvc.guide.modules.nvcprofile.dto.AbilityTrendDTO;
import nvc.guide.modules.nvcprofile.model.NvcLevel;
import nvc.guide.modules.nvcprofile.model.NvcUserAbilityScoreEntity;
import nvc.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAbilityService {

    /** 最少练习次数，低于此值不计算等级，直接返回 BEGINNER */
    private static final int MIN_SAMPLES_FOR_LEVEL = 3;
    /** 取最近 N 次练习的平均分 */
    private static final int RECENT_SCORES_WINDOW_SIZE = 10;
    /** 达到 ADVANCED 等级的平均分阈值 */
    private static final int ADVANCED_THRESHOLD = 80;
    /** 达到 INTERMEDIATE 等级的平均分阈值 */
    private static final int INTERMEDIATE_THRESHOLD = 60;

    private final NvcUserAbilityScoreRepository abilityScoreRepository;
    private final NvcUserProfileRepository profileRepository;

    /**
     * 获取能力雷达图数据
     */
    public AbilityRadarDTO getAbilityRadar(Long userId) {
        // 获取最近 10 次的能力分数，取平均
        List<NvcUserAbilityScoreEntity> recentScores =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        if (recentScores.isEmpty()) {
            return new AbilityRadarDTO(0, 0, 0, 0, 0, 0, "BEGINNER");
        }

        // 取最近 10 次的平均值
        List<NvcUserAbilityScoreEntity> last10 = recentScores.subList(
            0, Math.min(RECENT_SCORES_WINDOW_SIZE, recentScores.size()));

        // 使用 null 安全的拆箱方式，防止 Integer 为 null 时 NPE
        int avgObservation = (int) last10.stream()
            .filter(s -> s.getObservation() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getObservation).average().orElse(0);
        int avgFeeling = (int) last10.stream()
            .filter(s -> s.getFeeling() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getFeeling).average().orElse(0);
        int avgNeed = (int) last10.stream()
            .filter(s -> s.getNeed() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getNeed).average().orElse(0);
        int avgRequest = (int) last10.stream()
            .filter(s -> s.getRequest() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getRequest).average().orElse(0);
        int avgEmpathy = (int) last10.stream()
            .filter(s -> s.getEmpathy() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getEmpathy)
            .average().orElse(0);
        int overallAvg = (int) Math.round((avgObservation + avgFeeling + avgNeed + avgRequest) / 4.0);

        String levelName = profileRepository.findByUserId(userId)
            .map(p -> p.getNvcLevel().name())
            .orElse("BEGINNER");

        return new AbilityRadarDTO(
            avgObservation, avgFeeling, avgNeed, avgRequest, avgEmpathy,
            overallAvg, levelName
        );
    }

    /**
     * 获取能力趋势数据（最近 30 次）
     */
    public List<AbilityTrendDTO> getAbilityTrends(Long userId) {
        List<NvcUserAbilityScoreEntity> scores =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        return scores.stream()
            .map(s -> new AbilityTrendDTO(
                s.getScoredAt(),
                s.getObservation(),
                s.getFeeling(),
                s.getNeed(),
                s.getRequest(),
                s.getEmpathy(),
                s.getPracticeType() != null ? s.getPracticeType().name() : null
            ))
            .toList();
    }

    /**
     * 计算 NVC 等级
     * 基于最近 10 次练习的平均分
     */
    public NvcLevel calculateLevel(Long userId) {
        List<NvcUserAbilityScoreEntity> recent =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        if (recent.size() < MIN_SAMPLES_FOR_LEVEL) {
            return NvcLevel.BEGINNER;
        }

        List<NvcUserAbilityScoreEntity> last10 = recent.subList(0, Math.min(RECENT_SCORES_WINDOW_SIZE, recent.size()));
        double avgOverall = last10.stream()
            .mapToInt(s -> {
                // null 安全的拆箱
                int obs = s.getObservation() != null ? s.getObservation() : 0;
                int feel = s.getFeeling() != null ? s.getFeeling() : 0;
                int need = s.getNeed() != null ? s.getNeed() : 0;
                int req = s.getRequest() != null ? s.getRequest() : 0;
                return (int) Math.round((obs + feel + need + req) / 4.0);
            })
            .average()
            .orElse(0);

        if (avgOverall >= ADVANCED_THRESHOLD) return NvcLevel.ADVANCED;
        if (avgOverall >= INTERMEDIATE_THRESHOLD) return NvcLevel.INTERMEDIATE;
        return NvcLevel.BEGINNER;
    }
}
