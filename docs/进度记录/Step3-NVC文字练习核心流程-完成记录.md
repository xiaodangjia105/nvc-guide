# Step 3：NVC 文字练习核心流程 — 完成记录

> 完成日期：2026-07-10
>
> 状态：后端 API 完成，前端未对接

---

## 一、本步实现的功能

### 1. 创建练习会话

- **接口**: `POST /api/nvc/practice/sessions?userId={userId}`
- **功能**: 根据练习模式创建会话，支持三种模式：
  - `SCENARIO` — 场景驱动练习（可指定场景或随机分配）
  - `FREE_DIALOG` — 自由对话练习
  - `STRUCTURED_FOUR_STEP` — 结构化四步练习（自动设置初始步骤为 OBSERVE）
- **随机场景**: 场景驱动模式下，如果不传 `scenarioId`，会从数据库中按难度随机分配一个场景

### 2. 发送消息（非流式）

- **接口**: `POST /api/nvc/practice/sessions/{sessionId}/messages`
- **功能**: 发送用户消息 → Agent 调度 → 获取 AI 回复 → 保存消息 → 返回结果
- **自动状态切换**: 首次发送消息时，会话自动从 CREATED 切换到 IN_PROGRESS

### 3. 发送消息（流式 SSE）

- **接口**: `POST /api/nvc/practice/sessions/{sessionId}/messages/stream`
- **功能**: 同上，但以 SSE 流式返回
- **事件格式**:
  ```
  event: metadata
  data: {"agentScene":"DIALOGUE_GUIDE","decisionReason":"...","action":null,"currentStep":null}

  event: message
  data: 你

  event: message
  data: 好

  event: done
  data: {"length":3}
  ```

### 4. 获取对话历史

- **接口**: `GET /api/nvc/practice/sessions/{sessionId}/messages`
- **功能**: 返回该会话的所有消息（MessageResponse DTO，非 Entity）

### 5. 获取会话列表

- **接口**: `GET /api/nvc/practice/sessions?userId={userId}&phase={phase}`
- **功能**: 查询用户的练习会话列表，可按阶段过滤

### 6. 获取会话详情

- **接口**: `GET /api/nvc/practice/sessions/{sessionId}`
- **功能**: 获取单个会话的详细信息

### 7. 结束会话

- **接口**: `POST /api/nvc/practice/sessions/{sessionId}/complete`
- **功能**: 将会话状态切换为 COMPLETED，记录完成时间

---

## 二、新增/修改的文件

### 新增文件（10 个）

| 文件 | 说明 |
|------|------|
| `dto/CreatePracticeSessionRequest.java` | 创建会话请求 record |
| `dto/PracticeSessionResponse.java` | 会话响应 record |
| `dto/SendMessageRequest.java` | 发送消息请求 record |
| `dto/DialogueResponse.java` | 非流式对话响应 record |
| `dto/MessageResponse.java` | 消息响应 DTO（替代 Entity） |
| `dto/StreamMetadata.java` | SSE 首包元数据 record |
| `service/NvcPracticeSessionService.java` | 会话管理服务 |
| `service/NvcPracticeDialogueService.java` | 对话核心逻辑服务 |
| `controller/NvcPracticeController.java` | 练习 REST API 控制器 |
| `resources/prompts/nvc-scenario-generate-system.st` | 场景生成辅助 prompt |

### 修改文件（2 个）

| 文件 | 修改内容 |
|------|---------|
| `NvcAgentOrchestrator.java` | 新增 `executeAgentStream()` 流式执行方法 |
| `NvcScenarioRepository.java` | 新增 `findByDifficulty()` 查询方法 |

---

## 三、核心服务说明

### NvcPracticeSessionService

负责会话的 CRUD 操作：
- 创建会话（支持随机场景分配）
- 查询会话（带 Redis 缓存，24 小时过期）
- 更新会话状态/步骤/Agent 场景
- 结束会话

### NvcPracticeDialogueService

