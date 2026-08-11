# Architecture Optimization Design

**Date:** 2026-08-11
**Project:** NVC Guide - 非暴力沟通练习助手
**Approach:** Plan B — 平衡型（结构优化 + 性能修复）
**Duration:** 4-6 周

---

## 1. Background

基于 `docs/architecture-review/` 目录下的 6 份架构审查报告，识别出以下核心问题：

**严重问题（5 个）：**
1. N+1 查询性能隐患（Repository 层）
2. 测试覆盖率不足（后端 45%，前端 20%）
3. Controller 职责过重（NvcPracticeController: 7 依赖, 15+ 端点）
4. Controller 直接操作 Entity（Feature Envy）
5. 跨模块直接访问 Repository（不当亲密）

**高优先级问题（9 个）：**
- Service 类过大、前端 `any` 类型、缺少细粒度权限、前端 API 客户端重复创建
- 硬编码后端地址、Agent 路由用 switch、评估维度扩展性差
- 循环依赖风险、参数传递不一致

---

## 2. Design Goals

1. **解决所有严重和高优先级问题**，代码质量从 B+ 提升到 A-
2. **对外 API 零破坏性变更**，前端和后端接口保持兼容
3. **每个 Phase 可独立交付**，降低集成风险
4. **所有改动在独立分支进行**，审核后合并 master

---

## 3. Phase Overview

```
Phase 1: 性能修复（1 周）
  └─ N+1 查询 → 向量搜索索引 → 查询性能监控

Phase 2: 代码结构优化（2 周）
  └─ Controller 拆分 → Service 拆分 → 模块解耦

Phase 3: 类型安全与清理（1 周）
  └─ 前端类型补全 → API 客户端统一 → 死代码清理 → 配置集中化

Phase 4: 测试补充（1-2 周）
  └─ 后端 Service 测试 → 前端组件测试 → 关键路径集成测试
```

---

## 4. Phase 1: 性能修复（1 周）

### 4.1 修复 N+1 查询

**问题**：Repository 查询 session 时，messages/evaluations 是懒加载，循环访问时触发 N+1。

**方案**：在需要关联数据的查询方法上加 `JOIN FETCH`。

```java
// 改前：findByUserId → 每个 session 再查 messages
@Query("SELECT s FROM NvcPracticeSessionEntity s " +
       "LEFT JOIN FETCH s.messages " +
       "WHERE s.userId = :userId ORDER BY s.createdAt DESC")
List<NvcPracticeSessionEntity> findByUserIdWithMessages(@Param("userId") Long userId);
```

**排查范围**：
- `NvcPracticeSessionRepository` — 所有返回 List 的查询
- `NvcPracticeSessionService` — 循环中访问集合的地方
- 其他模块是否有类似模式

**改动范围**：Repository 接口 + Service 调用处，约 3-5 个文件。

### 4.2 向量搜索索引优化

**问题**：pgvector 默认使用 IVFFlat 索引，大数据量下查询慢。

**方案**：改用 HNSW 索引。

```sql
-- Flyway 迁移脚本
DROP INDEX IF EXISTS idx_knowledge_vectors_embedding;
CREATE INDEX idx_knowledge_vectors_embedding
  ON knowledge_base_vectors USING hnsw (embedding vector_cosine_ops)
  WITH (m = 16, ef_construction = 100);
```

**改动范围**：新增 Flyway 迁移脚本，1 个文件。

### 4.3 查询性能监控

**问题**：缺少慢查询可观测性。

**方案**：开发环境开启 Hibernate SQL 日志，生产环境接 Micrometer。

```yaml
# application-dev.yml
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true

# application.yml (通用)
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
```

**改动范围**：配置文件 + 可能加 `micrometer-registry-prometheus` 依赖，2-3 个文件。

### Phase 1 交付标准

- [ ] 所有列表查询不再触发 N+1（通过 Hibernate SQL 日志验证）
- [ ] 向量搜索响应时间 < 50ms（1 万条数据量下）
- [ ] 开发环境可看到完整 SQL 日志

---

## 5. Phase 2: 代码结构优化（2 周）

### 5.1 Controller 拆分 — 引入 Facade

**问题**：`NvcPracticeController` 有 7 个依赖、15+ 端点，职责过重。

**方案**：引入 `NvcPracticeFacade`，Controller 只依赖 Facade，Facade 编排多个 Service。

```
改前：
NvcPracticeController
  ├─ NvcPracticeSessionService
  ├─ NvcPracticeDialogueService
  ├─ NvcStructuredPracticeService
  ├─ NvcEvaluationService
  ├─ NvcSummaryService
  ├─ NvcScenarioService
  └─ NvcAgentOrchestrator

改后：
NvcPracticeController
  └─ NvcPracticeFacade
       ├─ NvcPracticeSessionService
       ├─ NvcPracticeDialogueService
       ├─ NvcStructuredPracticeService
       ├─ NvcEvaluationService
       ├─ NvcSummaryService
       ├─ NvcScenarioService
       └─ NvcAgentOrchestrator
```

