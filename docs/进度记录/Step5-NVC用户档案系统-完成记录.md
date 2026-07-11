# Step 5: NVC 用户档案系统 - 完成记录

## 完成时间

2026-07-11

## 实现内容

### 新增文件（14个）

| 文件 | 类型 | 职责 |
|------|------|------|
| NvcProfileController.java | Controller | 档案 + 沟通记录 API |
| NvcDashboardController.java | Controller | Dashboard 统计 API |
| UserProfileDTO.java | DTO | 档案响应 |
| UserProfileUpdateRequest.java | DTO | 档案更新请求 |
| AbilityRadarDTO.java | DTO | 雷达图数据 |
| AbilityTrendDTO.java | DTO | 趋势数据 |
| CommunicationRecordDTO.java | DTO | 沟通记录响应 |
| CommunicationAnalysisRequest.java | DTO | 分析请求 |
| CommunicationAnalysisResult.java | DTO | LLM 结构化输出 |
| NvcProfileService.java | Service | 档案 CRUD + 能力更新 |
| NvcCommunicationAnalysisService.java | Service | LLM 分析沟通记录 |
| NvcDashboardService.java | Service | 基础统计 |
| nvc-profile-analyze-system.st | Prompt | 分析提示词 |

### 修改文件（1个）

| 文件 | 修改内容 |
|------|----------|
| NvcEvaluateStreamConsumer.java | 集成档案更新 |

## API 端点

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

## 核心功能

### 1. 档案管理
- 获取/创建用户档案（不存在则自动创建默认档案）
- 更新档案字段（沟通背景、性格、风格、情绪触发点等）

### 2. 能力追踪
- 练习结束后自动记录四要素分数
- 基于最近 10 次练习平均分计算 NVC 等级
- 等级规则：练习次数 < 3 = BEGINNER，平均分 >= 80 = ADVANCED，>= 60 = INTERMEDIATE

### 3. 雷达图数据
- 返回最近 10 次练习的四要素平均分
- 包含 empathy（共情）维度

### 4. 趋势数据
- 返回最近 30 次练习的分数变化
- 包含练习类型信息

### 5. 沟通记录分析
- 用户上传真实沟通记录
- LLM 分析四要素质量
- 生成 NVC 改写建议
- 保存分析结果

### 6. 预留接口
- `getUserProfilePrompt(userId)` - 获取用户画像字符串，用于 Prompt 注入
- `enrichProfileFromDialogue(userId, messages)` - 后续实现 AI 从对话中自动分析

## 技术亮点

1. **结构化输出**：使用 `StructuredOutputInvoker` + `BeanOutputConverter` 实现 LLM 结构化输出
2. **等级计算**：基于滑动窗口（最近 10 次）的动态等级计算
3. **异步集成**：在 Redis Stream Consumer 中集成档案更新，不影响主流程性能

## 编译验证

- ✅ `gradlew compileJava` 编译通过
- ✅ 所有文件已提交到 Git

## Git 提交

```
feat(step5): add user profile system with ability tracking
