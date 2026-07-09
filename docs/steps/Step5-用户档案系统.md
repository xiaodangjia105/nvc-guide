# Step 5：用户档案系统

> 目标：实现用户个人档案管理 — NVC 能力画像、沟通背景、性格分析、真实沟通记录分析、能力趋势
>
> 预计耗时：2-3 天
>
> 前置条件：Step 4 已完成，评估引擎就绪
>
> 技术亮点：用户档案向量化（为 Step 10 RAG 个性化做准备）、能力趋势追踪

---

## 5.1 本步要创建的文件清单

```
app/src/main/java/interview/guide/modules/nvcprofile/
├── service/
│   ├── NvcProfileService.java             ← 档案 CRUD + 能力分数更新
│   ├── NvcCommunicationAnalysisService.java ← 真实沟通记录分析
│   └── NvcDashboardService.java           ← 数据统计与趋势
├── dto/
│   ├── UserProfileDTO.java                ← 档案响应 DTO
│   ├── UserProfileUpdateRequest.java      ← 档案更新请求
│   ├── CommunicationRecordDTO.java        ← 沟通记录 DTO
│   ├── CommunicationAnalysisRequest.java  ← 沟通记录分析请求
│   ├── AbilityRadarDTO.java               ← 能力雷达图数据
│   └── AbilityTrendDTO.java               ← 能力趋势数据
└── controller/
    ├── NvcProfileController.java          ← 档案 API
    └── NvcDashboardController.java        ← 仪表盘 API

app/src/main/resources/prompts/
└── nvc-profile-analyze-system.st          ← 沟通记录分析提示词
```

---

## 5.2 NvcProfileService — 档案管理

路径：`app/src/main/java/interview/guide/modules/nvcprofile/service/NvcProfileService.java`

### 功能

- 获取/创建用户档案
- 更新档案信息（沟通背景、性格、场景偏好等）
- 练习结束后自动更新能力分数
- 计算 NVC 等级（BEGINNER/INTERMEDIATE/ADVANCED）

### 实现要点

