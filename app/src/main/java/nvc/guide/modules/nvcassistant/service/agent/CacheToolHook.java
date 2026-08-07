package nvc.guide.modules.nvcassistant.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcpractice.tool.NvcToolContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 缓存钩子 — 对高频查询工具提供内存缓存
 *
 * <p>缓存 TTL 配置：
 * <ul>
 *   <li>dashboard_query: 5 分钟</li>
 *   <li>profile_query: 10 分钟</li>
 *   <li>rag_search: 30 分钟</li>
 *   <li>wiki_search: 30 分钟</li>
 * </ul>
 *
 * <p>缓存 key 策略：
 * <ul>
 *   <li>dashboard_query / profile_query: {toolName}:{userId}（用户级缓存）</li>
 *   <li>rag_search / wiki_search: {toolName}:{userId}:{参数hash}（参数级缓存）</li>
 * </ul>
 *
 * <p>缓存命中时，设置 skipReason 为缓存结果，ToolExecutor 会将其作为跳过原因返回给 LLM。
 *
 * <p>Order=3（在限流和权限检查之后执行）
 */
@Component
@Slf4j
@Order(3)
public class CacheToolHook implements NvcToolHook {

    /** 用户级缓存工具（只按 userId 缓存） */
    private static final Set<String> USER_LEVEL_CACHE_TOOLS = Set.of(
        "dashboard_query",
        "profile_query"
    );

    /** 缓存 TTL 配置（毫秒） */
    private static final Map<String, Long> CACHE_TTL_MS = Map.of(
        "dashboard_query", 5 * 60 * 1000L,    // 5 分钟
        "profile_query",   10 * 60 * 1000L,   // 10 分钟
        "rag_search",      30 * 60 * 1000L,   // 30 分钟
        "wiki_search",     30 * 60 * 1000L    // 30 分钟
    );

    /** 可缓存的工具集合 */
    private static final Set<String> CACHEABLE_TOOLS = CACHE_TTL_MS.keySet();

    /** 缓存存储 */
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 定期清理过期缓存的调度器 */
    private final ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "cache-cleanup");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void init() {
        // 每 5 分钟清理一次过期缓存
        cleanupScheduler.scheduleAtFixedRate(this::cleanupExpiredEntries, 5, 5, TimeUnit.MINUTES);
        log.info("[CacheToolHook] Initialized with periodic cleanup every 5 minutes");
    }

    @PreDestroy
    public void destroy() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[CacheToolHook] Shutdown complete, cache cleared");
    }

    /**
     * 清理过期缓存条目
     */
    private void cleanupExpiredEntries() {
        int before = cache.size();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        int after = cache.size();
        if (before != after) {
            log.debug("[CacheToolHook] Cleaned up {} expired entries: {} -> {}", before - after, before, after);
        }
    }

    @Override
    public CompletableFuture<ToolCallDecision> beforeToolCall(String toolName, JsonNode arguments, NvcToolContext context) {
        if (!CACHEABLE_TOOLS.contains(toolName)) {
            return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
        }

        Long userId = context.getUserId();
        String cacheKey = buildCacheKey(toolName, userId, arguments);

        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            // 缓存命中
            context.setAttribute("cachedResult", entry.result);
            context.setAttribute("skipReason", "从缓存返回结果");
            log.info("[CacheToolHook] Cache HIT: tool={}, userId={}, key={}", toolName, userId, cacheKey);
            return CompletableFuture.completedFuture(ToolCallDecision.SKIP);
        }

        // 缓存未命中或已过期，记录 cacheKey 供 afterToolCall 使用
        if (entry != null) {
            cache.remove(cacheKey); // 清除过期条目
        }
        context.setAttribute("cacheKey", cacheKey);
        context.setAttribute("cacheToolName", toolName);
        log.debug("[CacheToolHook] Cache MISS: tool={}, userId={}, key={}", toolName, userId, cacheKey);
        return CompletableFuture.completedFuture(ToolCallDecision.PROCEED);
    }

    @Override
    public CompletableFuture<String> afterToolCall(String toolName, String result, NvcToolContext context) {
        if (!CACHEABLE_TOOLS.contains(toolName)) {
            return CompletableFuture.completedFuture(result);
        }

        // 只在成功时缓存（不缓存错误结果）
        if (result == null || result.startsWith("Error:")) {
            return CompletableFuture.completedFuture(result);
        }

        String cacheKey = context.getAttribute("cacheKey");
        if (cacheKey == null) {
            return CompletableFuture.completedFuture(result);
        }

        // 检查是否有 fromCache 标记（防止缓存循环）
        Boolean fromCache = context.getAttribute("fromCache");
        if (Boolean.TRUE.equals(fromCache)) {
            return CompletableFuture.completedFuture(result);
        }

        Long ttlMs = CACHE_TTL_MS.getOrDefault(toolName, 5 * 60 * 1000L);
        cache.put(cacheKey, new CacheEntry(result, System.currentTimeMillis() + ttlMs));
        log.debug("[CacheToolHook] Cached result: tool={}, key={}, ttlMs={}", toolName, cacheKey, ttlMs);

        return CompletableFuture.completedFuture(result);
    }

    /**
     * 构建缓存 key
     */
    private String buildCacheKey(String toolName, Long userId, JsonNode arguments) {
        if (USER_LEVEL_CACHE_TOOLS.contains(toolName)) {
            // 用户级缓存，不包含参数
            return toolName + ":" + userId;
        }
        // 参数级缓存，包含参数 hash
        String argsHash = hashArguments(arguments);
        return toolName + ":" + userId + ":" + argsHash;
    }

    /**
     * 计算参数 hash
     */
    private String hashArguments(JsonNode arguments) {
        if (arguments == null || arguments.isNull()) {
            return "null";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(arguments.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16); // 取前 16 位
        } catch (Exception e) {
            return String.valueOf(arguments.hashCode());
        }
    }

    /**
     * 缓存条目
     */
    private record CacheEntry(String result, long expireAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }
    }
}
