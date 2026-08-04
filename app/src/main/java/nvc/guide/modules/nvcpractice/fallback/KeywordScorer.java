package nvc.guide.modules.nvcpractice.fallback;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 四要素关键词评分器
 *
 * <p>LLM 评估异常时，用关键词匹配给出粗略评分。
 * 每个要素基础分 5 分，根据正向/负向关键词加减分，最终 1-10 分。
 *
 * <p>评分规则：
 * <ul>
 *   <li>基础 5 分</li>
 *   <li>每个正向关键词 +0.5（上限 +3）</li>
 *   <li>每个负向关键词 -0.8（下限 -4）</li>
 *   <li>最终分数 clamp 到 1-10</li>
 * </ul>
 */
@Component
public class KeywordScorer {

    // ===== 观察维度 =====
    private static final List<String> OBSERVATION_POSITIVE = List.of(
        "看到", "听到", "注意到", "发现", "观察到", "记录到",
        "在...时候", "当...的时候", "第一次", "第二次", "那天",
        "具体来说", "实际上", "数据显示"
    );
    private static final List<String> OBSERVATION_NEGATIVE = List.of(
        "总是", "从来", "每次", "永远", "根本", "简直",
        "应该", "必须", "一定", "当然",
        "自私", "懒", "不负责任", "不尊重", "不关心",
        "太...了", "那么...", "这么..."
    );

    // ===== 感受维度 =====
    private static final List<String> FEELING_POSITIVE = List.of(
        "感到", "觉得", "感觉", "内心",
        "开心", "高兴", "感激", "温暖", "安心", "兴奋", "满足", "欣慰", "感动", "放心", "愉快",
        "失落", "焦虑", "委屈", "疲惫", "孤独", "沮丧", "不安", "愤怒", "失望", "困惑",
        "紧张", "害怕", "担心", "难过", "伤心", "痛苦", "无奈", "无力"
    );
    private static final List<String> FEELING_NEGATIVE = List.of(
        "我觉得", "我认为", "我想",
        "被忽视", "被抛弃", "被控制", "被误解",
        "不公平", "不合理", "不应该",
        "他让我", "她让我", "你让我"
    );

    // ===== 需求维度 =====
    private static final List<String> NEED_POSITIVE = List.of(
        "需要", "希望", "想要", "渴望", "期待", "重视",
        "被尊重", "被理解", "被认可", "被关心", "被接纳",
        "安全感", "归属感", "自主", "自由", "成长", "连接",
        "诚实", "信任", "公平", "平等", "和谐"
    );
    private static final List<String> NEED_NEGATIVE = List.of(
        "你必须", "你应该", "你得",
        "不要", "别再", "停止",
        "因为你", "都怪你", "都是你"
    );

    // ===== 请求维度 =====
    private static final List<String> REQUEST_POSITIVE = List.of(
        "能不能", "可以", "请你", "你愿意", "是否可以",
        "我希望你", "我请求", "请", "麻烦",
        "具体来说", "比如", "比如说",
        "今天", "明天", "以后", "每次"
    );
    private static final List<String> REQUEST_NEGATIVE = List.of(
        "不要", "别", "停止", "不许",
        "永远", "一直", "每次都要",
        "你应该知道", "你心里清楚"
    );

    /**
     * 对单条消息进行四要素评分
     *
     * @param userMessage 用户消息
     * @param currentStep 当前步骤（用于权重调整，可为 null）
     * @return 各维度评分（1-10）
     */
    public Map<String, Double> score(String userMessage, String currentStep) {
        if (userMessage == null || userMessage.isBlank()) {
            return Map.of("observation", 5.0, "feeling", 5.0, "need", 5.0, "request", 5.0);
        }

        double observation = scoreDimension(userMessage, OBSERVATION_POSITIVE, OBSERVATION_NEGATIVE);
        double feeling = scoreDimension(userMessage, FEELING_POSITIVE, FEELING_NEGATIVE);
        double need = scoreDimension(userMessage, NEED_POSITIVE, NEED_NEGATIVE);
        double request = scoreDimension(userMessage, REQUEST_POSITIVE, REQUEST_NEGATIVE);

        return Map.of(
            "observation", clamp(observation),
            "feeling", clamp(feeling),
            "need", clamp(need),
            "request", clamp(request)
        );
    }

    /**
     * 单维度评分
     */
    private double scoreDimension(String text, List<String> positive, List<String> negative) {
        double score = 5.0;

        // 正向关键词 +0.5（上限 +3）
        long positiveCount = positive.stream().filter(text::contains).count();
        score += Math.min(positiveCount * 0.5, 3.0);

        // 负向关键词 -0.8（下限 -4）
        long negativeCount = negative.stream().filter(text::contains).count();
        score -= Math.min(negativeCount * 0.8, 4.0);

        return score;
    }

    /**
     * 钳制分数到 1-10
     */
    private double clamp(double score) {
        return Math.max(1, Math.min(10, Math.round(score * 10) / 10.0));
    }
}