```java
package interview.guide.modules.nvcprofile.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.nvcpractice.model.NvcEvaluationEntity;
import interview.guide.modules.nvcpractice.model.NvcPracticeType;
import interview.guide.modules.nvcprofile.dto.UserProfileDTO;
import interview.guide.modules.nvcprofile.dto.UserProfileUpdateRequest;
import interview.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import interview.guide.modules.nvcprofile.dto.AbilityTrendDTO;
import interview.guide.modules.nvcprofile.model.*;
import interview.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import interview.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcProfileService {

    private final NvcUserProfileRepository profileRepository;
    private final NvcUserAbilityScoreRepository abilityScoreRepository;

    /**
     * 获取用户档案（不存在则创建默认档案）
     */
    public NvcUserProfileEntity getOrCreateProfile(Long userId) {
        return profileRepository.findByUserId(userId)
            .orElseGet(() -> createDefaultProfile(userId));
    }

    /**
     * 创建默认档案
     */
    private NvcUserProfileEntity createDefaultProfile(Long userId) {
        NvcUserProfileEntity profile = NvcUserProfileEntity.builder()
            .userId(userId)
            .nvcLevel(NvcLevel.BEGINNER)
            .totalPracticeCount(0)
            .totalPracticeMinutes(0)
            .build();
        NvcUserProfileEntity saved = profileRepository.save(profile);
        log.info("Default NVC profile created: userId={}", userId);
        return saved;
    }

    /**
     * 更新用户档案
     */
    public NvcUserProfileEntity updateProfile(Long userId, UserProfileUpdateRequest request) {
        NvcUserProfileEntity profile = getOrCreateProfile(userId);

        if (request.communicationBackground() != null) {
            profile.setCommunicationBackground(request.communicationBackground());
        }
        if (request.personalityTraits() != null) {
            profile.setPersonalityTraits(request.personalityTraits());
        }
        if (request.communicationStyle() != null) {
            profile.setCommunicationStyle(request.communicationStyle());
        }
        if (request.emotionalTriggers() != null) {
            profile.setEmotionalTriggers(request.emotionalTriggers());
        }
        if (request.commonScenarios() != null) {
            profile.setCommonScenarios(request.commonScenarios());
        }
        if (request.relationshipTypes() != null) {
            profile.setRelationshipTypes(request.relationshipTypes());
        }

        return profileRepository.save(profile);
    }

    /**
     * 练习结束后更新能力分数
     * 基于评估结果记录能力分数，并更新 NVC 等级
     */
    public void updateAbilityScore(Long userId, Long sessionId, NvcEvaluationEntity evaluation,
                                    NvcPracticeType practiceType) {
        // 1. 记录能力分数
        NvcUserAbilityScoreEntity score = NvcUserAbilityScoreEntity.builder()
            .userId(userId)
            .sessionId(sessionId)
            .observation(evaluation.getObservationScore())
            .feeling(evaluation.getFeelingScore())
            .need(evaluation.getNeedScore())
            .request(evaluation.getRequestScore())
            .empathy(evaluation.getEmpathyScore())
            .practiceType(practiceType)
            .build();
        abilityScoreRepository.save(score);

        // 2. 更新档案统计
        NvcUserProfileEntity profile = getOrCreateProfile(userId);
        profile.setTotalPracticeCount(profile.getTotalPracticeCount() + 1);
        profile.setLastPracticeAt(LocalDateTime.now());

        // 3. 计算 NVC 等级
        NvcLevel newLevel = calculateLevel(userId);
        profile.setNvcLevel(newLevel);

        profileRepository.save(profile);
        log.info("Ability score updated: userId={}, overall={}, level={}",
            userId, evaluation.getOverallScore(), newLevel);
    }

    /**
     * 获取能力雷达图数据
     */
    public AbilityRadarDTO getAbilityRadar(Long userId) {
        // 获取最近 10 次的能力分数，取平均
        List<NvcUserAbilityScoreEntity> recentScores =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        if (recentScores.isEmpty()) {
            return new AbilityRadarDTO(0, 0, 0, 0, 0, 0, "BEGINNER");
        }

        // 取最近 10 次的平均值
        List<NvcUserAbilityScoreEntity> last10 = recentScores.subList(
            0, Math.min(10, recentScores.size()));

        int avgObservation = (int) last10.stream().mapToInt(NvcUserAbilityScoreEntity::getObservation).average().orElse(0);
        int avgFeeling = (int) last10.stream().mapToInt(NvcUserAbilityScoreEntity::getFeeling).average().orElse(0);
        int avgNeed = (int) last10.stream().mapToInt(NvcUserAbilityScoreEntity::getNeed).average().orElse(0);
        int avgRequest = (int) last10.stream().mapToInt(NvcUserAbilityScoreEntity::getRequest).average().orElse(0);
        int avgEmpathy = (int) last10.stream()
            .filter(s -> s.getEmpathy() != null)
            .mapToInt(NvcUserAbilityScoreEntity::getEmpathy)
            .average().orElse(0);
        int overallAvg = (avgObservation + avgFeeling + avgNeed + avgRequest) / 4;

        NvcUserProfileEntity profile = getOrCreateProfile(userId);

        return new AbilityRadarDTO(
            avgObservation, avgFeeling, avgNeed, avgRequest, avgEmpathy,
            overallAvg, profile.getNvcLevel().name()
        );
    }

    /**
     * 获取能力趋势数据（最近 30 次）
     */
    public List<AbilityTrendDTO> getAbilityTrends(Long userId) {
        List<NvcUserAbilityScoreEntity> scores =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        return scores.stream()
            .map(s -> new AbilityTrendDTO(
                s.getScoredAt(),
                s.getObservation(),
                s.getFeeling(),
                s.getNeed(),
                s.getRequest(),
                s.getEmpathy(),
                s.getPracticeType() != null ? s.getPracticeType().name() : null
            ))
            .toList();
    }

    /**
     * 计算 NVC 等级
     * 基于最近 10 次练习的平均分
     */
    private NvcLevel calculateLevel(Long userId) {
        List<NvcUserAbilityScoreEntity> recent =
            abilityScoreRepository.findTop30ByUserIdOrderByScoredAtDesc(userId);

        if (recent.size() < 3) {
            return NvcLevel.BEGINNER;
        }

        List<NvcUserAbilityScoreEntity> last10 = recent.subList(0, Math.min(10, recent.size()));
        double avgOverall = last10.stream()
            .mapToInt(s -> (s.getObservation() + s.getFeeling() + s.getNeed() + s.getRequest()) / 4)
            .average()
            .orElse(0);

        if (avgOverall >= 80) return NvcLevel.ADVANCED;
        if (avgOverall >= 60) return NvcLevel.INTERMEDIATE;
        return NvcLevel.BEGINNER;
    }

    /**
     * 转换为 DTO
     */
    public UserProfileDTO toDTO(NvcUserProfileEntity profile) {
        AbilityRadarDTO radar = getAbilityRadar(profile.getUserId());
        return new UserProfileDTO(
            profile.getUserId(),
            profile.getCommunicationBackground(),
            profile.getPersonalityTraits(),
            profile.getCommunicationStyle(),
            profile.getEmotionalTriggers(),
            profile.getCommonScenarios(),
            profile.getRelationshipTypes(),
            profile.getNvcLevel(),
            profile.getTotalPracticeCount(),
            profile.getTotalPracticeMinutes(),
            profile.getLastPracticeAt(),
            radar
        );
    }
}
```

