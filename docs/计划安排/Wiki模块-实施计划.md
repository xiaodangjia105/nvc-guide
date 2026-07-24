# Wiki 模块 — 详细实施计划

> 基于 4 个并行探索 Agent 的代码分析，结合已确认的 5 个设计决策。

---

## 一、设计决策回顾

| # | 决策 | 结论 |
|---|------|------|
| 1 | 存储 | 两层：DB TEXT + pgvector，复用 `knowledge_base` 表 |
| 2 | 分类 | 5 个：`CONVERSATION_CASE / REAL_SCENARIO / LEARNING_SUMMARY / BOOK_KNOWLEDGE / OTHER` |
| 3 | 向量化 | 复用 `knowledge_base`，`type=PERSONAL_WIKI`，`userId` 隔离 |
| 4 | 自动生成 | 默认关闭，练习结束时询问，用户确认后异步生成 |
| 5 | 原文展示 | 元数据（标题、日期、分类）+ 内容内联展示，不跳转 |

---

## 二、现有基建分析

### 已有（可直接复用）

| 基建 | 位置 | 说明 |
|------|------|------|
| `KnowledgeBaseType.PERSONAL_WIKI` | `knowledgebase/model/KnowledgeBaseType.java` | 枚举已存在，注释"按用户隔离" |
| `WikiWriteTool`（桩） | `nvcpractice/tool/WikiWriteTool.java` | 已存在，返回"功能暂未开放" |
| `WikiSearchTool`（桩） | `nvcpractice/tool/WikiSearchTool.java` | 已存在，返回"功能暂未开放" |
| `NvcToolSceneMapping` | `nvcpractice/tool/NvcToolSceneMapping.java` | `NVC_KNOWLEDGE_ADVISOR` 已绑定 `wiki_search` |
| Redis Stream 基建 | `common/async/AbstractStreamProducer/Consumer` | Producer/Consumer 模式成熟 |
| 向量化链路 | `knowledgebase/service/KnowledgeBaseVectorService.java` | 分块 + 嵌入 + pgvector 存储 |
| RAG 检索 | `nvcpractice/service/NvcRagService.java` | `retrieve()` + `retrievePersonalized()` |
| 前端 Markdown 渲染 | `react-markdown` + `remark-gfm` | 已有渲染能力 |

### 需要新增/修改

| 缺失 | 说明 |
|------|------|
| `KnowledgeBaseEntity` 无 `userId` 字段 | 当前所有知识库都是全局的，无法按用户隔离 |
| `KnowledgeBaseRepository` 无 userId 查询 | 需要新增 `findByTypeAndUserId` 等方法 |
| `NvcRagService.retrieve()` 无 userId 参数 | PERSONAL_WIKI 检索时无法过滤用户 |
| Wiki 自动生成 Prompt | 需要新建 `nvc-wiki-auto-generate.st` |
| Wiki 自动生成 Service | 需要新建 `NvcWikiAutoGenerateService` |
| 用户偏好开关 | `NvcUserProfileEntity` 无 preferences 字段 |
| 前端 Markdown 编辑器 | 项目中只有渲染器，没有编辑器 |
| 前端 Wiki 页面 | 全新页面 + 组件 |
| Wiki 错误码 | 需要在 `ErrorCode` 中新增 |

---

## 三、实施步骤（分 4 个阶段）

### 阶段 1：后端基建改造（knowledge_base 表扩展）

**目标**：让 `knowledge_base` 表支持用户隔离，为 Wiki 模块打基础。

#### 1.1 KnowledgeBaseEntity 增加 userId 字段

**文件**: `modules/knowledgebase/model/KnowledgeBaseEntity.java`

```java
@Column(name = "user_id")
private Long userId;  // null = 系统知识库，非 null = 用户个人 Wiki
```

**影响分析**：
- 现有系统知识库的 `userId` 全为 null，不受影响
- PERSONAL_WIKI 类型的记录必须有 `userId`
- 需要加联合索引 `idx_kb_type_user(type, userId)`

#### 1.2 KnowledgeBaseRepository 新增查询方法

**文件**: `modules/knowledgebase/repository/KnowledgeBaseRepository.java`

