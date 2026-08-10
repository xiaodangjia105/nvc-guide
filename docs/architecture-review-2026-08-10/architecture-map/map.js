window.MAP_DATA = {
  "project": "nvc-guide",
  "generatedAt": "2026-08-10T16:00:00Z",
  "source": "audit",
  "subsystems": [
    {
      "id": "common",
      "label": "公共层 & 基础设施",
      "color": "#6366f1"
    },
    {
      "id": "knowledgebase",
      "label": "知识库 RAG",
      "color": "#8b5cf6"
    },
    {
      "id": "llmprovider",
      "label": "LLM 提供者管理",
      "color": "#06b6d4"
    },
    {
      "id": "nvcassistant",
      "label": "Agent 核心系统",
      "color": "#f59e0b"
    },
    {
      "id": "nvcpractice",
      "label": "NVC 练习",
      "color": "#10b981"
    },
    {
      "id": "profile-scenario",
      "label": "用户档案 & 场景库",
      "color": "#ec4899"
    },
    {
      "id": "nvcvoice",
      "label": "语音练习",
      "color": "#f97316"
    },
    {
      "id": "nvcwiki",
      "label": "NVC 知识百科",
      "color": "#14b8a6"
    },
    {
      "id": "frontend",
      "label": "前端 React SPA",
      "color": "#3b82f6"
    },
    {
      "id": "external",
      "label": "外部系统",
      "color": "#6b7280"
    }
  ],
  "nodes": [
    {
      "id": "postgres",
      "label": "PostgreSQL + pgvector",
      "tech": "pgvector/pgvector:pg16",
      "engine": "db",
      "engineLabel": "PostgreSQL 16",
      "subsystem": "external",
      "status": "shipped",
      "role": "业务数据存储 + 向量搜索",
      "input": "SQL 查询 / 向量相似度查询",
      "processing": "JPA 持久化 + pgvector 余弦距离检索",
      "output": "查询结果 / Top-K 向量匹配",
      "files": [
        "docker-compose.yml:13-36"
      ],
      "notes": ""
    },
    {
      "id": "redis",
      "label": "Redis 缓存 & 消息队列",
      "tech": "redis:7",
      "engine": "cache",
      "engineLabel": "Redis 7 + Redis Stream",
      "subsystem": "external",
      "status": "shipped",
      "role": "Session 缓存、异步任务队列、限流计数",
      "input": "缓存读写命令 / Stream 生产者消息",
      "processing": "LRU 缓存 + Stream 消费者组 + Lua 滑动窗口限流",
      "output": "缓存值 / 消费者组消息分发",
      "files": [
        "docker-compose.yml:47-59"
      ],
      "notes": ""
    },
    {
      "id": "minio",
      "label": "MinIO 对象存储",
      "tech": "minio/minio",
      "engine": "storage",
      "engineLabel": "MinIO (S3 兼容)",
      "subsystem": "external",
      "status": "shipped",
      "role": "文件存储（知识库文档、头像等）",
      "input": "S3 PUT/GET 请求",
      "processing": "对象存储 + 公共读 Bucket",
      "output": "文件 URL / 文件流",
      "files": [
        "docker-compose.yml:72-89"
      ],
      "notes": ""
    },
    {
      "id": "dashscope",
      "label": "DashScope LLM (通义千问)",
      "tech": "qwen3.5-flash via OpenAI-compatible API",
      "engine": "llm",
      "engineLabel": "阿里云 DashScope · Qwen3.5-Flash",
      "subsystem": "external",
      "status": "shipped",
      "role": "对话生成、意图路由、NVC 评估、摘要压缩",
      "input": "System Prompt + 用户消息 + 工具定义",
      "processing": "Chat Completion / Embedding / ASR / TTS",
      "output": "AI 回复 / 嵌入向量 / 语音转文字 / 文字转语音",
      "files": [
        "app/src/main/java/nvc/guide/common/ai/LlmProviderRegistry.java"
      ],
      "notes": ""
    },
    {
      "id": "app-entry",
      "label": "Spring Boot 应用入口",
      "tech": "App.java",
      "engine": "server",
      "subsystem": "common",
      "status": "shipped",
      "role": "应用启动、组件扫描、配置加载",
      "input": "JVM 启动参数 + application.yml",
      "processing": "Spring 容器初始化 + 自动配置",
      "output": "运行中的 Spring 上下文",
      "files": [
        "app/src/main/java/nvc/guide/App.java"
      ],
      "notes": ""
    },
    {
      "id": "llm-registry",
      "label": "LLM 提供者注册中心",
      "tech": "LlmProviderRegistry.java",
      "engine": "llm",
      "subsystem": "common",
      "status": "shipped",
      "role": "管理多个 LLM 提供者，动态选择 ChatClient",
      "input": "Provider 配置（DB + Properties）",
      "processing": "构建 ChatClient（Default / Plain / Voice 三种变体）",
      "output": "ChatClient 实例",
      "files": [
        "app/src/main/java/nvc/guide/common/ai/LlmProviderRegistry.java:102-141"
      ],
      "notes": ""
    },
    {
      "id": "global-exception",
      "label": "全局异常处理器",
      "tech": "GlobalExceptionHandler.java",
      "engine": "server",
      "subsystem": "common",
      "status": "shipped",
      "role": "统一异常处理，BusinessException → HTTP 200 + Result.error",
      "input": "Controller 层抛出的异常",
      "processing": "异常分类 + ErrorCode 映射",
      "output": "统一 JSON 响应",
      "files": [
        "app/src/main/java/nvc/guide/common/exception/GlobalExceptionHandler.java"
      ],
      "notes": ""
    },
    {
      "id": "rate-limiter",
      "label": "API 限流切面",
      "tech": "RateLimitAspect.java + @RateLimit",
      "engine": "server",
      "subsystem": "common",
      "status": "shipped",
      "role": "Redis Lua 滑动窗口限流",
      "input": "HTTP 请求",
      "processing": "AOP 拦截 + Redis Lua 脚本计数",
      "output": "放行或抛 RateLimitExceededException",
      "files": [
        "app/src/main/java/nvc/guide/common/aspect/RateLimitAspect.java"
      ],
      "notes": ""
    },
    {
      "id": "prompt-security",
      "label": "Prompt 注入防护",
      "tech": "PromptInjectionDetector.java",
      "engine": "server",
      "subsystem": "common",
      "status": "shipped",
      "role": "19 种注入模式检测 + 输入消毒",
      "input": "用户原始输入",
      "processing": "正则匹配 19 种注入模式",
      "output": "安全的输入文本 或 拒绝",
      "files": [
        "app/src/main/java/nvc/guide/common/security/PromptInjectionDetector.java"
      ],
      "notes": ""
    },
    {
      "id": "async-stream",
      "label": "Redis Stream 异步管道",
      "tech": "AbstractStreamProducer/Consumer.java",
      "engine": "queue",
      "subsystem": "common",
      "status": "shipped",
      "role": "统一异步任务基础设施",
      "input": "业务事件消息",
      "processing": "Producer 发布 → Consumer 消费，maxLen=1000，3 次重试",
      "output": "异步处理结果",
      "files": [
        "app/src/main/java/nvc/guide/common/async/"
      ],
      "notes": ""
    },
    {
      "id": "trace-system",
      "label": "链路追踪系统",
      "tech": "TraceContext.java",
      "engine": "server",
      "subsystem": "common",
      "status": "shipped",
      "role": "自研 Trace 系统",
      "input": "Agent 调用事件",
      "processing": "Span 创建/结束 + 智能截断",
      "output": "Trace/Span 持久化到 DB",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/trace/"
      ],
      "notes": ""
    },
    {
      "id": "file-storage",
      "label": "文件存储服务",
      "tech": "FileStorageService.java",
      "engine": "storage",
      "subsystem": "common",
      "status": "shipped",
      "role": "S3/MinIO 文件操作封装",
      "input": "文件流",
      "processing": "AWS S3 SDK",
      "output": "文件 URL",
      "files": [
        "app/src/main/java/nvc/guide/infrastructure/file/FileStorageService.java"
      ],
      "notes": ""
    },
    {
      "id": "doc-parser",
      "label": "文档解析服务",
      "tech": "DocumentParseService.java + Tika",
      "engine": "server",
      "subsystem": "common",
      "status": "shipped",
      "role": "PDF/DOCX/TXT 解析",
      "input": "上传文件",
      "processing": "Apache Tika + 文本清洗",
      "output": "纯文本",
      "files": [
        "app/src/main/java/nvc/guide/infrastructure/file/DocumentParseService.java"
      ],
      "notes": ""
    },
    {
      "id": "redis-cache",
      "label": "Redis 缓存服务",
      "tech": "RedisService.java",
      "engine": "cache",
      "subsystem": "common",
      "status": "shipped",
      "role": "通用 Redis 操作封装",
      "input": "缓存 key/value",
      "processing": "Redisson 客户端",
      "output": "缓存值",
      "files": [
        "app/src/main/java/nvc/guide/infrastructure/redis/RedisService.java"
      ],
      "notes": ""
    },
    {
      "id": "pdf-export",
      "label": "PDF 导出",
      "tech": "PdfExportService.java + iText",
      "engine": "server",
      "subsystem": "common",
      "status": "shipped",
      "role": "对话/评估报告导出 PDF",
      "input": "对话数据",
      "processing": "iText 8 生成（中文方块问题）",
      "output": "PDF 文件",
      "files": [
        "app/src/main/java/nvc/guide/infrastructure/export/PdfExportService.java"
      ],
      "notes": ""
    },
    {
      "id": "kb-upload",
      "label": "知识库文档上传",
      "tech": "KnowledgeBaseUploadService.java",
      "engine": "server",
      "subsystem": "knowledgebase",
      "status": "shipped",
      "role": "文档上传到 MinIO + DB 记录",
      "input": "MultipartFile",
      "processing": "验证 → MinIO 上传 → DB",
      "output": "KnowledgeBaseEntity",
      "files": [
        "app/src/main/java/nvc/guide/modules/knowledgebase/service/KnowledgeBaseUploadService.java"
      ],
      "notes": ""
    },
    {
      "id": "kb-parse",
      "label": "知识库文档解析",
      "tech": "KnowledgeBaseParseService.java",
      "engine": "server",
      "subsystem": "knowledgebase",
      "status": "shipped",
      "role": "Tika 解析文档内容",
      "input": "KnowledgeBaseEntity",
      "processing": "DocumentParseService",
      "output": "纯文本",
      "files": [
        "app/src/main/java/nvc/guide/modules/knowledgebase/service/KnowledgeBaseParseService.java"
      ],
      "notes": ""
    },
    {
      "id": "kb-vector",
      "label": "知识库向量化",
      "tech": "KnowledgeBaseVectorService.java",
      "engine": "llm",
      "subsystem": "knowledgebase",
      "status": "shipped",
      "role": "文本 → Embedding → pgvector",
      "input": "解析文本",
      "processing": "VectorStore.add()",
      "output": "向量写入 pgvector",
      "files": [
        "app/src/main/java/nvc/guide/modules/knowledgebase/service/KnowledgeBaseVectorService.java"
      ],
      "notes": ""
    },
    {
      "id": "kb-query",
      "label": "RAG 查询",
      "tech": "KnowledgeBaseQueryService.java",
      "engine": "llm",
      "subsystem": "knowledgebase",
      "status": "shipped",
      "role": "向量检索 + LLM 增强回答",
      "input": "用户查询",
      "processing": "Embedding → Top-K → LLM",
      "output": "RAG 回答",
      "files": [
        "app/src/main/java/nvc/guide/modules/knowledgebase/service/KnowledgeBaseQueryService.java"
      ],
      "notes": ""
    },
    {
      "id": "rag-chat",
      "label": "RAG 对话会话",
      "tech": "RagChatController.java",
      "engine": "server",
      "subsystem": "knowledgebase",
      "status": "shipped",
      "role": "多轮 RAG 对话管理",
      "input": "用户消息 + sessionId",
      "processing": "会话上下文 + RAG 查询",
      "output": "对话响应",
      "files": [
        "app/src/main/java/nvc/guide/modules/knowledgebase/RagChatController.java"
      ],
      "notes": ""
    },
    {
      "id": "kb-vectorize-pipeline",
      "label": "向量化异步管道",
      "tech": "VectorizeStreamProducer/Consumer",
      "engine": "queue",
      "subsystem": "knowledgebase",
      "status": "shipped",
      "role": "Redis Stream 异步向量化",
      "input": "文档上传事件",
      "processing": "Producer → Consumer → kb-vector",
      "output": "向量化完成",
      "files": [
        "app/src/main/java/nvc/guide/modules/knowledgebase/listener/"
      ],
      "notes": ""
    },
    {
      "id": "kb-seed",
      "label": "种子知识库",
      "tech": "SeedKnowledgeBaseService.java",
      "engine": "server",
      "subsystem": "knowledgebase",
      "status": "shipped",
      "role": "启动时预置 NVC 文档",
      "input": "classpath 文档",
      "processing": "检查 → 上传 → 向量化",
      "output": "预置条目",
      "files": [
        "app/src/main/java/nvc/guide/modules/knowledgebase/service/SeedKnowledgeBaseService.java"
      ],
      "notes": ""
    },
    {
      "id": "llm-provider-crud",
      "label": "LLM Provider CRUD",
      "tech": "LlmProviderController.java",
      "engine": "server",
      "subsystem": "llmprovider",
      "status": "shipped",
      "role": "LLM 提供者管理 + API Key 加密",
      "input": "Provider 配置请求",
      "processing": "AES 加密 → DB → 清缓存",
      "output": "ProviderDTO",
      "files": [
        "app/src/main/java/nvc/guide/modules/llmprovider/controller/LlmProviderController.java"
      ],
      "notes": ""
    },
    {
      "id": "llm-bootstrap",
      "label": "Provider 初始化",
      "tech": "LlmProviderBootstrapService.java",
      "engine": "server",
      "subsystem": "llmprovider",
      "status": "shipped",
      "role": "启动时 seed 默认 Provider",
      "input": "application.yml",
      "processing": "检查 DB → 创建默认",
      "output": "就绪配置",
      "files": [
        "app/src/main/java/nvc/guide/modules/llmprovider/service/LlmProviderBootstrapService.java"
      ],
      "notes": ""
    },
    {
      "id": "agent-loop",
      "label": "Agent 多轮对话循环",
      "tech": "AgentLoop.java",
      "engine": "llm",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "核心循环：LLM → 工具 → 回传",
      "input": "用户消息 + 上下文",
      "processing": "ChatClient → 工具执行 → 循环",
      "output": "Agent 回复",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/service/agent/AgentLoop.java"
      ],
      "notes": ""
    },
    {
      "id": "intent-router",
      "label": "意图路由器",
      "tech": "IntentRouter.java",
      "engine": "llm",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "用户意图分类",
      "input": "用户消息",
      "processing": "LLM 分类",
      "output": "IntentType",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/service/agent/IntentRouter.java"
      ],
      "notes": ""
    },
    {
      "id": "prompt-builder",
      "label": "Prompt 构建器",
      "tech": "PromptBuilder.java",
      "engine": "server",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "动态构建 System Prompt",
      "input": "Agent 配置 + 场景 + 档案",
      "processing": "模板拼接 + 变量替换",
      "output": "System Prompt",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/service/agent/PromptBuilder.java"
      ],
      "notes": ""
    },
    {
      "id": "tool-executor",
      "label": "工具执行器",
      "tech": "ToolExecutor.java",
      "engine": "server",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "工具调用分发",
      "input": "工具名 + 参数",
      "processing": "查找 → Hook → 执行",
      "output": "ToolCallResult",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/service/agent/ToolExecutor.java"
      ],
      "notes": ""
    },
    {
      "id": "hook-chain",
      "label": "Hook 链 (7 个)",
      "tech": "NvcToolHook @Order(1-7)",
      "engine": "server",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "工具调用拦截链",
      "input": "工具调用",
      "processing": "RateLimit→Permission→Cache→Error→Eval→Persist→Log",
      "output": "处理结果",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/service/agent/"
      ],
      "notes": ""
    },
    {
      "id": "context-manager",
      "label": "上下文管理器",
      "tech": "ContextManager.java",
      "engine": "server",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "对话历史 + 上下文压缩",
      "input": "消息列表",
      "processing": "Token 计数 → LLM 摘要",
      "output": "压缩消息",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/service/agent/ContextManager.java"
      ],
      "notes": ""
    },
    {
      "id": "metrics-collector",
      "label": "指标采集",
      "tech": "MetricsCollector.java",
      "engine": "queue",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "异步采集调用指标",
      "input": "Agent 事件",
      "processing": "Redis Stream 发布",
      "output": "MetricsEntity",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/metrics/MetricsCollector.java"
      ],
      "notes": ""
    },
    {
      "id": "offline-evaluation",
      "label": "离线评估",
      "tech": "OfflineEvaluationService.java",
      "engine": "llm",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "NVC 四要素评估",
      "input": "对话消息",
      "processing": "LLM 结构化输出",
      "output": "EvaluationReport",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/evaluation/OfflineEvaluationService.java"
      ],
      "notes": ""
    },
    {
      "id": "llm-fallback",
      "label": "LLM 降级",
      "tech": "LlmFallbackHandler.java",
      "engine": "server",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "LLM 失败重试+降级",
      "input": "LLM 异常",
      "processing": "3 次重试 → 降级",
      "output": "降级回复",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/fallback/LlmFallbackHandler.java"
      ],
      "notes": ""
    },
    {
      "id": "nvc-assistant-controller",
      "label": "NVC 助手 API",
      "tech": "NvcAssistantController.java",
      "engine": "server",
      "subsystem": "nvcassistant",
      "status": "shipped",
      "role": "对话 REST API 入口",
      "input": "POST /api/nvc/assistant/chat",
      "processing": "参数验证 → AgentLoop",
      "output": "SSE 流式响应",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/controller/NvcAssistantController.java"
      ],
      "notes": ""
    },
    {
      "id": "practice-session",
      "label": "练习会话管理",
      "tech": "NvcPracticeSessionService.java",
      "engine": "server",
      "subsystem": "nvcpractice",
      "status": "shipped",
      "role": "会话状态机",
      "input": "开始练习请求",
      "processing": "CREATED→IN_PROGRESS→COMPLETED",
      "output": "SessionEntity",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeSessionService.java"
      ],
      "notes": ""
    },
    {
      "id": "practice-dialogue",
      "label": "练习对话",
      "tech": "NvcPracticeDialogueService.java",
      "engine": "llm",
      "subsystem": "nvcpractice",
      "status": "shipped",
      "role": "练习消息处理",
      "input": "用户消息",
      "processing": "AgentLoop 调用",
      "output": "Agent 回复",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPracticeDialogueService.java"
      ],
      "notes": ""
    },
    {
      "id": "practice-evaluation",
      "label": "练习评估触发",
      "tech": "EvaluationTriggerHook.java",
      "engine": "llm",
      "subsystem": "nvcpractice",
      "status": "shipped",
      "role": "完成时触发评估+Wiki",
      "input": "evaluate_nvc 调用",
      "processing": "触发 OfflineEval + Wiki",
      "output": "评估报告",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/service/agent/EvaluationTriggerHook.java"
      ],
      "notes": ""
    },
    {
      "id": "user-profile",
      "label": "用户能力画像",
      "tech": "NvcProfileService.java",
      "engine": "server",
      "subsystem": "profile-scenario",
      "status": "shipped",
      "role": "NVC 能力发展轨迹",
      "input": "评估结果",
      "processing": "能力聚合 + 趋势",
      "output": "ProfileDTO",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcprofile/service/NvcProfileService.java"
      ],
      "notes": ""
    },
    {
      "id": "scenario-manager",
      "label": "场景库",
      "tech": "NvcScenarioService.java",
      "engine": "server",
      "subsystem": "profile-scenario",
      "status": "shipped",
      "role": "场景 CRUD + 分级",
      "input": "场景请求",
      "processing": "DB CRUD + 标签",
      "output": "ScenarioDTO",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcscenario/service/NvcScenarioService.java"
      ],
      "notes": ""
    },
    {
      "id": "voice-handler",
      "label": "WebSocket 语音",
      "tech": "VoiceWebSocketHandler.java",
      "engine": "server",
      "subsystem": "nvcvoice",
      "status": "shipped",
      "role": "WebSocket 连接管理",
      "input": "音频帧",
      "processing": "连接管理 → Pipeline",
      "output": "音频响应",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcvoice/handler/"
      ],
      "notes": ""
    },
    {
      "id": "voice-pipeline",
      "label": "语音管道",
      "tech": "VoicePipelineCoordinator.java",
      "engine": "server",
      "subsystem": "nvcvoice",
      "status": "shipped",
      "role": "ASR→LLM→TTS 编排",
      "input": "音频帧",
      "processing": "ASR → Agent → TTS",
      "output": "合成语音",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcvoice/pipeline/"
      ],
      "notes": ""
    },
    {
      "id": "voice-asr",
      "label": "语音识别",
      "tech": "DashScopeAsrProvider.java",
      "engine": "llm",
      "engineLabel": "DashScope ASR",
      "subsystem": "nvcvoice",
      "status": "shipped",
      "role": "实时语音转文字",
      "input": "音频流",
      "processing": "DashScope ASR API",
      "output": "文字",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcvoice/service/provider/"
      ],
      "notes": ""
    },
    {
      "id": "voice-tts",
      "label": "语音合成",
      "tech": "DashScopeTtsProvider.java",
      "engine": "llm",
      "engineLabel": "DashScope TTS",
      "subsystem": "nvcvoice",
      "status": "shipped",
      "role": "文字转语音",
      "input": "回复文本",
      "processing": "DashScope TTS API",
      "output": "音频流",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcvoice/service/provider/"
      ],
      "notes": ""
    },
    {
      "id": "wiki-service",
      "label": "NVC 百科",
      "tech": "NvcWikiService.java",
      "engine": "server",
      "subsystem": "nvcwiki",
      "status": "shipped",
      "role": "知识管理+AI生成",
      "input": "查询/生成请求",
      "processing": "DB CRUD + LLM",
      "output": "WikiDTO",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcwiki/service/NvcWikiService.java"
      ],
      "notes": ""
    },
    {
      "id": "wiki-generator",
      "label": "Wiki 异步生成",
      "tech": "WikiGenerateStreamProducer",
      "engine": "queue",
      "subsystem": "nvcwiki",
      "status": "shipped",
      "role": "异步生成知识内容",
      "input": "评估事件",
      "processing": "Redis Stream → LLM",
      "output": "Wiki 内容",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcwiki/listener/"
      ],
      "notes": ""
    },
    {
      "id": "fe-app",
      "label": "React SPA",
      "tech": "App.tsx",
      "engine": "browser",
      "subsystem": "frontend",
      "status": "shipped",
      "role": "路由和页面组织",
      "input": "浏览器访问",
      "processing": "React Router",
      "output": "UI",
      "files": [
        "frontend/src/App.tsx"
      ],
      "notes": ""
    },
    {
      "id": "fe-api-client",
      "label": "API 客户端",
      "tech": "api/*.ts",
      "engine": "browser",
      "subsystem": "frontend",
      "status": "shipped",
      "role": "封装后端调用",
      "input": "页面调用",
      "processing": "axios HTTP",
      "output": "API 数据",
      "files": [
        "frontend/src/api/"
      ],
      "notes": ""
    },
    {
      "id": "fe-practice-ui",
      "label": "练习界面",
      "tech": "PracticePage.tsx",
      "engine": "browser",
      "subsystem": "frontend",
      "status": "shipped",
      "role": "聊天+评估+语音",
      "input": "用户输入",
      "processing": "SSE 流式 + Markdown",
      "output": "对话界面",
      "files": [
        "frontend/src/pages/"
      ],
      "notes": ""
    },
    {
      "id": "fe-kb-ui",
      "label": "知识库界面",
      "tech": "KnowledgeBasePage.tsx",
      "engine": "browser",
      "subsystem": "frontend",
      "status": "shipped",
      "role": "文档管理+RAG",
      "input": "用户操作",
      "processing": "上传/列表/对话",
      "output": "KB 界面",
      "files": [
        "frontend/src/pages/"
      ],
      "notes": ""
    },
    {
      "id": "fe-admin-ui",
      "label": "管理后台",
      "tech": "Admin pages",
      "engine": "browser",
      "subsystem": "frontend",
      "status": "shipped",
      "role": "Provider/Trace/Metrics",
      "input": "管理员操作",
      "processing": "CRUD + 可视化",
      "output": "管理界面",
      "files": [
        "frontend/src/pages/"
      ],
      "notes": ""
    },
    {
      "id": "dialog-fallback-templates",
      "label": "降级对话模板（未接入）",
      "tech": "DialogFallbackTemplates.java",
      "engine": "server",
      "subsystem": "nvcassistant",
      "status": "parked",
      "role": "27个NVC引导模板",
      "input": "降级场景",
      "processing": "模板匹配",
      "output": "预设回复",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/fallback/DialogFallbackTemplates.java"
      ],
      "removable": {
        "category": "dead-functions",
        "tier": "sure",
        "evidence": "@Component 创建但无任何类注入。selectTemplate() 从未被调用。",
        "blastRadius": 0
      }
    },
    {
      "id": "ordered-tts-emitter",
      "label": "有序TTS发射器（未接入）",
      "tech": "OrderedTtsChunkEmitter.java",
      "engine": "server",
      "subsystem": "nvcvoice",
      "status": "parked",
      "role": "保证TTS音频块按序发送",
      "input": "TTS音频块",
      "processing": "序号排序",
      "output": "有序音频帧",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcvoice/pipeline/OrderedTtsChunkEmitter.java"
      ],
      "removable": {
        "category": "dead-functions",
        "tier": "sure",
        "evidence": "普通类，无 Spring 注解，无 new OrderedTtsChunkEmitter() 调用。",
        "blastRadius": 0
      }
    },
    {
      "id": "trace-sampler",
      "label": "Trace采样器（未接入）",
      "tech": "TraceSampler.java",
      "engine": "server",
      "subsystem": "common",
      "status": "parked",
      "role": "按采样率决定是否记录Trace",
      "input": "Trace请求",
      "processing": "shouldSample()判断",
      "output": "采样决策",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcassistant/trace/TraceSampler.java"
      ],
      "removable": {
        "category": "dead-functions",
        "tier": "sure",
        "evidence": "@Component 创建但 TraceManager.startTrace() 不注入或调用 shouldSample()。",
        "blastRadius": 0
      }
    },
    {
      "id": "practice-completed-event",
      "label": "PracticeCompletedEvent（无监听器）",
      "tech": "PracticeCompletedEvent.java",
      "engine": "server",
      "subsystem": "common",
      "status": "parked",
      "role": "练习完成事件已发布但无监听器",
      "input": "练习完成",
      "processing": "Spring Event发布",
      "output": "无（事件被丢弃）",
      "files": [
        "app/src/main/java/nvc/guide/common/event/PracticeCompletedEvent.java"
      ],
      "removable": {
        "category": "dead-functions",
        "tier": "sure",
        "evidence": "NvcPracticeSessionService:286 发布事件，但全代码库无 @EventListener 监听此事件。",
        "blastRadius": 0
      }
    },
    {
      "id": "evaluation-fallback",
      "label": "评估降级服务（未接入）",
      "tech": "EvaluationFallbackService.java",
      "engine": "server",
      "subsystem": "nvcpractice",
      "status": "parked",
      "role": "LLM评估不可用时的关键词降级",
      "input": "评估请求",
      "processing": "关键词匹配评分",
      "output": "降级评估结果",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcpractice/fallback/EvaluationFallbackService.java"
      ],
      "removable": {
        "category": "dead-functions",
        "tier": "sure",
        "evidence": "@Service 创建但 evaluateByKeyWords() 和 markAsDegraded() 从未被调用。",
        "blastRadius": 0
      }
    },
    {
      "id": "semantic-cache",
      "label": "语义缓存（核心方法未使用）",
      "tech": "NvcSemanticCacheService.java",
      "engine": "cache",
      "subsystem": "nvcpractice",
      "status": "parked",
      "role": "lookup/cache未被调用",
      "input": "缓存查询",
      "processing": "语义相似度匹配",
      "output": "缓存结果",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcSemanticCacheService.java"
      ],
      "removable": {
        "category": "dead-functions",
        "tier": "check",
        "evidence": "Controller 暴露 GET /stats 和 DELETE /clear，但 lookup()/cache() 从未被业务代码调用。",
        "blastRadius": 1
      }
    },
    {
      "id": "prompt-version",
      "label": "A/B Prompt版本路由（未接入）",
      "tech": "NvcPromptVersionService.java",
      "engine": "server",
      "subsystem": "nvcpractice",
      "status": "parked",
      "role": "selectVersion()未被调用",
      "input": "版本选择",
      "processing": "流量权重分配",
      "output": "Prompt版本",
      "files": [
        "app/src/main/java/nvc/guide/modules/nvcpractice/service/NvcPromptVersionService.java"
      ],
      "removable": {
        "category": "dead-functions",
        "tier": "check",
        "evidence": "Controller 暴露 CRUD 端点，但 selectVersion() 从未被 PromptBuilder 或任何业务代码调用。",
        "blastRadius": 1
      }
    },
    {
      "id": "prop-cache-hot-queries",
      "label": "添加 Redis 缓存层（热查询）",
      "tech": "@Cacheable + RedisCacheManager",
      "engine": "cache",
      "subsystem": "common",
      "status": "proposed",
      "proposed": true,
      "proposal": {
        "problem": "NvcScenarioService、NvcProfileService、KnowledgeBaseQueryService 等高频查询直接走 DB，无任何缓存。全代码库零 @Cacheable 注解。",
        "change": "引入 Spring Cache 抽象 + RedisCacheManager。为场景列表、用户画像、知识库统计等热查询添加 @Cacheable，TTL 5-15 分钟。评估写操作加 @CacheEvict。",
        "tradeoff": "增加缓存一致性复杂度；需要处理缓存穿透/雪崩。但 Redis 已在基础设施中，成本低。",
        "effort": "S",
        "impact": "high",
        "lens": "pipeline"
      }
    },
    {
      "id": "prop-fix-tx-vectorize",
      "label": "修复向量化事务边界",
      "tech": "KnowledgeBaseVectorService.java",
      "engine": "server",
      "subsystem": "knowledgebase",
      "status": "proposed",
      "proposed": true,
      "proposal": {
        "problem": "vectorizeAndStore() 在 @Transactional 内调用 DashScope Embedding API。如果 DashScope 超时或失败，DB 已删除旧向量但新向量未写入，数据不一致。",
        "change": "拆分为两步：1) @Transactional 内只做 DB 操作（删除旧向量）；2) 事务外调 DashScope Embedding + 写入新向量。失败时保留旧向量或标记为 REBUILDING。",
        "tradeoff": "需要处理中间状态（旧向量已删、新向量未就绪）。但比当前的「静默数据丢失」更安全。",
        "effort": "M",
        "impact": "high",
        "lens": "pipeline"
      }
    },
    {
      "id": "prop-llm-failover",
      "label": "LLM Provider 故障转移",
      "tech": "LlmProviderRegistry.java + LlmFallbackHandler.java",
      "engine": "llm",
      "subsystem": "common",
      "status": "proposed",
      "proposed": true,
      "proposal": {
        "problem": "LlmProviderRegistry 支持多 Provider 但无自动故障转移。LlmFallbackHandler 重试同一 Provider 3 次后降级为硬编码文本，不尝试备用 Provider。",
        "change": "在 LlmFallbackHandler 中添加 Provider 级故障转移：主 Provider 失败 → 尝试次 Provider → 再失败才降级。利用 DB 中已有的多 Provider 配置。",
        "tradeoff": "备用 Provider 可能能力不同（如模型差异），需要统一 prompt 兼容性。增加调用延迟。",
        "effort": "M",
        "impact": "high",
        "lens": "pipeline"
      }
    },
    {
      "id": "prop-split-agent-loop",
      "label": "拆分 AgentLoop 职责",
      "tech": "AgentLoop.java (8 个依赖)",
      "engine": "server",
      "subsystem": "nvcassistant",
      "status": "proposed",
      "proposed": true,
      "proposal": {
        "problem": "AgentLoop 直接注入 8 个依赖（LlmProviderRegistry、ToolRegistry、ToolExecutor、IntentRouter、ConfigRepository、MetricsCollector、TraceManager、FallbackHandler），承担了路由、调用、工具执行、指标、追踪、降级的全部职责。",
        "change": "提取 LlmCallService（封装 LLM 调用 + 重试 + 降级）和 AgentMetricsRecorder（封装指标 + 追踪），AgentLoop 只保留核心循环逻辑。依赖数从 8 降到 4-5。",
        "tradeoff": "增加类数量，需要明确职责边界。但降低单类复杂度，便于独立测试。",
        "effort": "L",
        "impact": "med",
        "lens": "structure"
      }
    },
    {
      "id": "prop-trace-sampling",
      "label": "启用 Trace 采样配置",
      "tech": "TraceSampler.java（已实现但未接入）",
      "engine": "server",
      "subsystem": "common",
      "status": "proposed",
      "proposed": true,
      "proposal": {
        "problem": "TraceSampler 已实现 shouldSample() 但 TraceManager.startTrace() 未调用它。生产环境全量 Trace 写入 DB，随数据增长性能下降。",
        "change": "在 TraceManager.startTrace() 中接入 TraceSampler.shouldSample()，通过 application.yml 配置采样率（如 10%）。调试用户可设为 100%。",
        "tradeoff": "低采样率可能遗漏有价值的 Trace 数据。但比全量写入的性能问题更可接受。",
        "effort": "S",
        "impact": "med",
        "lens": "pipeline"
      }
    },
    {
      "id": "prop-event-listener",
      "label": "实现 PracticeCompletedEvent 监听器",
      "tech": "PracticeCompletedEvent.java（已发布无监听器）",
      "engine": "server",
      "subsystem": "common",
      "status": "proposed",
      "proposed": true,
      "proposal": {
        "problem": "PracticeCompletedEvent 在练习完成时发布，但无 @EventListener 监听。用户画像更新、统计聚合等功能缺失。",
        "change": "创建 PracticeCompletedEventListener，监听事件后触发：1) 用户画像更新 2) 练习统计聚合 3) 成就/徽章检查。",
        "tradeoff": "异步监听可能丢失事件（Redis Stream 重启）。需要确认是否需要持久化事件。",
        "effort": "M",
        "impact": "med",
        "lens": "pipeline"
      }
    }
  ],
  "edges": [
    {
      "from": "fe-app",
      "to": "fe-api-client",
      "data": "路由分发",
      "notes": ""
    },
    {
      "from": "fe-api-client",
      "to": "nvc-assistant-controller",
      "data": "HTTP/SSE",
      "notes": ""
    },
    {
      "from": "fe-practice-ui",
      "to": "fe-api-client",
      "data": "练习消息",
      "notes": ""
    },
    {
      "from": "fe-kb-ui",
      "to": "fe-api-client",
      "data": "知识库操作",
      "notes": ""
    },
    {
      "from": "fe-admin-ui",
      "to": "fe-api-client",
      "data": "管理操作",
      "notes": ""
    },
    {
      "from": "nvc-assistant-controller",
      "to": "agent-loop",
      "data": "启动 Agent",
      "notes": ""
    },
    {
      "from": "agent-loop",
      "to": "intent-router",
      "data": "意图识别",
      "notes": ""
    },
    {
      "from": "agent-loop",
      "to": "prompt-builder",
      "data": "构建 Prompt",
      "notes": ""
    },
    {
      "from": "agent-loop",
      "to": "dashscope",
      "data": "LLM 调用",
      "notes": ""
    },
    {
      "from": "agent-loop",
      "to": "tool-executor",
      "data": "工具调用",
      "notes": ""
    },
    {
      "from": "tool-executor",
      "to": "hook-chain",
      "data": "Hook 链",
      "notes": ""
    },
    {
      "from": "agent-loop",
      "to": "context-manager",
      "data": "上下文管理",
      "notes": ""
    },
    {
      "from": "context-manager",
      "to": "dashscope",
      "data": "摘要压缩",
      "notes": ""
    },
    {
      "from": "agent-loop",
      "to": "llm-fallback",
      "data": "异常降级",
      "notes": ""
    },
    {
      "from": "metrics-collector",
      "to": "async-stream",
      "data": "指标发布",
      "notes": ""
    },
    {
      "from": "offline-evaluation",
      "to": "dashscope",
      "data": "评估调用",
      "notes": ""
    },
    {
      "from": "practice-session",
      "to": "practice-dialogue",
      "data": "会话上下文",
      "notes": ""
    },
    {
      "from": "practice-dialogue",
      "to": "agent-loop",
      "data": "Agent 调用",
      "notes": ""
    },
    {
      "from": "practice-evaluation",
      "to": "offline-evaluation",
      "data": "触发评估",
      "notes": ""
    },
    {
      "from": "practice-evaluation",
      "to": "wiki-generator",
      "data": "触发 Wiki",
      "notes": ""
    },
    {
      "from": "kb-upload",
      "to": "minio",
      "data": "文件存储",
      "notes": ""
    },
    {
      "from": "kb-upload",
      "to": "kb-vectorize-pipeline",
      "data": "触发向量化",
      "notes": ""
    },
    {
      "from": "kb-vectorize-pipeline",
      "to": "kb-parse",
      "data": "文档解析",
      "notes": ""
    },
    {
      "from": "kb-parse",
      "to": "kb-vector",
      "data": "文本切片",
      "notes": ""
    },
    {
      "from": "kb-vector",
      "to": "dashscope",
      "data": "Embedding",
      "notes": ""
    },
    {
      "from": "kb-vector",
      "to": "postgres",
      "data": "向量存储",
      "notes": ""
    },
    {
      "from": "kb-query",
      "to": "postgres",
      "data": "向量检索",
      "notes": ""
    },
    {
      "from": "kb-query",
      "to": "dashscope",
      "data": "RAG 生成",
      "notes": ""
    },
    {
      "from": "rag-chat",
      "to": "kb-query",
      "data": "RAG 查询",
      "notes": ""
    },
    {
      "from": "kb-seed",
      "to": "kb-upload",
      "data": "预置文档",
      "notes": ""
    },
    {
      "from": "llm-provider-crud",
      "to": "postgres",
      "data": "Provider 存储",
      "notes": ""
    },
    {
      "from": "llm-bootstrap",
      "to": "llm-provider-crud",
      "data": "初始化",
      "notes": ""
    },
    {
      "from": "llm-registry",
      "to": "llm-provider-crud",
      "data": "读取配置",
      "notes": ""
    },
    {
      "from": "llm-registry",
      "to": "dashscope",
      "data": "API 调用",
      "notes": ""
    },
    {
      "from": "voice-handler",
      "to": "voice-pipeline",
      "data": "音频帧",
      "notes": ""
    },
    {
      "from": "voice-pipeline",
      "to": "voice-asr",
      "data": "ASR",
      "notes": ""
    },
    {
      "from": "voice-pipeline",
      "to": "agent-loop",
      "data": "对话",
      "notes": ""
    },
    {
      "from": "voice-pipeline",
      "to": "voice-tts",
      "data": "TTS",
      "notes": ""
    },
    {
      "from": "voice-asr",
      "to": "dashscope",
      "data": "ASR API",
      "notes": ""
    },
    {
      "from": "voice-tts",
      "to": "dashscope",
      "data": "TTS API",
      "notes": ""
    },
    {
      "from": "trace-system",
      "to": "postgres",
      "data": "Trace 持久化",
      "notes": ""
    },
    {
      "from": "async-stream",
      "to": "redis",
      "data": "Stream 消息",
      "notes": ""
    },
    {
      "from": "rate-limiter",
      "to": "redis",
      "data": "限流计数",
      "notes": ""
    },
    {
      "from": "redis-cache",
      "to": "redis",
      "data": "缓存读写",
      "notes": ""
    },
    {
      "from": "prompt-security",
      "to": "agent-loop",
      "data": "输入校验",
      "notes": ""
    },
    {
      "from": "user-profile",
      "to": "postgres",
      "data": "档案存储",
      "notes": ""
    },
    {
      "from": "scenario-manager",
      "to": "postgres",
      "data": "场景存储",
      "notes": ""
    },
    {
      "from": "wiki-service",
      "to": "postgres",
      "data": "Wiki 存储",
      "notes": ""
    },
    {
      "from": "wiki-generator",
      "to": "async-stream",
      "data": "异步生成",
      "notes": ""
    },
    {
      "from": "fe-practice-ui",
      "to": "voice-handler",
      "data": "WebSocket",
      "notes": ""
    },
    {
      "from": "prop-cache-hot-queries",
      "to": "redis",
      "data": "缓存层",
      "status": "proposed",
      "proposed": true
    },
    {
      "from": "prop-cache-hot-queries",
      "to": "scenario-manager",
      "data": "场景缓存",
      "status": "proposed",
      "proposed": true
    },
    {
      "from": "prop-cache-hot-queries",
      "to": "user-profile",
      "data": "画像缓存",
      "status": "proposed",
      "proposed": true
    },
    {
      "from": "prop-fix-tx-vectorize",
      "to": "kb-vector",
      "data": "重构事务边界",
      "status": "proposed",
      "proposed": true
    },
    {
      "from": "prop-llm-failover",
      "to": "llm-registry",
      "data": "故障转移逻辑",
      "status": "proposed",
      "proposed": true
    },
    {
      "from": "prop-llm-failover",
      "to": "llm-fallback",
      "data": "增强降级",
      "status": "proposed",
      "proposed": true
    },
    {
      "from": "prop-split-agent-loop",
      "to": "agent-loop",
      "data": "职责拆分",
      "status": "proposed",
      "proposed": true
    },
    {
      "from": "prop-trace-sampling",
      "to": "trace-system",
      "data": "接入采样器",
      "status": "proposed",
      "proposed": true
    },
    {
      "from": "prop-event-listener",
      "to": "practice-completed-event",
      "data": "监听事件",
      "status": "proposed",
      "proposed": true
    },
    {
      "from": "prop-event-listener",
      "to": "user-profile",
      "data": "触发画像更新",
      "status": "proposed",
      "proposed": true
    }
  ]
};