---

## 5.3 NvcCommunicationAnalysisService — 沟通记录分析

路径：`app/src/main/java/interview/guide/modules/nvcprofile/service/NvcCommunicationAnalysisService.java`

### 功能

- 用户上传真实沟通记录
- AI 分析记录中的 NVC 四要素质量
- 生成 NVC 改写建议
- 保存分析结果

### 实现要点

```java
package interview.guide.modules.nvcprofile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.nvcprofile.dto.CommunicationAnalysisRequest;
import interview.guide.modules.nvcprofile.model.NvcCommunicationRecordEntity;
import interview.guide.modules.nvcprofile.model.NvcScenarioType;
import interview.guide.modules.nvcprofile.repository.NvcCommunicationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcCommunicationAnalysisService {

    private final NvcCommunicationRecordRepository recordRepository;
    private final LlmProviderRegistry llmProviderRegistry;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final ObjectMapper objectMapper;

    private String analysisSystemPrompt;

    private String getAnalysisSystemPrompt() {
        if (analysisSystemPrompt == null) {
            try {
                ClassPathResource resource = new ClassPathResource("prompts/nvc-profile-analyze-system.st");
                analysisSystemPrompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                analysisSystemPrompt = getDefaultAnalysisPrompt();
            }
        }
        return analysisSystemPrompt;
    }

    /**
     * 分析沟通记录
     */
    public NvcCommunicationRecordEntity analyzeAndSave(Long userId, CommunicationAnalysisRequest request) {
        // 1. 调用 LLM 分析
        String analysisPrompt = buildAnalysisPrompt(request.rawContent(), request.scenarioType());
        ChatClient chatClient = llmProviderRegistry.getPlainChatClient(null);

        String result = structuredOutputInvoker.invokeStructuredOutput(
            analysisPrompt + "\n\n" + getAnalysisSystemPrompt(),
            chatClient,
            output -> output
        );

        // 2. 保存记录
        NvcCommunicationRecordEntity record = NvcCommunicationRecordEntity.builder()
            .userId(userId)
            .title(request.title())
            .scenarioType(request.scenarioType())
            .rawContent(request.rawContent())
            .analysisResult(result)
            .nvcSuggestion(extractNvcSuggestion(result))
            .build();

        NvcCommunicationRecordEntity saved = recordRepository.save(record);
        log.info("Communication record analyzed and saved: userId={}, recordId={}", userId, saved.getId());
        return saved;
    }

    /**
     * 获取用户的沟通记录列表
     */
    public List<NvcCommunicationRecordEntity> getUserRecords(Long userId) {
        return recordRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private String buildAnalysisPrompt(String rawContent, NvcScenarioType scenarioType) {
        return """
            请分析以下真实沟通记录，评估其中的NVC四要素质量，并给出NVC改写建议。

            [沟通记录]
            %s

            场景类型：%s

            请分析：
            1. 这段沟通中哪些是"观察"（客观事实），哪些是"评论"（主观判断）
            2. 表达了哪些"感受"，哪些是"想法"伪装成感受
            3. 隐藏了哪些"需求"
            4. 有没有具体的"请求"，还是只有命令或指责
            5. 用NVC方式重新改写这段沟通

            请以JSON格式返回分析结果。
            """.formatted(rawContent, scenarioType != null ? scenarioType : "未知");
    }

    private String extractNvcSuggestion(String analysisResult) {
        // 从分析结果中提取 NVC 改写建议
        try {
            var node = objectMapper.readTree(analysisResult);
            if (node.has("nvc_rewrite")) {
                return node.get("nvc_rewrite").asText();
            }
            if (node.has("nvc_suggestion")) {
                return node.get("nvc_suggestion").asText();
            }
        } catch (Exception e) {
            log.warn("Failed to extract NVC suggestion from analysis result", e);
        }
        return null;
    }

    private String getDefaultAnalysisPrompt() {
        return """
            你是NVC表达分析专家。请分析用户的沟通记录，评估其中的NVC四要素质量。

            请以JSON格式返回：
            {
              "observation_analysis": "观察维度分析",
              "feeling_analysis": "感受维度分析",
              "need_analysis": "需求维度分析",
              "request_analysis": "请求维度分析",
              "overall_assessment": "整体评估",
              "nvc_rewrite": "用NVC方式改写的版本",
              "key_improvements": ["关键改进建议1", "关键改进建议2"]
            }
            """;
    }
}
```

