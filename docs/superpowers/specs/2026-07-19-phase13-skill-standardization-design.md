# Phase 1.3 Skill 标准化 — 设计文档

> 日期：2026-07-19
> 状态：已批准
> 范围：NvcScenarioRecommendService + Skill 理念确认

---

## 一、背景与目标

### 1.1 原规划 vs 修正

Phase 1.3 原规划设计了自定义 `NvcSkill<T>` 接口 + `SkillInput` + `SkillContext` + 3 个 Skill 实现类（共 6 个新文件）。经评估发现：

- **Spring AI 原生 `@Tool` 注解**已覆盖 Function Calling 需求
- **现有 `NvcEvaluationService` / `NvcScenarioService`** 已经是事实上的 "Skill"
- 自定义接口层违反 YAGNI 原则

**修正结论：** 不新建 Skill 接口。Service 即 Skill，Tool 即 `@Tool` 方法。

### 1.2 NvcTool 层保留决策

经评估，现有的 `NvcTool` 接口 + `NvcToolRegistry` 架构保留不变：

| 组件 | 保留理由 |
|---|---|
| `NvcTool` 接口 | 统一契约，11 个 Tool 已基于此实现 |
| `NvcToolRegistry` | **场景过滤**是核心业务价值，Spring AI 不提供 |
| `NvcToolSceneMapping` | scene → toolNames 映射，纯业务逻辑 |
| `NvcToolResult` / `NvcToolContext` | 已有工具依赖，迁移成本 > 收益 |

**结论：** 不做 Tool 层重构。Phase 1.3 只新增 `NvcScenarioRecommendService`。

---

## 二、NvcScenarioRecommendService 设计

### 2.1 功能定位

**用户价值：** 根据用户的薄弱环节，智能推荐最合适的练习场景。

**技术定位：** 纯内部 Service，不暴露为 Tool。供练习结束推荐、首页推荐等场景调用。

### 2.2 核心决策

| 决策点 | 选择 | 理由 |
|---|---|---|
| 推荐策略 | 加权排序（4 维度按分数加权） | 比单维度匹配更均衡 |
| 查询方式 | 内存过滤 | 场景量小（<1000），性能无差异 |
| 使用方式 | 纯 Service（内部调用） | 推荐是系统主动行为，不需要 LLM 调用 |
| 排除策略 | 不排除最近练过的 | 数据量小时排除会减少推荐结果 |
| 场景范围 | 全部场景（不限 isSystem） | 后续可配置 |

### 2.3 算法

```
输入：userId, limit（默认 5）
输出：List<NvcScenarioEntity>（按推荐优先级降序）

步骤：
1. 获取用户能力雷达 AbilityRadarDTO
   → observation / feeling / need / request 各维度分数（0-100）

2. 计算每个维度的推荐权重
   weight[dimension] = (100 - score[dimension]) / 100.0
   → 分数越低，权重越高

3. 加载全部场景

4. 对每个场景：
   a. 解析 focusElements（JSON 数组，如 ["FEELING","OBSERVATION"]）
   b. 计算场景得分：sceneScore = Σ weight[dimension]
   c. 无 focusElements 的场景得分为 0（不推荐）

5. 按 sceneScore 降序排列，取 Top limit 个
```

**示例：**
```
用户分数：observation=45, feeling=30, need=60, request=50
权重：observation=0.55, feeling=0.70, need=0.40, request=0.50

场景A focusElements=["FEELING","OBSERVATION"] → 0.70 + 0.55 = 1.25
场景B focusElements=["NEED"]                   → 0.40
场景C focusElements=["FEELING","REQUEST"]      → 0.70 + 0.50 = 1.20

推荐排序：场景A(1.25) > 场景C(1.20) > 场景B(0.40)
```

### 2.4 接口设计

```java
@Service
@RequiredArgsConstructor
public class NvcScenarioRecommendService {

    private final NvcProfileService profileService;
    private final NvcScenarioRepository scenarioRepository;

    /**
     * 基于用户薄弱维度推荐场景
     * @param userId 用户ID
     * @param limit  返回数量，默认 5
     * @return 按推荐优先级排序的场景列表
     */
    public List<NvcScenarioEntity> recommend(Long userId, int limit) { ... }
}
```

### 2.5 依赖关系

```
NvcScenarioRecommendService
  ├── NvcProfileService.getAbilityRadar(userId)  → 获取用户维度分数
  └── NvcScenarioRepository.findAll()            → 加载场景（内存过滤）
```

### 2.6 文件变更清单

| 操作 | 文件 | 说明 |
|---|---|---|
| ➕ 新建 | `modules/nvcpractice/service/NvcScenarioRecommendService.java` | 推荐服务 |

无其他文件变更。

---

## 三、后续集成点（不在本次范围）

| 集成点 | 说明 |
|---|---|
| `NvcAgentOrchestrator.reflect()` | 练习结束后调用 recommend，返回推荐场景 |
| 首页 API | 展示"为你推荐"区域 |
| 前端推荐卡片 | 展示推荐场景及推荐理由 |

---

## 四、验收标准

```
□ NvcScenarioRecommendService 实现完成
  □ recommend(userId, limit) 方法可用
  □ 基于 4 维度加权排序
  □ 分数越低的维度，对应场景推荐优先级越高
  □ 无 focusElements 的场景不推荐
  □ 返回结果按得分降序排列
```
