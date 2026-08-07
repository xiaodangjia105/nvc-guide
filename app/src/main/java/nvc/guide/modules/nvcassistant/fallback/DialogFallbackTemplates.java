package nvc.guide.modules.nvcassistant.fallback;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * NVC 对话降级模板库
 *
 * <p>LLM 异常时，返回预设的 NVC 引导话术（不是"服务不可用"的废话）。
 * 按练习步骤分类，每个步骤 6 条模板，共 27 条（含自由对话和场景模式）。
 *
 * <p>选择逻辑：根据当前步骤 + 随机选择，避免重复。
 */
@Component
public class DialogFallbackTemplates {

    private static final Map<String, List<String>> STEP_TEMPLATES = new LinkedHashMap<>();

    static {
        // 观察步骤模板（6 条）
        STEP_TEMPLATES.put("OBSERVE", List.of(
            "让我们先停下来，客观描述一下刚才发生了什么？注意区分事实和评价哦。",
            "能告诉我具体发生了什么吗？尽量只描述你看到和听到的，不加判断。",
            "试着用摄像机回放的方式描述——如果有人录下来，画面里是什么？",
            "你说的'他总是...'，能换成'在这次具体的事件中，他做了什么'吗？",
            "我们先聚焦事实：什么时候、在哪里、发生了什么？",
            "观察是 NVC 的第一步。能用'我看到/听到...'开头重新描述一下吗？"
        ));

        // 感受步骤模板（6 条）
        STEP_TEMPLATES.put("FEELING", List.of(
            "当你经历这些的时候，内心的感受是什么？试着用'我感到...'来表达。",
            "这个 situation 让你产生了什么情绪？开心、失落、焦虑、还是其他？",
            "注意区分感受和想法哦。'我感到被忽视'是想法，'我感到孤独'才是感受。",
            "你能找到一个词来描述此刻的内心状态吗？比如委屈、不安、释然...",
            "身体有没有给你信号？胸口发紧、肩膀僵硬——这些往往是感受的线索。",
            "如果用一个颜色来形容你现在的感受，会是什么？背后的情绪是什么？"
        ));

        // 需求步骤模板（6 条）
        STEP_TEMPLATES.put("NEED", List.of(
            "这个感受背后，你有什么需要没有被满足呢？",
            "NVC 认为所有感受都指向某个需要。你的需要是被尊重、被理解、还是其他？",
            "试着用'我需要...'开头，说出你内心最渴望的东西。",
            "如果对方完全理解了你的感受，你最希望他做什么改变？那个'希望'就是你的需要。",
            "需要是普世的——安全、尊重、连接、自主。你的需要属于哪一类？",
            "有时候愤怒背后是未被满足的需要。你的愤怒在告诉你什么？"
        ));

        // 请求步骤模板（6 条）
        STEP_TEMPLATES.put("REQUEST", List.of(
            "基于你的需要，你能提出一个具体、可执行的请求吗？",
            "好的请求是具体的、正向的、可操作的。'不要这样做'不如'请你那样做'。",
            "如果对方只能说'好'或'不行'，你的请求足够清晰让他做出回应吗？",
            "试着用'你愿意...吗？'的句式，把你的需要转化为一个具体请求。",
            "请求不是命令。你愿意接受对方说'不'吗？如果愿意，这就是一个真正的请求。",
            "你希望对方具体做什么？比如'今晚我们能花 30 分钟聊聊吗？'"
        ));

        // 自由对话降级模板（4 条）
        STEP_TEMPLATES.put("FREE_DIALOG", List.of(
            "我注意到你提到了一些重要的事情。能再多说一些吗？我想更好地理解你。",
            "谢谢你愿意分享。在 NVC 中，我们试着用观察-感受-需求-请求的框架来表达。你想从哪一步开始？",
            "听起来这对你很重要。你能试着描述一下具体发生了什么，以及你当时的感受吗？",
            "我在这里倾听你。如果你愿意，我们可以一起用 NVC 的方式来梳理这件事。"
        ));

        // 场景模式降级模板（3 条）
        STEP_TEMPLATES.put("SCENARIO", List.of(
            "这是一个很好的练习场景。让我们从观察开始——在这个场景中，你看到了什么具体事实？",
            "进入这个场景，你的第一反应是什么？试着区分事实和评价。",
            "这个场景触发了你的什么感受？背后有什么需要？"
        ));
    }

    /** 已使用的模板索引（避免连续重复）- 使用 ConcurrentHashMap 确保线程安全 */
    private final Map<String, Integer> lastUsedIndex = new ConcurrentHashMap<>();

    /**
     * 根据当前步骤选择降级话术
     *
     * @param step 练习步骤（OBSERVE / FEELING / NEED / REQUEST / FREE_DIALOG / SCENARIO）
     * @return 降级话术
     */
    public String selectTemplate(String step) {
        String key = step != null ? step.toUpperCase() : "FREE_DIALOG";
        List<String> templates = STEP_TEMPLATES.getOrDefault(key, STEP_TEMPLATES.get("FREE_DIALOG"));

        // 随机选择，避免与上次相同
        int index;
        do {
            index = ThreadLocalRandom.current().nextInt(templates.size());
        } while (templates.size() > 1 && index == lastUsedIndex.getOrDefault(key, -1));

        lastUsedIndex.put(key, index);
        return templates.get(index);
    }

    /**
     * 获取所有可用的步骤
     */
    public Set<String> getAvailableSteps() {
        return STEP_TEMPLATES.keySet();
    }
}
