# Step 10：RAG 知识库与个性化检索

> 目标：利用已有的 knowledgebase 模块，上传 NVC 知识文档，实现个性化 RAG 检索
>
> 预计耗时：2-3 天
>
> 前置条件：Step 5（用户档案）已完成，knowledgebase 模块可正常使用
>
> 技术亮点：pgvector 向量搜索、档案向量化、多路 RAG 检索

---

## 10.1 改造思路

knowledgebase 模块**基本不改**，核心工作是：
1. 准备 NVC 知识库内容并上传
2. 在对话流程中集成 RAG 检索
3. 实现基于用户档案的个性化检索

---

## 10.2 NVC 知识库内容准备

### 需要准备的文档

#### 知识库 1：NVC 理论基础

文件名：`NVC理论基础.md`

```markdown
# 非暴力沟通（NVC）理论基础

## 什么是非暴力沟通

非暴力沟通（Nonviolent Communication，简称NVC）是由马歇尔·卢森堡（Marshall Rosenberg）博士提出的一种沟通方式。
它强调通过观察、感受、需求和请求四个要素来表达自己，同时倾听他人。

## NVC 四要素

### 1. 观察（Observation）
观察是指客观描述你看到、听到、触摸到的具体事实，不加入个人的解释、判断或评价。

关键区分：
- 观察：描述具体发生的事情
  - ✅ "你这周有三天都是晚上10点以后回家"
  - ✅ "在昨天的会议上，你打断了我三次"
- 评论：加入个人判断
  - ❌ "你总是不着家"
  - ❌ "你根本不尊重我"

常见评判词汇识别：
- "总是"、"从来"、"每次" → 以偏概全
- "应该"、"必须" → 强加期望
- "自私"、"懒"、"不负责任" → 人身标签
- "永远"、"从不" → 绝对化

### 2. 感受（Feeling）
感受是指你内心的情绪状态，而不是对他人的想法或判断。

关键区分：
- 感受：描述内心的情绪
  - ✅ "我感到失落"
  - ✅ "我很担心"
  - ✅ "我有些委屈"
- 想法：对他人行为的解读
  - ❌ "我觉得你不尊重我"
  - ❌ "我觉得自己没用"

常见误区：
- "我觉得..." → 往往是想法
- "我感到被忽视" → "被忽视"是想法，"感到孤独"才是感受
- "我感到不公平" → "不公平"是判断，"感到委屈"才是感受

感受词汇表：
- 需求被满足时：开心、感激、温暖、安心、兴奋、满足、欣慰、感动、放心、愉快
- 需求未被满足时：失落、焦虑、委屈、疲惫、孤独、沮丧、不安、愤怒、失望、困惑

### 3. 需求（Need）
需求是指人类共通的基本需要，是感受背后的深层原因。

关键区分：
- 需求：普适的人类需要
  - 安全感、尊重、理解、归属感、自主性、成长、陪伴、认可、公平、自由、诚实、意义
- 策略：满足需求的具体方式
  - "你每天陪我吃饭" → 这是策略，不是需求
  - 需求是"陪伴"，策略可以有很多种

从感受追溯需求：
- 感到失落 → 可能需要被认可、被看见
- 感到焦虑 → 可能需要安全感、确定性
- 感到愤怒 → 可能需要公平、尊重
- 感到孤独 → 可能需要陪伴、连接
- 感到疲惫 → 可能需要休息、支持

### 4. 请求（Request）
请求是指将需求转化为具体、可执行、正向的表达。

请求 vs 命令：
- 请求允许对方说"不"，并愿意协商
- 命令不允许拒绝，对方必须服从

好的请求特点：
- 正向表达：说你想要什么，而不是不想要什么
  - ✅ "你能每周六陪我散步吗？"
  - ❌ "你不要再加班了"
- 具体可行：越具体越好
  - ✅ "你能在下次汇报时提到我负责的部分吗？"
  - ❌ "你要尊重我的付出"
- 允许拒绝：给对方选择的空间
  - ✅ "你愿意...吗？"
  - ❌ "你应该..."

请求公式：
- "你愿意 + 具体行动 + 吗？"
- "我希望 + 具体行为 + 你觉得可以吗？"

## NVC 的四个耳朵

当我们听到他人的表达时，可以用四个"耳朵"来倾听：
1. 观察：对方描述了什么事实？
2. 感受：对方的感受是什么？
3. 需求：对方的需求是什么？
4. 请求：对方的请求是什么？

## NVC 与传统沟通方式的区别

| 传统沟通 | NVC 沟通 |
|---------|---------|
| "你总是迟到！" | "这周你有三次9点以后到，我感到焦虑，因为我需要确定性。你能提前告诉我如果会晚到吗？" |
| "你怎么这么自私！" | "我注意到你没有问我的意见就做了决定。我感到失落，因为我需要被尊重。下次你能先和我商量吗？" |
| "你根本不在乎我！" | "我生病那天你没有来看我。我感到孤独，因为我需要关心和陪伴。你能在我生病时陪我去医院吗？" |
```

