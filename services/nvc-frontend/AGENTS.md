# nvc-frontend — 服务事实层入口

> NVC 非暴力沟通练习助手的前端服务（React SPA）。提供用户交互界面，经 HTTP/SSE 调用后端 API。本文件是该服务的导航根。

## 职责
负责 NVC 练习的用户界面：三种练习模式的交互、用户档案展示、数据可视化仪表盘、场景库浏览、知识库管理。不负责业务逻辑和 AI 推理（见 `services/nvc-backend/`）。

## 内部约定（仅非标准、Agent 读代码发现不了的）

- **状态管理**：轻量级，React Context + Hooks，无 Redux。
- **API 调用**：集中式 API 客户端，统一封装请求/响应处理和错误拦截。
- **流式对话**：通过 SSE（EventSource）接收后端流式 AI 回复，逐字渲染。
- **路由**：React Router，按功能模块组织路由。
- **样式**：TailwindCSS 4，原子化 CSS。
- **图表**：雷达图（能力画像）+ 趋势图（练习进步），使用 recharts。

## 对外契约
- API 调用遵循：`docs/contracts/api.md`

## 协作方
- **nvc-backend**：唯一的后端依赖，经 `/api/nvc/*` 消费接口。

## 子文档
- `BUILD.md` — 构建与运行命令
