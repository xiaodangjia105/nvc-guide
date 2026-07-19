# Phase 1.1 RAG 知识库集成设计文档

> 创建时间：2026-07-18
> 方案：方案 B — 完整 Phase 1.1（个性化权重 + 前端筛选）
> 预估：1.5 天

---

## 一、背景与目标

### 现状

Phase 0.5 已完成 `profileSummary` 注入，但 `ragContext` 仍是空的。Agent 对话无法引用 NVC 知识。

**已完成的基础设施**：
- `NvcRagService` — `retrieve()` + `retrievePersonalized()` 已实现
- `KnowledgeBaseType` 枚举 — 5 个类型已定义
- `KnowledgeBaseEntity.type` 字段已存在
- `KnowledgeBaseRepository.findByTypeInOrderByUploadedAtDesc()` 已有
- `KnowledgeBaseVectorService` — 向量检索 + 分块 + 存储完整
- `EmbeddingService` — DashScope embedding 已接入
- `NvcAgentChatService.buildMessages()` — `[参考资料]` 注入点已预留

**核心差距**：
1. `NvcAgentOrchestrator.buildPracticeContext()` 仅在场景驱动模式下触发 RAG
2. 没有 NVC 知识文档可检索
3. 个性化检索未接入

### 目标

1. 让 Agent 对话在所有练习模式下都能引用 NVC 知识
2. 基于用户薄弱要素提供个性化知识推荐
3. 提供前端知识库类型筛选管理

---

## 二、NVC 知识文档结构

### 目录结构

```
app/src/main/resources/knowledge/
├── theory/                          # NVC 理论（5 份）
│   ├── 01-观察与评价的区别.md
│   ├── 02-感受的识别与表达.md
│   ├── 03-需求的探索与理解.md
│   ├── 04-请求的表达与沟通.md
│   └── 05-NVC四要素综合运用.md
├── vocabulary/                      # 情绪与需求词汇（3 份）
│   ├── 06-正面感受词汇库.md
│   ├── 07-负面感受词汇库.md
│   └── 08-需求词汇库.md
├── templates/                       # 话术模板（4 份）
│   ├── 09-职场沟通话术.md
│   ├── 10-家庭关系话术.md
│   ├── 11-亲密关系话术.md
│   └── 12-社交场景话术.md
└── cases/                           # 案例库（3 份）
    ├── 13-成功案例集.md
    ├── 14-失败案例对比.md
    └── 15-常见场景改写.md
```

### 文档格式

```markdown
# 标题

## 概述
简要说明本文档主题（50-100 字）

## 核心内容
详细知识点，使用具体例子说明

## 实践要点
- 要点 1
- 要点 2
- 要点 3

## 常见误区
❌ 错误示例
✅ 正确示例
```

### 文档规格

- 每份文档：800-1500 字
- RAG 分块后约 1-2 个 chunk/文档（TokenTextSplitter ~800 tokens/chunk）
- 内容来源：AI 生成 + 人工审核

### 文件夹 → 知识库类型映射

| 文件夹 | KnowledgeBaseType | 说明 |
|--------|-------------------|------|
| theory/ | NVC_THEORY | NVC 理论知识 |
| vocabulary/ | EMOTION_VOCAB | 情绪与需求词汇 |
| templates/ | SPEECH_TEMPLATE | 话术模板 |
| cases/ | USER_CASE | 案例库 |

---

## 三、全模式 RAG 触发改造

### 修改文件

`NvcAgentOrchestrator.java` — `buildPracticeContext()` 方法

### 当前逻辑（第 134-141 行）

```java
String ragContext = null;
if (scenarioDescription != null) {
    List<RagResult> ragResults = ragService.retrieve(...);
    ragContext = ragService.formatForPrompt(ragResults);
}
```

仅在有场景描述时触发 RAG，自由对话和结构化四步模式不检索。

### 改造后逻辑

```java
String ragContext = null;
String ragQuery = buildRagQuery(session, scenarioDescription, recentMessages);
if (ragQuery != null) {
    List<RagResult> ragResults = ragService.retrieve(
        ragQuery,
        List.of(KnowledgeBaseType.NVC_THEORY,
                KnowledgeBaseType.SPEECH_TEMPLATE,
                KnowledgeBaseType.EMOTION_VOCAB),
        3);
    ragContext = ragService.formatForPrompt(ragResults);
}
```

### buildRagQuery() 策略

