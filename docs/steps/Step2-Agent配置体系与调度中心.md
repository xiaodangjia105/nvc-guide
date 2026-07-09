# Step 2：Agent 配置体系与调度中心

> 目标：实现 NvcAgentConfigService（Agent 配置热更新）+ NvcAgentOrchestrator（Agent 调度中心）
>
> 预计耗时：3-5 天（这是项目核心亮点，值得多花时间）
>
> 前置条件：Step 1 已完成，数据库表和初始数据就绪
>
> 技术亮点：多 Agent 角色协同调度、Agent 配置热更新、Plan-Execute-Reflect 思维链

---

## 2.1 本步要创建的文件清单

```
app/src/main/java/interview/guide/modules/nvcpractice/
├── service/
│   ├── NvcAgentConfigService.java         ← Agent 配置读取 + Redis 缓存 + 热更新
│   ├── NvcAgentOrchestrator.java          ← Agent 调度中心（核心类）
│   └── NvcAgentChatService.java           ← Agent 对话执行（调用 ChatClient）
├── dto/
│   ├── AgentConfigDTO.java                ← Agent 配置响应 DTO
│   ├── AgentConfigUpdateRequest.java      ← Agent 配置更新请求
│   ├── AgentDecision.java                 ← Agent 调度决策结果
│   └── PracticeContext.java               ← 练习上下文（传递给 Agent 的信息）
└── controller/
    └── NvcAgentConfigController.java      ← Agent 配置管理 API
```

---

## 2.2 NvcAgentConfigService — Agent 配置服务

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcAgentConfigService.java`

### 功能说明

- 从数据库读取 Agent 配置
- Redis 缓存（TTL 5 分钟），更新时主动失效
- 根据 NvcAgentScene 获取对应的系统提示词、模型参数
- 运行时热更新：更新数据库 + 清除缓存，下次调用自动生效

### 实现要点

```java
package interview.guide.modules.nvcpractice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.infrastructure.redis.RedisService;
import interview.guide.modules.nvcpractice.dto.AgentConfigDTO;
import interview.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import interview.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import interview.guide.modules.nvcpractice.model.NvcAgentScene;
import interview.guide.modules.nvcpractice.repository.NvcAgentConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentConfigService {

    private final NvcAgentConfigRepository agentConfigRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "nvc:agent:config:";
    private static final long CACHE_TTL_MINUTES = 5;

    /**
     * 获取 Agent 配置（带缓存）
     * 优先从 Redis 读取，miss 则查 DB 并写入缓存
     */
    public NvcAgentConfigEntity getConfig(NvcAgentScene scene) {
        String cacheKey = CACHE_KEY_PREFIX + scene.name();

        // 1. 尝试从 Redis 读取
        String cached = redisService.get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, NvcAgentConfigEntity.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize agent config from cache: scene={}", scene, e);
                redisService.delete(cacheKey);
            }
        }

        // 2. 从 DB 读取
        NvcAgentConfigEntity config = agentConfigRepository.findByAgentScene(scene)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_AGENT_CONFIG_NOT_FOUND,
                "Agent config not found: " + scene.name()));

        // 3. 写入缓存
        try {
            String json = objectMapper.writeValueAsString(config);
            redisService.set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to cache agent config: scene={}", scene, e);
        }

        return config;
    }

    /**
     * 获取所有已启用的 Agent 配置
     */
    public List<NvcAgentConfigEntity> getAllEnabledConfigs() {
        return agentConfigRepository.findByIsEnabledTrue();
    }

    /**
     * 获取所有 Agent 配置（包括禁用的）
     */
    public List<NvcAgentConfigEntity> getAllConfigs() {
        return agentConfigRepository.findAll();
    }

    /**
     * 更新 Agent 配置（热更新）
     * 更新 DB + 清除缓存，下次调用自动生效
     */
    public NvcAgentConfigEntity updateConfig(NvcAgentScene scene, AgentConfigUpdateRequest request) {
        NvcAgentConfigEntity config = agentConfigRepository.findByAgentScene(scene)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NVC_AGENT_CONFIG_NOT_FOUND,
                "Agent config not found: " + scene.name()));

        // 更新字段
        if (request.systemPrompt() != null) {
            config.setSystemPrompt(request.systemPrompt());
        }
        if (request.temperature() != null) {
            config.setTemperature(request.temperature());
        }
        if (request.maxTokens() != null) {
            config.setMaxTokens(request.maxTokens());
        }
        if (request.topP() != null) {
            config.setTopP(request.topP());
        }
        if (request.isEnabled() != null) {
            config.setIsEnabled(request.isEnabled());
        }
        if (request.modelProvider() != null) {
            config.setModelProvider(request.modelProvider());
        }
        if (request.modelName() != null) {
            config.setModelName(request.modelName());
        }

        // 保存到 DB
        NvcAgentConfigEntity saved = agentConfigRepository.save(config);

        // 清除缓存（主动失效）
        String cacheKey = CACHE_KEY_PREFIX + scene.name();
        redisService.delete(cacheKey);
        log.info("Agent config updated and cache cleared: scene={}", scene);

        return saved;
    }

    /**
     * 转换为 DTO
     */
    public AgentConfigDTO toDTO(NvcAgentConfigEntity entity) {
        return new AgentConfigDTO(
            entity.getAgentScene(),
            entity.getDisplayName(),
            entity.getDescription(),
            entity.getSystemPrompt(),
            entity.getModelProvider(),
            entity.getModelName(),
            entity.getTemperature(),
            entity.getMaxTokens(),
            entity.getTopP(),
            entity.getIsEnabled()
        );
    }
}
```

---

## 2.3 NvcAgentOrchestrator — Agent 调度中心（核心类）

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcAgentOrchestrator.java`