```java
// 按类型 + 用户查询（PERSONAL_WIKI 专用）
List<KnowledgeBaseEntity> findByTypeAndUserIdOrderByUploadedAtDesc(
    KnowledgeBaseType type, Long userId);

// 按类型 + 用户 + 关键词搜索
@Query("SELECT k FROM KnowledgeBaseEntity k WHERE k.type = :type AND k.userId = :userId " +
       "AND (LOWER(k.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
       "OR LOWER(k.originalFilename) LIKE LOWER(CONCAT('%', :keyword, '%')))")
List<KnowledgeBaseEntity> searchByTypeAndUserIdAndKeyword(
    @Param("type") KnowledgeBaseType type,
    @Param("userId") Long userId,
    @Param("keyword") String keyword);

// 统计用户 Wiki 数量
long countByTypeAndUserId(KnowledgeBaseType type, Long userId);
```

#### 1.3 NvcRagService 增加 userId 参数

**文件**: `nvcpractice/service/NvcRagService.java`

```java
// 新增重载方法
public List<RagResult> retrieve(String query, List<KnowledgeBaseType> knowledgeTypes,
                                 int topK, Long userId) {
    List<Long> kbIds;
    if (userId != null) {
        // 用户个人 Wiki：按 userId 过滤
        kbIds = knowledgeBaseRepository.findByTypeAndUserIdOrderByUploadedAtDesc(
            KnowledgeBaseType.PERSONAL_WIKI, userId)
            .stream().map(KnowledgeBaseEntity::getId).toList();
        // 合并系统知识库 ID
        List<KnowledgeBaseType> systemTypes = knowledgeTypes.stream()
            .filter(t -> t != KnowledgeBaseType.PERSONAL_WIKI).toList();
        if (!systemTypes.isEmpty()) {
            kbIds = new ArrayList<>(kbIds);
            kbIds.addAll(knowledgeBaseRepository.findByTypeInOrderByUploadedAtDesc(systemTypes)
                .stream().map(KnowledgeBaseEntity::getId).toList());
        }
    } else {
        kbIds = knowledgeBaseRepository.findByTypeInOrderByUploadedAtDesc(knowledgeTypes)
            .stream().map(KnowledgeBaseEntity::getId).toList();
    }
    // ... 后续向量检索逻辑不变
}
```

#### 1.4 ErrorCode 新增 Wiki 错误码

**文件**: `common/exception/ErrorCode.java`

```java
// Wiki 模块 68xx
WIKI_NOT_FOUND(6801, "Wiki 条目不存在"),
WIKI_GENERATION_FAILED(6802, "Wiki 自动生成失败"),
WIKI_ACCESS_DENIED(6803, "无权访问该 Wiki 条目"),
```

---

### 阶段 2：Wiki 后端模块

**目标**：创建完整的 Wiki CRUD + 语义搜索 + 自动生成服务。

#### 2.1 新建 NvcWikiCategory 枚举

**文件**: `modules/nvcwiki/model/NvcWikiCategory.java`

```java
public enum NvcWikiCategory {
    CONVERSATION_CASE,   // 对话中的案例
    REAL_SCENARIO,       // 真实生活场景
    LEARNING_SUMMARY,    // 学习总结
    BOOK_KNOWLEDGE,      // 书籍/理论知识
    OTHER                // 其他
}
```

#### 2.2 新建 NvcWikiSourceType 枚举

**文件**: `modules/nvcwiki/model/NvcWikiSourceType.java`

```java
public enum NvcWikiSourceType {
    AUTO_GENERATED,  // 练习后自动生成
    MANUAL,          // 用户手动记录
    AI_ASSISTED      // Agent 对话中生成
}
```

#### 2.3 新建 DTO

**文件**: `modules/nvcwiki/dto/`

