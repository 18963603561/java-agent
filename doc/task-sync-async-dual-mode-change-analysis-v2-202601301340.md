# 同时支持同步与异步执行的改动分析（v2）

- 生成时间：2026-01-30 13:40
- 约束：仅分析与产出文档，不修改代码、不运行测试
- 证据范围：
  - 本项目：`src/main/java`、`src/test/java`（全量检索）
  - `Shannon`：`vendor/Shannon`（只读）
  - 规格权威：`specs/001-agent-core-spec/spec.md`、`specs/001-agent-core-spec/contracts/openapi.yaml`、`specs/001-agent-core-spec/quickstart.md`、`specs/001-agent-core-spec/checklists/checklist.md`

## 0. 检索记录（全量）
- `rg -n "TaskController|TaskOrchestrator|WorkflowRouter|TaskSubmissionService|TaskRequest|TaskResponse|TaskStatusResponse|TaskRepository|TaskRecord|EventStream|SseStream|EventLog|Timeline|EventType|GlobalExceptionHandler|ErrorCode" src/main/java src/test/java`
- `rg -n "sync|async|executionMode|waitForResult|timeout|queue|executor|thread|Mono|Flux|CompletableFuture|@Async" src/main/java src/test/java`
- `rg -n "^package com\\.example\\.agent\\.(gateway|orchestrator|streaming|history|common|runtime|auth)" src/main/java src/test/java`
- `rg -n "TaskExecutionService|TaskExecutionWorker|TaskQueueRepository|ExecutionMode|waitForResult|executionMode" src/main/java src/test/java`（无匹配）

## 1. 本项目现状结论（与同步/异步执行相关）
1) 同步执行链路已确认：`TaskController.submitTask` -> `TaskOrchestrator.submitTask` -> `handleWorkflowRoute` -> `WorkflowRouter.route` -> `AgentRuntime.run` 在同一调用链完成执行。
2) 返回语义偏异步：`TaskResponse` 仅返回 `taskId/workflowId/status`，最终结果通过 `TaskStatusResponse` 与事件/时间线获取。
3) 实时查询：`SseStreamController.stream` + `EventStreamService.stream` 支持 `last_event_id`、类型过滤与 `Redis` 回放；游标异常抛出 `STREAM_GAP`。
4) 离线查询：`EventLogService` 持久化非 `LLM_PARTIAL` 事件；`TimelineService` 基于事件日志生成 `summary/full`。
5) 执行模式字段与异步队列/Worker 未发现（`Confirmed Missing`）。

## 2. `Shannon` 实时与离线查询实现分析
### 2.1 实时查询
- 多协议：`SSE`/`WebSocket`/`gRPC`（见 `docs/streaming-api.md`）。
- `SSE` 处理器：`go/orchestrator/internal/httpapi/streaming.go#handleSSE`，支持 `Last-Event-ID`、`types` 过滤、基于 `Redis` 流 ID 或 `seq` 续传。
- 事件流管理：`go/orchestrator/internal/streaming/manager.go` 使用 `Redis Streams` 环形缓冲与回放（`ReplaySince`/`ReplayFromStreamID`）。
- 事件发布：`go/orchestrator/internal/activities/stream_events.go#EmitTaskUpdate` 统一发布至流管理器。

### 2.2 离线查询
- 持久化事件：`go/orchestrator/internal/streaming/manager.go#shouldPersistEvent` 过滤持久化类型，
  `go/orchestrator/internal/db/event_log.go#SaveEventLog` 写入 `event_logs`。
- 时间线：`go/orchestrator/internal/httpapi/timeline.go#handleBuildTimeline` 基于 `Temporal` 历史构建，
  `persist=true` 异步落盘并返回 `202`。
- 网关查询：`go/orchestrator/cmd/gateway/internal/handlers/task.go#GetTaskEvents` 提供持久化事件查询；
  `docs/task-history-and-timeline.md` 明确 `SSE`、持久化事件与时间线分工。

### 2.3 与本项目差异（聚焦实时/离线）
- 协议：本项目仅 `SSE`，`Shannon` 同时支持 `SSE`/`WebSocket`/`gRPC`。
- 续传基准：本项目以 `workflowId:seq` 作为 `eventId`；`Shannon` 以 `Redis stream_id` 或 `seq` 续传。
- 时间线来源：本项目基于事件日志；`Shannon` 基于 `Temporal` 决定性历史并可异步落盘。
- 首事件验证：本项目首事件超时输出 `ERROR_OCCURRED/STREAM_TIMEOUT`；`Shannon` 使用 `Temporal` 校验工作流存在性并输出错误事件。