### 功能说明

这是整个 NVC 练习系统的"大脑"，负责：
1. 根据练习模式和当前状态，决定下一步使用哪个 Agent
2. 实现 Plan-Execute-Reflect 思维链
3. 管理 Agent 切换逻辑（评分低→引导、要素缺失→针对性教练、通过→推进）

### 实现要点

```java
package interview.guide.modules.nvcpractice.service;

import interview.guide.common.ai.StructuredOutputInvoker;
import interview.guide.modules.nvcpractice.dto.AgentDecision;
import interview.guide.modules.nvcpractice.dto.PracticeContext;
import interview.guide.modules.nvcpractice.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentOrchestrator {

    private final NvcAgentConfigService agentConfigService;
    private final NvcAgentChatService agentChatService;
    private final StructuredOutputInvoker structuredOutputInvoker;

    // ==================== PLAN：分析当前状态，规划下一步 ====================

    /**
     * 决定下一步使用哪个 Agent
     * 这是核心调度方法，根据练习状态做出决策
     */
    public AgentDecision decideNextAgent(PracticeContext context) {
        NvcPracticeSessionEntity session = context.getSession();

        // 场景生成阶段
        if (session.getCurrentPhase() == NvcSessionPhase.CREATED) {
            return new AgentDecision(
                NvcAgentScene.SCENARIO_GENERATOR,
                "练习刚开始，需要生成场景",
                null
            );
        }

        // 结构化四步模式的步骤推进
        if (session.getPracticeMode() == NvcPracticeMode.STRUCTURED_FOUR_STEP) {
            return decideStructuredStep(context);
        }

        // 场景驱动和自由对话模式
        return decideFreeDialog(context);
    }

    /**
     * 结构化四步模式的调度逻辑
     */
    private AgentDecision decideStructuredStep(PracticeContext context) {
        NvcPracticeStep currentStep = context.getSession().getCurrentStep();

        // 如果没有评估结果，继续当前步骤的教练
        if (context.getLastEvaluation() == null) {
            return new AgentDecision(
                stepToCoach(currentStep),
                "当前步骤 " + currentStep + " 尚未评估，继续练习",
                null
            );
        }

        int stepScore = getStepScore(context.getLastEvaluation(), currentStep);

        // 评分 >= 70，推进到下一步
        if (stepScore >= 70) {
            NvcPracticeStep nextStep = nextStep(currentStep);
            if (nextStep == NvcPracticeStep.COMPLETED) {
                return new AgentDecision(
                    NvcAgentScene.DIALOGUE_GUIDE,
                    "四步全部通过，进入综合练习",
                    "COMPLETED_ALL_STEPS"
                );
            }
            return new AgentDecision(
                stepToCoach(nextStep),
                currentStep + " 通过（" + stepScore + "分），推进到 " + nextStep,
                "STEP_ADVANCE"
            );
        }

        // 评分 < 70，继续当前步骤的教练
        return new AgentDecision(
            stepToCoach(currentStep),
            currentStep + " 未通过（" + stepScore + "分），继续练习",
            "STEP_RETRY"
        );
    }

    /**
     * 自由对话/场景驱动模式的调度逻辑
     */
    private AgentDecision decideFreeDialog(PracticeContext context) {
        // 没有评估结果，使用对话引导
        if (context.getLastEvaluation() == null) {
            return new AgentDecision(
                NvcAgentScene.DIALOGUE_GUIDE,
                "对话进行中，使用引导官",
                null
            );
        }

        int overallScore = context.getLastEvaluation().getOverallScore();
        int roundCount = context.getRoundCount();

        // 评分 < 50，切换到引导模式帮助用户
        if (overallScore < 50) {
            return new AgentDecision(
                NvcAgentScene.DIALOGUE_GUIDE,
                "评分较低（" + overallScore + "），切换到引导模式",
                "LOW_SCORE_GUIDE"
            );
        }

        // 评分 >= 50 且轮次 > 3，偶尔切换困难搭档增加难度
        if (overallScore >= 60 && roundCount > 3 && roundCount % 3 == 0) {
            return new AgentDecision(
                NvcAgentScene.DIFFICULT_PARTNER,
                "用户表现良好（" + overallScore + "），切换困难搭档提升难度",
                "DIFFICULT_UPGRADE"
            );
        }

        // 检查是否有薄弱要素需要针对性练习
        NvcPracticeStep weakElement = findWeakestElement(context);
        if (weakElement != null && overallScore < 70) {
            return new AgentDecision(
                stepToCoach(weakElement),
                "薄弱要素 " + weakElement + " 需要针对性练习",
                "WEAK_ELEMENT_FOCUS"
            );
        }

        // 默认继续对话引导
        return new AgentDecision(
            NvcAgentScene.DIALOGUE_GUIDE,
            "对话正常进行",
            null
        );
    }

    // ==================== EXECUTE：执行 Agent 对话 ====================

    /**
     * 执行 Agent 对话，获取 AI 回复
     */
    public String executeAgent(NvcAgentScene scene, PracticeContext context, String userMessage) {
        NvcAgentConfigEntity config = agentConfigService.getConfig(scene);

        // 检查 Agent 是否启用
        if (!config.getIsEnabled()) {
            log.warn("Agent is disabled: {}, falling back to DIALOGUE_GUIDE", scene);
            config = agentConfigService.getConfig(NvcAgentScene.DIALOGUE_GUIDE);
        }

        return agentChatService.chat(config, context, userMessage);
    }

    // ==================== REFLECT：反思与策略调整 ====================

    /**
     * 练习结束后反思，更新用户档案
     * 分析整体表现，为下次练习提供策略建议
     */
    public String reflect(PracticeContext context) {
        NvcAgentConfigEntity evaluatorConfig = agentConfigService.getConfig(
            NvcAgentScene.NVC_EXPRESSION_EVALUATOR);

        String reflectPrompt = """
            基于以下练习记录，请分析用户的表现并给出下次练习的策略建议：

            练习模式：%s
            练习轮次：%d
            最终评分：观察%d 感受%d 需求%d 请求%d 综合%d

            请输出JSON格式：
            {
              "weak_elements": ["薄弱要素列表"],
              "suggested_difficulty": "EASY/MEDIUM/HARD",
              "suggested_scenario_type": "建议的场景类型",
              "strategy_note": "策略建议说明"
            }
            """.formatted(
                context.getSession().getPracticeMode(),
                context.getRoundCount(),
                context.getLastEvaluation().getObservationScore(),
                context.getLastEvaluation().getFeelingScore(),
                context.getLastEvaluation().getNeedScore(),
                context.getLastEvaluation().getRequestScore(),
                context.getLastEvaluation().getOverallScore()
            );

        return agentChatService.chatPlain(evaluatorConfig, reflectPrompt);
    }

    // ==================== 辅助方法 ====================

    private NvcAgentScene stepToCoach(NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> NvcAgentScene.STEP_OBSERVE_COACH;
            case FEELING -> NvcAgentScene.STEP_FEELING_COACH;
            case NEED -> NvcAgentScene.STEP_NEED_COACH;
            case REQUEST -> NvcAgentScene.STEP_REQUEST_COACH;
            default -> NvcAgentScene.DIALOGUE_GUIDE;
        };
    }

    private NvcPracticeStep nextStep(NvcPracticeStep current) {
        return switch (current) {
            case OBSERVE -> NvcPracticeStep.FEELING;
            case FEELING -> NvcPracticeStep.NEED;
            case NEED -> NvcPracticeStep.REQUEST;
            case REQUEST -> NvcPracticeStep.COMPLETED;
            default -> NvcPracticeStep.COMPLETED;
        };
    }

    private int getStepScore(NvcEvaluationEntity eval, NvcPracticeStep step) {
        return switch (step) {
            case OBSERVE -> eval.getObservationScore();
            case FEELING -> eval.getFeelingScore();
            case NEED -> eval.getNeedScore();
            case REQUEST -> eval.getRequestScore();
            default -> eval.getOverallScore();
        };
    }

    private NvcPracticeStep findWeakestElement(PracticeContext context) {
        NvcEvaluationEntity eval = context.getLastEvaluation();
        if (eval == null) return null;

        int minScore = Integer.MAX_VALUE;
        NvcPracticeStep weakest = null;

        if (eval.getObservationScore() != null && eval.getObservationScore() < minScore) {
            minScore = eval.getObservationScore();
            weakest = NvcPracticeStep.OBSERVE;
        }
        if (eval.getFeelingScore() != null && eval.getFeelingScore() < minScore) {
            minScore = eval.getFeelingScore();
            weakest = NvcPracticeStep.FEELING;
        }
        if (eval.getNeedScore() != null && eval.getNeedScore() < minScore) {
            minScore = eval.getNeedScore();
            weakest = NvcPracticeStep.NEED;
        }
        if (eval.getRequestScore() != null && eval.getRequestScore() < minScore) {
            minScore = eval.getRequestScore();
            weakest = NvcPracticeStep.REQUEST;
        }

        return weakest;
    }
}
```

