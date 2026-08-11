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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcProfileService {

    private final NvcUserProfileRepository profileRepository;
    private final NvcUserAbilityScoreRepository abilityScoreRepository;
    private final NvcAbilityService abilityService;

    /**
     * 获取用户档案（不存在则创建默认档案）
     *
     * <p>使用 try-catch 处理并发创建的竞态条件：
     * 两个并发请求可能都发现档案不存在，都尝试创建，
     * 第二个会因唯一约束冲突抛出 DataIntegrityViolationException。
     */
    @Transactional
    public NvcUserProfileEntity getOrCreateProfile(Long userId) {
        return profileRepository.findByUserId(userId)
            .orElseGet(() -> {
                try {
                    return createDefaultProfile(userId);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // 并发创建冲突，重新读取已创建的档案
                    log.debug("Profile creation race condition detected, re-reading: userId={}", userId);
                    return profileRepository.findByUserId(userId)
                        .orElseThrow(() -> e);
                }
            });
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
     * 保存用户档案（用于 preferences 等直接修改场景）
     */
    public NvcUserProfileEntity saveProfile(NvcUserProfileEntity profile) {
        return profileRepository.save(profile);
    }

    /**
     * 原子更新用户偏好（读-改-写）
     *
     * <p>使用 @Transactional 确保并发安全，防止丢失更新
     */
    @Transactional
    public NvcUserProfileEntity updatePreferences(Long userId, Map<String, Object> newPreferences) {
        NvcUserProfileEntity profile = getOrCreateProfile(userId);
        Map<String, Object> existing = profile.getPreferences() != null
            ? profile.getPreferences() : new java.util.HashMap<>();
        existing.putAll(newPreferences);
        profile.setPreferences(existing);
        return profileRepository.save(profile);
    }

    /**
     * 更新用户档案
     */
    @Transactional
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
    @Transactional
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
        // TODO: totalPracticeMinutes 应在会话完成时更新，需要传入会话时长
        // 当前由 NvcDashboardService.getUserStats() 动态计算

        // 3. 计算 NVC 等级
        NvcLevel newLevel = abilityService.calculateLevel(userId);
        profile.setNvcLevel(newLevel);

        profileRepository.save(profile);
        log.info("Ability score updated: userId={}, overall={}, level={}",
            userId, evaluation.getOverallScore(), newLevel);
    }

    /**
     * 获取能力雷达图数据（委托给 NvcAbilityService）
     */
    public AbilityRadarDTO getAbilityRadar(Long userId) {
        return abilityService.getAbilityRadar(userId);
    }

    /**
     * 获取能力趋势数据（委托给 NvcAbilityService）
     */
    public List<AbilityTrendDTO> getAbilityTrends(Long userId) {
        return abilityService.getAbilityTrends(userId);
    }

    /**
     * 转换为 DTO
     */
    public UserProfileDTO toDTO(NvcUserProfileEntity profile) {
        AbilityRadarDTO radar = abilityService.getAbilityRadar(profile.getUserId());
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
