package nvc.guide.modules.nvcassistant.trace;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "nvc.trace")
public class TraceProperties {

    /**
     * 默认级别：BASIC / DETAILED / FULL
     */
    private String defaultLevel = "BASIC";

    /**
     * 按 Span 类型的配置
     */
    private Map<String, SpanConfig> spans = new HashMap<>();

    /**
     * 运行时动态配置
     */
    private RuntimeConfig runtime = new RuntimeConfig();

    /**
     * 采样率配置
     */
    private SamplingConfig sampling = new SamplingConfig();

    /**
     * 自动清理配置
     */
    private CleanupConfig cleanup = new CleanupConfig();

    @Data
    public static class SpanConfig {
        /**
         * 级别：BASIC / DETAILED / FULL
         */
        private String level = "BASIC";

        /**
         * 是否启用 Hook 详细记录
         */
        private boolean hookDetailEnabled = false;

        /**
         * payload 最大长度
         */
        private int payloadMaxLength = 4096;
    }

    @Data
    public static class RuntimeConfig {
        /**
         * 是否启用运行时动态配置
         */
        private boolean enabled = true;

        /**
         * 调试用户 ID 列表（这些用户的 trace 会使用详细级别）
         */
        private List<Long> debugUsers = List.of();

        /**
         * 调试会话 ID 列表
         */
        private List<Long> debugSessions = List.of();
    }

    @Data
    public static class SamplingConfig {
        /**
         * 是否启用采样
         */
        private boolean enabled = false;

        /**
         * 采样率（0.0 - 1.0）
         */
        private double rate = 1.0;
    }

    @Data
    public static class CleanupConfig {
        /**
         * 是否启用自动清理
         */
        private boolean enabled = true;

        /**
         * 保留天数
         */
        private int retentionDays = 30;

        /**
         * 批量删除大小
         */
        private int batchSize = 1000;

        /**
         * cron 表达式（默认每天凌晨 3 点执行）
         */
        private String cron = "0 0 3 * * ?";
    }

    /**
     * 获取指定 Span 类型的配置
     */
    public SpanConfig getSpanConfig(String spanType) {
        return spans.getOrDefault(spanType, getDefaultSpanConfig());
    }

    /**
     * 获取默认 Span 配置
     */
    public SpanConfig getDefaultSpanConfig() {
        SpanConfig config = new SpanConfig();
        config.setLevel(defaultLevel);
        return config;
    }

    /**
     * 检查是否应该记录详细信息
     */
    public boolean shouldRecordDetailed(String spanType, Long userId, Long sessionId) {
        SpanConfig config = getSpanConfig(spanType);

        // 检查配置级别
        if ("FULL".equalsIgnoreCase(config.getLevel()) || "DETAILED".equalsIgnoreCase(config.getLevel())) {
            return true;
        }

        // 检查运行时动态配置
        if (runtime.isEnabled()) {
            if (userId != null && runtime.getDebugUsers().contains(userId)) {
                return true;
            }
            if (sessionId != null && runtime.getDebugSessions().contains(sessionId)) {
                return true;
            }
        }

        return false;
    }
}
