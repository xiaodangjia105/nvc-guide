package nvc.guide.modules.nvcprofile.service;

import nvc.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import nvc.guide.modules.nvcpractice.model.NvcPracticeType;
import nvc.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import nvc.guide.modules.nvcprofile.dto.UserProfileUpdateRequest;
import nvc.guide.modules.nvcprofile.model.NvcLevel;
import nvc.guide.modules.nvcprofile.model.NvcUserAbilityScoreEntity;
import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import nvc.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NvcProfileService 测试")
class NvcProfileServiceTest {

    @Mock
    private NvcUserProfileRepository profileRepository;
    @Mock
    private NvcUserAbilityScoreRepository abilityScoreRepository;

    private NvcAbilityService abilityService;
    private NvcProfileService service;

    @BeforeEach
    void setUp() {
        abilityService = new NvcAbilityService(abilityScoreRepository, profileRepository);
        service = new NvcProfileService(profileRepository, abilityScoreRepository, abilityService);
    }

    private NvcUserProfileEntity buildProfile(Long userId, NvcLevel level) {
        return NvcUserProfileEntity.builder()
                .id(1L)
                .userId(userId)
                .nvcLevel(level)
                .totalPracticeCount(0)
                .totalPracticeMinutes(0)
                .build();
    }

    private NvcUserAbilityScoreEntity buildScore(Long userId, int observation, int feeling, int need, int request) {
        return NvcUserAbilityScoreEntity.builder()
                .id(1L)
                .userId(userId)
                .observation(observation)
                .feeling(feeling)
                .need(need)
                .request(request)
                .build();
    }

    // ==================== getOrCreateProfile ====================

    @Nested
    @DisplayName("getOrCreateProfile()")
    class GetOrCreateProfileTests {

        @Test
        @DisplayName("返回已存在的用户档案")
        void returnsExistingProfile() {
            NvcUserProfileEntity existing = buildProfile(1L, NvcLevel.INTERMEDIATE);
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));

            NvcUserProfileEntity result = service.getOrCreateProfile(1L);