```java
// WikiCreateRequest.java
public record WikiCreateRequest(
    @NotBlank String title,
    @NotNull NvcWikiCategory category,
    @NotNull NvcWikiSourceType sourceType,
    String content,           // Markdown 内容
    List<String> tags,
    Long sessionId            // 可空，关联练习会话
) {}

// WikiUpdateRequest.java
public record WikiUpdateRequest(
    String title,
    NvcWikiCategory category,
    String content,
    List<String> tags
) {}

// WikiResponse.java
public record WikiResponse(
    Long id,
    String title,
    NvcWikiCategory category,
    NvcWikiSourceType sourceType,
    String content,
    List<String> tags,
    Long sessionId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}

// WikiSearchResult.java
public record WikiSearchResult(
    Long id,
    String title,
    NvcWikiCategory category,
    String contentSnippet,    // 匹配片段
    double score,             // 相似度分数
    LocalDateTime createdAt
) {}
```

#### 2.4 新建 NvcWikiService

**文件**: `modules/nvcwiki/service/NvcWikiService.java`

核心方法：
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NvcWikiService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeBaseVectorService vectorService;
    private final NvcRagService ragService;

    // 创建 Wiki 条目
    @Transactional
    public WikiResponse createWiki(Long userId, WikiCreateRequest request) {
        KnowledgeBaseEntity entity = KnowledgeBaseEntity.builder()
            .userId(userId)
            .type(KnowledgeBaseType.PERSONAL_WIKI)
            .name(request.title())
            .category(request.category().name())
            .contentType("text/markdown")
            .vectorStatus(VectorStatus.PENDING)
            .build();
        // 保存到 DB
        entity = knowledgeBaseRepository.save(entity);
        // 异步向量化
        vectorService.vectorizeAndStore(entity.getId(), request.content());
        return toResponse(entity, request.content());
    }

    // 更新 Wiki 条目
    @Transactional
    public WikiResponse updateWiki(Long userId, Long wikiId, WikiUpdateRequest request) {
        KnowledgeBaseEntity entity = getWikiOrThrow(wikiId, userId);
        // 更新字段...
        // 重新向量化
        vectorService.vectorizeAndStore(entity.getId(), request.content());
        return toResponse(entity, request.content());
    }

    // 删除 Wiki 条目
    @Transactional
    public void deleteWiki(Long userId, Long wikiId) {
        KnowledgeBaseEntity entity = getWikiOrThrow(wikiId, userId);
        vectorService.deleteByKnowledgeBaseId(wikiId);
        knowledgeBaseRepository.delete(entity);
    }

    // 获取单个 Wiki
    public WikiResponse getWiki(Long userId, Long wikiId) { ... }

    // 列出用户所有 Wiki（分页 + 分类筛选）
    public Page<WikiResponse> listWikis(Long userId, NvcWikiCategory category, Pageable pageable) { ... }

    // 语义搜索
    public List<WikiSearchResult> searchWikis(Long userId, String query, int topK) {
        List<RagResult> results = ragService.retrieve(
            query, List.of(KnowledgeBaseType.PERSONAL_WIKI), topK, userId);
        return results.stream().map(this::toSearchResult).toList();
    }

    // 关键词搜索
    public List<WikiResponse> searchByKeyword(Long userId, String keyword) { ... }
}
```

#### 2.5 新建 NvcWikiAutoGenerateService

**文件**: `modules/nvcwiki/service/NvcWikiAutoGenerateService.java`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NvcWikiAutoGenerateService {

    private final NvcWikiService wikiService;
    private final StructuredOutputInvoker structuredOutputInvoker;

    // 从练习会话生成 Wiki 笔记
    @Transactional
    public WikiResponse generateFromSession(Long userId, Long sessionId,
                                             List<NvcMessageEntity> messages) {
        // 1. 加载 Prompt 模板
        String systemPrompt = loadPrompt("prompts/nvc-wiki-auto-generate.st");

        // 2. 构建 user prompt（包含对话历史）
        String userPrompt = buildUserPrompt(messages);

        // 3. 调用 LLM 生成笔记
        WikiGenerateResult result = structuredOutputInvoker.invoke(
            systemPrompt, userPrompt, WikiGenerateResult.class);

        // 4. 保存为 Wiki 条目
        return wikiService.createWiki(userId, new WikiCreateRequest(
            result.title(),
            NvcWikiCategory.CONVERSATION_CASE,
            NvcWikiSourceType.AUTO_GENERATED,
            result.content(),
            result.tags(),
            sessionId
        ));
    }
}
```