负责对话的核心流程：
- 接收用户消息 → 调用 `NvcAgentOrchestrator` 决策 → 执行 Agent → 保存消息
- 支持非流式和流式（SSE）两种模式
- 流式模式下，首包发送 metadata 事件，后续纯文本 token，最后 done 事件
- 对话历史自动传递给 Agent（上下文连续）

### NvcAgentOrchestrator（已有，本步补充）

- `buildPracticeContext()` — 构建练习上下文
- `decideNextAgent()` — 根据模式和状态决定使用哪个 Agent
- `executeAgent()` — 非流式执行 Agent 对话
- `executeAgentStream()` — 流式执行 Agent 对话（本步新增）

---

## 四、Agent 调度逻辑

### 场景驱动 / 自由对话模式

```
首轮 → DIALOGUE_GUIDE（或 SCENARIO_GENERATOR，取决于场景是否已加载）
后续 → 根据评估分数动态切换：
  - 低分 → DIALOGUE_GUIDE（引导用户改进）
  - 高分 → DIFFICULT_PARTNER（增加难度）
```

### 结构化四步模式

```
OBSERVE → STEP_OBSERVE_COACH
FEELING → STEP_FEELING_COACH
NEED    → STEP_NEED_COACH
REQUEST → STEP_REQUEST_COACH
```

步骤推进条件：该步骤评估分数 >= 70 分

---

## 五、当前状态与限制

### ✅ 已完成

- 后端 7 个 REST API 端点
- Agent 调度与对话执行
- SSE 流式输出
- 会话状态管理（CREATED → IN_PROGRESS → COMPLETED）
- Redis 缓存
- 随机场景分配
- 编译通过

### ❌ 未完成（后续步骤）

- **前端未对接**: 前端目前没有 NVC 练习相关的页面和 API 调用
- **实时评估**: 对话后不会自动评估 NVC 表达质量（Step 3 设计决策：不实现评估）
- **用户档案**: `userProfileSummary` 暂为空（Step 5 实现）
- **RAG 知识**: `ragContext` 暂为空（知识库模块实现）
- **AI 生成场景**: 仅支持从 DB 加载场景，不支持 AI 动态生成

---

## 六、API 测试方法

### 前置条件

1. 确保 Docker 服务运行（PostgreSQL + Redis）
2. 启动应用：`.\gradlew.bat bootRun`

### 测试用例

#### 1. 创建会话（指定场景）

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions?userId=1" \
  -H "Content-Type: application/json" \
  -d '{"practiceMode":"SCENARIO","scenarioId":1,"difficulty":"MEDIUM"}'
```

#### 2. 创建会话（随机场景）

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions?userId=1" \
  -H "Content-Type: application/json" \
  -d '{"practiceMode":"SCENARIO","difficulty":"HARD"}'
```

#### 3. 发送消息

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions/1/messages" \
  -H "Content-Type: application/json" \
  -d '{"content":"开始练习"}'
```

#### 4. 获取对话历史

```bash
curl "http://localhost:8080/api/nvc/practice/sessions/1/messages"
```

#### 5. 流式对话

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions/1/messages/stream" \
  -H "Content-Type: application/json" \
  -d '{"content":"你好"}' \
  --no-buffer
```

#### 6. 结束会话

```bash
curl -X POST "http://localhost:8080/api/nvc/practice/sessions/1/complete"
```

---

## 七、Git 提交记录

```
a0d0aa2 feat(step3): add practice dialogue DTOs
ac6300f feat(step3): add findByDifficulty to NvcScenarioRepository
c270be9 feat(step3): add executeAgentStream to NvcAgentOrchestrator
126c7c9 feat(step4): add NvcPracticeSessionService with random scenario
caae728 feat(step3): add NvcPracticeDialogueService with SSE streaming
287cee1 fix(nvcpractice): add logging for StreamMetadata serialization failure
900eed9 feat(step3): add NvcPracticeController with SSE support
1d15f71 feat(step3): add scenario generation prompt template
```
