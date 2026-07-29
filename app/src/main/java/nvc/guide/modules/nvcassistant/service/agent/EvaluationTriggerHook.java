package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.model.NvcPracticeMessageEntity;
import nvc.guide.modules.nvcpractice.repository.NvcPracticeMessageRepository;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import nvc.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import nvc.guide.modules.nvcprofile.service.NvcProfileService;
import nvc.guide.modules.nvcwiki.service.NvcWikiAutoGenerateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 评估触发钩子 — 在 evaluate_nvc 成功后异步触发 Wiki 自动生成
 *
 * <p>触发条件：
 * <ul>
 *   <li>工具为 evaluate_nvc</li>
 *   <li>工具执行成功（结果不以 "Error:" 开头）</li>
 *   <li>用户 preferences 中 autoGenerateWiki 为 true（默认不开启）</li>
 * </ul>
 *
 * <p>不阻塞主流程，异常只记录日志。
 *
 * <p>Order=5（在错误增强之后执行）
 */
@Component
@Slf4j
@Order(5)
public class EvaluationTriggerHook implements NvcToolHook {

    private static final String TARGET_TOOL = "evaluate_nvc";
    private static final String PREF_KEY = "autoGenerateWiki";

    @Autowired(required = false)
    private NvcWikiAutoGenerateService wikiAutoGenerateService;

    @Autowired(required = false)
    private NvcProfileService profileService;

    @Autowired(required = false)
    private NvcPracticeMessageRepository messageRepository;

    @Override
    public CompletableFuture<String> afterToolCall(String toolName, String result, NvcToolContext context) {
        // 只处理 evaluate_nvc 的成功结果
        if (!TARGET_TOOL.equals(toolName)) {
            return CompletableFuture.completedFuture(result);
        }
        if (result == null || result.startsWith("Error:")) {
            return CompletableFuture.completedFuture(result);
        }

        // 异步触发 Wiki 生成（不阻塞主流程）
        CompletableFuture.runAsync(() -> triggerWikiGeneration(context));

        return CompletableFuture.completedFuture(result);
    }

    /**
     * 异步触发 Wiki 自动生成
     */
    private void triggerWikiGeneration(NvcToolContext context) {
        try {
            Long userId = context.getUserId();
            Long sessionId = context.getSessionId();

            if (userId == null || sessionId == null) {
                return;
            }

            // 检查依赖服务是否可用
            if (wikiAutoGenerateService == null || profileService == null || messageRepository == null) {
                log.debug("[EvaluationTriggerHook] Wiki auto-generate services not available, skipping");
                return;
            }

            // 检查用户偏好
            boolean shouldGenerate = checkAutoGeneratePreference(userId);
            if (!shouldGenerate) {
                log.debug("[EvaluationTriggerHook] User {} has autoGenerateWiki disabled", userId);
                return;
            }

            // 获取会话消息
            List<NvcPracticeMessageEntity> messages =
                messageRepository.findBySessionIdOrderBySequenceNumAsc(sessionId);
            if (messages.isEmpty()) {
                log.debug("[EvaluationTriggerHook] No messages found for session {}", sessionId);
                return;
            }

            // 异步生成 Wiki
            log.info("[EvaluationTriggerHook] Triggering Wiki auto-generation: userId={}, sessionId={}",
                userId, sessionId);
            wikiAutoGenerateService.generateFromSession(userId, sessionId, messages);
            log.info("[EvaluationTriggerHook] Wiki auto-generation completed: userId={}, sessionId={}",
                userId, sessionId);

        } catch (Exception e) {
            // 不阻塞主流程，只记录日志
            log.error("[EvaluationTriggerHook] Wiki auto-generation failed: userId={}, sessionId={}",
                context.getUserId(), context.getSessionId(), e);
        }
    }

    /**
     * 检查用户是否开启了自动生成 Wiki 偏好
     *
     * @return true 如果用户偏好 autoGenerateWiki=true
     */
    private boolean checkAutoGeneratePreference(Long userId) {
        try {
            NvcUserProfileEntity profile = profileService.getOrCreateProfile(userId);
            Map<String, Object> preferences = profile.getPreferences();
            if (preferences == null || preferences.isEmpty()) {
                return false;
            }
            Object value = preferences.get(PREF_KEY);
            if (value instanceof Boolean b) {
                return b;
            }
            // 支持字符串 "true"
            if (value instanceof String s) {
                return Boolean.parseBoolean(s);
            }
            return false;
        } catch (Exception e) {
            log.warn("[EvaluationTriggerHook] Failed to check user preferences: userId={}", userId, e);
            return false;
        }
    }
}