---

## 2.4 NvcAgentChatService — Agent 对话执行

路径：`app/src/main/java/interview/guide/modules/nvcpractice/service/NvcAgentChatService.java`

### 功能说明

- 根据 Agent 配置创建/获取 ChatClient
- 组装对话上下文（系统提示词 + 用户档案 + 对话历史 + RAG 知识）
- 执行对话，返回 AI 回复
- 支持流式输出（SSE）

### 实现要点

```java
package interview.guide.modules.nvcpractice.service;

import interview.guide.common.ai.LlmProviderRegistry;
import interview.guide.modules.nvcpractice.dto.PracticeContext;
import interview.guide.modules.nvcpractice.model.NvcAgentConfigEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NvcAgentChatService {

    private final LlmProviderRegistry llmProviderRegistry;

    /**
     * 执行 Agent 对话（非流式）
     */
    public String chat(NvcAgentConfigEntity config, PracticeContext context, String userMessage) {
        ChatClient chatClient = getChatClient(config);
        List<Message> messages = buildMessages(config, context, userMessage);

        return chatClient.prompt()
            .messages(messages)
            .call()
            .content();
    }

    /**
     * 执行 Agent 对话（流式）
     */
    public Flux<String> chatStream(NvcAgentConfigEntity config, PracticeContext context, String userMessage) {
        ChatClient chatClient = getChatClient(config);
        List<Message> messages = buildMessages(config, context, userMessage);

        return chatClient.prompt()
            .messages(messages)
            .stream()
            .content();
    }

    /**
     * 使用 Plain ChatClient（无工具、无记忆）执行简单对话
     * 用于评估、反思等需要结构化输出的场景
     */
    public String chatPlain(NvcAgentConfigEntity config, String prompt) {
        ChatClient chatClient = llmProviderRegistry.getPlainChatClient(
            config.getModelProvider());

        return chatClient.prompt()
            .user(prompt)
            .system(config.getSystemPrompt())
            .call()
            .content();
    }

    /**
     * 根据 Agent 配置获取 ChatClient
     * 如果配置指定了特定 Provider，使用该 Provider；否则用默认
     */
    private ChatClient getChatClient(NvcAgentConfigEntity config) {
        if (config.getModelProvider() != null && !config.getModelProvider().isEmpty()) {
            return llmProviderRegistry.getChatClientOrDefault(config.getModelProvider());
        }
        return llmProviderRegistry.getChatClientOrDefault(null);
    }

    /**
     * 组装对话消息列表
     * 包含：系统提示词 + 用户档案摘要 + 对话历史 + RAG 知识 + 当前用户消息
     */
    private List<Message> buildMessages(NvcAgentConfigEntity config, PracticeContext context, String userMessage) {
        List<Message> messages = new ArrayList<>();

        // 1. 系统提示词
        String systemPrompt = config.getSystemPrompt();

        // 2. 注入用户档案信息（如果有）
        if (context.getUserProfileSummary() != null) {
            systemPrompt += "\n\n[用户档案]\n" + context.getUserProfileSummary();
        }

        // 3. 注入场景信息（如果有）
        if (context.getScenarioDescription() != null) {
            systemPrompt += "\n\n[练习场景]\n" + context.getScenarioDescription();
        }

        // 4. 注入 RAG 知识（如果有）
        if (context.getRagContext() != null) {
            systemPrompt += "\n\n[参考资料]\n" + context.getRagContext();
        }

        messages.add(new SystemMessage(systemPrompt));

        // 5. 对话历史
        if (context.getRecentMessages() != null) {
            for (var msg : context.getRecentMessages()) {
                if (msg.getRole() == interview.guide.modules.nvcpractice.model.NvcMessageRole.USER) {
                    messages.add(new UserMessage(msg.getContent()));
                } else if (msg.getRole() == interview.guide.modules.nvcpractice.model.NvcMessageRole.ASSISTANT) {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }

        // 6. 当前用户消息
        messages.add(new UserMessage(userMessage));

        return messages;
    }
}
```