#### 知识库 2：NVC 话术模板

文件名：`NVC话术模板.md`

```markdown
# NVC 话术模板

## 职场场景话术

### 同事抢功
观察：我注意到在上周的项目汇报中，你提到了三个技术方案，但没有提及这些方案是我设计和实现的。
感受：我感到有些失落和不公平。
需求：我需要我的工作得到认可和尊重。
请求：你能在下次汇报时，各自介绍自己负责的部分吗？

### 被领导当众批评
观察：在今天的团队会议上，你对我的方案提出了比较严厉的批评。
感受：我感到有些难堪和沮丧。
需求：我需要被尊重，也希望得到建设性的反馈。
请求：以后如果对我的方案有意见，你能在会后私下和我沟通吗？

### 同事拖延影响你
观察：这周你负责的接口延迟了三天交付。
感受：我感到焦虑和被动，因为我的工作计划被打乱了。
需求：我需要可预测性和协作效率。
请求：如果预计会延迟，你能提前一天告诉我吗？这样我可以调整计划。

## 家庭场景话术

### 父母催婚
观察：这次回家，你提了五次找对象的事，还安排了相亲。
感受：我感到压力很大，也有些委屈。
需求：我需要被理解和尊重我的生活节奏。
请求：你能给我一些空间，让我按自己的节奏来处理这件事吗？

### 消费观冲突
观察：我给自己买了一件2000块的大衣。
感受：我知道你是担心我乱花钱，但我感到被误解。
需求：我需要被信任，也希望你能理解我的消费选择。
请求：我们能坐下来聊聊各自对消费的看法吗？

## 亲密关系话术

### 伴侣忽视感受
观察：今天我想和你聊聊工作上的事，你在看手机，只是"嗯"了几声。
感受：我感到被忽视和孤独。
需求：我需要被倾听和理解。
请求：你能放下手机，花10分钟听我说说吗？

### 家务分配
观察：这周的晚饭都是我做的，碗也基本是我在洗。
感受：我感到疲惫，也有些不公平。
需求：我需要分担和支持。
请求：我们能商量一下家务分工吗？比如你负责洗碗，我负责做饭？
```

#### 知识库 3：情绪与需求词汇库

文件名：`情绪与需求词汇库.md`

```markdown
# 情绪与需求词汇库

## 感受词汇表

### 需求被满足时的感受
- 开心、高兴、愉快、欣喜
- 感激、感谢、感恩
- 温暖、温馨、感动
- 安心、放心、踏实
- 兴奋、激动、振奋
- 满足、满意、充实
- 欣慰、释然、轻松
- 自豪、骄傲、自信
- 喜爱、热爱、珍惜
- 平静、宁静、安详

### 需求未被满足时的感受
- 失落、失望、沮丧
- 焦虑、紧张、不安
- 委屈、冤枉、不甘
- 疲惫、疲倦、劳累
- 孤独、寂寞、孤立
- 愤怒、恼火、烦躁
- 困惑、迷茫、疑惑
- 伤心、悲伤、难过
- 尴尬、难堪、窘迫
- 害怕、恐惧、担忧
- 嫉妒、羡慕、眼红
- 无奈、无力、无助
- 厌烦、反感、排斥
- 羞耻、羞愧、惭愧

## 需求词汇表

### 基本需求
- 安全感：安全、稳定、保护、健康
- 尊重：被尊重、被重视、被认可
- 理解：被理解、被接纳、被包容
- 归属感：归属、连接、陪伴、亲密
- 自主性：自由、选择、独立、空间
- 成长：学习、进步、挑战、发展
- 意义：价值、目的、贡献、意义
- 诚实：真实、坦诚、信任、可靠
- 公平：公正、平等、合理、正义
- 创造：创造、表达、想象、美

### 关系需求
- 爱：被爱、关爱、温情、呵护
- 陪伴：在场、共处、分享、交流
- 支持：鼓励、帮助、依靠、后盾
- 信任：信赖、放心、托付、依靠
- 倾听：被听见、被关注、被在意
```

---

## 10.3 上传知识库文档

通过已有的知识库 API 上传文档：

```
1. 创建知识库：
   POST /api/knowledge-bases
   { "name": "NVC 理论基础", "description": "非暴力沟通核心理论" }

2. 上传文档：
   POST /api/knowledge-bases/{id}/documents
   上传 NVC理论基础.md、NVC话术模板.md、情绪与需求词汇库.md

3. 等待向量化完成（异步任务）
```