#### 2.6 新建 NvcWikiAutoGeneratePrompt

**文件**: `resources/prompts/nvc-wiki-auto-generate.st`

```
你是一个 NVC（非暴力沟通）学习助手。用户刚完成一次 NVC 练习，请根据对话内容生成一份个人知识库笔记。

## 要求

1. **聚焦案例和知识**，不要记录评估分数或改进建议
2. 记录用户在对话中**实际使用的 NVC 技巧**
3. 提取**关键对话片段**作为案例
4. 总结用户**学到的知识点**
5. 如果有真实生活场景的应用，单独记录

## 输出格式

{
  "title": "简洁的标题（如：用观察替代评价 - 职场沟通案例）",
  "content": "Markdown 格式的笔记内容，包含：\\n- 背景描述\\n- 关键对话片段\\n- 使用的 NVC 技巧\\n- 学到的知识点",
  "tags": ["标签1", "标签2"]
}
```

#### 2.7 新建 NvcWikiController

**文件**: `modules/nvcwiki/controller/NvcWikiController.java`

```java
@RestController
@RequestMapping("/api/nvc/wiki")
@Slf4j
@RequiredArgsConstructor
public class NvcWikiController {

    private final NvcWikiService wikiService;

    @RateLimit(count = 20)
    @PostMapping
    public Result<WikiResponse> createWiki(
            @RequestParam Long userId,
            @Valid @RequestBody WikiCreateRequest request) {
        return Result.success(wikiService.createWiki(userId, request));
    }

    @GetMapping("/{wikiId}")
    public Result<WikiResponse> getWiki(
            @RequestParam Long userId,
            @PathVariable Long wikiId) {
        return Result.success(wikiService.getWiki(userId, wikiId));
    }

    @PutMapping("/{wikiId}")
    public Result<WikiResponse> updateWiki(
            @RequestParam Long userId,
            @PathVariable Long wikiId,
            @Valid @RequestBody WikiUpdateRequest request) {
        return Result.success(wikiService.updateWiki(userId, wikiId, request));
    }

    @DeleteMapping("/{wikiId}")
    public Result<Void> deleteWiki(
            @RequestParam Long userId,
            @PathVariable Long wikiId) {
        wikiService.deleteWiki(userId, wikiId);
        return Result.success(null);
    }

    @GetMapping
    public Result<Page<WikiResponse>> listWikis(
            @RequestParam Long userId,
            @RequestParam(required = false) NvcWikiCategory category,
            Pageable pageable) {
        return Result.success(wikiService.listWikis(userId, category, pageable));
    }

    @GetMapping("/search")
    public Result<List<WikiSearchResult>> searchWikis(
            @RequestParam Long userId,
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        return Result.success(wikiService.searchWikis(userId, query, topK));
    }
}
```

#### 2.8 实现 WikiWriteTool 和 WikiSearchTool

**文件**: `nvcpractice/tool/WikiWriteTool.java`（替换桩实现）

```java
@Override
public String execute(ToolContext ctx, WikiWriteInput input) {
    Long userId = ctx.getUserId();
    WikiCreateRequest request = new WikiCreateRequest(
        input.title(),
        NvcWikiCategory.valueOf(input.category()),
        NvcWikiSourceType.AI_ASSISTED,
        input.content(),
        input.tags(),
        null  // sessionId 从 ctx 获取
    );
    WikiResponse wiki = wikiService.createWiki(userId, request);
    return "已保存到你的个人知识库：" + wiki.title() + " (ID: " + wiki.id() + ")";
}
```

**文件**: `nvcpractice/tool/WikiSearchTool.java`（替换桩实现）

```java
@Override
public String execute(ToolContext ctx, WikiSearchInput input) {
    Long userId = ctx.getUserId();
    List<WikiSearchResult> results = wikiService.searchWikis(userId, input.query(), 5);
    if (results.isEmpty()) {
        return "在你的个人知识库中没有找到相关内容。";
    }
    StringBuilder sb = new StringBuilder("根据你的个人知识库，找到以下相关内容：\n\n");
    for (WikiSearchResult r : results) {
        sb.append("【").append(r.title()).append("】(").append(r.category()).append(")\n");
        sb.append(r.contentSnippet()).append("\n\n");
    }
    return sb.toString();
}
```

