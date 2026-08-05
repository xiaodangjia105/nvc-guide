# Phase 2 Wiki 模块设计文档

> 创建时间：2026-07-24
> 分支：feat/wiki-module
> 状态：已完成

---

## 一、背景与目标

### 现状

用户在练习 NVC 过程中积累的经验和心得没有沉淀机制。Agent 对话也无法检索用户的个人知识库，导致回答缺乏个性化。

### 目标

1. 创建个人 Wiki 模块，让用户沉淀 NVC 学习笔记、案例、心得
2. 复用 knowledge_base 表存储，通过 userId 隔离个人数据
3. 支持语义搜索，Agent 可检索用户 Wiki 提供个性化回答
4. 支持练习结束后自动生成 Wiki 条目（默认关闭，用户确认后生成）

---

## 二、架构设计

### 模块结构

```
nvcwiki/
├── controller/
│   └── NvcWikiController.java            # REST API
├── service/
│   ├── NvcWikiService.java               # 核心 CRUD + 搜索
│   └── NvcWikiAutoGenerateService.java   # 自动生成服务
├── model/
│   ├── NvcWikiCategory.java              # 分类枚举
│   └── NvcWikiSourceType.java            # 来源类型枚举
├── listener/
│   ├── WikiStreamProducer.java           # Redis Stream 生产者
│   └── WikiStreamConsumer.java           # Redis Stream 消费者
└── dto/
    ├── WikiCreateRequest.java
    ├── WikiUpdateRequest.java
    ├── WikiResponse.java
    └── WikiSearchResult.java
```

### 存储设计

**复用 knowledge_base 表**，通过以下字段区分：

| 字段 | Wiki 条目 | 系统知识库 |
|------|----------|-----------|
| type | PERSONAL_WIKI | NVC_THEORY / SPEECH_TEMPLATE / ... |
| userId | 用户 ID | null |
| category | CONVERSATION_CASE / REAL_SCENARIO / ... | null |

### 分类体系

```java
public enum NvcWikiCategory {
    CONVERSATION_CASE,  // 对话案例
    REAL_SCENARIO,      // 真实场景
    LEARNING_SUMMARY,   // 学习总结
    BOOK_KNOWLEDGE,     // 书籍知识
    OTHER               // 其他
}
```

### 来源类型

```java
public enum NvcWikiSourceType {
    MANUAL,             // 手动创建
    AUTO_GENERATED,     // 自动生成
    IMPORTED            // 导入
}
```

---

## 三、关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 存储方案 | 复用 knowledge_base 表 | 避免重复建表，统一向量化流程 |
| 隔离机制 | userId 字段 | null=系统知识库，非null=个人 Wiki |
| 向量化 | 复用 KnowledgeBaseVectorService | 统一 embedding 流程 |
| 自动生成 | 默认关闭，用户确认后异步生成 | 避免噪音，尊重用户选择 |
| 异步机制 | Redis Stream | 复用现有基础设施，解耦生成和存储 |

### 自动生成流程

```
练习结束 → NvcEvaluateStreamConsumer
  → 检查用户偏好 autoGenerateWiki
  → 如果开启 → WikiStreamProducer.send()
    → WikiStreamConsumer 消费
      → NvcWikiAutoGenerateService.generate()
        → LLM 生成 Wiki 内容
        → NvcWikiService.createWiki()
```

---

## 四、API 设计

### 端点列表

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/nvc/wiki` | 创建 Wiki 条目 |
| GET | `/api/nvc/wiki` | 获取用户 Wiki 列表（分页） |
| GET | `/api/nvc/wiki/{id}` | 获取单个 Wiki 详情 |
| PUT | `/api/nvc/wiki/{id}` | 更新 Wiki 条目 |
| DELETE | `/api/nvc/wiki/{id}` | 删除 Wiki 条目 |
| GET | `/api/nvc/wiki/search` | 语义搜索 Wiki |

### 请求/响应 DTO

```java
// 创建请求
public record WikiCreateRequest(
    String title,
    String content,
    NvcWikiCategory category,
    List<String> tags
) {}

