# Step 1：数据库实体与 Repository 创建

> 目标：创建 NVC 项目所有核心 Entity、DTO、Repository，为后续 Service 层开发打基础
>
> 预计耗时：2-3 天
>
> 前置条件：Step 0 已完成，项目能正常启动

---

## 1.1 需要创建的 Entity 清单

共 8 个 Entity，对应 8 张数据库表。所有 Entity 使用 JPA + Lombok 注解。

### Entity 1：NvcPracticeSessionEntity

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcPracticeSessionEntity.java`

```java
package interview.guide.modules.nvcpractice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "nvc_practice_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(name = "agent_scene", length = 50)
    private String agentScene;

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

### Entity 2：NvcPracticeMessageEntity

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcPracticeMessageEntity.java`

```java
@Entity
@Table(name = "nvc_practice_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcPracticeMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NvcMessageRole role;

    @Column(name = "agent_scene", length = 50)
    private String agentScene;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NvcPracticeStep step;

    @Column(name = "sequence_num", nullable = false)
    private Integer sequenceNum;

    @Column(columnDefinition = "JSONB")
    private String metadata;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### Entity 3：NvcEvaluationEntity

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcEvaluationEntity.java`

```java
@Entity
@Table(name = "nvc_evaluation")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "observation_score")
    private Integer observationScore;

    @Column(name = "feeling_score")
    private Integer feelingScore;

    @Column(name = "need_score")
    private Integer needScore;

    @Column(name = "request_score")
    private Integer requestScore;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "empathy_score")
    private Integer empathyScore;

    @Column(name = "observation_detail", columnDefinition = "TEXT")
    private String observationDetail;

    @Column(name = "feeling_detail", columnDefinition = "TEXT")
    private String feelingDetail;

    @Column(name = "need_detail", columnDefinition = "TEXT")
    private String needDetail;

    @Column(name = "request_detail", columnDefinition = "TEXT")
    private String requestDetail;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String improvements;

    @Column(name = "reference_expressions", columnDefinition = "TEXT")
    private String referenceExpressions;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", length = 20)
    @Builder.Default
    private NvcEvaluationType evaluationType = NvcEvaluationType.REALTIME;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### Entity 4：NvcUserProfileEntity

路径：`app/src/main/java/interview/guide/modules/nvcprofile/model/NvcUserProfileEntity.java`

```java
@Entity
@Table(name = "nvc_user_profile")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcUserProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "communication_background", columnDefinition = "TEXT")
    private String communicationBackground;

    @Column(name = "personality_traits", columnDefinition = "JSONB")
    private String personalityTraits;

    @Enumerated(EnumType.STRING)
    @Column(name = "communication_style", length = 50)
    private NvcCommunicationStyle communicationStyle;

    @Column(name = "emotional_triggers", columnDefinition = "TEXT")
    private String emotionalTriggers;

    @Column(name = "common_scenarios", columnDefinition = "JSONB")
    private String commonScenarios;

    @Column(name = "relationship_types", columnDefinition = "JSONB")
    private String relationshipTypes;

    @Enumerated(EnumType.STRING)
    @Column(name = "nvc_level", length = 20)
    @Builder.Default
    private NvcLevel nvcLevel = NvcLevel.BEGINNER;

    @Column(name = "total_practice_count")
    @Builder.Default
    private Integer totalPracticeCount = 0;

    @Column(name = "total_practice_minutes")
    @Builder.Default
    private Integer totalPracticeMinutes = 0;

    @Column(name = "last_practice_at")
    private LocalDateTime lastPracticeAt;

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

注意：`profile_embedding` 字段（VECTOR(1024)）需要等 pgvector 扩展启用后再添加，或者用 `@Column(columnDefinition = "vector(1024)")` 注解。先跳过向量字段，后续 Step 10 再加。

### Entity 5：NvcUserAbilityScoreEntity

路径：`app/src/main/java/interview/guide/modules/nvcprofile/model/NvcUserAbilityScoreEntity.java`

```java
@Entity
@Table(name = "nvc_user_ability_score")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcUserAbilityScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false)
    private Integer observation;

    @Column(nullable = false)
    private Integer feeling;

    @Column(nullable = false)
    private Integer need;

    @Column(nullable = false)
    private Integer request;

    private Integer empathy;

    @Enumerated(EnumType.STRING)
    @Column(name = "practice_type", length = 20)
    private NvcPracticeType practiceType;

    @Column(name = "scored_at", updatable = false)
    private LocalDateTime scoredAt;

    @PrePersist
    protected void onCreate() {
        scoredAt = LocalDateTime.now();
    }
}
```

### Entity 6：NvcScenarioEntity

路径：`app/src/main/java/interview/guide/modules/nvcscenario/model/NvcScenarioEntity.java`

```java
@Entity
@Table(name = "nvc_scenario")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcScenarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", nullable = false, length = 50)
    private NvcScenarioType scenarioType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NvcDifficulty difficulty;

    @Column(name = "focus_elements", columnDefinition = "JSONB")
    private String focusElements;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(name = "sample_dialogue", columnDefinition = "TEXT")
    private String sampleDialogue;

    @Column(columnDefinition = "JSONB")
    private String tags;

    @Column(name = "is_system")
    @Builder.Default
    private Boolean isSystem = true;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