---

### 阶段 3：自动沉淀集成

**目标**：在练习结束后，根据用户偏好异步生成 Wiki 笔记。

#### 3.1 NvcUserProfileEntity 增加 preferences 字段

**文件**: `modules/nvcprofile/model/NvcUserProfileEntity.java`

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "preferences", columnDefinition = "jsonb")
@Builder.Default
private Map<String, Object> preferences = new HashMap<>();
```

**DB Migration**（Hibernate ddl-auto=update 自动处理）：
- `nvc_user_profile` 表新增 `preferences` JSONB 列

#### 3.2 新建 WikiStreamProducer

**文件**: `modules/nvcwiki/listener/WikiStreamProducer.java`

```java
@Component
@Slf4j
public class WikiStreamProducer extends AbstractStreamProducer {

    public WikiStreamProducer(RedisService redisService) {
        super(redisService);
    }

    @Override
    protected String getStreamKey() {
        return "nvc:wiki:generate:stream";
    }

    public void sendWikiGenerateTask(Long sessionId, Long userId) {
        Map<String, String> body = Map.of(
            "sessionId", String.valueOf(sessionId),
            "userId", String.valueOf(userId)
        );
        sendTask(body);
        log.info("Wiki generate task sent: sessionId={}, userId={}", sessionId, userId);
    }
}
```

#### 3.3 新建 WikiStreamConsumer

**文件**: `modules/nvcwiki/listener/WikiStreamConsumer.java`

```java
@Component
@Slf4j
public class WikiStreamConsumer extends AbstractStreamConsumer {

    private final NvcWikiAutoGenerateService wikiAutoGenerateService;
    private final NvcPracticeMessageRepository messageRepository;

    @Override
    protected String getStreamKey() {
        return "nvc:wiki:generate:stream";
    }

    @Override
    protected String getConsumerGroup() {
        return "nvc-wiki-generate-group";
    }

    @Override
    protected void processBusiness(Map<String, String> body) {
        Long sessionId = Long.parseLong(body.get("sessionId"));
        Long userId = Long.parseLong(body.get("userId"));

        log.info("Processing Wiki generate task: sessionId={}, userId={}", sessionId, userId);

        // 1. 加载会话消息
        List<NvcMessageEntity> messages = messageRepository
            .findBySessionIdOrderBySequenceNumAsc(sessionId);

        if (messages.isEmpty()) {
            log.warn("No messages found for session: {}", sessionId);
            return;
        }

        // 2. 生成 Wiki 笔记
        wikiAutoGenerateService.generateFromSession(userId, sessionId, messages);

        log.info("Wiki auto-generated for session: {}", sessionId);
    }
}
```

#### 3.4 修改 NvcEvaluateStreamConsumer（插入 Wiki 触发）

**文件**: `nvcpractice/listener/NvcEvaluateStreamConsumer.java`

在 `processBusiness()` 方法末尾添加：

```java
// 现有：评估完成后更新用户档案
profileService.updateAbilityScore(...);

// 新增：检查用户是否开启自动沉淀偏好
if (isAutoGenerateWikiEnabled(userId)) {
    wikiStreamProducer.sendWikiGenerateTask(sessionId, userId);
}
```

新增辅助方法：
```java
private boolean isAutoGenerateWikiEnabled(Long userId) {
    return profileService.getProfile(userId)
        .map(profile -> {
            Map<String, Object> prefs = profile.getPreferences();
            return prefs != null && Boolean.TRUE.equals(prefs.get("autoGenerateWiki"));
        })
        .orElse(false);
}
```

#### 3.5 前端偏好开关

在 `SettingsPage.tsx` 或 `NvcProfilePage.tsx` 中增加开关：

```typescript
const [autoGenerateWiki, setAutoGenerateWiki] = useState(false);

// 保存偏好
const handleSavePreference = async () => {
  await profileApi.updatePreferences(userId, { autoGenerateWiki });
};
```

---

### 阶段 4：前端页面

**目标**：创建 Wiki 主页、编辑器、卡片、搜索组件。

#### 4.1 安装依赖

```bash
pnpm add @uiw/react-md-editor
```

#### 4.2 新建 API 模块

**文件**: `frontend/src/api/wiki.ts`

```typescript
import { request } from './request';

