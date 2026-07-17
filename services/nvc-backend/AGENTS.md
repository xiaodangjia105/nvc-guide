# nvc-backend — 服务事实层入口

> NVC 非暴力沟通练习助手的后端服务（Spring Boot）。提供 REST API，承载全部业务规则、AI Agent 调度与评估。前端为 `nvc-frontend`，经 HTTP/SSE 调用本服务。本文件是该服务的导航根。

## 职责
负责 NVC 练习的全生命周期：会话管理、Agent 调度与对话、四要素评估、用户能力画像、场景库管理、知识库 RAG、语音练习。对前端只暴露 HTTP 接口。不负责前端呈现（见 `services/nvc-frontend/`）。

## 内部约定（仅非标准、Agent 读代码发现不了的）

- **分层**：Controller → Service → Repository，Controller 仅路由和委派，禁止业务逻辑。
- **Agent 配置热更新**：Agent 的 system prompt、温度等配置存 DB，通过 `PUT /api/nvc/agents/configs/{scene}` 更新后清 Redis 缓存，下次调用自动生效。
- **评估走异步**：用户发消息后先返回 Agent 回复，评估通过 Redis Stream 异步执行，结果写回 DB。
- **异常**：业务错误抛 `BusinessException(ErrorCode.XXX, message)`，全局异常处理器统一返回 HTTP 200 + `Result.error(code, message)`。禁止 `throw new RuntimeException`。
- **事务**：在 Service 层控制；事务内禁止调用 LLM 等外部 API。
- **配置管理**：敏感信息放 `.env`，业务配置用 `@ConfigurationProperties`，禁止 `@Value` 散落在 Service 中。
- **限流**：`@RateLimit` 注解（可重复），AOP 切面 + Redis Lua 滑动窗口。
- **实体后缀**：`XxxEntity`（JPA）、`XxxDTO`（传输）、`XxxRequest`（请求）、`XxxResponse`（响应）。禁止直接返回 Entity 给前端。

## 对外契约
- API 响应/错误码：`docs/contracts/api.md`

## 协作方
- **nvc-frontend**：唯一的 Web 调用方，经 `/api/nvc/*` 消费本服务。

## 子文档
- `BUILD.md` — 构建与运行命令
