# /api/v1/tasks 提交任务同步/异步行为分析

## 结论
- 目前实现是**同步执行**：`POST /api/v1/tasks` 会在同一调用链中完成任务运行（`AgentRuntime.run` 执行完成后才返回 HTTP 响应）。
- 但对外接口语义是**异步返回**：响应体只包含 `taskId/workflowId/status`，不包含最终结果，实际结果需要通过查询接口或事件流获取。

## 关键调用链与同步证据
1) 入口控制器直接调用服务并同步返回：
- 文件：`src/main/java/com/example/agent/gateway/controller/TaskController.java`
- 方法：`submitTask` 直接调用 `taskSubmissionService.submitTask(...)`，并立即返回 `ApiResponse<TaskResponse>`，未使用 `Mono/Flux`、`CompletableFuture` 或 `@Async`。

2) 提交服务内部同步执行运行时流程：
- 文件：`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
- 方法：`submitTask` 创建任务记录后**直接调用** `handleWorkflowRoute(...)`。
- 方法：`handleWorkflowRoute` 内部调用 `workflowRouter.route(...)` 并在 try/catch/finally 中更新状态与写入结果。

3) 路由层与运行时均为同步调用：
- 文件：`src/main/java/com/example/agent/orchestrator/WorkflowRouter.java`
- 方法：`route` 直接调用 `agentRuntime.run(...)` 并返回 `RuntimeResult`。
- 文件：`src/main/java/com/example/agent/runtime/AgentRuntime.java`
- 方法：`run` 为串行执行（规划 → 步骤执行 → 最终输出），无异步拆分或后台执行。

4) 事件发布默认同步：
- `TaskOrchestrator` 使用 `ApplicationEventPublisher` 发布 `StreamEvent`，项目中未配置异步事件分发器或 `@Async` 监听器，因此事件发布默认仍在当前线程执行。

## 为什么没有直接返回结果（即使执行是同步的）
1) 响应模型不包含结果字段：
- `TaskResponse` 仅包含 `taskId/workflowId/status`，未设计 `result` 字段。
- 文件：`src/main/java/com/example/agent/common/TaskResponse.java`

2) 返回的 `status` 是提交时的初始状态：
- `TaskOrchestrator.createTask` 将状态设置为 `SUBMITTED`。
- `submitTask` 返回的 `TaskResponse` 是创建时的状态值，即使随后任务已被同步执行完成，响应也不会被更新为最终状态。

3) 结果被持久化并通过查询接口获取：
- `handleWorkflowRoute` 完成后调用 `updateTaskStatus(..., "COMPLETED", buildResultPayload(...))` 将结果写入 `TaskRecord`。
- 查询结果使用 `GET /api/v1/tasks/{taskId}`，返回 `TaskStatusResponse`（包含 `result` 字段）。
- 文件：`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
- 文件：`src/main/java/com/example/agent/common/TaskStatusResponse.java`

4) 事件/时间线接口承接“异步”获取过程：
- 任务过程中会发布 `WORKFLOW_STARTED`、`WORKFLOW_COMPLETED`、`ERROR_OCCURRED` 等事件。
- 客户端可通过以下接口获取进展与结果：
  - `GET /api/v1/events`：事件日志分页（`TimelineController.listEvents`）。
  - `GET /api/v1/stream/sse`：SSE 订阅事件流（`SseStreamController.stream`）。
  - `GET /api/v1/timeline` 与 `GET /api/v1/timeline/steps`：时间线与步骤详情。

## 如果按“异步语义”理解，相关步骤体现在哪里
- **任务创建与持久化**：`createTask` 写入 `TaskRepository`，状态为 `SUBMITTED`。
- **状态流转与结果落盘**：`handleWorkflowRoute` 更新为 `RUNNING`/`COMPLETED`/`FAILED`，并写入结果映射。
- **事件驱动的进度获取**：`TaskOrchestrator` 发布 `StreamEvent`，`EventStreamService` 监听并将事件进入流（SSE）或写入 Redis 流供追溯。
- **查询式结果获取**：客户端在 `POST /api/v1/tasks` 后使用 `GET /api/v1/tasks/{taskId}` 或 `GET /api/v1/events` 拉取结果/状态。

## 现状带来的影响与注意点
- 实际执行为同步，HTTP 请求会阻塞到流程结束，存在超时风险。
- 响应体不包含结果，导致“同步执行但异步返回”的语义不一致。
- 如需真正异步处理，应将 `handleWorkflowRoute` 放入独立执行器或消息队列，确保 `POST /api/v1/tasks` 快速返回，同时由查询/事件接口提供最终结果。