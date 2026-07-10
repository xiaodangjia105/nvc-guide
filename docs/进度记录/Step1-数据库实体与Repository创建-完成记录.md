# Step 1：数据库实体与 Repository 创建 — 完成记录

> 完成日期：2026-07-09
>
> 操作人：Claude Code + 用户协作 + 4 个并行 Agent
>
> 耗时：约 30 分钟

---

## 一、做了什么

### 1.1 设计分析阶段

在实现前，先对 Step1 文档进行了全面分析，发现并修复了以下问题：

| 问题 | 严重性 | 修复方式 |
|------|--------|---------|
| 包名错误：文档用 `interview.guide.modules.*` | 🔴 严重 | 统一改为 `nvc.guide.modules.*` |
| 缺少数据库索引定义 | 🟡 中等 | 在 `@Table` 注解中添加 `indexes` |
| `agentScene` 字段类型不一致 | 🟡 中等 | 从 `String` 改为 `NvcAgentScene` 枚举 |
| `PROFILE_ANALYZER` 初始数据缺失 | 🟡 中等 | 补充到 `data-nvc-agent-config.sql` |
| `userId` 类型未统一 | 🟡 中等 | 确认统一使用 `Long` |

### 1.2 用户确认的设计决策

| 决策项 | 结论 | 原因 |
|--------|------|------|
| userId 类型 | `Long` | 与 JPA 自增 ID 一致，后续对接 Spring Security 更自然 |
| agentScene 类型 | `NvcAgentScene` 枚举 | 编译时检查有效值，与 NvcAgentConfigEntity 保持一致 |
| 外键关系 | 不加 `@ManyToOne` | 避免 JPA 懒加载问题和 N+1 查询陷阱，手动管理关联 |
| PROFILE_ANALYZER | 补充到初始 SQL | 枚举与数据保持一致 |

### 1.3 并行 Agent 实现

使用 4 个并行 Agent 同时创建文件，大幅提高效率：

| Agent | 任务 | 创建文件数 | 耗时 |
|-------|------|-----------|------|
| Agent 1 | nvcpractice 模型（枚举 + Entity） | 12 | ~2 分钟 |
| Agent 2 | nvcscenario + nvcprofile 模型 | 7 | ~2 分钟 |
| Agent 3 | 所有 8 个 Repository 接口 | 8 | ~2 分钟 |
| Agent 4 | 种子数据 SQL + application.yml 配置 | 3 | ~1 分钟 |

---

## 二、创建的文件清单

### 2.1 枚举类（11 个）

**nvcpractice 模块（8 个）：**

| 文件 | 说明 | 值 |
|------|------|-----|
| `NvcPracticeMode.java` | 练习模式 | SCENARIO, FREE_DIALOG, STRUCTURED_FOUR_STEP |
| `NvcSessionPhase.java` | 会话阶段 | CREATED, IN_PROGRESS, PAUSED, COMPLETED, EVALUATED |
| `NvcPracticeStep.java` | 练习步骤 | OBSERVE, FEELING, NEED, REQUEST, COMPLETED |
| `NvcMessageRole.java` | 消息角色 | USER, ASSISTANT, SYSTEM |
| `NvcDifficulty.java` | 难度等级 | EASY, MEDIUM, HARD |
| `NvcAgentScene.java` | Agent 场景 | 11 个值（含 PROFILE_ANALYZER） |
| `NvcEvaluationType.java` | 评估类型 | REALTIME, FINAL |
| `NvcPracticeType.java` | 练习类型 | TEXT, VOICE |

**nvcscenario 模块（1 个）：**

| 文件 | 说明 | 值 |
|------|------|-----|
| `NvcScenarioType.java` | 场景类型 | WORKPLACE, FAMILY, INTIMATE, SOCIAL, SELF |

**nvcprofile 模块（2 个）：**

| 文件 | 说明 | 值 |
|------|------|-----|
| `NvcLevel.java` | NVC 水平 | BEGINNER, INTERMEDIATE, ADVANCED |
| `NvcCommunicationStyle.java` | 沟通风格 | ASSERTIVE, PASSIVE, AGGRESSIVE, PASSIVE_AGGRESSIVE, NVC |

### 2.2 Entity 类（8 个）