---

## 10.4 个性化 RAG 检索集成

### 修改 NvcContextBuilder

在 `NvcContextBuilder.java` 中，添加 RAG 检索逻辑：

```java
// 新增依赖
private final KnowledgeBaseQueryService knowledgeBaseQueryService;
private final NvcProfileService profileService;

/**
 * 构建练习上下文（增强版，含 RAG 检索）
 */
public PracticeContext build(NvcPracticeSessionEntity session) {
    // ... 原有逻辑 ...

    // 新增：RAG 个性化检索
    String ragContext = null;
    if (session.getUserId() != null) {
        ragContext = retrievePersonalizedContext(session);
    }

    return PracticeContext.builder()
        // ... 原有字段 ...
        .ragContext(ragContext)
        .build();
}

/**
 * 个性化 RAG 检索
 * 根据用户档案和当前对话上下文，检索最相关的 NVC 知识
 */
private String retrievePersonalizedContext(NvcPracticeSessionEntity session) {
    try {
        NvcUserProfileEntity profile = profileService.getOrCreateProfile(session.getUserId());

        // 构建检索查询：结合用户薄弱要素 + 当前场景
        StringBuilder query = new StringBuilder();

        // 1. 根据用户薄弱要素检索
        AbilityRadarDTO radar = profileService.getAbilityRadar(session.getUserId());
        if (radar != null) {
            String weakest = findWeakestElement(radar);
            if (weakest != null) {
                query.append(weakest).append(" NVC 表达方法 ");
            }
        }

        // 2. 根据场景类型检索
        if (session.getScenarioId() != null) {
            NvcScenarioEntity scenario = scenarioRepository.findById(session.getScenarioId()).orElse(null);
            if (scenario != null) {
                query.append(scenario.getScenarioType().name()).append(" 场景 NVC 话术 ");
            }
        }

        // 3. 执行 RAG 检索
        if (query.length() > 0) {
            String searchQuery = query.toString().trim();
            // 使用已有的知识库查询服务
            // 注意：这里需要适配现有的 RAG 查询接口
            return knowledgeBaseQueryService.queryForContext(searchQuery);
        }

    } catch (Exception e) {
        log.warn("Failed to retrieve personalized RAG context", e);
    }

    return null;
}

private String findWeakestElement(AbilityRadarDTO radar) {
    int minScore = Integer.MAX_VALUE;
    String weakest = null;

    if (radar.observation() < minScore) { minScore = radar.observation(); weakest = "观察"; }
    if (radar.feeling() < minScore) { minScore = radar.feeling(); weakest = "感受"; }
    if (radar.need() < minScore) { minScore = radar.need(); weakest = "需求"; }
    if (radar.request() < minScore) { minScore = radar.request(); weakest = "请求"; }

    return weakest;
}
```

---

## 10.5 NvcKnowledgeAdvisor 对话

当用户在练习过程中询问 NVC 理论问题时，自动切换到知识顾问 Agent：

在 `NvcAgentOrchestrator` 中添加识别逻辑：

```java
/**
 * 判断用户消息是否是知识问题
 * 如果是，返回 NVC_KNOWLEDGE_ADVISOR
 */
private boolean isKnowledgeQuestion(String userMessage) {
    // 简单的关键词匹配
    String[] keywords = {"什么是", "怎么理解", "区别", "解释", "理论", "NVC", "非暴力沟通"};
    for (String keyword : keywords) {
        if (userMessage.contains(keyword)) {
            return true;
        }
    }
    return false;
}
```

在 `decideFreeDialog()` 方法开头添加：

```java
// 如果用户在问知识问题，切换到知识顾问
if (isKnowledgeQuestion(context.getRecentMessages().get(
        context.getRecentMessages().size() - 1).getContent())) {
    return new AgentDecision(
        NvcAgentScene.NVC_KNOWLEDGE_ADVISOR,
        "用户询问NVC知识问题",
        "KNOWLEDGE_QUERY"
    );
}
```

---

## 10.6 验证清单

```
□ NVC 知识库文档上传成功
□ 文档向量化完成（查询 pgvector 确认有向量数据）
□ RAG 检索测试：
  □ 查询"观察和评论的区别"返回相关内容
  □ 查询"职场 NVC 话术"返回相关模板
  □ 查询"感受词汇"返回词汇表
□ 个性化检索验证：
  □ 用户薄弱要素为"请求"时，检索到更多请求相关的知识
  □ 场景类型为"职场"时，检索到职场相关话术
□ 对话集成验证：
  □ 对话中 Agent 能引用 RAG 检索到的知识
  □ 用户问"什么是观察"时，知识顾问能基于知识库回答
□ Git 提交："Step 10: Integrate RAG knowledge base with personalized retrieval"
```