export interface WikiCreateRequest {
  title: string;
  category: 'CONVERSATION_CASE' | 'REAL_SCENARIO' | 'LEARNING_SUMMARY' | 'BOOK_KNOWLEDGE' | 'OTHER';
  sourceType: 'AUTO_GENERATED' | 'MANUAL' | 'AI_ASSISTED';
  content: string;
  tags?: string[];
  sessionId?: number;
}

export interface WikiResponse {
  id: number;
  title: string;
  category: string;
  sourceType: string;
  content: string;
  tags: string[];
  sessionId?: number;
  createdAt: string;
  updatedAt: string;
}

export interface WikiSearchResult {
  id: number;
  title: string;
  category: string;
  contentSnippet: string;
  score: number;
  createdAt: string;
}

export const wikiApi = {
  create: (userId: number, data: WikiCreateRequest) =>
    request.post<WikiResponse>(`/api/nvc/wiki?userId=${userId}`, data),

  get: (userId: number, wikiId: number) =>
    request.get<WikiResponse>(`/api/nvc/wiki/${wikiId}?userId=${userId}`),

  update: (userId: number, wikiId: number, data: Partial<WikiCreateRequest>) =>
    request.put<WikiResponse>(`/api/nvc/wiki/${wikiId}?userId=${userId}`, data),

  delete: (userId: number, wikiId: number) =>
    request.delete(`/api/nvc/wiki/${wikiId}?userId=${userId}`),

  list: (userId: number, category?: string, page = 0, size = 20) =>
    request.get<{ content: WikiResponse[]; totalElements: number }>('/api/nvc/wiki', {
      params: { userId, ...(category ? { category } : {}), page, size },
    }),

  search: (userId: number, query: string, topK = 5) =>
    request.get<WikiSearchResult[]>('/api/nvc/wiki/search', {
      params: { userId, query, topK },
    }),
};
```

#### 4.3 新建 WikiCard 组件

**文件**: `frontend/src/components/nvc/NvcWikiCard.tsx`

参考 `NvcScenarioCard.tsx` 的写法：
- 卡片布局：标题 + 分类标签 + 内容摘要 + 时间
- 悬停效果：阴影增大、边框颜色变化
- 支持深色模式
- 点击展开详情

#### 4.4 新建 WikiEditor 组件

**文件**: `frontend/src/components/nvc/NvcWikiEditor.tsx`

使用 `@uiw/react-md-editor`：
- 标题输入框
- 分类下拉选择
- 标签输入（支持多标签）
- Markdown 编辑区
- 保存/取消按钮

#### 4.5 新建 WikiSearch 组件

**文件**: `frontend/src/components/nvc/NvcWikiSearch.tsx`

参考 `KnowledgeBaseManagePage.tsx` 的搜索写法：
- 搜索输入框 + 搜索按钮
- 语义搜索结果列表（显示相似度分数）
- 关键词高亮

#### 4.6 新建 Wiki 主页

**文件**: `frontend/src/pages/NvcWikiPage.tsx`

布局：
```
┌─────────────────────────────────────────┐
│  我的知识库                    [+ 新建]  │
├─────────────────────────────────────────┤
│  [搜索框]           [分类筛选 ▼]         │
├─────────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ WikiCard│ │ WikiCard│ │ WikiCard│   │
│  └─────────┘ └─────────┘ └─────────┘   │
│  ┌─────────┐ ┌─────────┐ ┌─────────┐   │
│  │ WikiCard│ │ WikiCard│ │ WikiCard│   │
│  └─────────┘ └─────────┘ └─────────┘   │
├─────────────────────────────────────────┤
│  [分页]                                  │
└─────────────────────────────────────────┘
```

#### 4.7 路由配置

**文件**: `frontend/src/constants/routes.ts`

```typescript
export const ROUTES = {
  // ... 现有路由
  nvcWiki: '/nvc/wiki',
  nvcWikiDetail: (wikiId: number) => `/nvc/wiki/${wikiId}`,
} as const;
```

**文件**: `frontend/src/App.tsx`

```typescript
{
  path: ROUTES.nvcWiki,
  element: <NvcWikiPage />,
},
```

---

## 四、文件清单汇总

### 后端新建文件（11 个）

| 文件 | 说明 |
|------|------|
| `modules/nvcwiki/model/NvcWikiCategory.java` | 分类枚举 |
| `modules/nvcwiki/model/NvcWikiSourceType.java` | 来源枚举 |
| `modules/nvcwiki/dto/WikiCreateRequest.java` | 创建请求 |
| `modules/nvcwiki/dto/WikiUpdateRequest.java` | 更新请求 |
| `modules/nvcwiki/dto/WikiResponse.java` | 响应 |
| `modules/nvcwiki/dto/WikiSearchResult.java` | 搜索结果 |
| `modules/nvcwiki/service/NvcWikiService.java` | 核心服务 |
| `modules/nvcwiki/service/NvcWikiAutoGenerateService.java` | 自动生成服务 |
| `modules/nvcwiki/controller/NvcWikiController.java` | REST 控制器 |
| `modules/nvcwiki/listener/WikiStreamProducer.java` | 异步任务生产者 |
| `modules/nvcwiki/listener/WikiStreamConsumer.java` | 异步任务消费者 |
| `resources/prompts/nvc-wiki-auto-generate.st` | 自动生成 Prompt |

### 后端修改文件（6 个）

| 文件 | 改动 |
|------|------|
| `knowledgebase/model/KnowledgeBaseEntity.java` | 增加 `userId` 字段 |
| `knowledgebase/repository/KnowledgeBaseRepository.java` | 增加 userId 查询方法 |
| `nvcpractice/service/NvcRagService.java` | 增加 userId 参数重载 |
| `nvcpractice/listener/NvcEvaluateStreamConsumer.java` | 评估后触发 Wiki 生成 |
| `nvcpractice/tool/WikiWriteTool.java` | 替换桩实现 |
| `nvcpractice/tool/WikiSearchTool.java` | 替换桩实现 |
| `nvcprofile/model/NvcUserProfileEntity.java` | 增加 preferences JSONB 字段 |
| `common/exception/ErrorCode.java` | 增加 Wiki 错误码 |

### 前端新建文件（6 个）

| 文件 | 说明 |
|------|------|
| `api/wiki.ts` | API 模块 |
| `pages/NvcWikiPage.tsx` | Wiki 主页 |
| `components/nvc/NvcWikiCard.tsx` | 条目卡片 |
| `components/nvc/NvcWikiEditor.tsx` | Markdown 编辑器 |
| `components/nvc/NvcWikiSearch.tsx` | 语义搜索组件 |

### 前端修改文件（2 个）

| 文件 | 改动 |
|------|------|
| `constants/routes.ts` | 增加 Wiki 路由 |
| `App.tsx` | 注册 Wiki 路由 |

---

## 五、风险点

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `KnowledgeBaseEntity` 加 `userId` 字段 | Hibernate ddl-auto=update 自动加列，现有数据 userId=null | 测试环境先验证，确认现有查询不受影响 |
| `NvcRagService` 加 userId 参数 | 调用方需要适配 | 新增重载方法，不改原方法签名 |
| Wiki 自动生成 token 成本 | 每次练习后生成笔记消耗 LLM token | 默认关闭，用户主动开启；Prompt 简洁 |
| Markdown 编辑器包大小 | `@uiw/react-md-editor` 约 200KB | 懒加载 Wiki 页面 |

---

## 六、实施顺序建议

```
阶段 1（基建改造）→ 阶段 2（Wiki 后端）→ 阶段 3（自动沉淀）→ 阶段 4（前端页面）
     ↓                    ↓                    ↓                    ↓
  Entity+Repo          Service+Controller    Stream+Trigger       Page+Components
  RAG 适配             Tool 实现              偏好开关              API 模块
```

**阶段 1 和阶段 2 可以在同一分支上连续开发。**
**阶段 3 依赖阶段 2 完成。**
**阶段 4 可以和阶段 2/3 并行开发（前后端分离）。**