---

## 2.5 DTO 类

### AgentDecision

路径：`app/src/main/java/interview/guide/modules/nvcpractice/dto/AgentDecision.java`

```java
package interview.guide.modules.nvcpractice.dto;

import interview.guide.modules.nvcpractice.model.NvcAgentScene;

/**
 * Agent 调度决策结果
 */
public record AgentDecision(
    NvcAgentScene scene,       // 选择的 Agent 角色
    String reason,             // 决策原因（日志/调试用）
    String action              // 动作标记（STEP_ADVANCE / LOW_SCORE_GUIDE / DIFFICULT_UPGRADE 等）
) {}
```

### PracticeContext

路径：`app/src/main/java/interview/guide/modules/nvcpractice/dto/PracticeContext.java`

```java
package interview.guide.modules.nvcpractice.dto;

import interview.guide.modules.nvcpractice.model.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 练习上下文 - 传递给 Agent 的所有信息
 */
@Data
@Builder
public class PracticeContext {

    private NvcPracticeSessionEntity session;           // 当前会话
    private List<NvcPracticeMessageEntity> recentMessages; // 最近的对话消息（滑动窗口）
    private NvcEvaluationEntity lastEvaluation;         // 最近一次评估结果
    private int roundCount;                             // 当前轮次
    private String userProfileSummary;                  // 用户档案摘要（注入到系统提示词）
    private String scenarioDescription;                 // 场景描述（场景驱动模式）
    private String ragContext;                          // RAG 检索到的知识（注入到系统提示词）
    private NvcScenarioEntity scenario;                 // 场景详情
}
```

