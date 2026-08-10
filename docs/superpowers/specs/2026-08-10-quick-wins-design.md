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

**问题：** 4 个包无任何 import，增加 ~3.3MB node_modules

**目标包：**
- `lucide-react` — 图标库，0 imports
- `react-window` — 虚拟滚动，0 imports
- `react-big-calendar` — 日历组件，0 imports
- `onnxruntime-web` — ONNX 推理，0 imports

**操作：**
```bash
cd frontend
pnpm remove lucide-react react-window react-big-calendar onnxruntime-web
```

**验证：**
- `pnpm build` 成功
- `grep -r "lucide-react\|react-window\|react-big-calendar\|onnxruntime-web" frontend/src/` 无结果

**风险：** 极低 — 这些包确实没有被使用

---

## 修复 2：统一 DB 名称

**问题：** `docker-compose.yml` 使用 `nvc_practice`，`.env.example` 使用 `nvc_guide`。新开发者复制 `.env.example` 会连接失败。

**方案：** 统一为 `nvc_practice`（与 docker-compose 一致，已是实际运行配置）

**改动文件：** `.env.example` 第 38 行
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

**改动文件（3 处）：**

1. **`NvcAssistantController.java:126`**
   ```java
   // 改前
   log.error("SSE error: " + e.getMessage());
   // 改后
   log.error("SSE error", e);
   ```

2. **`NvcVoiceWebSocketHandler.java:195`**
   ```java
   // 改前
   log.error("WebSocket error: " + e.getMessage());
   // 改后
   log.error("WebSocket error", e);
   ```

3. **`VoicePipelineCoordinator.java:332`**
   ```java
   // 改前
   log.error("Pipeline error: " + e.getMessage());
   // 改后
   log.error("Pipeline error", e);
   ```

**验证：** 编译通过，手动触发异常场景确认堆栈完整输出

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

**验证：** 启动后 `curl http://localhost:8080/actuator/health` 返回 200

**风险：** 极低 — Actuator 是 Spring Boot 标准组件

---

## 实施顺序

1. 创建分支 `fix/quick-wins-2026-08-10`
2. 修复 1：删除未使用依赖
3. 修复 2：统一 DB 名称
4. 修复 3：修复 log.error
5. 修复 4：添加 Actuator
6. 验证：编译 + 启动 + 端点测试
7. 提交 + 创建 PR

---

## 不在范围内

- CI/CD 流水线（第二批）
- 测试覆盖率提升（后续 Sprint）
- God File 拆分（后续 Sprint）
- @Transactional 边界修复（后续 Sprint）
