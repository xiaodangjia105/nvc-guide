# Step 6: 场景库管理 - 完成记录

## 完成时间

2026-07-11

## 实现内容

### 新增文件（6个）

| 文件 | 类型 | 职责 |
|------|------|------|
| NvcScenarioController.java | Controller | 场景 REST API |
| NvcScenarioService.java | Service | 场景 CRUD + AI 生成 + 使用统计 |
| ScenarioDTO.java | DTO | 场景响应 |
| ScenarioGenerateRequest.java | DTO | AI 生成请求 |
| ScenarioQueryRequest.java | DTO | 查询条件 |
| NvcScenarioServiceTest.java | 测试 | Service 单元测试（15 tests） |
| NvcScenarioControllerTest.java | 测试 | Controller 单元测试（5 tests） |

### 修改文件（1个）

| 文件 | 修改内容 |
|------|----------|
| NvcPracticeDialogueService.java | 集成场景使用统计 |

## API 端点

### NvcScenarioController

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/nvc/scenarios | 查询场景列表（可按类型/难度筛选） |
| GET | /api/nvc/scenarios/{id} | 获取单个场景详情 |
| POST | /api/nvc/scenarios/generate | AI 生成新场景 |

## 核心功能

### 1. 场景查询
- 按场景类型筛选（WORKPLACE/FAMILY/INTIMATE/SOCIAL/SELF）
- 按难度筛选（EASY/MEDIUM/HARD）
- 无条件查询返回系统场景（按使用次数排序）

### 2. 场景详情
- 获取单个场景的完整信息
- 场景不存在时抛出 `BusinessException(NVC_SCENARIO_NOT_FOUND)`

### 3. AI 生成场景
- 调用 LLM 生成 NVC 练习场景
- Prompt 模板：`nvc-scenario-generate-system.st`
- 防御性 JSON 解析：缺失字段使用默认值
- 生成的场景标记为 `isSystem = false`
- 限流：`@RateLimit(count = 10)`

### 4. 场景使用统计
- `incrementUsage()` 方法：使用次数 +1
- `@Transactional` 保证并发安全
- 集成到对话流程：场景驱动模式下每次发送消息自动统计

## 技术实现

### Service 层
- `queryScenarios(ScenarioQueryRequest)` — 按条件查询
- `getScenario(Long id)` — 获取单个场景
- `generateScenario(ScenarioGenerateRequest)` — AI 生成场景
- `incrementUsage(Long scenarioId)` — 使用次数 +1
- `toDTO(NvcScenarioEntity)` — Entity → DTO 转换

### AI 生成逻辑
1. 加载 Prompt 模板（`loadSystemPrompt()`）
2. 构建用户 Prompt（`buildGeneratePrompt()`）
3. 调用 ChatClient 获取 LLM 响应
4. 解析 JSON 并构建实体
5. 保存到数据库

### 错误处理
- 场景不存在：`NVC_SCENARIO_NOT_FOUND`
- JSON 解析失败：`AI_SERVICE_ERROR`
- Prompt 加载失败：使用默认 Prompt + 日志警告

## 技术亮点

1. **防御性 JSON 解析**：使用 `node.has()` 检查 + 默认值，避免 NPE
2. **原子性使用统计**：`@Transactional` + `incrementUsage()` 防止并发竞态
3. **限流保护**：`@RateLimit` 防止 LLM API 成本暴露
4. **结构化日志**：关键操作记录日志（场景生成、使用统计）

## 测试覆盖

### NvcScenarioServiceTest（15 tests）
- `queryScenarios()` — 4 个分支全覆盖（类型+难度、仅类型、仅难度、无条件）
- `getScenario()` — 存在/不存在
- `incrementUsage()` — 存在/不存在
- `toDTO()` — 字段映射验证
- `generateScenario()` — 成功/缺失字段/解析失败

### NvcScenarioControllerTest（5 tests）
- `queryScenarios()` — 返回列表
- `getScenario()` — 返回单个
- `generateScenario()` — 生成成功

## 编译验证

- ✅ `gradlew compileJava` 编译通过
- ✅ `gradlew test` 全部测试通过（253/253）
- ✅ 所有文件已提交到 Git

## Git 提交

```
494ce94 feat(step6): add scenario DTOs
122571b feat(step6): add NvcScenarioService with basic methods
3163423 test(step6): add NvcScenarioService unit tests
dd55c78 feat(step6): add AI scenario generation
505e625 feat(step6): add NvcScenarioController and tests
1271c82 feat(step6): complete scenario library management
d55b401 fix(nvcscenario): add @Transactional, @RateLimit, fix error code and silent catch
2d28179 feat(step6): integrate scenario usage tracking into dialogue flow
```

## 后续扩展

根据设计文档，未来可扩展：
- **方案 B**：缓存（@Cacheable）、分页（Pageable）、编辑删除（PUT/DELETE）
- **方案 C**：智能推荐（用户画像匹配）、标签搜索（JSONB 查询）、场景克隆
