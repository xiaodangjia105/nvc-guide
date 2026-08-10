# Quick Wins 批次 1 设计文档

**日期：** 2026-08-10
**范围：** 4 个代码级快速修复
**预计耗时：** ~30 分钟
**分支：** `fix/quick-wins-2026-08-10`

---

## 背景

架构审计报告（2026-08-10）识别出 5 个 Quick Win 问题。本文档覆盖第一批 4 个代码级修复，CI/CD 流水线作为第二批单独处理。

**参考报告：**
- `docs/architecture-review-2026-08-10/arch-review-nvc-guide-2026-08-10.md`
- `docs/architecture-review-2026-08-10/smell-report-2026-08-10-1600.md`

---

## 修复 1：删除前端未使用依赖

**问题：** 报告声称 4 个包无 import，但经验证 `lucide-react` 实际被 34 个文件使用。只删除 3 个确认未使用的包。

**实际删除：**
- `react-window` — 虚拟滚动，0 imports
- `react-big-calendar` — 日历组件，0 imports
- `onnxruntime-web` — ONNX 推理，0 imports

**保留：**
- `lucide-react` — 图标库，34 个文件使用（报告有误）

**操作：** 手动编辑 `frontend/package.json` 移除 3 个依赖及其 `@types/*` 类型定义

**验证：** `grep -r "react-window\|react-big-calendar\|onnxruntime-web" frontend/src/` 无结果

**风险：** 极低

---

## 修复 2：统一 DB 名称

**问题：** `docker-compose.yml` 使用 `nvc_practice`，`.env.example` 使用 `nvc_guide`。新开发者复制 `.env.example` 会连接失败。

**方案：** 统一为 `nvc_practice`（与 docker-compose 一致，已是实际运行配置）

**改动文件：** `.env.example` 第 35-38 行
```properties
# 改前
POSTGRES_DB=nvc_guide
# 改后
POSTGRES_DB=nvc_practice
```

**验证：** 对比 docker-compose.yml 和 .env.example 的 DB 名称一致

**风险：** 极低 — 仅影响新开发者初始化

---

## 修复 3：修复 log.error 丢失堆栈

**问题：** `log.error(e.getMessage())` 只传消息不传异常对象，丢失完整堆栈信息，生产环境排查困难。

**改动文件（8 处，超出原计划 3 处）：**

1. **`NvcVoiceWebSocketHandler.java:119`** — Transport error
2. **`NvcVoiceWebSocketHandler.java:195`** — Welcome message
3. **`VoicePipelineCoordinator.java:102`** — ASR error
4. **`VoicePipelineCoordinator.java:332`** — WebSocket message
5. **`FileStorageService.java:133`** — S3 检查文件存在性
6. **`FileStorageService.java:149`** — 获取文件大小
7. **`VectorRepository.java:60`** — 删除向量 SQL
8. **`NvcReflectionService.java:150`** — 解析 reflection JSON

**注意：** `NvcAssistantController.java:123` 已经是正确格式（`log.error("...", convId, e)`），无需修改。

**验证：** 编译通过

**风险：** 极低 — 只改变日志格式

---

## 修复 4：添加 Actuator 健康检查端点

**问题：** 无 `/health` 端点，无法监控服务状态

**改动：**

1. **`app/build.gradle`** — 添加依赖
   ```gradle
   implementation 'org.springframework.boot:spring-boot-starter-actuator'
   ```

2. **`application.yml`** — 添加配置
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: health,info
   ```

**端点：**
- `GET /actuator/health` — 返回 `{"status":"UP"}`
- `GET /actuator/info` — 返回应用信息

**验证：** 后端编译成功（`./gradlew :app:compileJava`）

**风险：** 极低 — Actuator 是 Spring Boot 标准组件

---

## 实施顺序

1. 创建分支 `fix/quick-wins-2026-08-10`
2. 修复 1：删除未使用依赖（3 个，非 4 个）
3. 修复 2：统一 DB 名称
4. 修复 3：修复 log.error（8 处，非 3 处）
5. 修复 4：添加 Actuator
6. 验证：编译通过
7. 提交 + 创建 PR

---

## 不在范围内

- CI/CD 流水线（第二批）
- 测试覆盖率提升（后续 Sprint）
- God File 拆分（后续 Sprint）
- @Transactional 边界修复（后续 Sprint）
