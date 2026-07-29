package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.model.NvcSessionPhase;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 权限钩子 — 检查用户是否有权使用特定工具
 *
 * <p>权限规则：
 * <ul>
 *   <li>scenario_generate: 需要已完成至少一次练习</li>
 *   <li>evaluate_nvc: 需要已完成至少一次练习</li>
 * </ul>
 *
 * <p>Order=2（在限流之后执行）
 */
@Component
@Slf4j
@Order(2)
public class PermissionToolHook implements NvcToolHook {

    /** 需要练习经验的工具 */
    private static final Set<String> PRACTICE_REQUIRED_TOOLS = Set.of(
        "scenario_generate",
        "evaluate_nvc"
    );

    @Autowired(required = false)
    private NvcProfileService profileService;

    @Autowired(required = false)
    private NvcPracticeSessionRepository sessionRepository;

    @Override
    public CompletableFuture<ToolCallDecision> beforeToolCall(String toolName, JsonNode arguments, NvcToolContext context) {
        if (!PRACTICE_REQUIRED_TOOLS.contains(toolName)) {
            return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
        }

        Long userId = context.getUserId();
        if (userId == null) {
            return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
        }

        // 检查用户是否完成了至少一次练习
        boolean hasPracticeExperience = checkPracticeExperience(userId);
        if (!hasPracticeExperience) {
            String toolLabel = "scenario_generate".equals(toolName) ? "场景生成" : "NVC 评估";
            String reason = "使用" + toolLabel + "功能前，请先完成至少一次 NVC 练习";
            context.setAttribute("skipReason", reason);
            log.info("[PermissionToolHook] Permission denied: tool={}, userId={}, reason=no_practice_experience",
                toolName, userId);
            return CompletableFuture.completedFuture(ToolCallDecision.SKIP);
        }

        log.debug("[PermissionToolHook] Passed: tool={}, userId={}", toolName, userId);
        return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
    }

    /**
     * 检查用户是否有练习经验
     * 优先使用 NvcProfileService.totalPracticeCount，
     * 降级使用 NvcPracticeSessionRepository.countByUserIdAndCurrentPhase
     */
    private boolean checkPracticeExperience(Long userId) {
        // 方式 1: 通过 Profile 检查总练习次数
        if (profileService != null) {
            try {
                NvcUserProfileEntity profile = profileService.getOrCreateProfile(userId);
                if (profile.getTotalPracticeCount() != null && profile.getTotalPracticeCount() > 0) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("[PermissionToolHook] Failed to check profile, falling back to session repo", e);
            }
        }

        // 方式 2: 通过 Session 仓储检查已完成/已评估的会话数
        if (sessionRepository != null) {
            try {
                long completedCount = sessionRepository.countByUserIdAndCurrentPhase(userId, NvcSessionPhase.COMPLETED);
                long evaluatedCount = sessionRepository.countByUserIdAndCurrentPhase(userId, NvcSessionPhase.EVALUATED);
                return (completedCount + evaluatedCount) > 0;
            } catch (Exception e) {
                log.warn("[PermissionToolHook] Failed to check session repo", e);
            }
        }

        // 如果两个服务都不可用，默认放行（避免阻塞新用户）
        log.warn("[PermissionToolHook] No profile/session service available, defaulting to ALLOW");
        return true;
    }
}
