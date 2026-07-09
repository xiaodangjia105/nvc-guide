package nvc.guide.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 会话 Redis 缓存服务（骨架）
 * 原 InterviewSessionCache，后续改造为 NvcSessionCache
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionCache {

    private final RedisService redisService;

    /**
     * 缓存键前缀
     */
    private static final String SESSION_KEY_PREFIX = "nvc:session:";

    /**
     * 会话默认过期时间（24小时）
     */
    private static final Duration SESSION_TTL = Duration.ofHours(24);

    /**
     * 保存会话到缓存
     */
    public void saveSession(String sessionId, Object sessionData) {
        String key = buildSessionKey(sessionId);
        redisService.set(key, sessionData, SESSION_TTL);
        log.debug("会话已缓存: sessionId={}", sessionId);
    }

    /**
     * 获取缓存的会话
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getSession(String sessionId, Class<T> clazz) {
        String key = buildSessionKey(sessionId);
        Object session = redisService.get(key);
        if (session != null) {
            log.debug("从缓存获取会话: sessionId={}", sessionId);
            return Optional.of(clazz.cast(session));
        }
        return Optional.empty();
    }

    /**
     * 删除会话缓存
     */
    public void deleteSession(String sessionId) {
        String key = buildSessionKey(sessionId);
        redisService.delete(key);
        log.debug("删除会话缓存: sessionId={}", sessionId);
    }

    /**
     * 刷新会话过期时间
     */
    public void refreshSessionTTL(String sessionId) {
        String key = buildSessionKey(sessionId);
        redisService.expire(key, SESSION_TTL);
    }

    /**
     * 检查会话是否在缓存中
     */
    public boolean exists(String sessionId) {
        String key = buildSessionKey(sessionId);
        return redisService.exists(key);
    }

    // ==================== 私有方法 ====================

    private String buildSessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }
}