**Facade 方法分组**：

| Facade 方法 | 编排的 Service | 对应端点 |
|------------|---------------|---------|
| `createSession()` | session + scenario | POST /sessions |
| `getSessions()` | session | GET /sessions |
| `sendMessage()` | dialogue + orchestrator | POST /messages/stream |
| `getEvaluation()` | evaluation | GET /evaluation |
| `completeSession()` | session + summary | POST /complete |

**改动范围**：新增 `NvcPracticeFacade.java`，修改 `NvcPracticeController.java`，约 2 个文件。

### 5.2 大 Service 拆分

**问题**：`NvcPracticeSessionService`（450 行）和 `NvcProfileService`（320 行）职责过多。

**方案**：按子职责拆分。

```
NvcPracticeSessionService (450行)
  → NvcPracticeSessionService      ← 会话 CRUD（精简到 ~200行）
  → NvcPracticeSessionValidator    ← 验证逻辑提取

NvcProfileService (320行)
  → NvcProfileService              ← 档案 CRUD（精简到 ~150行）
  → NvcAbilityService              ← 能力分析/雷达图
  → NvcTrendService                ← 趋势计算
```

**原则**：
- 新类放在同包下，不改包路径
- 原 Service 的 public 方法签名不变，内部委托给新类
- 对外 API 零改动

**改动范围**：新增 3 个类，修改 2 个 Service，约 5 个文件。

### 5.3 模块解耦

**问题**：`nvcpractice` 模块直接注入 `NvcScenarioRepository`（跨模块访问 Repository）。

**方案**：改走 Service 接口。

```java
// 改前：直接访问其他模块的 Repository
@Autowired
private NvcScenarioRepository scenarioRepository;

// 改后：通过 Service 接口
@Autowired
private NvcScenarioService scenarioService;
```

**排查范围**：
- `NvcPracticeSessionService` 中对 `NvcScenarioRepository` 的直接调用
- 其他跨模块的 Repository 直接注入

**改动范围**：约 3-5 个文件。

### 5.4 Feature Envy 修复

**问题**：Controller 直接操作 Entity 对象（`session.getMessages().size()`）。

**方案**：Service 层返回 DTO，Controller 不再接触 Entity。

```java
// 改前
NvcPracticeSessionEntity session = sessionService.getSession(id);
session.getMessages().size(); // Controller 访问内部状态

// 改后
PracticeSessionResponse resp = sessionService.getSessionResponse(id);
// resp 已经是扁平化的 DTO
```

**改动范围**：新增 Response DTO 类 + 修改 Service 返回类型，约 5-8 个文件。

### Phase 2 交付标准

- [ ] `NvcPracticeController` 依赖从 7 个降到 1 个（Facade）
- [ ] 所有 Service < 300 行
- [ ] 无跨模块 Repository 直接注入
- [ ] Controller 层零 Entity 引用
- [ ] 现有测试全部通过

---

## 6. Phase 3: 类型安全与清理（1 周）

### 6.1 前端 TypeScript 类型补全

**问题**：API 调用中有 `any` 类型，丢失类型安全。

**方案**：为所有 API 响应定义具体类型。

```typescript
// types/nvc.ts 新增
interface EvaluationResult {
  id: number;
  sessionId: number;
  observation: { score: number; feedback: string };
  feeling: { score: number; feedback: string };
  need: { score: number; feedback: string };
  request: { score: number; feedback: string };
  overallScore: number;
  suggestions: string[];
  createdAt: string;
}

// api/nvc.ts 改后
getEvaluation: (sessionId: number) =>
  request.get<Result<EvaluationResult>>(`/api/nvc/practice/sessions/${sessionId}/evaluation`),
```

**排查范围**：`frontend/src/api/` 下所有使用 `any` 的文件。

**改动范围**：`types/` 目录新增类型定义 + `api/` 目录替换 `any`，约 4-6 个文件。

### 6.2 前端 API 客户端统一

**问题**：多个 API 文件各自 `axios.create()` 重复创建实例。

**方案**：确认 `request.ts` 为唯一实例源，其他文件改为 import。

```typescript
// frontend/src/api/request.ts — 唯一实例
export const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30000,
});

// nvc.ts / knowledgebase.ts / llmProvider.ts — 改为导入
import { request } from './request';
```

**改动范围**：约 3-4 个 API 文件。

### 6.3 硬编码后端地址移除

**问题**：`import.meta.env.PROD ? '' : 'http://localhost:8080'` 硬编码。

**方案**：统一用环境变量。