---

## 5.4 NvcDashboardService — 数据统计

路径：`app/src/main/java/interview/guide/modules/nvcprofile/service/NvcDashboardService.java`

```java
package interview.guide.modules.nvcprofile.service;

import interview.guide.modules.nvcpractice.model.NvcSessionPhase;
import interview.guide.modules.nvcpractice.repository.NvcPracticeSessionRepository;
import interview.guide.modules.nvcprofile.repository.NvcUserAbilityScoreRepository;
import interview.guide.modules.nvcprofile.repository.NvcUserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NvcDashboardService {

    private final NvcPracticeSessionRepository sessionRepository;
    private final NvcUserAbilityScoreRepository abilityScoreRepository;
    private final NvcUserProfileRepository profileRepository;

    /**
     * 获取用户练习统计
     */
    public Map<String, Object> getUserStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // 总练习次数
        long totalSessions = sessionRepository.countByUserId(userId);

        // 已完成的练习次数
        long completedSessions = sessionRepository
            .findByUserIdAndCurrentPhaseOrderByCreatedAtDesc(userId, NvcSessionPhase.COMPLETED)
            .size();

        // 能力评分记录数
        long totalScores = abilityScoreRepository.findByUserIdOrderByScoredAtDesc(userId).size();

        stats.put("totalSessions", totalSessions);
        stats.put("completedSessions", completedSessions);
        stats.put("totalScores", totalScores);

        return stats;
    }
}
```

---

## 5.5 DTO 类

### UserProfileDTO

```java
package interview.guide.modules.nvcprofile.dto;

import interview.guide.modules.nvcprofile.dto.AbilityRadarDTO;
import interview.guide.modules.nvcprofile.model.NvcCommunicationStyle;
import interview.guide.modules.nvcprofile.model.NvcLevel;

import java.time.LocalDateTime;

public record UserProfileDTO(
    Long userId,
    String communicationBackground,
    String personalityTraits,
    NvcCommunicationStyle communicationStyle,
    String emotionalTriggers,
    String commonScenarios,
    String relationshipTypes,
    NvcLevel nvcLevel,
    Integer totalPracticeCount,
    Integer totalPracticeMinutes,
    LocalDateTime lastPracticeAt,
    AbilityRadarDTO abilityRadar
) {}
```

### UserProfileUpdateRequest