### Entity 7：NvcAgentConfigEntity

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcAgentConfigEntity.java`

```java
@Entity
@Table(name = "nvc_agent_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcAgentConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_scene", nullable = false, unique = true, length = 50)
    private NvcAgentScene agentScene;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "system_prompt", nullable = false, columnDefinition = "TEXT")
    private String systemPrompt;

    @Column(name = "model_provider", length = 50)
    private String modelProvider;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Builder.Default
    private Double temperature = 0.7;

    @Column(name = "max_tokens")
    @Builder.Default
    private Integer maxTokens = 2000;

    @Column(name = "top_p")
    @Builder.Default
    private Double topP = 0.9;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "config_metadata", columnDefinition = "JSONB")
    private String configMetadata;

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

### Entity 8：NvcCommunicationRecordEntity

路径：`app/src/main/java/interview/guide/modules/nvcprofile/model/NvcCommunicationRecordEntity.java`

```java
@Entity
@Table(name = "nvc_communication_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NvcCommunicationRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_type", length = 50)
    private NvcScenarioType scenarioType;

    @Column(name = "raw_content", nullable = false, columnDefinition = "TEXT")
    private String rawContent;

    @Column(name = "analysis_result", columnDefinition = "JSONB")
    private String analysisResult;

    @Column(name = "nvc_suggestion", columnDefinition = "TEXT")
    private String nvcSuggestion;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

## 1.2 需要创建的枚举类

### 枚举 1：NvcPracticeMode

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcPracticeMode.java`

```java
public enum NvcPracticeMode {
    SCENARIO,           // 场景驱动练习
    FREE_DIALOG,        // 自由对话练习
    STRUCTURED_FOUR_STEP // 结构化四步练习
}
```

### 枚举 2：NvcSessionPhase

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcSessionPhase.java`

```java
public enum NvcSessionPhase {
    CREATED,      // 已创建
    IN_PROGRESS,  // 进行中
    PAUSED,       // 已暂停
    COMPLETED,    // 已完成
    EVALUATED     // 已评估
}
```

### 枚举 3：NvcPracticeStep

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcPracticeStep.java`

```java
public enum NvcPracticeStep {
    OBSERVE,   // 观察
    FEELING,   // 感受
    NEED,      // 需求
    REQUEST,   // 请求
    COMPLETED  // 完成
}
```

### 枚举 4：NvcMessageRole

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcMessageRole.java`

```java
public enum NvcMessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}
```

### 枚举 5：NvcDifficulty

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcDifficulty.java`

```java
public enum NvcDifficulty {
    EASY,
    MEDIUM,
    HARD
}
```

