# Step 1 数据库实体与 Repository 设计修订

> 基于 Step1 文档分析，修订后的实体设计规范
>
> 日期：2026-07-09

---

## 修订决策

| 决策项 | 结论 | 原因 |
|--------|------|------|
| userId 类型 | `Long` | 统一规范，与 JPA 自增 ID 一致 |
| agentScene 类型 | `NvcAgentScene` 枚举 | 编译时检查，与 NvcAgentConfigEntity 一致 |
| 外键关系 | 不加 `@ManyToOne` | 避免 JPA 懒加载/N+1 问题，手动管理关联 |
| PROFILE_ANALYZER | 补充到初始 SQL | 枚举与数据保持一致 |
| 包名 | `nvc.guide.modules.*` | 与实际项目一致（Step 0 已重命名） |

---

## 需要修复的问题

### 1. 包名修正

所有文件路径从 `interview.guide.modules.*` 改为 `nvc.guide.modules.*`。

### 2. 添加数据库索引

在 `@Table` 注解中添加 `indexes` 定义：

```java
// NvcPracticeSessionEntity
@Table(name = "nvc_practice_session", indexes = {
    @Index(name = "idx_nvc_session_user", columnList = "user_id"),
    @Index(name = "idx_nvc_session_phase", columnList = "current_phase")
})

// NvcPracticeMessageEntity
@Table(name = "nvc_practice_message", indexes = {
    @Index(name = "idx_nvc_message_session", columnList = "session_id, sequence_num")
})

// NvcEvaluationEntity
@Table(name = "nvc_evaluation", indexes = {
    @Index(name = "idx_nvc_eval_session", columnList = "session_id"),
    @Index(name = "idx_nvc_eval_user", columnList = "user_id, created_at")
})

// NvcUserAbilityScoreEntity
@Table(name = "nvc_user_ability_score", indexes = {
    @Index(name = "idx_nvc_ability_user", columnList = "user_id, scored_at")
})

// NvcScenarioEntity
@Table(name = "nvc_scenario", indexes = {
    @Index(name = "idx_nvc_scenario_type", columnList = "scenario_type, difficulty")
})

// NvcCommunicationRecordEntity
@Table(name = "nvc_communication_record", indexes = {
    @Index(name = "idx_nvc_comm_user", columnList = "user_id")
})
```

### 3. 统一 agentScene 字段类型

`NvcPracticeSessionEntity.agentScene` 从 `String` 改为 `NvcAgentScene` 枚举：

```java
@Enumerated(EnumType.STRING)
@Column(name = "agent_scene", length = 50)
private NvcAgentScene agentScene;
```

### 4. 补充 PROFILE_ANALYZER 初始数据

在 `data-nvc-agent-config.sql` 中添加：

```sql
('PROFILE_ANALYZER', '档案分析师', '分析用户沟通记录，更新个人档案',
'你是NVC用户档案分析师。你的职责是分析用户的沟通记录和练习数据，提取用户特征并更新个人档案。

分析维度：
1. 沟通风格：判断用户的沟通模式（自信型/被动型/攻击型/被动攻击型）
2. 性格特征：从对话中提取性格标签
3. 情绪触发点：识别容易引发用户强烈情绪的话题
4. 常见场景：统计用户最常练习的场景类型
5. 薄弱要素：分析用户在四要素中的弱项

请以JSON格式返回分析结果。', 0.3, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
```

### 5. 语音模块 userId 类型同步

> 注意：现有 `voiceinterview` 模块的 `userId` 是 `String` 类型。
> Step 8 改造时需要统一改为 `Long`，与新 Entity 保持一致。

---

## Entity 清单（修订后）

共 8 个 Entity，11 个枚举，8 个 Repository。以下仅列出与原文档不同的部分。

### 修订的 Entity

#### NvcPracticeSessionEntity（修订 agentScene 类型 + 添加索引）