| Entity | 表名 | 索引 | 关键字段 |
|--------|------|------|---------|
| `NvcPracticeSessionEntity` | `nvc_practice_session` | user_id, current_phase | userId, practiceMode, scenarioId, currentPhase, currentStep, agentScene(枚举), difficulty |
| `NvcPracticeMessageEntity` | `nvc_practice_message` | (session_id, sequence_num) | sessionId, role, agentScene(枚举), content, step, sequenceNum |
| `NvcEvaluationEntity` | `nvc_evaluation` | session_id, (user_id, created_at) | sessionId, userId, 四维分数+详情, empathyScore, strengths, improvements, evaluationType |
| `NvcUserProfileEntity` | `nvc_user_profile` | user_id(unique) | userId, communicationBackground, personalityTraits(JSONB), communicationStyle, nvcLevel |
| `NvcUserAbilityScoreEntity` | `nvc_user_ability_score` | (user_id, scored_at) | userId, sessionId, observation/feeling/need/request/empathy, practiceType |
| `NvcScenarioEntity` | `nvc_scenario` | (scenario_type, difficulty) | title, description, scenarioType, difficulty, focusElements(JSONB), tags(JSONB) |
| `NvcAgentConfigEntity` | `nvc_agent_config` | agent_scene(unique) | agentScene(枚举), displayName, systemPrompt, temperature, maxTokens, topP, isEnabled |
| `NvcCommunicationRecordEntity` | `nvc_communication_record` | user_id | userId, title, scenarioType, rawContent, analysisResult(JSONB), nvcSuggestion |

### 2.3 Repository 接口（8 个）

| Repository | 关键查询方法 |
|-----------|-------------|
| `NvcPracticeSessionRepository` | `findByUserIdOrderByCreatedAtDesc`, `findByUserIdAndCurrentPhaseOrderByCreatedAtDesc`, `countByUserId` |
| `NvcPracticeMessageRepository` | `findBySessionIdOrderBySequenceNumAsc`, `countBySessionId` |
| `NvcEvaluationRepository` | `findBySessionIdOrderByCreatedAtAsc`, `findByUserIdOrderByCreatedAtDesc`, `findBySessionIdAndEvaluationType` |
| `NvcAgentConfigRepository` | `findByAgentScene`, `findByIsEnabledTrue` |
| `NvcUserProfileRepository` | `findByUserId`, `existsByUserId` |
| `NvcUserAbilityScoreRepository` | `findByUserIdOrderByScoredAtDesc`, `findTop30ByUserIdOrderByScoredAtDesc` |
| `NvcCommunicationRecordRepository` | `findByUserIdOrderByCreatedAtDesc` |
| `NvcScenarioRepository` | `findByScenarioTypeAndDifficulty`, `findByIsSystemTrueOrderByUsageCountDesc`, `findByScenarioType` |

### 2.4 种子数据（2 个 SQL 文件）

**`data-nvc-agent-config.sql`** — 11 条 Agent 配置：

| Agent Scene | Display Name | Temperature | Max Tokens |
|-------------|-------------|-------------|------------|
| SCENARIO_GENERATOR | 场景生成官 | 0.8 | 2000 |
| DIALOGUE_GUIDE | 对话引导官 | 0.7 | 2000 |
| DIFFICULT_PARTNER | 困难搭档 | 0.9 | 2000 |
| STEP_OBSERVE_COACH | 观察步骤教练 | 0.5 | 2000 |
| STEP_FEELING_COACH | 感受步骤教练 | 0.5 | 2000 |
| STEP_NEED_COACH | 需求步骤教练 | 0.5 | 2000 |
| STEP_REQUEST_COACH | 请求步骤教练 | 0.5 | 2000 |
| NVC_EXPRESSION_EVALUATOR | NVC表达评估官 | 0.3 | 3000 |
| EMPATHY_COACH | 共情教练 | 0.5 | 2000 |
| NVC_KNOWLEDGE_ADVISOR | NVC知识顾问 | 0.3 | 2000 |
| PROFILE_ANALYZER | 档案分析师 | 0.3 | 2000 |

**`data-nvc-scenario.sql`** — 13 条练习场景：

| 场景类型 | 数量 | 难度分布 |
|---------|------|---------|
| WORKPLACE（职场） | 3 | EASY × 1, MEDIUM × 1, HARD × 1 |
| FAMILY（家庭） | 3 | EASY × 1, MEDIUM × 1, HARD × 1 |
| INTIMATE（亲密关系） | 3 | EASY × 1, MEDIUM × 1, HARD × 1 |
| SOCIAL（社交） | 2 | EASY × 1, MEDIUM × 1 |
| SELF（自我对话） | 2 | MEDIUM × 1, HARD × 1 |

### 2.5 配置修改

**`application.yml`** 新增：