### 枚举 6：NvcAgentScene

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcAgentScene.java`

```java
public enum NvcAgentScene {
    // 练习引导类
    SCENARIO_GENERATOR,      // 场景生成官
    DIALOGUE_GUIDE,          // 对话引导官
    DIFFICULT_PARTNER,       // 困难搭档

    // 结构化练习类
    STEP_OBSERVE_COACH,      // 观察步骤教练
    STEP_FEELING_COACH,      // 感受步骤教练
    STEP_NEED_COACH,         // 需求步骤教练
    STEP_REQUEST_COACH,      // 请求步骤教练

    // 评估反馈类
    NVC_EXPRESSION_EVALUATOR, // NVC 表达评估官
    EMPATHY_COACH,            // 共情教练

    // 知识辅助类
    NVC_KNOWLEDGE_ADVISOR     // NVC 知识顾问
}
```

### 枚举 7：NvcScenarioType

路径：`app/src/main/java/interview/guide/modules/nvcscenario/model/NvcScenarioType.java`

```java
public enum NvcScenarioType {
    WORKPLACE,   // 职场
    FAMILY,      // 家庭
    INTIMATE,    // 亲密关系
    SOCIAL,      // 社交
    SELF         // 自我对话
}
```

### 枚举 8：NvcEvaluationType

路径：`app/src/main/java/interview/guide/modules/nvcpractice/model/NvcEvaluationType.java`

```java
public enum NvcEvaluationType {
    REALTIME,  // 实时评估（每轮对话后）
    FINAL      // 最终评估（练习结束后）
}
```

### 枚举 9：NvcLevel

路径：`app/src/main/java/interview/guide/modules/nvcprofile/model/NvcLevel.java`

```java
public enum NvcLevel {
    BEGINNER,      // 初学者
    INTERMEDIATE,  // 中级
    ADVANCED       // 高级
}
```

### 枚举 10：NvcCommunicationStyle

路径：`app/src/main/java/interview/guide/modules/nvcprofile/model/NvcCommunicationStyle.java`

```java
public enum NvcCommunicationStyle {
    ASSERTIVE,    // 自信型
    PASSIVE,      // 被动型
    AGGRESSIVE,   // 攻击型
    PASSIVE_AGGRESSIVE, // 被动攻击型
    NVC           // NVC 型
}
```

### 枚举 11：NvcPracticeType

路径：`app/src/main/java/interview/guide/modules/nvcprofile/model/NvcPracticeType.java`

```java
public enum NvcPracticeType {
    TEXT,   // 文字练习
    VOICE   // 语音练习
}
```

## 1.3 需要创建的 Repository

### Repository 1：NvcPracticeSessionRepository

路径：`app/src/main/java/interview/guide/modules/nvcpractice/repository/NvcPracticeSessionRepository.java`

```java
package interview.guide.modules.nvcpractice.repository;

import interview.guide.modules.nvcpractice.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NvcPracticeSessionRepository extends JpaRepository<NvcPracticeSessionEntity, Long> {

    List<NvcPracticeSessionEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<NvcPracticeSessionEntity> findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(
        Long userId, NvcSessionPhase phase);

    long countByUserId(Long userId);
}
```

### Repository 2：NvcPracticeMessageRepository

路径：`app/src/main/java/interview/guide/modules/nvcpractice/repository/NvcPracticeMessageRepository.java`

```java
public interface NvcPracticeMessageRepository extends JpaRepository<NvcPracticeMessageEntity, Long> {

    List<NvcPracticeMessageEntity> findBySessionIdOrderBySequenceNumAsc(Long sessionId);

    int countBySessionId(Long sessionId);
}
```

### Repository 3：NvcEvaluationRepository

路径：`app/src/main/java/interview/guide/modules/nvcpractice/repository/NvcEvaluationRepository.java`

```java
public interface NvcEvaluationRepository extends JpaRepository<NvcEvaluationEntity, Long> {

    List<NvcEvaluationEntity> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    List<NvcEvaluationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<NvcEvaluationEntity> findBySessionIdAndEvaluationType(
        Long sessionId, NvcEvaluationType type);
}
```

### Repository 4：NvcUserProfileRepository

路径：`app/src/main/java/interview/guide/modules/nvcprofile/repository/NvcUserProfileRepository.java`

```java
public interface NvcUserProfileRepository extends JpaRepository<NvcUserProfileEntity, Long> {