### AgentConfigDTO

路径：`app/src/main/java/interview/guide/modules/nvcpractice/dto/AgentConfigDTO.java`

```java
package interview.guide.modules.nvcpractice.dto;

import interview.guide.modules.nvcpractice.model.NvcAgentScene;

public record AgentConfigDTO(
    NvcAgentScene agentScene,
    String displayName,
    String description,
    String systemPrompt,
    String modelProvider,
    String modelName,
    Double temperature,
    Integer maxTokens,
    Double topP,
    Boolean isEnabled
) {}
```

### AgentConfigUpdateRequest

路径：`app/src/main/java/interview/guide/modules/nvcpractice/dto/AgentConfigUpdateRequest.java`

```java
package interview.guide.modules.nvcpractice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AgentConfigUpdateRequest(
    String systemPrompt,
    String modelProvider,
    String modelName,

    @DecimalMin("0.0") @DecimalMax("2.0")
    Double temperature,

    @Min(100) @Max(8000)
    Integer maxTokens,

    @DecimalMin("0.0") @DecimalMax("1.0")
    Double topP,

    Boolean isEnabled
) {}
```

---

## 2.6 NvcAgentConfigController — Agent 配置管理 API

路径：`app/src/main/java/interview/guide/modules/nvcpractice/controller/NvcAgentConfigController.java`