```yaml
spring:
  jpa:
    defer-datasource-initialization: true  # 新增：确保 JPA 建表后再执行 SQL
  sql:
    init:
      mode: always
      data-locations:
        - classpath:data-nvc-agent-config.sql
        - classpath:data-nvc-scenario.sql
```

---

## 三、目录结构

```
app/src/main/java/nvc/guide/modules/
├── nvcpractice/
│   ├── model/
│   │   ├── NvcPracticeMode.java          # 枚举
│   │   ├── NvcSessionPhase.java          # 枚举
│   │   ├── NvcPracticeStep.java          # 枚举
│   │   ├── NvcMessageRole.java           # 枚举
│   │   ├── NvcDifficulty.java            # 枚举
│   │   ├── NvcAgentScene.java            # 枚举（11 个值）
│   │   ├── NvcEvaluationType.java        # 枚举
│   │   ├── NvcPracticeType.java          # 枚举
│   │   ├── NvcPracticeSessionEntity.java # Entity
│   │   ├── NvcPracticeMessageEntity.java # Entity
│   │   ├── NvcEvaluationEntity.java      # Entity
│   │   └── NvcAgentConfigEntity.java     # Entity
│   └── repository/
│       ├── NvcPracticeSessionRepository.java
│       ├── NvcPracticeMessageRepository.java
│       ├── NvcEvaluationRepository.java
│       └── NvcAgentConfigRepository.java
│
├── nvcscenario/
│   ├── model/
│   │   ├── NvcScenarioType.java          # 枚举
│   │   └── NvcScenarioEntity.java        # Entity
│   └── repository/
│       └── NvcScenarioRepository.java
│
├── nvcprofile/
│   ├── model/
│   │   ├── NvcLevel.java                 # 枚举
│   │   ├── NvcCommunicationStyle.java    # 枚举
│   │   ├── NvcUserProfileEntity.java     # Entity
│   │   ├── NvcUserAbilityScoreEntity.java# Entity
│   │   └── NvcCommunicationRecordEntity.java # Entity
│   └── repository/
│       ├── NvcUserProfileRepository.java
│       ├── NvcUserAbilityScoreRepository.java
│       └── NvcCommunicationRecordRepository.java
│
├── nvcvoice/                              # 空目录，Step 8 改造
└── nvcreport/                             # 空目录，后续 Step 创建
```

---

## 四、与原 Step1 文档的差异

| 项目 | 原文档 | 实际实现 | 原因 |
|------|--------|---------|------|
| 包名 | `interview.guide.modules.*` | `nvc.guide.modules.*` | Step 0 已重命名 |
| agentScene 类型 | `String` | `NvcAgentScene` 枚举 | 编译时类型检查，与其他 Entity 一致 |
| 索引 | 无 | 6 个表共 8 个索引 | 改造方案 SQL 中有定义，JPA 需显式声明 |
| PROFILE_ANALYZER 初始数据 | 缺失 | 已补充（11 条） | 枚举与数据保持一致 |
| NvcPracticeType 位置 | `nvcprofile` 包 | `nvcpractice` 包 | 被 NvcUserAbilityScoreEntity 引用，放在 practice 包更合理 |
| 外键约束 | 无 | 保持无 | 设计决策：避免 JPA 懒加载问题 |

---

## 五、验证结果

| 验证项 | 结果 | 说明 |
|--------|------|------|
| 编译 | ✅ BUILD SUCCESSFUL | `./gradlew compileJava` 通过 |
| Agent 配置数据 | ✅ 11 条 | 含 PROFILE_ANALYZER |
| 场景库数据 | ✅ 13 条 | 5 类型 × 各难度 |
| 索引定义 | ✅ 8 个索引 | 6 个表 |
| 包名一致性 | ✅ `nvc.guide.modules.*` | 全部文件统一 |

---

## 六、设计文档

- 设计文档：`docs/superpowers/specs/2026-07-09-step1-database-entities-design.md`
- 原始文档：`docs/steps/Step1-数据库实体与Repository创建.md`

---

## 七、后续步骤

按照实施计划，下一步是 **Step 2：Agent 配置体系与调度中心**，需要：

1. 创建 DTO 类（请求/响应）
2. 创建 MapStruct Mapper
3. 实现 `NvcAgentConfigService`（Agent 配置读取 + Redis 缓存）
4. 实现 `NvcAgentOrchestrator`（Agent 调度中心）
5. 创建 `NvcPracticeSessionService`（会话管理）
6. 创建 Controller（REST API）

以及后续 Step 的依赖：
- Step 3：NVC 文字练习核心流程
- Step 4：NVC 评估引擎
- Step 5：用户档案系统
- Step 6：场景库管理
