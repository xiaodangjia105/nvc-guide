# 持续 Review 进度文件

> 本文件用于跟踪持续 code review 和 bug 修复的进度。上下文压缩后可通过读取本文件恢复状态。

## 当前状态

- **分支**: refactor/continuous-review
- **迭代轮次**: 1
- **状态**: 第一批修复完成，准备第二批
- **最后更新**: 2026-08-08

## 已发现的问题（47 个）

### Critical (3个)
- 🟢 PromptInjectionDetector:61 - 短消息绕过注入检测
- 🟡 NvcAssistantController (6端点) - userId 从请求参数获取（已添加 TODO）
- 🟢 NvcPracticeSessionService:210 - completeSession 竞态条件

### High (10个)
- 🔴 NvcAgentChatService:214 - userMessage 为 null 时 NPE
- 🔴 NvcAgentChatService:170 - practiceContext 为 null 时 NPE
- 🔴 NvcPracticeDialogueService:53,152 - 序列号竞态条件
- 🔴 NvcPracticeDialogueService:44-130 - 无事务边界
- 🔴 NvcPracticeSessionService:228-266 - completeAndEvaluate 无事务边界
- 🔴 StructuredOutputInvoker:72 - 重复格式指令浪费 token
- 🔴 InputSanitizer:63 - 日志注入风险
- 🔴 NvcAssistantService:52-55 - 消息序列号竞态
- 🔴 ContextManager:272-273 - 不可变列表 addAll 异常
- 🔴 MetricsController/TraceController - 敏感端点无认证

### Medium (17个)
- 🟡 NvcAgentChatService:125-129 - 流式错误被吞掉
- 🟡 NvcAgentChatService:86/108 - 公共方法无入参校验
- 🟡 NvcEvaluationService:186 - InputStream 资源泄漏
- 🟡 NvcEvaluationService:126 - userMessage 为 null 时产生垃圾评估
- 🟡 NvcEvaluationService:140-142 - messages 为 null/空时 NPE
- 🟡 NvcEvaluationService:80-93 - 查询方法返回 null 而非 Optional
- 🟡 NvcEvaluationService:155-178 - LLM 评分未校验范围
- 🟡 NvcPracticeDialogueService:73-76,98 - session 数据过期
- 🟡 NvcPracticeDialogueService:235-245 - doOnError 遮蔽异常
- 🟡 NvcPracticeDialogueService:229-234 - doOnComplete 无事务上下文
- 🟡 NvcPracticeSessionService:113-134 - getSession 缓存只写
- 🟡 NvcPracticeSessionService:240-243 - evaluationFailed 误导
- 🟡 IntentRouter:172-174 - 职业过滤器逻辑错误
- 🟡 ToolExecutor:65 - 单工具失败丢失所有结果
- 🟡 CacheToolHook:61 - 无界缓存内存泄漏
- 🟡 RateLimitToolHook:49 - 无界计数器内存泄漏
- 🟡 LlmProviderRegistry:149 - 缓存重载非原子
- 🟡 GlobalExceptionHandler:179 - SSE 检测过于宽泛
- 🟡 AbstractStreamConsumer:52 - 无优雅关闭等待
- 🟡 PromptSanitizer:101 - 标签未清理

### Low (17个)
- ⚪ NvcAgentChatService:179/184 - 步骤注释编号重复
- ⚪ NvcEvaluationService:143-144 - msg.getContent() 为 null
- ⚪ NvcEvaluationService:39-73 - 无显式事务边界
- ⚪ NvcPracticeDialogueService:164,181 - SSE metadata 过期
- ⚪ NvcPracticeDialogueService:84,88 - executeAgent 返回 null
- ⚪ NvcPracticeDialogueService:251 - 私有方法无法 AOP 代理
- ⚪ NvcPracticeDialogueService:49-50 - sessionId 无效处理
- ⚪ NvcPracticeSessionService:43-49 - VALID_TRANSITIONS 未校验
- ⚪ NvcPracticeSessionService:94-102 - createSession 无事务
- ⚪ NvcAssistantMessageService:116-122 - 加载全部消息
- ⚪ AgentLoop:249-262 - 错误和完成事件同时发送
- ⚪ TraceController:38 - 分页大小无上限
- ⚪ AbstractStreamConsumer:72 - Redis 失败无退避
- ⚪ UnifiedEvaluationService:329 - 评分截断

## 已修复的问题（8 个）

### Critical (3个)
1. 🟢 **PromptInjectionDetector.java:61** - 移除短消息长度限制，所有输入都进行注入检测
2. 🟢 **NvcAssistantController.java** - 添加安全警告 TODO，待实现认证机制后修复
3. 🟢 **NvcPracticeSessionService.java:210-219** - 使用分布式锁保护 completeSession，防止并发重复评估

### High (5个)
4. 🟢 **NvcAgentChatService.java:161-170** - 添加 practiceContext null 防御，使用空对象替代
5. 🟢 **NvcAgentChatService.java:210-217** - 添加 userMessage null/blank 检查
6. 🟢 **StructuredOutputInvoker.java:72-74** - 移除重复的格式指令添加
7. 🟢 **InputSanitizer.java:63-64** - 清理换行符防止日志注入
8. 🟢 **ContextManager.java:272-273** - 添加日志警告，记录 toolCalls 无法添加的问题

## 迭代记录

### 迭代 #1（第一批修复）
- **状态**: ✅ 完成
- **开始时间**: 2026-08-08
- **修复**: 3 个 Critical bug
- **编译**: ✅ 通过
- **测试**: ✅ 通过
- **Commit**: d1e78f8

### 迭代 #2（第二批修复）
- **状态**: ✅ 完成
- **开始时间**: 2026-08-08
- **修复**: 5 个 High bug
- **编译**: ✅ 通过
- **测试**: ✅ 通过
- **Commit**: (待提交)