```java
package interview.guide.modules.nvcpractice.controller;

import interview.guide.common.result.Result;
import interview.guide.modules.nvcpractice.dto.AgentConfigDTO;
import interview.guide.modules.nvcpractice.dto.AgentConfigUpdateRequest;
import interview.guide.modules.nvcpractice.model.NvcAgentScene;
import interview.guide.modules.nvcpractice.service.NvcAgentConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nvc/agents")
@RequiredArgsConstructor
public class NvcAgentConfigController {

    private final NvcAgentConfigService agentConfigService;

    /**
     * 获取所有 Agent 配置
     */
    @GetMapping("/configs")
    public Result<List<AgentConfigDTO>> getAllConfigs() {
        var configs = agentConfigService.getAllConfigs().stream()
            .map(agentConfigService::toDTO)
            .toList();
        return Result.success(configs);
    }

    /**
     * 获取单个 Agent 配置
     */
    @GetMapping("/configs/{scene}")
    public Result<AgentConfigDTO> getConfig(@PathVariable NvcAgentScene scene) {
        var config = agentConfigService.getConfig(scene);
        return Result.success(agentConfigService.toDTO(config));
    }

    /**
     * 更新 Agent 配置（热更新）
     * 更新后立即生效，无需重启
     */
    @PutMapping("/configs/{scene}")
    public Result<AgentConfigDTO> updateConfig(
            @PathVariable NvcAgentScene scene,
            @Valid @RequestBody AgentConfigUpdateRequest request) {
        var updated = agentConfigService.updateConfig(scene, request);
        return Result.success(agentConfigService.toDTO(updated));
    }
}
```

---

## 2.7 补充 ErrorCode

在 `ErrorCode.java` 中确认以下错误码存在（Step 1 中已添加）：

```java
// NVC 练习相关 (3xxx)
NVC_AGENT_CONFIG_NOT_FOUND(3005, "Agent配置不存在"),
```

如果 Step 1 没有添加，在这一步补充。

---

## 2.8 验证清单

```
□ NvcAgentConfigService 编译通过
  □ getConfig() 能从 DB 读取配置
  □ getConfig() 第二次调用从 Redis 缓存读取
  □ updateConfig() 更新 DB 后缓存自动失效
  □ updateConfig() 后再次 getConfig() 返回新配置
□ NvcAgentOrchestrator 编译通过
  □ decideNextAgent() 在 CREATED 阶段返回 SCENARIO_GENERATOR
  □ decideStructuredStep() 在评分 >= 70 时推进到下一步
  □ decideStructuredStep() 在评分 < 70 时保持当前步骤
  □ decideFreeDialog() 在评分 < 50 时切换到引导模式
  □ decideFreeDialog() 在评分 >= 60 且轮次足够时切换困难搭档
□ NvcAgentChatService 编译通过
  □ chat() 能正常调用 LLM 并返回结果
  □ chatStream() 能正常流式输出
□ NvcAgentConfigController 编译通过
  □ GET /api/nvc/agents/configs 返回所有配置
  □ GET /api/nvc/agents/configs/{scene} 返回单个配置
  □ PUT /api/nvc/agents/configs/{scene} 更新配置并立即生效
□ 所有 DTO 编译通过
□ 项目启动成功
□ 通过 Swagger 或 curl 测试 Agent 配置 API
□ Git 提交："Step 2: Add Agent config service and orchestrator"
```

---

## 2.9 关键设计决策记录

### 为什么用 Redis 缓存 Agent 配置？

- Agent 配置在每次对话时都需要读取（系统提示词、温度等）
- 频繁查 DB 会有性能开销
- Redis 缓存 + 主动失效 = 热更新 + 高性能

### 为什么 Orchestartor 不直接依赖具体 Agent 实现？

- Orchestator 只做**决策**（选择哪个 Agent），不做**执行**（调用 LLM）
- 执行由 NvcAgentChatService 负责
- 这样 Orchestator 的逻辑可以单独测试，不依赖 LLM 调用

### 为什么 Plan-Execute-Reflect 不用 LLM 做 Plan？

- 当前版本的 Plan 逻辑用代码实现（规则判断），更可控、更快
- 如果未来需要更复杂的策略，可以在 decideNextAgent 中加入 LLM 调用做 Plan
- Reflect 阶段用了 LLM（chatPlain），因为反思需要自然语言理解能力
