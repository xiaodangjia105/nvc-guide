package nvc.guide.modules.nvcprofile.service;

import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NvcDashboardService {

    private final NvcPracticeSessionRepository sessionRepository;
    private final NvcUserAbilityScoreRepository abilityScoreRepository;
    private final NvcUserProfileRepository profileRepository;

    /**
     * 获取用户练习统计（基础版本）
     */
    public Map<String, Object> getUserStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 总练习次数
        long totalSessions = sessionRepository.countByUserId(userId);

        // 已完成的练习次数（使用 countBy 避免全量加载）
        long completedSessions = sessionRepository
            .countByUserIdAndCurrentPhase(userId, NvcSessionPhase.COMPLETED);

        // 能力评分记录数（使用 countBy 避免全量加载）
        long totalScores = abilityScoreRepository.countByUserId(userId);

        stats.put("totalSessions", totalSessions);
        stats.put("completedSessions", completedSessions);
        stats.put("totalScores", totalScores);

        return stats;
    }
}
