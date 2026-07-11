# Step 5: 用户档案系统设计

## 1. 概述

### 目标

实现用户个人档案管理 — NVC 能力画像、沟通背景、性格分析、真实沟通记录分析、能力趋势。

### 技术亮点

- 用户档案向量化（为 Step 10 RAG 个性化做准备）
- 能力趋势追踪
- 沟通记录 AI 分析

### 前置条件

Step 4 已完成，评估引擎就绪。

---

## 2. 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                    NvcProfileController                     │
│                    NvcDashboardController                   │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    NvcProfileService                        │
│              NvcCommunicationAnalysisService                │
│                    NvcDashboardService                      │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│          NvcUserProfileRepository                           │
│          NvcUserAbilityScoreRepository                      │
│          NvcCommunicationRecordRepository                   │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│          PostgreSQL (nvc_user_profile,                      │
│          nvc_user_ability_score, nvc_communication_record)  │
└─────────────────────────────────────────────────────────────┘
```

### 个性化应用架构

```
┌─────────────────────────────────────────────────────────────┐
│                    用户档案 (PostgreSQL)                     │
│  结构化数据：风格、等级、偏好、统计                            │
└────────────────────────────┬────────────────────────────────┘
                             │ 实时查询
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    Agent 对话系统                            │
│  - 场景生成：根据 commonScenarios 选择                        │
│  - 对话引导：根据 communicationStyle 调整                     │
│  - 练习评估：根据薄弱环节重点反馈                              │
└─────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    NVC 知识库 (pgvector)                     │
│  非结构化知识：理论文档、案例、技巧                            │
└─────────────────────────────────────────────────────────────┘
                             │ RAG 召回
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    NVC 知识顾问 Agent                         │
│  回答用户关于 NVC 理论的问题                                   │
└─────────────────────────────────────────────────────────────┘
```

**核心思路：**
- **用户档案**：实时查询，用于个性化对话策略
- **知识库**：RAG 召回，用于回答理论问题

---

## 3. 文件清单

### 需创建的文件

| 类型 | 文件名 | 职责 |
|------|--------|------|
| Service | NvcProfileService.java | 档案 CRUD + 能力分数更新 + 等级计算 |
| Service | NvcCommunicationAnalysisService.java | LLM 分析沟通记录 |
| Service | NvcDashboardService.java | 练习统计 |
| DTO | UserProfileDTO.java | 档案响应 |
| DTO | UserProfileUpdateRequest.java | 档案更新请求 |
| DTO | AbilityRadarDTO.java | 雷达图数据 |
| DTO | AbilityTrendDTO.java | 趋势数据 |
| DTO | CommunicationRecordDTO.java | 沟通记录响应 |
| DTO | CommunicationAnalysisRequest.java | 分析请求 |
| DTO | CommunicationAnalysisResult.java | LLM 分析结果（结构化输出） |
| Controller | NvcProfileController.java | 档案 + 沟通记录 API |
| Controller | NvcDashboardController.java | Dashboard API |
| Prompt | nvc-profile-analyze-system.st | 分析提示词 |

### 需修改的文件

| 文件 | 修改内容 |
|------|----------|
| NvcEvaluateStreamConsumer.java | 集成档案更新 |

---

## 4. NvcProfileService 设计

### 职责

1. **档案 CRUD**：获取/创建/更新用户档案
2. **能力分数更新**：练习完成后记录四要素分数
3. **等级计算**：基于最近 10 次练习平均分计算 NVC 等级
4. **雷达图数据**：返回最近 10 次平均分
5. **趋势数据**：返回最近 30 次分数变化
6. **Prompt 注入**：预留接口，获取用户画像字符串用于 Prompt

### 核心方法

```java
// 获取档案（不存在则自动创建）
NvcUserProfileEntity getOrCreateProfile(Long userId)

// 更新档案字段（沟通背景、性格、风格等）
NvcUserProfileEntity updateProfile(Long userId, UserProfileUpdateRequest request)

// 练习结束后更新能力分数 + 统计 + 等级
void updateAbilityScore(Long userId, Long sessionId,
                        NvcEvaluationEntity evaluation, NvcPracticeType practiceType)

// 雷达图数据（最近 10 次平均）
AbilityRadarDTO getAbilityRadar(Long userId)

// 趋势数据（最近 30 次）
List<AbilityTrendDTO> getAbilityTrends(Long userId)

