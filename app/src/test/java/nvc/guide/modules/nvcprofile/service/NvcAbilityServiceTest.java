package nvc.guide.modules.nvcprofile.service;

import nvc.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import nvc.guide.modules.nvcprofile.model.NvcLevel;
import nvc.guide.modules.nvcprofile.model.NvcUserAbilityScoreEntity;
import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import nvc.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NvcAbilityServiceTest {

    @Mock
    private NvcUserAbilityScoreRepository abilityScoreRepository;

    @Mock
    private NvcUserProfileRepository profileRepository;

    @InjectMocks
    private NvcAbilityService abilityService;

    @Test
    void getAbilityRadar_noScores_returnsZeros() {
        when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L))
            .thenReturn(Collections.emptyList());

        AbilityRadarDTO result = abilityService.getAbilityRadar(1L);

        assertEquals(0, result.observation());
        assertEquals(0, result.feeling());
        assertEquals(0, result.need());
        assertEquals(0, result.request());
        assertEquals(0, result.empathy());
        assertEquals("BEGINNER", result.level());
    }

    @Test
    void getAbilityRadar_withScores_returnsAverages() {
        List<NvcUserAbilityScoreEntity> scores = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            scores.add(createScore(80, 70, 90, 60, 85));
        }
        when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L))
            .thenReturn(scores);

        NvcUserProfileEntity profile = NvcUserProfileEntity.builder()
            .userId(1L)
            .nvcLevel(NvcLevel.INTERMEDIATE)
            .build();
        when(profileRepository.findByUserId(1L))
            .thenReturn(Optional.of(profile));

        AbilityRadarDTO result = abilityService.getAbilityRadar(1L);

        assertEquals(80, result.observation());
        assertEquals(70, result.feeling());
        assertEquals(90, result.need());
        assertEquals(60, result.request());
        assertEquals(85, result.empathy());
        assertEquals(75, result.overall());
        assertEquals("INTERMEDIATE", result.level());
    }

    @Test
    void calculateLevel_insufficientSamples_returnsBeginner() {
        when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L))
            .thenReturn(List.of(
                createScore(80, 80, 80, 80, 80),
                createScore(80, 80, 80, 80, 80)
            ));

        NvcLevel level = abilityService.calculateLevel(1L);

        assertEquals(NvcLevel.BEGINNER, level);
    }

    @Test
    void calculateLevel_highScores_returnsAdvanced() {
        List<NvcUserAbilityScoreEntity> scores = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            scores.add(createScore(85, 85, 85, 85, 85));
        }
        when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L))
            .thenReturn(scores);

        NvcLevel level = abilityService.calculateLevel(1L);

        assertEquals(NvcLevel.ADVANCED, level);
    }

    @Test
    void calculateLevel_mediumScores_returnsIntermediate() {
        List<NvcUserAbilityScoreEntity> scores = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            scores.add(createScore(65, 65, 65, 65, 65));
        }
        when(abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(1L))
            .thenReturn(scores);

        NvcLevel level = abilityService.calculateLevel(1L);

        assertEquals(NvcLevel.INTERMEDIATE, level);
    }

    private NvcUserAbilityScoreEntity createScore(int obs, int feel, int need, int req, int empathy) {
        return NvcUserAbilityScoreEntity.builder()
            .observation(obs)
            .feeling(feel)
            .need(need)
            .request(req)
            .empathy(empathy)
            .scoredAt(LocalDateTime.now())
            .build();
    }
}