```java
package interview.guide.modules.nvcprofile.dto;

import interview.guide.modules.nvcprofile.model.NvcCommunicationStyle;

public record UserProfileUpdateRequest(
    String communicationBackground,
    String personalityTraits,
    NvcCommunicationStyle communicationStyle,
    String emotionalTriggers,
    String commonScenarios,
    String relationshipTypes
) {}
```

### AbilityRadarDTO

```java
package interview.guide.modules.nvcprofile.dto;

public record AbilityRadarDTO(
    Integer observation,
    Integer feeling,
    Integer need,
    Integer request,
    Integer empathy,
    Integer overall,
    String level
) {}
```

### AbilityTrendDTO

```java
package interview.guide.modules.nvcprofile.dto;

import java.time.LocalDateTime;

public record AbilityTrendDTO(
    LocalDateTime scoredAt,
    Integer observation,
    Integer feeling,
    Integer need,
    Integer request,
    Integer empathy,
    String practiceType
) {}
```

### CommunicationRecordDTO

```java
package interview.guide.modules.nvcprofile.dto;

import interview.guide.modules.nvcprofile.model.NvcScenarioType;
import java.time.LocalDateTime;

public record CommunicationRecordDTO(
    Long id,
    String title,
    NvcScenarioType scenarioType,
    String rawContent,
    String analysisResult,
    String nvcSuggestion,
    LocalDateTime createdAt
) {}
```

### CommunicationAnalysisRequest

```java
package interview.guide.modules.nvcprofile.dto;

import interview.guide.modules.nvcprofile.model.NvcScenarioType;
import jakarta.validation.constraints.NotBlank;

public record CommunicationAnalysisRequest(
    String title,

    @NotBlank String rawContent,

    NvcScenarioType scenarioType
) {}
```

---

## 5.6 Controller

### NvcProfileController

路径：`app/src/main/java/interview/guide/modules/nvcprofile/controller/NvcProfileController.java`

```java
package interview.guide.modules.nvcprofile.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.nvcprofile.dto.*;
import interview.guide.modules.nvcprofile.model.NvcCommunicationRecordEntity;
import interview.guide.modules.nvcprofile.model.NvcUserProfileEntity;
import interview.guide.modules.nvcprofile.service.NvcCommunicationAnalysisService;
import interview.guide.modules.nvcprofile.service.NvcProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/profile")
@RequiredArgsConstructor
public class NvcProfileController {

    private final NvcProfileService profileService;
    private final NvcCommunicationAnalysisService analysisService;

    /**
     * 获取当前用户档案
     */
    @GetMapping
    public Result<UserProfileDTO> getProfile(@RequestParam Long userId) {
        NvcUserProfileEntity profile = profileService.getOrCreateProfile(userId);
        return Result.success(profileService.toDTO(profile));
    }

    /**
     * 更新用户档案
     */
    @PutMapping
    public Result<UserProfileDTO> updateProfile(
            @RequestParam Long userId,
            @Valid @RequestBody UserProfileUpdateRequest request) {
        NvcUserProfileEntity profile = profileService.updateProfile(userId, request);
        return Result.success(profileService.toDTO(profile));
    }

    /**
     * 获取能力雷达图数据
     */
    @GetMapping("/ability-radar")
    public Result<AbilityRadarDTO> getAbilityRadar(@RequestParam Long userId) {
        return Result.success(profileService.getAbilityRadar(userId));
    }

    /**
     * 获取能力趋势数据
     */
    @GetMapping("/ability-trends")
    public Result<List<AbilityTrendDTO>> getAbilityTrends(@RequestParam Long userId) {
        return Result.success(profileService.getAbilityTrends(userId));
    }

    /**
     * 分析沟通记录
     */
    @PostMapping("/communication-records/analyze")
    public Result<CommunicationRecordDTO> analyzeCommunication(
            @RequestParam Long userId,
            @Valid @RequestBody CommunicationAnalysisRequest request) {
        NvcCommunicationRecordEntity record = analysisService.analyzeAndSave(userId, request);
        return Result.success(toRecordDTO(record));
    }

    /**
     * 获取沟通记录列表
     */
    @GetMapping("/communication-records")
    public Result<List<CommunicationRecordDTO>> getCommunicationRecords(@RequestParam Long userId) {
        var records = analysisService.getUserRecords(userId).stream()
            .map(this::toRecordDTO)
            .toList();
        return Result.success(records);
    }

    private CommunicationRecordDTO toRecordDTO(NvcCommunicationRecordEntity record) {
        return new CommunicationRecordDTO(
            record.getId(),
            record.getTitle(),
            record.getScenarioType(),
            record.getRawContent(),
            record.getAnalysisResult(),
            record.getNvcSuggestion(),
            record.getCreatedAt()
        );
    }
}
```