// 预留：获取用户画像字符串，用于 Prompt 注入
String getUserProfilePrompt(Long userId)
```

### 等级计算规则

| 条件 | 等级 |
|------|------|
| 练习次数 < 3 | BEGINNER |
| 平均分 >= 80 | ADVANCED |
| 平均分 >= 60 | INTERMEDIATE |
| 其他 | BEGINNER |

### 档案信息来源规划

**当前实现（模式 A）：用户手动填写**

```
用户 → 填写表单 → PUT /api/nvc/profile → 保存到 DB
```

**预留接口（模式 B）：AI 从对话中自动分析**

```java
// 后续实现：从对话中提取特征更新档案
void enrichProfileFromDialogue(Long userId, List<NvcPracticeMessageEntity> messages)
```

**字段来源规划：**

| 字段 | 来源 | 说明 |
|------|------|------|
| communicationBackground | 用户填写 | 个人背景，AI 无法得知 |
| personalityTraits | AI 分析 + 用户覆盖 | 从对话模式推断 |
| communicationStyle | AI 分析 + 用户覆盖 | 从表达方式推断 |
| emotionalTriggers | AI 分析 + 用户覆盖 | 从情绪反应模式推断 |
| commonScenarios | 用户选择 | 用户自己选择常练习的场景 |
| relationshipTypes | 用户选择 | 用户自己选择常练习的关系类型 |

---

## 5. NvcCommunicationAnalysisService 设计

### 职责

1. 用户上传真实沟通记录
2. AI 分析记录中的 NVC 四要素质量
3. 生成 NVC 改写建议
4. 保存分析结果

### 核心流程

```
用户提交沟通记录 → 构建分析 Prompt → 调用 LLM → 解析结构化结果 → 保存到 DB
```

### 结构化输出 DTO

```java
// CommunicationAnalysisResult.java
public record CommunicationAnalysisResult(
    String observationAnalysis,    // 观察维度分析
    String feelingAnalysis,        // 感受维度分析
    String needAnalysis,           // 需求维度分析
    String requestAnalysis,        // 请求维度分析
    Integer observationScore,      // 观察评分 0-100
    Integer feelingScore,          // 感受评分 0-100
    Integer needScore,             // 需求评分 0-100
    Integer requestScore,          // 请求评分 0-100
    Integer overallScore,          // 总分 0-100
    String overallAssessment,      // 整体评估
    String nvcRewrite,             // NVC 改写版本
    List<String> keyImprovements   // 关键改进建议
) {}
```

### 核心方法

```java
// 分析并保存沟通记录
NvcCommunicationRecordEntity analyzeAndSave(Long userId, CommunicationAnalysisRequest request)

// 获取用户的沟通记录列表
List<NvcCommunicationRecordEntity> getUserRecords(Long userId)
```

### LLM 集成

```java
ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);
BeanOutputConverter<CommunicationAnalysisResult> converter =
    new BeanOutputConverter<>(CommunicationAnalysisResult.class);

CommunicationAnalysisResult result = structuredOutputInvoker.invoke(
    chatClient,
    systemPrompt,
    userPrompt,
    converter,
    ErrorCode.NVC_ANALYSIS_FAILED,
    "沟通记录分析失败: ",
    "CommunicationAnalysis",
    log
);
```

---

## 6. NvcDashboardService 设计

### 基础版本

```java
@Service
@RequiredArgsConstructor
public class NvcDashboardService {

    private final NvcPracticeSessionRepository sessionRepository;
    private final NvcUserAbilityScoreRepository abilityScoreRepository;
    private final NvcUserProfileRepository profileRepository;

    /**
     * 获取用户练习统计（基础版本）
     */
    public Map<String, Object> getUserStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalSessions", sessionRepository.countByUserId(userId));
        stats.put("completedSessions", getCompletedCount(userId));
        stats.put("totalScores", abilityScoreRepository.findByUserIdOrderByScoredAtDesc(userId).size());
        return stats;
    }
}
```

### 预留扩展点

后续可添加：
- 按时间维度统计（每日/每周练习次数）
- 按练习类型分布
- 连续练习天数
- 最近活跃度趋势

---

## 7. Controller API 设计

### NvcProfileController

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/nvc/profile?userId={id} | 获取用户档案 |
| PUT | /api/nvc/profile?userId={id} | 更新用户档案 |
| GET | /api/nvc/profile/ability-radar?userId={id} | 获取雷达图数据 |
| GET | /api/nvc/profile/ability-trends?userId={id} | 获取趋势数据 |
| POST | /api/nvc/profile/communication-records/analyze | 分析沟通记录 |
| GET | /api/nvc/profile/communication-records?userId={id} | 获取沟通记录列表 |

### NvcDashboardController

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/nvc/dashboard/stats?userId={id} | 获取练习统计 |

### 请求/响应示例

**获取档案响应：**
```json
{
  "code": 200,
  "data": {
    "userId": 1,
    "communicationBackground": "5年产品经理，经常需要和开发沟通需求",
    "personalityTraits": "[\"内向\",\"注重细节\"]",
    "communicationStyle": "PASSIVE",
    "nvcLevel": "BEGINNER",
    "totalPracticeCount": 15,
    "totalPracticeMinutes": 45,
    "lastPracticeAt": "2026-07-10T14:30:00",
    "abilityRadar": {
      "observation": 72,
      "feeling": 65,
      "need": 58,
      "request": 70,
      "empathy": 55,
      "overall": 66,
      "level": "INTERMEDIATE"
    }
  }
}
```

**更新档案请求：**
```json
{
  "communicationBackground": "5年产品经理...",
  "personalityTraits": "[\"内向\",\"注重细节\"]",
  "communicationStyle": "PASSIVE",
  "emotionalTriggers": "被否定、被忽视",
  "commonScenarios": "[\"WORKPLACE\",\"FAMILY\"]",
  "relationshipTypes": "[\"同事\",\"家人\"]"
}
```

**分析沟通记录请求：**
```json
{
  "title": "和开发的需求沟通",
  "rawContent": "你总是不按需求做！每次都这样...",
  "scenarioType": "WORKPLACE"
}
```

**分析沟通记录响应：**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "title": "和开发的需求沟通",
    "scenarioType": "WORKPLACE",
    "rawContent": "你总是不按需求做！每次都这样...",
    "analysisResult": "{\"observationAnalysis\":\"...\",\"feelingAnalysis\":\"...\"}",
    "nvcSuggestion": "我注意到这次需求文档中的3个功能点..."
  }
}
```