    Optional<NvcUserProfileEntity> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
```

### Repository 5：NvcUserAbilityScoreRepository

路径：`app/src/main/java/interview/guide/modules/nvcprofile/repository/NvcUserAbilityScoreRepository.java`

```java
public interface NvcUserAbilityScoreRepository extends JpaRepository<NvcUserAbilityScoreEntity, Long> {

    List<NvcUserAbilityScoreEntity> findByUserIdOrderByScoredAtDesc(Long userId);

    List<NvcUserAbilityScoreEntity> findTop30ByUserIdOrderByScoredAtDesc(Long userId);
}
```

### Repository 6：NvcScenarioRepository

路径：`app/src/main/java/interview/guide/modules/nvcscenario/repository/NvcScenarioRepository.java`

```java
public interface NvcScenarioRepository extends JpaRepository<NvcScenarioEntity, Long> {

    List<NvcScenarioEntity> findByScenarioTypeAndDifficulty(
        NvcScenarioType type, NvcDifficulty difficulty);

    List<NvcScenarioEntity> findByIsSystemTrueOrderByUsageCountDesc();

    List<NvcScenarioEntity> findByScenarioType(NvcScenarioType type);
}
```

### Repository 7：NvcAgentConfigRepository

路径：`app/src/main/java/interview/guide/modules/nvcpractice/repository/NvcAgentConfigRepository.java`

```java
public interface NvcAgentConfigRepository extends JpaRepository<NvcAgentConfigEntity, Long> {

    Optional<NvcAgentConfigEntity> findByAgentScene(NvcAgentScene agentScene);

    List<NvcAgentConfigEntity> findByIsEnabledTrue();
}
```

### Repository 8：NvcCommunicationRecordRepository

路径：`app/src/main/java/interview/guide/modules/nvcprofile/repository/NvcCommunicationRecordRepository.java`

```java
public interface NvcCommunicationRecordRepository extends JpaRepository<NvcCommunicationRecordEntity, Long> {