```java
private String buildRagQuery(
    NvcPracticeSessionEntity session,
    String scenarioDescription,
    List<NvcPracticeMessageEntity> recentMessages) {

    // 优先级 1：场景描述（场景驱动模式）
    if (scenarioDescription != null) {
        return scenarioDescription;
    }

    // 优先级 2：用户最新消息（自由对话模式）
    if (recentMessages != null && !recentMessages.isEmpty()) {
        NvcPracticeMessageEntity lastUserMsg = recentMessages.stream()
            .filter(m -> m.getRole() == NvcMessageRole.USER)
            .reduce((a, b) -> b)
            .orElse(null);
        if (lastUserMsg != null) {
            return lastUserMsg.getContent();
        }
    }

    // 优先级 3：练习模式 + 当前步骤（结构化四步模式）
    NvcPracticeMode mode = session.getPracticeMode();
    if (mode == NvcPracticeMode.STRUCTURED_FOUR_STEP
        && session.getCurrentStep() != null) {
        return "NVC " + session.getCurrentStep().name() + " 练习";
    }

    return null;
}
```

### 触发逻辑总结

| 练习模式 | RAG 查询来源 | 知识类型 |
|----------|-------------|---------|
| 场景驱动 | 场景描述 | THEORY + SPEECH_TEMPLATE |
| 自由对话 | 用户最新消息 | THEORY + SPEECH_TEMPLATE + EMOTION_VOCAB |
| 结构化四步 | 步骤名称（如 "NVC OBSERVATION 练习"） | THEORY + SPEECH_TEMPLATE |

---

## 四、个性化检索增强

### 设计思路

根据用户档案中最低分的 NVC 要素，在 RAG 检索时对该要素相关的知识进行加权排序。

### 实现

在 `NvcAgentOrchestrator` 中增加个性化检索逻辑：

```java
// buildPracticeContext() 中
String weakKeyword = findWeakElementKeyword(profile);

List<RagResult> ragResults;
if (weakKeyword != null) {
    ragResults = ragService.retrievePersonalized(ragQuery, weakKeyword, 3);
} else {
    ragResults = ragService.retrieve(ragQuery, knowledgeTypes, 3);
}
```

### findWeakElementKeyword()

```java
private String findWeakElementKeyword(NvcUserProfileEntity profile) {
    if (profile == null) return null;

    Map<String, Integer> scores = Map.of(
        "observation", profile.getObservationScore() != null ? profile.getObservationScore() : 50,
        "feeling", profile.getFeelingScore() != null ? profile.getFeelingScore() : 50,
        "need", profile.getNeedScore() != null ? profile.getNeedScore() : 50,
        "request", profile.getRequestScore() != null ? profile.getRequestScore() : 50
    );

    return scores.entrySet().stream()
        .min(Map.Entry.comparingByValue())
        .map(Map.Entry::getKey)
        .orElse(null);
}
```

### 加权机制

`NvcRagService.retrievePersonalized()` 已实现：
- 基础检索：多取 2 倍候选结果
- 加权排序：包含薄弱要素关键词的结果 ×1.5 权重
- 截取 topK 返回

---

## 五、种子数据服务

