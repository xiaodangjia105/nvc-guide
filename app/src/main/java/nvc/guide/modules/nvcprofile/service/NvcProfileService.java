package nvc.guide.modules.nvcprofile.service;

import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeType;
import nvc.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import nvc.guide.modules.nvcprofile.dto.AbilityTrendDTO;
import nvc.guide.modules.nvcprofile.dto.UserProfileDTO;
import nvc.guide.modules.nvcprofile.dto.UserProfileUpdateRequest;
import nvc.guide.modules.nvcprofile.model.NvcLevel;
import nvc.guide.modules.nvcprofile.model.NvcUserAbilityScoreEntity;
import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import nvc.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcProfileService {

    private final NvcUserProfileRepository profileRepository;
    private final NvcUserAbilityScoreRepository abilityScoreRepository;

    /**
     * 获取用户档案（不存在则创建默认档案）
     */
    public NvcUserProfileEntity getOrCreateProfile(Long userId) {
        return profileRepository.findByUserId(userId)
            .orElseGet(() -> createDefaultProfile(userId));
    }

    /**
     * 创建默认档案
     */
    private NvcUserProfileEntity createDefaultProfile(Long userId) {
        NvcUserProfileEntity profile = NvcUserProfileEntity.builder()
            .userId(userId)
            .nvcLevel(NvcLevel.BEGINNER)
            .totalPracticeCount(0)
            .totalPracticeMinutes(0)
            .build();
        NvcUserProfileEntity saved = profileRepository.save(profile);
        log.info("Default NVC profile created: userId={}", userId);
        return saved;
    }

    /**
     * 更新用户档案
     */
    public NvcUserProfileEntity updateProfile(Long userId, UserProfileUpdateRequest request) {
        NvcUserProfileEntity profile = getOrCreateProfile(userId);

        if (request.communicationBackground() != null) {
            profile.setCommunicationBackground(request.communicationBackground());
        }
        if (request.personalityTraits() != null) {
            profile.setPersonalityTraits(request.personalityTraits());
        }
        if (request.communicationStyle() != null) {
            profile.setCommunicationStyle(request.communicationStyle());
        }
        if (request.emotionalTriggers() != null) {
            profile.setEmotionalTriggers(request.emotionalTriggers());
        }
        if (request.commonScenarios() != null) {
            profile.setCommonScenarios(request.commonScenarios());
        }
        if (request.relationshipTypes() != null) {
            profile.setRelationshipTypes(request.relationshipTypes());
        }

        return profileRepository.save(profile);
    }

    /**
     * 练习结束后更新能力分数
     * 基于评估结果记录能力分数，并更新 NVC 等级
     */
    public void updateAbilityScore(Long userId, Long sessionId, NvcEvaluationEntity evaluation,
                                    NvcPracticeType practiceType) {
        // 1. 记录能力分数
        NvcUserAbilityScoreEntity score = NvcUserAbilityScoreEntity.builder()
            .userId(userId)
            .sessionId(sessionId)
            .observation(evaluation.getObservationScore())
            .feeling(evaluation.getFeelingScore())
            .need(evaluation.getNeedScore())
            .request(evaluation.getRequestScore())
            .empathy(evaluation.getEmpathyScore())
            .practiceType(practiceType)
            .build();
        abilityScoreRepository.save(score);

        // 2. 更新档案统计
        NvcUserProfileEntity profile = getOrCreateProfile(userId);
        profile.setTotalPracticeCount(profile.getTotalPracticeCount() + 1);
        profile.setLastPracticeAt(LocalDateTime.now());

        // 3. 计算 NVC 等级
        NvcLevel newLevel = calculateLevel(userId);
        profile.setNvcLevel(newLevel);

        profileRepository.save(profile);
        log.info("Ability score updated: userId={}, overall={}, level={}",
            userId, evaluation.getOverallScore(), newLevel);
    }

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
            0, Math.min(10, recentScores.size()));

        int avgObservation = (int) last10.stream().mapToInt(NvcUserAbilityScoreEntity::getObservation).average().orElse(0);
        int avgFeeling = (int) last10.stream().mapToInt(NvcUserAbilityScoreEntity::getFeeling).average().orElse(0);
        int avgNeed = (int) last10.stream().mapToInt(NvcUserAbilityScoreEntity::getNeed).average().orElse(0);
        int avgRequest = (int) last10.stream().mapToInt(NvcUserAbilityScoreEntity::getRequest).average().orElse(0);
        int avgEmpathy = (int) last10.stream()
            .filter(s -> s.getEmpathy() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getEmpathy)
            .average().orElse(0);
        int overallAvg = (int) Math.round((avgObservation + avgFeeling + avgNeed + avgRequest) / 4.0);

        NvcUserProfileEntity profile = getOrCreateProfile(userId);

        return new AbilityRadarDTO(
            avgObservation, avgFeeling, avgNeed, avgRequest, avgEmpathy,
            overallAvg, profile.getNvcLevel().name()
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
    private NvcLevel calculateLevel(Long userId) {
        List<NvcUserAbilityScoreEntity> recent =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        if (recent.size() < 3) {
            return NvcLevel.BEGINNER;
        }

        List<NvcUserAbilityScoreEntity> last10 = recent.subList(0, Math.min(10, recent.size()));
        double avgOverall = last10.stream()
            .mapToInt(s -> (int) Math.round((s.getObservation() + s.getFeeling() + s.getNeed() + s.getRequest()) / 4.0))
            .average()
            .orElse(0);

        if (avgOverall >= 80) return NvcLevel.ADVANCED;
        if (avgOverall >= 60) return NvcLevel.INTERMEDIATE;
        return NvcLevel.BEGINNER;
    }

    /**
     * 转换为 DTO
     */
    public UserProfileDTO toDTO(NvcUserProfileEntity profile) {
        AbilityRadarDTO radar = getAbilityRadar(profile.getUserId());
        return new UserProfileDTO(
            profile.getUserId(),
            profile.getCommunicationBackground(),
            profile.getPersonalityTraits(),
            profile.getCommunicationStyle(),
            profile.getEmotionalTriggers(),
            profile.getCommonScenarios(),
            profile.getRelationshipTypes(),
            profile.getNvcLevel(),
            profile.getTotalPracticeCount(),
            profile.getTotalPracticeMinutes(),
            profile.getLastPracticeAt(),
            radar
        );
    }

    /**
     * 获取用户画像字符串，用于 Prompt 注入（预留接口）
     */
    public String getUserProfilePrompt(Long userId) {
        NvcUserProfileEntity profile = getOrCreateProfile(userId);
        return formatProfileForPrompt(profile);
    }

    private String formatProfileForPrompt(NvcUserProfileEntity profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户画像\n");

        if (profile.getCommunicationBackground() != null) {
            sb.append("- 沟通背景：").append(profile.getCommunicationBackground()).append("\n");
        }
        if (profile.getCommunicationStyle() != null) {
            sb.append("- 沟通风格：").append(profile.getCommunicationStyle().getDisplayName()).append("\n");
        }
        if (profile.getPersonalityTraits() != null) {
            sb.append("- 性格特征：").append(profile.getPersonalityTraits()).append("\n");
        }
        if (profile.getEmotionalTriggers() != null) {
            sb.append("- 情绪触发点：").append(profile.getEmotionalTriggers()).append("\n");
        }

        sb.append("- NVC 等级：").append(profile.getNvcLevel()).append("\n");
        sb.append("- 练习次数：").append(profile.getTotalPracticeCount()).append("\n");

        return sb.toString();
    }
}