---

## 8. 与评估引擎集成

### NvcEvaluateStreamConsumer 修改

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class NvcEvaluateStreamConsumer extends AbstractStreamConsumer<NvcEvaluateStreamMessage> {

    private final NvcEvaluationService evaluationService;
    private final NvcPracticeSessionService sessionService;
    private final NvcProfileService profileService;  // 新增注入

    @Override
    protected void consume(NvcEvaluateStreamMessage message) {
        // ... 现有逻辑 ...
        NvcEvaluationEntity saved = evaluationService.evaluateFinal(
            message.getSessionId(), message.getUserId(), messages);

        // 新增：更新用户档案能力分数
        profileService.updateAbilityScore(
            message.getUserId(),
            message.getSessionId(),
            saved,
            NvcPracticeType.TEXT
        );
        log.info("User profile ability score updated: userId={}", message.getUserId());

        // ... 后续逻辑 ...
    }
}
```

---

## 9. Prompt 设计

### nvc-profile-analyze-system.st

```
你是 NVC（非暴力沟通）表达分析专家。你的职责是分析用户提供的真实沟通记录，评估其中的 NVC 四要素质量，并给出改写建议。

分析维度：
1. 观察：哪些是客观事实描述，哪些是主观评判
2. 感受：哪些是真实的情绪表达，哪些是想法/判断伪装成感受
3. 需求：沟通中隐含了哪些深层需求
4. 请求：有没有具体可执行的请求，还是只有命令或指责

请以 JSON 格式返回分析结果：

{
  "observationAnalysis": "观察维度分析：哪些是事实，哪些是评判",
  "feelingAnalysis": "感受维度分析：哪些是真实感受，哪些是想法",
  "needAnalysis": "需求维度分析：隐含了哪些需求",
  "requestAnalysis": "请求维度分析：有没有具体请求",
  "observationScore": 0-100,
  "feelingScore": 0-100,
  "needScore": 0-100,
  "requestScore": 0-100,
  "overallScore": 0-100,
  "overallAssessment": "整体评估",
  "nvcRewrite": "用 NVC 方式改写的完整版本",
  "keyImprovements": ["关键改进建议1", "关键改进建议2"]
}
```

---

## 10. 验证清单

- [ ] NvcProfileService 编译通过
  - [ ] getOrCreateProfile() 首次调用自动创建默认档案
  - [ ] updateProfile() 能更新各个字段
  - [ ] updateAbilityScore() 能记录能力分数并更新等级
  - [ ] getAbilityRadar() 返回正确的平均分
  - [ ] getAbilityTrends() 返回最近 30 次的趋势数据
- [ ] NvcCommunicationAnalysisService 编译通过
  - [ ] analyzeAndSave() 能调用 LLM 分析沟通记录
  - [ ] 分析结果包含四要素评分和改写建议
- [ ] API 测试：
  - [ ] GET /api/nvc/profile?userId=1 返回用户档案
  - [ ] PUT /api/nvc/profile?userId=1 更新档案
  - [ ] GET /api/nvc/profile/ability-radar?userId=1 返回雷达图数据
  - [ ] GET /api/nvc/profile/ability-trends?userId=1 返回趋势数据
  - [ ] POST /api/nvc/profile/communication-records/analyze 分析沟通记录
  - [ ] GET /api/nvc/profile/communication-records?userId=1 返回记录列表
- [ ] 集成验证：
  - [ ] 完成一次练习后，能力分数自动更新
  - [ ] 完成 3 次练习后，NVC 等级自动计算