### 设计

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class SeedKnowledgeBaseService {

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeBaseVectorService vectorService;

    private static final Map<String, KnowledgeBaseType> FOLDER_TO_TYPE = Map.of(
        "theory",     KnowledgeBaseType.NVC_THEORY,
        "vocabulary", KnowledgeBaseType.EMOTION_VOCAB,
        "templates",  KnowledgeBaseType.SPEECH_TEMPLATE,
        "cases",      KnowledgeBaseType.USER_CASE
    );

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void checkAndSeed() {
        long nvcCount = kbRepository.countByTypeIn(
            List.of(KnowledgeBaseType.NVC_THEORY,
                    KnowledgeBaseType.SPEECH_TEMPLATE,
                    KnowledgeBaseType.EMOTION_VOCAB,
                    KnowledgeBaseType.USER_CASE));

        if (nvcCount > 0) {
            log.info("NVC knowledge base already seeded ({} docs), skipping", nvcCount);
            return;
        }

        log.info("Seeding NVC knowledge base...");
        // 扫描 resources/knowledge/ 目录
        // 对每个 .md 文件：
        //   1. 计算 fileHash（SHA-256）
        //   2. 创建 KnowledgeBaseEntity（设置 type）
        //   3. 异步调用 vectorService.vectorizeAndStore()
    }
}
```

### 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 触发方式 | `@EventListener(ApplicationReadyEvent.class)` | 应用启动自动执行，无需手动操作 |
| 幂等性 | 通过 `fileHash` 去重 | 重复启动不会重复创建 |
| 向量化 | 异步执行（`@Async`） | 避免阻塞启动，向量化是耗时操作 |
| type 设置 | 按文件夹自动映射 | 无需手动设置类型 |

---

## 六、前端知识库类型筛选

### 修改文件

`KnowledgeBaseManagePage.tsx`

### 改动点

1. 增加类型下拉筛选器（NVC_THEORY / SPEECH_TEMPLATE / EMOTION_VOCAB / USER_CASE / 全部）
2. 列表项显示类型标签
3. 调用 `GET /api/knowledgebase/type/{type}` 筛选

### UI 变化

```
┌─────────────────────────────────────────────────┐
│  知识库管理                    [类型筛选 ▼] [上传] │
│  ┌─────────────────────────────────────────────┐ │
│  │ 📄 NVC理论基础.md    [理论]  ✅ 已向量化     │ │
│  │ 📄 职场沟通话术.md   [话术]  ✅ 已向量化     │ │
│  │ 📄 情绪词汇库.md     [词汇]  ⏳ 向量化中     │ │
│  └─────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────┘
```

### 改动量

~50 行代码，主要是增加一个 `<select>` 和类型标签显示。

---

## 七、完整改动清单

### 新建文件（16 个）

```
# NVC 知识文档（15 份）
app/src/main/resources/knowledge/theory/01-观察与评价的区别.md
app/src/main/resources/knowledge/theory/02-感受的识别与表达.md
app/src/main/resources/knowledge/theory/03-需求的探索与理解.md
app/src/main/resources/knowledge/theory/04-请求的表达与沟通.md
app/src/main/resources/knowledge/theory/05-NVC四要素综合运用.md
app/src/main/resources/knowledge/vocabulary/06-正面感受词汇库.md
app/src/main/resources/knowledge/vocabulary/07-负面感受词汇库.md
app/src/main/resources/knowledge/vocabulary/08-需求词汇库.md
app/src/main/resources/knowledge/templates/09-职场沟通话术.md
app/src/main/resources/knowledge/templates/10-家庭关系话术.md
app/src/main/resources/knowledge/templates/11-亲密关系话术.md
app/src/main/resources/knowledge/templates/12-社交场景话术.md
app/src/main/resources/knowledge/cases/13-成功案例集.md
app/src/main/resources/knowledge/cases/14-失败案例对比.md
app/src/main/resources/knowledge/cases/15-常见场景改写.md

# 种子数据服务
app/src/main/java/nvc/guide/modules/knowledgebase/service/SeedKnowledgeBaseService.java
```

### 修改文件（3 个）

```
app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcAgentOrchestrator.java
    - buildPracticeContext() 全模式 RAG 触发
    - buildRagQuery() 按模式构建查询
    - findWeakElementKeyword() 薄弱要素提取

frontend/src/pages/KnowledgeBaseManagePage.tsx
    - 类型下拉筛选器
    - 类型标签显示

app/src/main/java/nvc/guide/modules/knowledgebase/KnowledgeBaseController.java
    - （可选）上传时支持 type 参数
```

---

## 八、验收标准

```
□ NVC 知识文档
  □ 15 份文档已创建，内容准确
  □ 文档按 4 个文件夹分类
  □ 每份 800-1500 字，格式统一

□ 种子数据
  □ SeedKnowledgeBaseService 启动时自动执行
  □ 15 份文档全部注册到 knowledge_bases 表
  □ type 字段正确设置
  □ 向量化完成（vectorStatus=COMPLETED）
  □ 重复启动不重复创建（幂等）

□ RAG 触发
  □ 场景驱动模式：基于场景描述检索 ✅（已有）
  □ 自由对话模式：基于用户最新消息检索 ✅（新增）
  □ 结构化四步模式：基于当前步骤检索 ✅（新增）

□ 个性化检索
  □ 基于用户薄弱要素加权排序
  □ 薄弱要素相关的知识优先展示

□ 前端
  □ 知识库管理页面增加类型筛选下拉
  □ 列表显示类型标签

□ 端到端验证
  □ 创建练习会话 → Agent 回复能引用 NVC 知识
  □ 自由对话模式也能引用知识
  □ 不同用户获得个性化知识推荐
```

---

## 九、风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| AI 生成的知识内容不准确 | RAG 引用错误知识 | 人工审核 + 标记来源文档 |
| 向量化失败 | 知识无法被检索 | 种子服务记录失败状态，支持重试 |
| RAG 检索结果噪音大 | Agent 回复质量下降 | minScore 阈值 0.3 过滤低质量结果 |
| 个性化加权效果不明显 | 用户体验无差异 | 后续可升级为语义权重（基于 embedding 距离） |
