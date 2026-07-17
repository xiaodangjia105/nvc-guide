# 服务地图

> 系统的服务组成、依赖关系和通信方式。

## 服务清单

| 服务 | 技术栈 | 职责 |
|------|--------|------|
| nvc-backend | Spring Boot 4.0 / Java 21 / Spring AI 2.0 | 全部业务逻辑、AI Agent、数据持久化 |
| nvc-frontend | React 18 / TypeScript / Vite / TailwindCSS 4 | NVC 练习交互界面 |

## 外部依赖

| 依赖 | 用途 | 通信方式 |
|------|------|----------|
| PostgreSQL + pgvector | 业务数据 + 向量搜索 | JPA / JDBC |
| Redis | 缓存、限流、异步评估 Stream | Redisson |
| LLM Provider（通义千问等） | AI 对话、评估、场景生成 | HTTP API |
| ASR/TTS 服务 | 语音识别/合成 | HTTP API / WebSocket |
| S3 / RustFS | 文件存储（知识库文档） | HTTP API |

## 数据流

```
用户 → nvc-frontend → HTTP/SSE → nvc-backend
                                      ├── PostgreSQL（持久化）
                                      ├── Redis（缓存 + Stream）
                                      ├── LLM Provider（AI 推理）
                                      └── S3（文件存储）
```

## 部署形状
- 单体仓库，Docker Compose 部署
- 后端 + 前端 + PostgreSQL + Redis 四容器