```java
@Entity
@Table(name = "nvc_practice_session", indexes = {
    @Index(name = "idx_nvc_session_user", columnList = "user_id"),
    @Index(name = "idx_nvc_session_phase", columnList = "current_phase")
})
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NvcPracticeSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "practice_mode", nullable = false, length = 20)
    private NvcPracticeMode practiceMode;

    @Column(name = "scenario_id")
    private Long scenarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_phase", length = 30)
    @Builder.Default
    private NvcSessionPhase currentPhase = NvcSessionPhase.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", length = 20)
    private NvcPracticeStep currentStep;

    @Enumerated(EnumType.STRING)  // ← 从 String 改为枚举
    @Column(name = "agent_scene", length = 50)
    private NvcAgentScene agentScene;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private NvcDifficulty difficulty = NvcDifficulty.MEDIUM;

    @Column(name = "context_summary", columnDefinition = "TEXT")
    private String contextSummary;

    @Column(name = "session_config", columnDefinition = "JSONB")
    private String sessionConfig;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

#### NvcPracticeMessageEntity（添加索引）

```java
@Entity
@Table(name = "nvc_practice_message", indexes = {
    @Index(name = "idx_nvc_message_session", columnList = "session_id, sequence_num")
})
// ... 其余不变
```

#### NvcEvaluationEntity（添加索引）

```java
@Entity
@Table(name = "nvc_evaluation", indexes = {
    @Index(name = "idx_nvc_eval_session", columnList = "session_id"),
    @Index(name = "idx_nvc_eval_user", columnList = "user_id, created_at")
})
// ... 其余不变
```

#### NvcUserAbilityScoreEntity（添加索引）

```java
@Entity
@Table(name = "nvc_user_ability_score", indexes = {
    @Index(name = "idx_nvc_ability_user", columnList = "user_id, scored_at")
})
// ... 其余不变
```

#### NvcScenarioEntity（添加索引）

```java
@Entity
@Table(name = "nvc_scenario", indexes = {
    @Index(name = "idx_nvc_scenario_type", columnList = "scenario_type, difficulty")
})
// ... 其余不变
```

#### NvcCommunicationRecordEntity（添加索引）

```java
@Entity
@Table(name = "nvc_communication_record", indexes = {
    @Index(name = "idx_nvc_comm_user", columnList = "user_id")
})
// ... 其余不变
```

### 不变的 Entity

- `NvcUserProfileEntity` — 无需修改
- `NvcAgentConfigEntity` — 无需修改

### 不变的枚举（11 个）

- `NvcPracticeMode`, `NvcSessionPhase`, `NvcPracticeStep`, `NvcMessageRole`
- `NvcDifficulty`, `NvcAgentScene`, `NvcScenarioType`, `NvcEvaluationType`
- `NvcLevel`, `NvcCommunicationStyle`, `NvcPracticeType`

### 不变的 Repository（8 个）

所有 Repository 接口定义不变。

---

## 目录结构（修正包名）

```
app/src/main/java/nvc/guide/modules/
├── nvcpractice/
│   ├── model/          # Entity + 枚举
│   ├── repository/     # Repository
│   ├── dto/            # 后续 Step 创建
│   ├── service/        # 后续 Step 创建
│   ├── controller/     # 后续 Step 创建
│   └── agent/          # 后续 Step 创建
├── nvcprofile/
│   ├── model/
│   ├── repository/
│   ├── dto/
│   ├── service/
│   └── controller/
├── nvcscenario/
│   ├── model/
│   ├── repository/
│   ├── dto/
│   ├── service/
│   └── controller/
├── nvcvoice/           # Step 8 改造
└── nvcreport/          # 后续 Step 创建
```

---

## 验证清单

```
□ 所有 Entity 使用 nvc.guide.modules 包名
□ 所有 Entity 编译通过
□ 所有枚举编译通过
□ 所有 Repository 编译通过
□ 项目启动成功，数据库表自动创建
□ 索引正确创建（检查 pg_indexes 表）
□ Agent 配置初始数据插入成功（11 条，含 PROFILE_ANALYZER）
□ 场景库初始数据插入成功（13 条）
□ 无编译警告
□ Git 提交
```

---

## 与原文档差异汇总

| 项目 | 原文档 | 修订后 |
|------|--------|--------|
| 包名 | `interview.guide.modules.*` | `nvc.guide.modules.*` |
| agentScene 类型 | `String` | `NvcAgentScene` 枚举 |
| 索引 | 无 | 6 个表共 8 个索引 |
| PROFILE_ANALYZER 初始数据 | 缺失 | 已补充 |
| 外键约束 | 无 | 保持无（设计决策） |
| userId 类型 | Long | Long（确认统一） |