## 3. 关联模块矩阵（证据补齐）
| 模块 | 本项目证据 | `Shannon` 证据 | 规范证据 | 备注 |
| --- | --- | --- | --- | --- |
| 请求/响应模型 | `src/main/java/com/example/agent/common/TaskRequest.java`、`TaskResponse.java`、`TaskStatusResponse.java` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go#TaskRequest`、`#TaskResponse`、`#TaskStatusResponse` | `specs/001-agent-core-spec/contracts/openapi.yaml#/components/schemas/TaskRequest`、`TaskResponse`、`TaskStatusResponse` | 缺少执行模式/同步等待超时字段 |
| 任务提交控制器 | `src/main/java/com/example/agent/gateway/controller/TaskController.java#submitTask` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go#SubmitTask`、`#SubmitTaskAndGetStreamURL` | `specs/001-agent-core-spec/contracts/openapi.yaml#/paths//api/v1/tasks` | 提交后同步执行，未区分模式 |
| 编排与执行路由 | `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java#handleWorkflowRoute`、`WorkflowRouter.java#route`、`AgentRuntime.java#run` | `vendor/Shannon/go/orchestrator/internal/server/service.go#SubmitTask`（`ExecuteWorkflow`） | `specs/001-agent-core-spec/spec.md#依赖与假设` | 同步执行与 `Temporal` 异步模型不一致 |
| 实时事件流 | `src/main/java/com/example/agent/streaming/SseStreamController.java#stream`、`EventStreamService.java#stream` | `vendor/Shannon/go/orchestrator/internal/httpapi/streaming.go#handleSSE`、`internal/streaming/manager.go` | `specs/001-agent-core-spec/spec.md#流式 SSE 接口定义`、`specs/001-agent-core-spec/checklists/checklist.md#CHK007` | 支持过滤与断线续传 |
| 离线事件日志 | `src/main/java/com/example/agent/history/EventLogService.java#onStreamEvent`、`EventLogService.java#listEvents`、`JdbcEventLogRepository.java` | `vendor/Shannon/go/orchestrator/internal/streaming/manager.go#shouldPersistEvent`、`internal/db/event_log.go#SaveEventLog`、`cmd/gateway/internal/handlers/task.go#GetTaskEvents` | `specs/001-agent-core-spec/spec.md#任务历史与时间线模型`、`specs/001-agent-core-spec/contracts/openapi.yaml#/paths//api/v1/events` | 本项目排除 `LLM_PARTIAL` 持久化 |
| 时间线 | `src/main/java/com/example/agent/history/TimelineService.java#getTimeline`、`TimelineController.java#getTimeline` | `vendor/Shannon/go/orchestrator/internal/httpapi/timeline.go#handleBuildTimeline` | `specs/001-agent-core-spec/spec.md#任务历史与时间线模型`、`specs/001-agent-core-spec/contracts/openapi.yaml#/paths//api/v1/timeline` | 缺少 `persist=true` 语义 |
| 异步执行与队列 | `Confirmed Missing`（rg 未找到 `TaskExecutionService`/`TaskExecutionWorker`/`TaskQueueRepository`/`executionMode`/`waitForResult`） | `vendor/Shannon/go/orchestrator/internal/server/service.go#SubmitTask`（`TaskQueue` + `ExecuteWorkflow`） | `specs/001-agent-core-spec/spec.md#User Story 1 - 任务执行与事件流订阅`、`specs/001-agent-core-spec/spec.md#FR-001` | 需新增执行器与队列或调度层 |
| 配置与可用性 | `src/main/resources/application.yml`（`agent.sse.timeoutSeconds`、`agent.sse.max-stream-size`） | `vendor/Shannon/docs/streaming-api.md` | `specs/001-agent-core-spec/quickstart.md`、`specs/001-agent-core-spec/checklists/checklist.md#CHK029` | 与实时流超时/容量相关 |
| 异常与错误码 | `src/main/java/com/example/agent/gateway/controller/GlobalExceptionHandler.java`、`EventStreamService.java#validateCursor`、`SseStreamController.java#buildTimeoutEvent` | `vendor/Shannon/go/orchestrator/internal/httpapi/streaming.go#handleSSE` | `specs/001-agent-core-spec/spec.md#错误码与异常策略`、`specs/001-agent-core-spec/checklists/checklist.md#CHK015` | 同步/队列错误码尚未定义 |
| 测试覆盖 | `src/test/java/com/example/agent/streaming/EventStreamServiceTest.java`、`SseStreamControllerTest.java`、`TaskControllerTest.java` | `vendor/Shannon/go/orchestrator/internal/streaming/manager_test.go` | `specs/001-agent-core-spec/quickstart.md` | 未覆盖同步/异步分支 |