    List<NvcCommunicationRecordEntity> findByUserIdOrderByCreatedAtDesc(Long userId);
}
```

## 1.4 需要创建的目录结构

确保以下目录存在（不存在则创建）：

```
app/src/main/java/interview/guide/modules/
├── nvcpractice/
│   ├── model/
│   ├── repository/
│   ├── dto/
│   ├── service/
│   ├── controller/
│   └── agent/
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
├── nvcvoice/（暂时空目录，后续 Step 创建）
│   ├── model/
│   ├── repository/
│   ├── dto/
│   ├── service/
│   ├── controller/
│   └── handler/
└── nvcreport/（暂时空目录，后续 Step 创建）
    ├── dto/
    ├── service/
    └── controller/
```

## 1.5 初始数据准备

### 1.5.1 Agent 配置初始数据

创建文件：`app/src/main/resources/data-nvc-agent-config.sql`

```sql
INSERT INTO nvc_agent_config (agent_scene, display_name, description, system_prompt, temperature, max_tokens, top_p, is_enabled, created_at, updated_at) VALUES

('SCENARIO_GENERATOR', '场景生成官', '根据用户档案和偏好生成NVC练习场景',
'你是NVC非暴力沟通练习的场景设计师。你的职责是根据用户的沟通背景、薄弱要素和偏好，生成真实、有代入感的NVC练习场景。

生成场景时请包含：
1. 场景标题
2. 详细背景描述（人物关系、具体情境、冲突起因）
3. 对方角色设定（性格、说话风格、情绪状态）
4. 重点练习的NVC要素（观察/感受/需求/请求中的1-2个）
5. 建议难度（EASY/MEDIUM/HARD）

场景类型可以是：职场冲突、家庭矛盾、亲密关系、社交困境、自我对话。

请以JSON格式返回场景信息。', 0.8, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('DIALOGUE_GUIDE', '对话引导官', '模拟真实对话对象，引导用户练习NVC',
'你是NVC非暴力沟通练习的对话引导官。你将扮演一个真实的对话对象，与用户进行模拟对话。

你的行为准则：
1. 扮演场景中设定的角色，保持角色一致性
2. 用自然、真实的语气回应，不要过于教条
3. 当用户表达不完整时，用提问引导他们完善（如："你能具体说说是什么让你有这种感觉吗？"）
4. 当用户使用了评判性语言时，温和地引导他们转向观察性表达
5. 当用户表达了好的NVC表达时，给予积极回应
6. 适时表达自己的感受和需求，为用户示范NVC表达

注意：你不是在教学，而是在真实对话中自然引导。', 0.7, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('DIFFICULT_PARTNER', '困难搭档', '故意制造对抗情绪，训练用户在压力下用NVC',
'你是NVC非暴力沟通练习中的"困难搭档"。你会扮演一个带有情绪、会用评判和指责方式说话的对话对象。

你的行为准则：
1. 用带有评判、指责、情绪化的语言回应（如："你总是这样！"、"你怎么这么自私？"）
2. 模拟真实中人们在冲突中的常见表达方式
3. 不要太极端，保持在真实可信的范围内
4. 当用户坚持用NVC方式表达时，逐渐软化态度
5. 当用户被激怒或放弃NVC时，适度收敛，给用户练习空间
6. 对话结束时，从角色中跳出，说明你的"困难"表现是故意设计的

目的：训练用户在面对对抗性语言时，仍能保持NVC表达方式。', 0.9, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('STEP_OBSERVE_COACH', '观察步骤教练', '引导用户区分观察和评论',
'你是NVC练习的"观察步骤教练"。你的职责是引导用户学会区分"观察"（客观描述事实）和"评论"（带有主观判断）。

教学要点：
1. 观察：描述你看到、听到、触摸到的具体事实，不加解释和判断
   - ✅ "你这周有三天都是晚上10点以后回家"
   - ❌ "你总是不着家"
2. 评论：加入了个人解读、推断、标签
   - ❌ "你不关心这个家"
   - ✅ "上周你没有参加孩子的家长会"

引导方式：
- 给出一段冲突描述，让用户识别其中的观察和评论
- 用户练习后，逐句分析哪些是观察、哪些是评论
- 提供改写建议，帮助用户把评论转化为观察
- 用具体例子帮助用户理解区别', 0.5, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('STEP_FEELING_COACH', '感受步骤教练', '引导用户识别和表达感受',
'你是NVC练习的"感受步骤教练"。你的职责是引导用户学会识别和表达真实的感受，而不是想法和判断。

教学要点：
1. 感受：描述你内心的情绪状态
   - ✅ "我感到失落"、"我很担心"、"我有些委屈"
2. 想法/判断：对他人行为的解读
   - ❌ "我觉得你不尊重我"（这是对对方的判断）
   - ❌ "我觉得自己没用"（这是对自己的评判）
3. 常见误区：用"我觉得"开头的往往是想法，不是感受

引导方式：
- 给出一段表达，让用户区分哪些是感受、哪些是想法
- 帮助用户找到更准确的感受词汇（如不只是"不开心"，而是"失望"、"焦虑"、"疲惫"等）
- 引导用户从"你让我感到..."转向"我感到..."的表达方式
- 提供感受词汇表辅助练习', 0.5, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('STEP_NEED_COACH', '需求步骤教练', '引导用户发现深层需求',
'你是NVC练习的"需求步骤教练"。你的职责是引导用户从感受追溯到深层需求。

教学要点：
1. 需求：人类共通的基本需求（安全感、尊重、理解、归属感、自主性、成长等）
2. 策略：满足需求的具体方式（不是需求本身）
   - 需求："我需要陪伴"
   - 策略："你每天晚上7点陪我吃饭"（这是策略，不是需求）
3. 区分：需求是普适的，策略是具体的

引导方式：
- 给出一段感受表达，引导用户思考"这种感受背后，你真正需要的是什么？"
- 帮助用户区分需求和策略
- 引导用户用"我需要..."的句式表达需求
- 提供常见需求词汇表', 0.5, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('STEP_REQUEST_COACH', '请求步骤教练', '引导用户提出具体可执行的请求',
'你是NVC练习的"请求步骤教练"。你的职责是引导用户将需求转化为具体、可执行、正向的请求。

教学要点：
1. 请求 vs 命令：请求允许对方说"不"，命令不允许
2. 正向表达：说你想要什么，而不是你不想要什么
   - ✅ "你能每周六花一小时陪我散步吗？"
   - ❌ "你不要再加班了"
3. 具体可行：越具体越好，避免模糊
   - ✅ "你能在下次汇报时提到我负责的部分吗？"
   - ❌ "你要尊重我的付出"

引导方式：
- 给出一个需求，引导用户转化为具体请求
- 检查请求是否满足：正向、具体、可执行、允许拒绝
- 帮助用户练习用"你愿意...吗？"的句式
- 模拟对方可能的回应，练习协商', 0.5, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('NVC_EXPRESSION_EVALUATOR', 'NVC表达评估官', '评估用户表达中的四要素质量',
'你是NVC表达评估专家。你的职责是严格分析用户的每句话，评估其NVC四要素的质量。

评估维度：
1. 观察（Observation）：是否区分了观察和评论？是否描述了具体事实？
2. 感受（Feeling）：是否表达了真实感受？是否区分了感受和想法？
3. 需求（Need）：是否识别了深层需求？是否区分了需求和策略？
4. 请求（Request）：是否提出了具体、可执行、正向的请求？

评分标准（每项0-100）：
- 90-100：优秀，完全符合NVC表达方式
- 70-89：良好，基本符合但有改进空间
- 50-69：一般，需要明显改进
- 0-49：需要大幅改进，存在根本性误解

请以JSON格式返回评估结果，包含：
- observation_score, feeling_score, need_score, request_score, overall_score（取四维平均）
- observation_detail, feeling_detail, need_detail, request_detail（每维度的详细分析）
- strengths（优势）
- improvements（改进建议）
- reference_expressions（参考NVC表达）', 0.3, 3000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('EMPATHY_COACH', '共情教练', '分析用户的倾听和共情能力',
'你是共情倾听教练。你的职责是分析用户在对话中的共情能力。

评估维度：
1. 主动倾听：是否关注对方的感受和需求，而非急于回应
2. 情感确认：是否承认和理解对方的感受
3. 需求理解：是否试图理解对方的深层需求
4. 非评判态度：是否避免评判和建议，而是用理解回应

引导方式：
- 分析用户对话中哪些回应体现了共情，哪些缺乏共情
- 提供共情回应的示范（如："听起来你感到很...因为你需要..."）
- 帮助用户练习"反映式倾听"技巧', 0.5, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),

('NVC_KNOWLEDGE_ADVISOR', 'NVC知识顾问', '回答NVC理论问题，结合RAG检索',
'你是非暴力沟通（NVC）知识专家。你的职责是回答用户关于NVC理论和实践的问题。

回答原则：
1. 基于马歇尔·卢森堡的《非暴力沟通》原著理论
2. 结合知识库中的NVC文档内容回答（RAG检索结果会提供给你）
3. 用通俗易懂的语言解释，避免过于学术化
4. 用具体例子帮助用户理解抽象概念
5. 如果用户的问题涉及实际困境，引导他们用NVC框架思考

知识库内容会以[参考资料]的形式提供给你，请优先基于这些内容回答。', 0.3, 2000, 0.9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
```

### 1.5.2 场景库初始数据

创建文件：`app/src/main/resources/data-nvc-scenario.sql`

```sql
INSERT INTO nvc_scenario (title, description, scenario_type, difficulty, focus_elements, context, tags, is_system, usage_count, created_at) VALUES

-- 职场场景
('同事抢功', '你在团队项目中做了大部分技术实现工作，但同事在向领导汇报时把主要功劳归于自己，几乎没有提及你的贡献。你需要和这位同事沟通这个问题。',
 'WORKPLACE', 'MEDIUM', '["observation", "request"]',
 '你和这位同事同在一个5人技术团队，关系平时还可以，不算特别亲近但也无矛盾。这次项目持续了两个月，核心架构和难点攻克都是你完成的。领导对汇报很满意，但你以为的"共同汇报"变成了他一个人的演讲。',
 '["职场", "抢功", "沟通", "表达诉求"]', true, 0, CURRENT_TIMESTAMP),

('被领导当众批评', '在团队会议上，领导当着所有同事的面严厉批评了你最近的一个方案，措辞比较尖锐，让你感到很难堪。你需要会后和领导沟通。',
 'WORKPLACE', 'HARD', '["observation", "feeling"]',
 '你入职半年，一直努力表现。这次方案确实有一些考虑不周的地方，但领导的批评方式让你觉得不只是在讨论方案，而是在否定你这个人。其他同事在场，你感到很尴尬。',
 '["职场", "领导", "批评", "难堪", "沟通"]', true, 0, CURRENT_TIMESTAMP),

('同事总是拖延影响你', '你的上游同事总是延迟交付他负责的部分，导致你的工作也被迫延期。这已经是第三次了，你需要和他沟通。',
 'WORKPLACE', 'EASY', '["observation", "request"]',
 '你们是不同模块的开发者，你的工作依赖他的接口。每次他都说"快了快了"，但总是要拖一两天。你不想越级汇报，想先自己解决。',
 '["职场", "拖延", "依赖", "协作"]', true, 0, CURRENT_TIMESTAMP),

-- 家庭场景
('父母催婚', '过年回家，父母又开始催你结婚，甚至安排了相亲。你感到压力很大，但又不想和父母吵架。',
 'FAMILY', 'MEDIUM', '["feeling", "need"]',
 '你今年28岁，单身，在外地工作。父母每次通电话都会提到找对象的事，这次回家更是直接安排了相亲对象来家里。你理解他们的担心，但觉得自己的生活节奏被打乱了。',
 '["家庭", "催婚", "父母", "压力", "边界"]', true, 0, CURRENT_TIMESTAMP),

('和父母的消费观冲突', '你给自己买了一件比较贵的衣服或电子产品，父母知道后很不高兴，说你乱花钱、不知道存钱。',
 'FAMILY', 'EASY', '["observation", "feeling"]',
 '你已经工作有收入，经济独立。这件东西你考虑了很久才买的，觉得是对自己辛苦工作的奖励。但父母那一代人习惯了节俭，觉得这是浪费。',
 '["家庭", "消费观", "代际", "冲突"]', true, 0, CURRENT_TIMESTAMP),

('父母偏心', '你感觉父母明显偏心你的兄弟姐妹，无论是关注度还是资源分配上都偏向对方。你需要和父母表达你的感受。',
 'FAMILY', 'HARD', '["feeling", "need"]',
 '你有一个比你小几岁的弟弟/妹妹，从小你就觉得父母更关注ta。最近父母帮弟弟/妹妹付了首付，但当初你买房时他们说"要靠自己"。你不想破坏家庭关系，但内心很委屈。',
 '["家庭", "偏心", "委屈", "公平", "需求"]', true, 0, CURRENT_TIMESTAMP),

-- 亲密关系场景
('伴侣忽视你的感受', '你今天过得很糟糕，想和伴侣倾诉，但对方一直在看手机敷衍回应，让你感到被忽视。',
 'INTIMATE', 'EASY', '["observation", "feeling"]',
 '你们在一起两年了，住在一起。今天你工作上遇到了很大的挫折，回家想聊聊。但伴侣在刷短视频，你说的时候ta只是"嗯""哦"地回应，没有抬头看你。',
 '["亲密关系", "忽视", "感受", "倾听"]', true, 0, CURRENT_TIMESTAMP),

('和伴侣的家务分配问题', '你觉得家务大部分都是你在做，伴侣很少主动分担。你感到疲惫和不公平。',
 'INTIMATE', 'MEDIUM', '["observation", "request"]',
 '你们同居半年了，你发现做饭、洗碗、打扫卫生基本都是你在做。伴侣偶尔会帮忙，但需要你提醒。你不想变成"唠叨的人"，但确实很累。',
 '["亲密关系", "家务", "公平", "疲惫"]', true, 0, CURRENT_TIMESTAMP),

('伴侣和异性朋友走得近', '你的伴侣有一个关系很好的异性朋友，他们的互动让你感到不安。你需要表达你的感受而不是指责。',
 'INTIMATE', 'HARD', '["feeling", "need"]',
 '伴侣的这个异性朋友是大学同学，认识比你早。他们会单独吃饭、聊天很频繁。你不想显得控制欲强，但确实感到不安和嫉妒。',
 '["亲密关系", "安全感", "嫉妒", "信任"]', true, 0, CURRENT_TIMESTAMP),

-- 社交场景
('朋友借钱不还', '你的朋友半年前借了你一笔钱，说好一个月还，但到现在都没还。你不好意思催，但确实需要这笔钱。',
 'SOCIAL', 'MEDIUM', '["observation", "request"]',
 '你们关系不错，经常一起吃饭聚会。借钱时对方说得很肯定，但之后再也没提过。你催过一次，对方说"最近手头紧"，然后就没下文了。',
 '["社交", "借钱", "催还", "尴尬"]', true, 0, CURRENT_TIMESTAMP),

('被朋友开玩笑伤到', '朋友在聚会中开了一个关于你的玩笑，虽然大家都在笑，但你感到被冒犯了。',
 'SOCIAL', 'EASY', '["observation", "feeling"]',
 '朋友拿你的外貌/收入/感情状况开了一个玩笑，在场的人都笑了。你知道朋友可能没有恶意，但你确实感到不舒服。你不想破坏气氛，但也不想一直忍着。',
 '["社交", "玩笑", "冒犯", "边界"]', true, 0, CURRENT_TIMESTAMP),

-- 自我对话场景
('对自己的完美主义', '你又因为一件小事没做到完美而自责，内心有一个严厉的声音在批评你。',
 'SELF', 'MEDIUM', '["observation", "feeling"]',
 '你是一个对自己要求很高的人。今天工作中一个小细节没注意到，虽然影响不大，但你一直在内心责备自己"怎么这么粗心""什么都做不好"。',
 '["自我对话", "完美主义", "自责", "内耗"]', true, 0, CURRENT_TIMESTAMP),

('职业选择的迷茫', '你对现在的工作感到迷茫，不确定是否要换方向。你需要和自己对话，理清内心真正的需求。',
 'SELF', 'HARD', '["need", "request"]',
 '你做了3年现在的工作，薪资还行但总觉得没有热情。看到别人做其他方向好像更有意思，但又不确定。家人希望你稳定，你内心很纠结。',
 '["自我对话", "职业", "迷茫", "需求"]', true, 0, CURRENT_TIMESTAMP);
```

## 1.6 数据初始化方式

在 `application.yml` 中配置初始化 SQL 执行：

```yaml
spring:
  jpa:
    defer-datasource-initialization: true
  sql:
    init:
      mode: always
      data-locations:
        - classpath:data-nvc-agent-config.sql
        - classpath:data-nvc-scenario.sql
```

**注意**：`spring.jpa.defer-datasource-initialization: true` 确保 JPA 建表完成后才执行 SQL。

## 1.7 验证清单

```
□ 所有 Entity 类编译通过
□ 所有枚举类编译通过
□ 所有 Repository 接口编译通过
□ 项目启动成功，数据库表自动创建
□ Agent 配置初始数据插入成功（查询 nvc_agent_config 表确认 10 条记录）
□ 场景库初始数据插入成功（查询 nvc_scenario 表确认 13 条记录）
□ Swagger UI 显示新的 Entity（如果配了 OpenAPI）
□ Git 提交："Step 1: Add NVC entities, enums, repositories and seed data"
```