### NvcDashboardController

```java
package interview.guide.modules.nvcprofile.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.nvcprofile.service.NvcDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/nvc/dashboard")
@RequiredArgsConstructor
public class NvcDashboardController {

    private final NvcDashboardService dashboardService;

    @GetMapping("/stats")
    public Result<Map<String, Object>> getUserStats(@RequestParam Long userId) {
        return Result.success(dashboardService.getUserStats(userId));
    }
}
```

---

## 5.7 Prompt 模板

### nvc-profile-analyze-system.st

路径：`app/src/main/resources/prompts/nvc-profile-analyze-system.st`

```
你是NVC（非暴力沟通）表达分析专家。你的职责是分析用户提供的真实沟通记录，评估其中的NVC四要素质量，并给出改写建议。

分析维度：
1. 观察：哪些是客观事实描述，哪些是主观评判
2. 感受：哪些是真实的情绪表达，哪些是想法/判断伪装成感受
3. 需求：沟通中隐含了哪些深层需求
4. 请求：有没有具体可执行的请求，还是只有命令或指责

请以JSON格式返回分析结果：

{
  "observation_analysis": "观察维度分析：哪些是事实，哪些是评判",
  "feeling_analysis": "感受维度分析：哪些是真实感受，哪些是想法",
  "need_analysis": "需求维度分析：隐含了哪些需求",
  "request_analysis": "请求维度分析：有没有具体请求",
  "observation_score": 0-100,
  "feeling_score": 0-100,
  "need_score": 0-100,
  "request_score": 0-100,
  "overall_score": 0-100,
  "overall_assessment": "整体评估",
  "nvc_rewrite": "用NVC方式改写的完整版本",
  "key_improvements": ["关键改进建议1", "关键改进建议2"]
}
```

---

## 5.8 集成到评估流程

在 Step 4 的 `NvcEvaluationService` 中，评估完成后自动更新用户档案：

```java
// 在 evaluateFinal() 方法的最后添加：

// 更新用户档案能力分数
profileService.updateAbilityScore(userId, sessionId, saved, NvcPracticeType.TEXT);
```

需要在 `NvcEvaluationService` 中注入 `NvcProfileService`。

---

## 5.9 验证清单

```
□ NvcProfileService 编译通过
  □ getOrCreateProfile() 首次调用自动创建默认档案
  □ updateProfile() 能更新各个字段
  □ updateAbilityScore() 能记录能力分数并更新等级
  □ getAbilityRadar() 返回正确的平均分
  □ getAbilityTrends() 返回最近 30 次的趋势数据
□ NvcCommunicationAnalysisService 编译通过
  □ analyzeAndSave() 能调用 LLM 分析沟通记录
  □ 分析结果包含四要素评分和改写建议
□ API 测试：
  □ GET /api/nvc/profile?userId=1 返回用户档案
  □ PUT /api/nvc/profile?userId=1 更新档案
  □ GET /api/nvc/profile/ability-radar?userId=1 返回雷达图数据
  □ GET /api/nvc/profile/ability-trends?userId=1 返回趋势数据
  □ POST /api/nvc/profile/communication-records/analyze 分析沟通记录
  □ GET /api/nvc/profile/communication-records?userId=1 返回记录列表
□ 集成验证：
  □ 完成一次练习后，能力分数自动更新
  □ 完成 3 次练习后，NVC 等级自动计算
□ Git 提交："Step 5: Add user profile system with ability tracking"
```