## 4. 缺陷清单（基于证据补齐后重新评级）
### P0
#### P0-001 提交链路同步执行，存在阻塞风险且无法形成真正异步模式
- 本项目证据：`TaskController.submitTask`、`TaskOrchestrator.handleWorkflowRoute`、`WorkflowRouter.route`、`AgentRuntime.run`
- `Shannon` 证据：`vendor/Shannon/go/orchestrator/internal/server/service.go#SubmitTask`（`ExecuteWorkflow` 异步启动）
- 关联规范：`specs/001-agent-core-spec/spec.md#依赖与假设`、`specs/001-agent-core-spec/checklists/checklist.md#CHK030`

#### P0-002 异步执行队列与执行器缺失，无法实现双模式
- 本项目证据：`Confirmed Missing`（全量检索未发现 `TaskExecutionService`、`TaskQueueRepository` 等）
- `Shannon` 证据：`vendor/Shannon/go/orchestrator/internal/server/service.go#SubmitTask`（`TaskQueue` 选择与工作流异步执行）
- 关联规范：`specs/001-agent-core-spec/spec.md#User Story 1 - 任务执行与事件流订阅`、`specs/001-agent-core-spec/spec.md#FR-001`

### P1
#### P1-001 请求/响应模型缺少执行模式与同步等待超时，契约无法表达双模式
- 本项目证据：`TaskRequest`/`TaskResponse`/`TaskStatusResponse` 未含执行模式与等待超时字段
- `Shannon` 证据：`vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go#TaskRequest`（仅支持 `mode` 路由与异步提交）
- 关联规范：`specs/001-agent-core-spec/contracts/openapi.yaml#/components/schemas/TaskRequest`、`TaskResponse`；`specs/001-agent-core-spec/quickstart.md` 第 1 步

### P2
#### P2-001 测试缺少同步/异步分支与超时边界覆盖
- 本项目证据：`TaskControllerTest`、`EventStreamServiceTest`、`SseStreamControllerTest` 未出现执行模式相关用例
- `Shannon` 证据：`vendor/Shannon/go/orchestrator/internal/streaming/manager_test.go`
- 关联规范：`specs/001-agent-core-spec/quickstart.md` 第 1、2 步

## Evidence Index
| 模块 | 本项目类/方法 | 关联事件/错误码 | 关联 `Shannon` 位置 |
| --- | --- | --- | --- |
| orchestrator | `TaskOrchestrator.createTask`、`TaskOrchestrator.handleWorkflowRoute` | `WORKFLOW_STARTED`、`WORKFLOW_COMPLETED`、`ERROR_OCCURRED` | `vendor/Shannon/go/orchestrator/internal/activities/stream_events.go#EmitTaskUpdate` |
| streaming | `EventStreamService.validateCursor` | `STREAM_GAP`、`INVALID_CURSOR` | `vendor/Shannon/go/orchestrator/internal/httpapi/streaming.go#handleSSE` |
| streaming | `SseStreamController.stream` | `STREAM_TIMEOUT`（以 `ERROR_OCCURRED` 事件表达） | `vendor/Shannon/go/orchestrator/internal/httpapi/streaming.go#handleSSE` |
| history | `EventLogService.onStreamEvent` | `LLM_PARTIAL`（排除持久化） | `vendor/Shannon/go/orchestrator/internal/streaming/manager.go#shouldPersistEvent` |
| timeline | `TimelineService.getTimeline` | `NOT_FOUND` | `vendor/Shannon/go/orchestrator/internal/httpapi/timeline.go#handleBuildTimeline` |
| gateway | `TaskController.getTask` | `NOT_FOUND` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go#GetTaskStatus` |