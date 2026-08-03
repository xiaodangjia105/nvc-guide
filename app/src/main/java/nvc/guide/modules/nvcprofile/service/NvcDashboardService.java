package nvc.guide.modules.nvcprofile.service;

import nvc.guide.modules.nvcpractice.model.NvcPracticeSessionEntity;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import nvc.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
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

        // 累计练习时长（分钟）：从已完成的 session 的 startedAt/completedAt 计算
        long totalPracticeMinutes = 0L;
        List<NvcPracticeSessionEntity> completedSessionEntities =
            sessionRepository.findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
                userId, NvcSessionPhase.COMPLETED);
        for (NvcPracticeSessionEntity session : completedSessionEntities) {
            LocalDateTime started = session.getStartedAt();
            LocalDateTime completed = session.getCompletedAt();
            if (started != null && completed != null) {
                totalPracticeMinutes += Duration.between(started, completed).toMinutes();
            }
        }

        // 前端期望的字段名
        stats.put("totalPracticeCount", totalSessions);
        stats.put("completedSessions", completedSessions);
        stats.put("totalPracticeMinutes", totalPracticeMinutes);
        stats.put("totalScores", totalScores);

        return stats;
    }
}