```typescript
// .env.development
VITE_API_BASE_URL=http://localhost:8080

// .env.production
VITE_API_BASE_URL=

// request.ts
baseURL: import.meta.env.VITE_API_BASE_URL || '',
```

**改动范围**：新增 `.env.example`，修改 `request.ts`，2 个文件。

### 6.4 死代码清理

| 清理项 | 位置 | 方式 |
|--------|------|------|
| `getProfileLegacy()` | NvcProfileService | 确认无调用后删除 |
| `parseOldFormat()` | KnowledgeBaseParseService | 确认无调用后删除 |
| 未使用的 import | 多个文件 | IDE 自动清理 |
| `@types/react-is` | package.json | depcheck 确认后移除 |

**改动范围**：约 5-8 个文件。

### 6.5 配置集中化

**问题**：配置分散在多个 `application-*.yml` 中，有重复。

**方案**：提取公共配置到 `application-common.yml`。

```yaml
# application-common.yml
spring:
  jpa:
    open-in-view: false
    properties:
      hibernate:
        default_batch_fetch_size: 16

# application-dev.yml
spring:
  config:
    import: classpath:application-common.yml
  jpa:
    show-sql: true
```

**改动范围**：新增 `application-common.yml`，修改各 profile 文件，3-4 个文件。

### Phase 3 交付标准

- [ ] 前端 API 调用零 `any` 类型
- [ ] 所有 API 文件共用一个 axios 实例
- [ ] 后端地址零硬编码
- [ ] 无已知死代码（@Deprecated 方法已删除）
- [ ] 配置无重复定义

---

## 7. Phase 4: 测试补充（1-2 周）

### 7.1 后端 Service 层单元测试

**目标**：覆盖率从 45% → 70%。

**优先级**：
1. Phase 2 中拆分/重构的 Service（必须有测试）
2. 核心业务逻辑（NvcPracticeSessionService, NvcEvaluationService）
3. 其他 Service（按优先级补充）

**工具**：JUnit 5 + Mockito

### 7.2 前端关键路径测试

**目标**：核心组件测试覆盖率从 20% → 50%，API 调用层从 0% → 60%。

**优先级**：
1. API 调用层（mock 后端，验证请求/响应类型正确性）
2. 练习页面核心组件（NvcChatPanel, NvcStepIndicator）
3. 自定义 Hooks（useAuth, useUserId）

**工具**：Vitest + React Testing Library

### 7.3 集成测试基础设施

**方案**：引入 Testcontainers，为关键流程写集成测试。

```java
@SpringBootTest
@Testcontainers
class NvcPracticeServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Container
    static RedisContainer<?> redis = new RedisContainer<>("redis:7");
}
```

**改动范围**：pom.xml 加依赖 + 新增测试类，约 5-8 个文件。

### Phase 4 交付标准

- [ ] 后端测试覆盖率 ≥ 70%
- [ ] 前端核心组件有测试
- [ ] 关键业务流程有集成测试

---

## 8. Risk Mitigation

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 重构引入新 Bug | 中 | 高 | 小步重构 + 先补测试再改结构 |
| Phase 2 改动范围过大 | 中 | 中 | 拆分为独立 PR，逐个审核 |
| 模块解耦影响现有功能 | 低 | 高 | 改动前后跑全量测试 |
| 前端类型补全覆盖不全 | 低 | 低 | 逐步推进，不阻塞主流程 |

---

## 9. Success Metrics

| 指标 | 当前 | 目标 | 衡量方式 |
|------|------|------|----------|
| 测试覆盖率 | 45% | 70% | CI 报告 |
| Controller 依赖数 | 7 | 1 | 代码审查 |
| 最大 Service 行数 | 450 | < 300 | wc -l |
| 前端 `any` 类型数 | 多处 | 0 | tsc --noEmit |
| API 响应时间 | ~200ms | < 100ms | 性能测试 |
| 向量搜索时间 | ~500ms | < 50ms | 性能测试 |

---

## 10. Branch Strategy

所有改动在独立分支上进行：

```
Phase 1: fix/performance-n-plus-one
Phase 2: refactor/controller-service-decouple
Phase 3: fix/type-safety-and-cleanup
Phase 4: test/coverage-improvement
```

每个 Phase 完成后：
1. 跑全量测试
2. 提 PR 审核
3. 审核通过后合并 master
4. 删除特性分支

---

## 11. Appendix

### A. Reference Documents

- `docs/architecture-review/01-architecture-audit.md` — 架构审计
- `docs/architecture-review/03-architecture-cleanup.md` — 清理建议
- `docs/architecture-review/05-arch-review.md` — 深度审查
- `docs/architecture-review/06-smell.md` — 代码异味检测

### B. Related Memories

- N+1 查询修复 → `[[n-plus-one-fix]]`
- 模块解耦 → `[[module-decouple]]`