// 响应
public record WikiResponse(
    Long id,
    String title,
    String content,
    String category,
    List<String> tags,
    String sourceType,
    LocalDateTime createdAt
) {}
```

---

## 五、Agent 工具集成

### WikiWriteTool

```java
// 从桩实现替换为真实逻辑
public NvcToolResult execute(Map<String, Object> args, NvcToolContext context) {
    String title = (String) args.get("title");
    String content = (String) args.get("content");
    String category = (String) args.get("category");

    WikiCreateRequest request = new WikiCreateRequest(title, content, NvcWikiCategory.valueOf(category), null);
    WikiResponse response = wikiService.createWiki(context.getUserId(), request);

    return NvcToolResult.success("Wiki 创建成功: " + response.title());
}
```

### WikiSearchTool

```java
public NvcToolResult execute(Map<String, Object> args, NvcToolContext context) {
    String query = (String) args.get("query");

    List<WikiSearchResult> results = wikiService.searchWiki(context.getUserId(), query, 5);

    return NvcToolResult.success(formatSearchResults(results));
}
```

---

## 六、前端实现

### 文件清单

```
frontend/src/
├── api/wiki.ts                           # API 模块 + 类型定义
├── pages/NvcWikiPage.tsx                 # Wiki 主页
└── components/nvc/
    ├── NvcWikiCard.tsx                   # 条目卡片
    ├── NvcWikiEditor.tsx                 # Markdown 编辑器
    └── NvcWikiSearch.tsx                 # 语义搜索
```

### 核心特性

- 列表页：卡片展示、分类筛选、分页
- 编辑器：Markdown 编辑 + 预览
- 搜索：语义搜索 + 结果高亮
- 自动生成：练习结束后弹窗询问

---

## 七、文件清单

### 后端新建（11 个）

```
app/src/main/java/nvc/guide/modules/nvcwiki/
├── controller/NvcWikiController.java
├── service/NvcWikiService.java
├── service/NvcWikiAutoGenerateService.java
├── model/NvcWikiCategory.java
├── model/NvcWikiSourceType.java
├── listener/WikiStreamProducer.java
├── listener/WikiStreamConsumer.java
├── dto/WikiCreateRequest.java
├── dto/WikiUpdateRequest.java
├── dto/WikiResponse.java
└── dto/WikiSearchResult.java

app/src/main/resources/prompts/
└── nvc-wiki-auto-generate.st
```

### 后端修改（8 个）

```
KnowledgeBaseEntity.java          — 增加 userId 字段
KnowledgeBaseRepository.java      — 增加 findByUserId 查询
NvcRagService.java                — 增加 userId 参数重载
WikiWriteTool.java                — 桩实现替换为真实逻辑
WikiSearchTool.java               — 桩实现替换为真实逻辑
NvcEvaluateStreamConsumer.java    — 评估后触发 Wiki 生成
NvcUserProfileEntity.java         — 增加 preferences JSONB 字段
ErrorCode.java                    — 增加 Wiki 相关错误码
```

### 前端新建（5 个）

```
frontend/src/
├── api/wiki.ts
├── pages/NvcWikiPage.tsx
├── components/nvc/NvcWikiCard.tsx
├── components/nvc/NvcWikiEditor.tsx
└── components/nvc/NvcWikiSearch.tsx
```

---

## 八、测试要求

- NvcWikiService 单元测试：CRUD 操作、权限校验
- WikiSearchTool 单元测试：语义搜索准确性
- 集成测试：创建 Wiki → 搜索 → Agent 检索

---

## 九、验收标准

```
□ 后端
  □ CRUD API 正常工作
  □ 语义搜索返回相关结果
  □ userId 隔离（用户只能访问自己的 Wiki）
  □ 自动生成（用户确认后异步生成）

□ Agent 集成
  □ WikiWriteTool 真实创建 Wiki
  □ WikiSearchTool 语义搜索用户 Wiki
  □ Agent 对话可引用用户 Wiki 内容

□ 前端
  □ Wiki 列表页正常展示
  □ Markdown 编辑器可用
  □ 语义搜索功能正常

□ 端到端
  □ 手动创建 Wiki → 搜索 → 找到
  □ 练习结束 → 确认生成 → Wiki 自动创建
  □ Agent 对话引用 Wiki 内容
```
