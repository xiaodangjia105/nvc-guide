package nvc.guide.modules.nvcassistant.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Payload 智能截断器
 *
 * <p>JSON 格式化后截断，重要字段不截断
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PayloadTruncator {

    private final ObjectMapper objectMapper;

    /**
     * 重要字段集合（不截断）
     */
    private static final Set<String> IMPORTANT_FIELDS = Set.of(
        "toolName", "tool_name", "error", "status", "success",
        "failureReason", "failure_reason", "type", "name"
    );

    /**
     * 截断 payload
     *
     * @param payload    原始 payload
     * @param maxLength  最大长度
     * @param spanType   Span 类型（用于日志）
     * @return 截断后的 payload
     */
    public String truncate(String payload, int maxLength, String spanType) {
        if (payload == null) {
            return null;
        }

        if (payload.length() <= maxLength) {
            return payload;
        }

        // 尝试解析为 JSON
        try {
            JsonNode node = objectMapper.readTree(payload);
            return truncateJson(node, maxLength, spanType);
        } catch (Exception e) {
            // 非 JSON，直接截断
            log.debug("[PayloadTruncator] Non-JSON payload, truncating directly: spanType={}", spanType);
            return payload.substring(0, maxLength) + "...(truncated)";
        }
    }

    /**
     * 截断 JSON payload
     */
    private String truncateJson(JsonNode node, int maxLength, String spanType) {
        if (!node.isObject()) {
            // 非对象类型，直接截断字符串
            String str = node.toString();
            return str.length() > maxLength ? str.substring(0, maxLength) + "...(truncated)" : str;
        }

        ObjectNode result = objectMapper.createObjectNode();
        int currentLength = 2; // "{}" 的长度

        // 第一步：保留重要字段
        for (String field : IMPORTANT_FIELDS) {
            if (node.has(field)) {
                JsonNode value = node.get(field);
                String fieldStr = "\"" + field + "\":" + value.toString();
                if (currentLength + fieldStr.length() + 1 < maxLength) {
                    result.set(field, value);
                    currentLength += fieldStr.length() + 1; // +1 for comma
                }
            }
        }

        // 第二步：按顺序添加其他字段（直到达到长度限制）
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (!IMPORTANT_FIELDS.contains(entry.getKey())) {
                String valueStr = entry.getValue().toString();
                String fieldStr = "\"" + entry.getKey() + "\":" + valueStr;
                if (currentLength + fieldStr.length() + 1 < maxLength) {
                    result.set(entry.getKey(), entry.getValue());
                    currentLength += fieldStr.length() + 1;
                } else {
                    // 空间不足，添加截断标记
                    result.put("_truncated", true);
                    result.put("_totalFields", node.size());
                    result.put("_recordedFields", result.size() - 1); // 减去 _truncated 本身
                    break;
                }
            }
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[PayloadTruncator] Failed to serialize JSON: {}", e.getMessage());
            return result.toString();
        }
    }

    /**
     * 截断 payload（使用默认长度）
     */
    public String truncate(String payload, String spanType) {
        return truncate(payload, 4096, spanType);
    }
}
