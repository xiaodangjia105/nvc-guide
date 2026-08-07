package nvc.guide.modules.nvcassistant.service.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nvc.guide.modules.nvcassistant.trace.AgentSpanEntity;
import nvc.guide.modules.nvcassistant.trace.TraceManager;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 意图预路由 — 在 LLM 调用前，通过关键词匹配快速识别明确意图
 *
 * <p>解决 mimo-v2.5 模型工具调用不准确的问题：
 * 当用户意图非常明确时（如"我是程序员"→ profile_update），直接路由到正确工具，
 * 不依赖 LLM 选择工具。
 *
 * <p>匹配规则：
 * <ul>
 *   <li>只匹配高置信度的模式（避免误判）</li>
 *   <li>返回 null 表示无法确定意图，交给 LLM 判断</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IntentRouter {

    private final TraceManager traceManager;

    /**
     * 非职业词汇黑名单
     */
    private static final Set<String> NON_OCCUPATION_WORDS = Set.of(
        "人", "自己", "大家", "我们", "你们", "他们", "她们", "它们",
        "男人", "女人", "男孩", "女孩", "朋友", "同事", "家人",
        "什么", "怎么", "为什么", "哪里", "谁", "哪个"
    );

    /**
     * 意图匹配结果
     */
    public record IntentMatch(String toolName, String arguments, String reason) {}

    /**
     * 个人信息模式：用户描述自己的职业、年龄、性别等
     *
     * <p>示例：
     * - "我是程序员，21岁，男"
     * - "我是一名教师"
     * - "帮我记录到档案中"
     * - "更新我的个人档案"
     */
    private static final Pattern PROFILE_UPDATE_PATTERN = Pattern.compile(
        "(我是|我是|我是一名|我的职业是|帮我记录|记录到档案|更新.*档案|修改.*个人信息|补充.*个人信息|补充.*档案|更新.*个人信息|保存.*个人信息)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 查看档案模式
     */
    private static final Pattern PROFILE_QUERY_PATTERN = Pattern.compile(
        "(看看.*档案|查看.*档案|我的个人信息|看下.*档案|查询.*档案)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 练习数据模式
     */
    private static final Pattern DASHBOARD_QUERY_PATTERN = Pattern.compile(
        "(练习数据|练习统计|我练了多少|我的进度|查看.*数据|查看.*统计)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 意图识别
     *
     * @param userMessage 用户消息
     * @return 匹配结果，null 表示无法确定意图
     */
    public IntentMatch detectIntent(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return null;
        }

        // Trace 埋点
        long startTime = System.currentTimeMillis();
        AgentSpanEntity span = traceManager.startSpan("INTENT_ROUTING", "IntentRouter");

        try {
            String trimmed = userMessage.trim();

            // 1. 检查是否是更新档案意图
            if (PROFILE_UPDATE_PATTERN.matcher(trimmed).find()) {
                // 进一步检查是否包含个人信息（职业、年龄、性别）
                String personalInfo = extractPersonalInfo(trimmed);
                if (personalInfo != null) {
                    String arguments = "{\"field\": \"communicationBackground\", \"value\": \"" + escapeJson(personalInfo) + "\"}";
                    log.info("[IntentRouter] Detected profile_update intent: message={}, info={}", trimmed, personalInfo);
                    span.setDurationMs(System.currentTimeMillis() - startTime);
                    traceManager.endSpan(span, "SUCCESS", null);
                    return new IntentMatch("profile_update", arguments, "用户描述了个人信息");
                }
                // 如果只是说"更新档案"但没有具体信息，返回 null 让 LLM 处理
                log.info("[IntentRouter] Detected profile_update intent but no personal info found: {}", trimmed);
                span.setDurationMs(System.currentTimeMillis() - startTime);
                traceManager.endSpan(span, "SUCCESS", null);
                return null;
            }

            // 2. 检查是否是查看档案意图
            if (PROFILE_QUERY_PATTERN.matcher(trimmed).find()) {
                log.info("[IntentRouter] Detected profile_query intent: {}", trimmed);
                span.setDurationMs(System.currentTimeMillis() - startTime);
                traceManager.endSpan(span, "SUCCESS", null);
                return new IntentMatch("profile_query", "{}", "用户想查看档案");
            }

            // 3. 检查是否是查看练习数据意图
            if (DASHBOARD_QUERY_PATTERN.matcher(trimmed).find()) {
                log.info("[IntentRouter] Detected dashboard_query intent: {}", trimmed);
                span.setDurationMs(System.currentTimeMillis() - startTime);
                traceManager.endSpan(span, "SUCCESS", null);
                return new IntentMatch("dashboard_query", "{}", "用户想查看练习数据");
            }

            span.setDurationMs(System.currentTimeMillis() - startTime);
            traceManager.endSpan(span, "SUCCESS", null);
            return null;
        } catch (Exception e) {
            span.setDurationMs(System.currentTimeMillis() - startTime);
            traceManager.endSpan(span, "FAILED", e.getMessage());
            return null;
        }
    }

    /**
     * 从用户消息中提取个人信息
     *
     * <p>支持的格式：
     * - "我是程序员，21岁，男" → "程序员，21岁，男性"
     * - "我是一名教师，30岁，女" → "教师，30岁，女性"
     * - "我21岁，是个程序员" → "21岁，程序员"
     */
    private String extractPersonalInfo(String message) {
        StringBuilder info = new StringBuilder();

        // 提取职业
        String occupation = extractOccupation(message);
        if (occupation != null) {
            info.append(occupation);
        }

        // 提取年龄
        String age = extractAge(message);
        if (age != null) {
            if (!info.isEmpty()) info.append("，");
            info.append(age).append("岁");
        }

        // 提取性别
        String gender = extractGender(message);
        if (gender != null) {
            if (!info.isEmpty()) info.append("，");
            info.append(gender);
        }

        return info.isEmpty() ? null : info.toString();
    }

    /**
     * 提取职业
     */
    private String extractOccupation(String message) {
        // "我是程序员" / "我是一名教师" / "我的职业是医生"
        var matcher = Pattern.compile("我是(?:一名?|个)?([^，,。.！!？?\\d]+)").matcher(message);
        if (matcher.find()) {
            String occupation = matcher.group(1).trim();
            // 排除非职业词汇：长度合理，且不是无意义的占位词
            if (!occupation.isEmpty() && occupation.length() <= 10
                && !NON_OCCUPATION_WORDS.contains(occupation)) {
                return occupation;
            }
        }

        var matcher2 = Pattern.compile("我的职业是([^，,。.！!？?]+)").matcher(message);
        if (matcher2.find()) {
            return matcher2.group(1).trim();
        }

        // 直接匹配常见职业
        String[] occupations = {"程序员", "工程师", "教师", "医生", "设计师", "产品经理",
            "学生", "研究生", "大学生", "硕士", "博士", "护士", "律师", "会计"};
        for (String occ : occupations) {
            if (message.contains(occ)) {
                return occ;
            }
        }

        return null;
    }

    /**
     * 提取年龄
     */
    private String extractAge(String message) {
        var matcher = Pattern.compile("(\\d{1,3})\\s*岁").matcher(message);
        if (matcher.find()) {
            int age = Integer.parseInt(matcher.group(1));
            if (age > 0 && age < 150) {
                return String.valueOf(age);
            }
        }

        // "大四" → 约22岁
        if (message.contains("大四")) return "约22";
        if (message.contains("大三")) return "约21";
        if (message.contains("大二")) return "约20";
        if (message.contains("大一")) return "约19";

        return null;
    }

    /**
     * 提取性别
     */
    private String extractGender(String message) {
        if (message.contains("男") && !message.contains("女")) return "男性";
        if (message.contains("女") && !message.contains("男")) return "女性";
        return null;
    }

    /**
     * JSON 转义
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}