            assertNotNull(result);
            assertEquals(1L, result.getUserId());
            assertEquals(NvcLevel.INTERMEDIATE, result.getNvcLevel());
            verify(profileRepository, never()).save(any());
        }

        @Test
        @DisplayName("不存在时创建默认 BEGINNER 档案")
        void createsDefaultProfileWhenNotFound() {
            NvcUserProfileEntity savedProfile = buildProfile(2L, NvcLevel.BEGINNER);
            when(profileRepository.findByUserId(2L)).thenReturn(Optional.empty());
            when(profileRepository.save(any(NvcUserProfileEntity.class))).thenReturn(savedProfile);

            NvcUserProfileEntity result = service.getOrCreateProfile(2L);

            assertNotNull(result);
            assertEquals(2L, result.getUserId());
            assertEquals(NvcLevel.BEGINNER, result.getNvcLevel());
            verify(profileRepository).save(any(NvcUserProfileEntity.class));
        }
    }

    // ==================== updateProfile ====================

    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfileTests {

        @Test
        @DisplayName("部分更新字段")
        void partialUpdate() {
            NvcUserProfileEntity existing = buildProfile(1L, NvcLevel.BEGINNER);
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
            when(profileRepository.save(any(NvcUserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                    "新背景",
                    null,
                    null,
                    "新触发点",
                    null,
                    null
            );

            NvcUserProfileEntity result = service.updateProfile(1L, request);

            assertEquals("新背景", result.getCommunicationBackground());
            assertEquals("新触发点", result.getEmotionalTriggers());
            assertNull(result.getPersonalityTraits());
            verify(profileRepository).save(existing);
        }

        @Test
        @DisplayName("null 字段不覆盖")
        void nullFieldsDoNotOverwrite() {
            NvcUserProfileEntity existing = buildProfile(1L, NvcLevel.BEGINNER);
            existing.setCommunicationBackground("原始背景");
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(existing));
            when(profileRepository.save(any(NvcUserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            UserProfileUpdateRequest request = new UserProfileUpdateRequest(
                    null, null, null, null, null, null
            );

            NvcUserProfileEntity result = service.updateProfile(1L, request);

            assertEquals("原始背景", result.getCommunicationBackground());
        }
    }

    // ==================== calculateLevel ====================

    @Nested
    @DisplayName("calculateLevel()")
    class CalculateLevelTests {

        @Test
        @DisplayName("少于 3 次练习返回 BEGINNER")
        void returnsBeginnerWhenLessThan3Sessions() {
            List<NvcUserAbilityScoreEntity> scores = List.of(
                    buildScore(1L, 90, 90, 90, 90),
                    buildScore(1L, 90, 90, 90, 90)
            );
            when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L)).thenReturn(scores);

            // calculateLevel is private; we test it through updateAbilityScore
            NvcUserProfileEntity profile = buildProfile(1L, NvcLevel.BEGINNER);
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any(NvcUserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            NvcEvaluationEntity evaluation = NvcEvaluationEntity.builder()
                    .observationScore(90).feelingScore(90).needScore(90).requestScore(90).overallScore(90)
                    .build();
            service.updateAbilityScore(1L, 1L, evaluation, NvcPracticeType.TEXT);

            assertEquals(NvcLevel.BEGINNER, profile.getNvcLevel());
        }

        @Test
        @DisplayName("平均分 < 60 返回 BEGINNER")
        void returnsBeginnerWhenBelow60() {
            List<NvcUserAbilityScoreEntity> scores = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                scores.add(buildScore(1L, 50, 50, 50, 50));
            }
            when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L)).thenReturn(scores);

            NvcUserProfileEntity profile = buildProfile(1L, NvcLevel.BEGINNER);
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any(NvcUserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            NvcEvaluationEntity evaluation = NvcEvaluationEntity.builder()
                    .observationScore(50).feelingScore(50).needScore(50).requestScore(50).overallScore(50)
                    .build();
            service.updateAbilityScore(1L, 1L, evaluation, NvcPracticeType.TEXT);

            assertEquals(NvcLevel.BEGINNER, profile.getNvcLevel());
        }

        @Test
        @DisplayName("平均分 60-79 返回 INTERMEDIATE")
        void returnsIntermediateWhen60To79() {
            List<NvcUserAbilityScoreEntity> scores = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                scores.add(buildScore(1L, 70, 70, 70, 70));
            }
            when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L)).thenReturn(scores);

            NvcUserProfileEntity profile = buildProfile(1L, NvcLevel.BEGINNER);
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any(NvcUserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            NvcEvaluationEntity evaluation = NvcEvaluationEntity.builder()
                    .observationScore(70).feelingScore(70).needScore(70).requestScore(70).overallScore(70)
                    .build();
            service.updateAbilityScore(1L, 1L, evaluation, NvcPracticeType.TEXT);

            assertEquals(NvcLevel.INTERMEDIATE, profile.getNvcLevel());
        }

        @Test
        @DisplayName("平均分 >= 80 返回 ADVANCED")
        void returnsAdvancedWhen80OrAbove() {
            List<NvcUserAbilityScoreEntity> scores = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                scores.add(buildScore(1L, 85, 85, 85, 85));
            }
            when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L)).thenReturn(scores);

            NvcUserProfileEntity profile = buildProfile(1L, NvcLevel.INTERMEDIATE);
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
            when(profileRepository.save(any(NvcUserProfileEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            NvcEvaluationEntity evaluation = NvcEvaluationEntity.builder()
                    .observationScore(85).feelingScore(85).needScore(85).requestScore(85).overallScore(85)
                    .build();
            service.updateAbilityScore(1L, 1L, evaluation, NvcPracticeType.TEXT);

            assertEquals(NvcLevel.ADVANCED, profile.getNvcLevel());
        }
    }

    // ==================== getAbilityRadar ====================

    @Nested
    @DisplayName("getAbilityRadar()")
    class GetAbilityRadarTests {

        @Test
        @DisplayName("无评分记录返回全零")
        void returnsZerosWhenNoScores() {
            when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L)).thenReturn(List.of());

            AbilityRadarDTO result = service.getAbilityRadar(1L);

            assertEquals(0, result.observation());
            assertEquals(0, result.feeling());
            assertEquals(0, result.need());
            assertEquals(0, result.request());
            assertEquals(0, result.empathy());
            assertEquals(0, result.overall());
            assertEquals("BEGINNER", result.level());
        }

        @Test
        @DisplayName("最近 10 次取平均值")
        void averagesLast10Scores() {
            List<NvcUserAbilityScoreEntity> scores = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                scores.add(NvcUserAbilityScoreEntity.builder()
                        .userId(1L)
                        .observation(80)
                        .feeling(70)
                        .need(90)
                        .request(60)
                        .empathy(75)
                        .build());
            }
            when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L)).thenReturn(scores);
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(buildProfile(1L, NvcLevel.INTERMEDIATE)));

            AbilityRadarDTO result = service.getAbilityRadar(1L);

            assertEquals(80, result.observation());
            assertEquals(70, result.feeling());
            assertEquals(90, result.need());
            assertEquals(60, result.request());
            assertEquals(75, result.empathy());
            // overall = round((80 + 70 + 90 + 60) / 4.0) = round(75.0) = 75
            assertEquals(75, result.overall());
            assertEquals("INTERMEDIATE", result.level());
        }

        @Test
        @DisplayName("超过 10 条记录只取最近 10 条")
        void takesOnlyLast10WhenMoreExist() {
            // 20 条记录：前 10 条（最新的）全是 80，后 10 条全是 40
            List<NvcUserAbilityScoreEntity> scores = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                scores.add(NvcUserAbilityScoreEntity.builder()
                        .userId(1L).observation(80).feeling(80).need(80).request(80).build());
            }
            for (int i = 0; i < 10; i++) {
                scores.add(NvcUserAbilityScoreEntity.builder()
                        .userId(1L).observation(40).feeling(40).need(40).request(40).build());
            }
            when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L)).thenReturn(scores);
            when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(buildProfile(1L, NvcLevel.ADVANCED)));

            AbilityRadarDTO result = service.getAbilityRadar(1L);

            // 前 10 条都是 80，平均值应为 80
            assertEquals(80, result.observation());
            assertEquals(80, result.overall());
        }
    }
}
