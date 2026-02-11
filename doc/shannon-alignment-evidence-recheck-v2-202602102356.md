# Shannon 对齐证据补齐复核报告 v2（2026-02-10 23:56）

## 1. 复核范围与执行约束

- 复核目标：对 `doc/old/shannon-alignment-verification-20260127.md` 与 `doc/old/shannon-flow-alignment-20260126.md` 中“未见实现证据/需运行验证”结论进行证据补齐。
- 输入材料：
  - `doc/old/ai-agent-book-part5-coverage-20260127.md`
  - `doc/old/shannon-flow-alignment-20260126.md`
  - `doc/old/shannon-alignment-verification-20260127.md`
  - `vendor/Shannon/**`（只读）
  - `specs/001-agent-core-spec/spec.md`
  - `specs/001-agent-core-spec/contracts/openapi.yaml`
  - `specs/001-agent-core-spec/quickstart.md`
  - `specs/001-agent-core-spec/checklists/checklist.md`
- 执行约束：仅做静态分析与文档产出；未修改业务代码，未执行测试命令。
- 路径说明：用户输入中的 `doc/shannon-flow-alignment-20260126.md` 与 `docs/shannon-alignment-verification-20260127.md` 在仓库内实际路径为 `doc/old/` 下对应文件。

## 2. 全量检索方法与覆盖说明

### 2.1 检索范围

- 主代码：`src/main/java`
- 测试代码：`src/test/java`
- 两个目录均执行了关键词、类名、包名三级检索。

### 2.2 检索维度

1) 包名级检索（示例）
- `^package com.example.agent.api.http.controller`
- `^package com.example.agent.orchestration.task`
- `^package com.example.agent.streaming.sse`
- `^package com.example.agent.capabilities.memory`
- `^package com.example.agent.governance`

2) 类名级检索（示例）
- `TaskController`、`TaskOrchestrator`、`SseStreamController`、`EventStreamService`
- `MemoryStore`、`MemorySearchOrchestrator`、`MemoryMaintenanceService`
- `TokenBudgetManager`、`ReplayService`、`PolicyEngine`、`WasiSandboxExecutor`
- `MultiAgentCoordinator`、`DebateCoordinator`、`ResearchPipeline`

3) 关键词级检索（示例）
- 流式与回放：`Last-Event-ID`、`last_event_id`、`STREAM_GAP`、`REPLAY_NOT_FOUND`
- 记忆层次：`MemoryLayer`、`COMPRESSED`
- 观测与追踪：`MeterRegistry`、`Tracer`、`OpenTelemetry`
- 安全与治理：`TENANT_MISSING`、`AUTH_FAILED`、`POLICY_DENIED`、`SANDBOX_DENIED`、`MCP_UNAVAILABLE`
- 预算与事件：`BUDGET_THRESHOLD`、`MODEL_FALLBACK_APPLIED`、`LLM_OUTPUT`
- 企业策略：`OPA`、`opaUrl`

### 2.3 关键检索结果摘要（关键词计数）

| 关键词 | 匹配结果 | 结论 |
| --- | --- | --- |
| `Last-Event-ID` | 1 | 已实现头优先续传入口 |
| `last_event_id` | 1 | 已实现查询参数续传入口 |
| `STREAM_GAP` | 3 | 已实现超窗错误检测与映射 |
| `REPLAY_NOT_FOUND` | 5 | 已实现回放不存在错误链路 |
| `MemoryLayer` | 20 | 已实现分层记忆模型 |
| `COMPRESSED` | 8 | 已实现压缩记忆层 |
| `MeterRegistry` | 98 | 已实现指标埋点基础设施 |
| `Tracer` | 25 | 已实现追踪标识提取 |
| `TENANT_MISSING` | 20 | 已实现多入口租户缺失校验 |
| `MCP_UNAVAILABLE` | 29 | 已实现 MCP 不可用错误语义 |
| `LLM_OUTPUT` | 2 | 仅枚举 + 守卫测试，未见发布实现 |
| `OPA` | 0 | 确认未接入 OPA 代码路径 |
| `OpenTelemetry` | 0 | 代码层未出现关键字（依赖层存在） |

> 说明：`LLM_OUTPUT` 与 `OPA` 两项成为本次“Confirmed Missing”核心依据之一。

## 3. “未见实现证据/需运行验证”结论逐条复核

### 3.1 模块级逐条复核（对应 16 模块矩阵）

| 编号 | 原结论（20260127） | 复核三态 | 本项目证据（文件/类/方法） | Shannon 证据 | 复核说明 |
| --- | --- | --- | --- | --- | --- |
| M01 | `gateway` 入口控制器未见，需运行验证 | Found Evidence | `src/main/java/com/example/agent/api/http/controller/TaskController.java` `TaskController#submitTask/#getTask/#listTasks`；`src/main/java/com/example/agent/security/auth/ApiKeyAuthenticator.java` `ApiKeyAuthenticator#authenticate` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go` `TaskHandler#SubmitTask/#GetTaskStatus` | 入口控制器与鉴权入口已落地，原“未见证据”结论关闭。 |
| M02 | `orchestrator` 持久化一致性需验证 | Requires Runtime Only | `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java` `TaskOrchestrator#submitTask/#handleWorkflowRoute` | `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go` `DAGWorkflow` | 静态可见任务落库与状态推进；并发/失败场景一致性需运行验证。 |
| M03 | `streaming api` 未见实现证据 | Found Evidence | `src/main/java/com/example/agent/streaming/sse/SseStreamController.java` `SseStreamController#stream`；`src/main/java/com/example/agent/streaming/sse/EventStreamService.java` `EventStreamService#stream/#validateCursor` | `vendor/Shannon/go/orchestrator/internal/streaming/manager.go` `Manager#ReplaySince`；`vendor/Shannon/docs/streaming-api.md` | 已具备 `Last-Event-ID/last_event_id`、`STREAM_GAP`、历史回放拼接逻辑。 |
| M04 | `task history & timeline` 派生规则需验证 | Requires Runtime Only | `src/main/java/com/example/agent/history/eventlog/EventLogService.java` `EventLogService#onStreamEvent/#listEvents`；`src/main/java/com/example/agent/history/timeline/TimelineService.java` `TimelineService#getTimeline` | `vendor/Shannon/docs/task-history-and-timeline.md` | 静态存在事件落盘与 summary/full；派生正确性与时序一致性需运行验证。 |
| M05 | `memory system` 未见层次记忆实现 | Found Evidence | `src/main/java/com/example/agent/capabilities/memory/model/MemoryLayer.java`；`src/main/java/com/example/agent/capabilities/memory/store/RecentMemoryStore.java`；`src/main/java/com/example/agent/capabilities/memory/store/SemanticMemoryStore.java`；`src/main/java/com/example/agent/capabilities/memory/store/CompressedMemoryStore.java`；`src/main/java/com/example/agent/capabilities/memory/MemoryStore.java` `#search/#compress` | `vendor/Shannon/docs/memory-system-architecture.md` | 已形成近期/语义/压缩三层检索与压缩链路，原结论关闭。 |
| M06 | `scheduled tasks` 与历史一致性需验证 | Requires Runtime Only | `src/main/java/com/example/agent/scheduler/ScheduleEngine.java` `#register/#triggerExecution`；`src/main/java/com/example/agent/scheduler/ScheduleManager.java` `#recordExecution`；`src/main/java/com/example/agent/api/http/controller/ScheduleController.java` | `vendor/Shannon/docs/scheduled-tasks.md` | 调度与执行记录实现存在；触发稳定性与历史一致性需运行验证。 |
| M07 | `authentication & multitenancy` 需验证 | Found Evidence | `src/main/java/com/example/agent/api/http/filter/TenantContextFilter.java` `#filter`；`src/main/java/com/example/agent/security/auth/ApiKeyAuthenticator.java` `#authenticate`（含 JWT 分支） | `vendor/Shannon/docs/authentication-and-multitenancy.md` | 多租户上下文与鉴权实现明确，含日志字段与错误路径。 |
| M08 | `token budget tracking` 聚合一致性需验证 | Requires Runtime Only | `src/main/java/com/example/agent/budget/token/application/TokenBudgetManager.java` `#recordUsage/#summarize`；`src/main/java/com/example/agent/budget/token/event/DefaultBudgetEventPublisher.java` `#publishThresholdEvent/#publishFallbackEvent` | `vendor/Shannon/go/orchestrator/internal/activities/budget.go` `ExecuteAgentWithBudget`；`vendor/Shannon/docs/token-budget-tracking.md` | 计量与阈值事件已落地；真实运行下聚合精度仍需验收。 |
| M09 | `observability` 指标与追踪未见证据 | Found Evidence | `src/main/java/com/example/agent/streaming/observability/MetricsPublisher.java`；`src/main/java/com/example/agent/streaming/observability/TracingPublisher.java`；`pom.xml` 含 `micrometer-tracing-bridge-otel` | `vendor/Shannon/docs/agent-core-architecture.md` | 指标与 traceId 贯通代码已存在，原“未见证据”结论关闭。 |
| M10 | `runtime` 状态机与终止条件需验证 | Requires Runtime Only | `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java` `#run`；`src/main/java/com/example/agent/runtime/engine/StepExecutionCoordinator.java` `#executeStep` | `vendor/Shannon/go/orchestrator/internal/activities/agent.go` `ExecuteAgent` | 运行时循环与恢复路径可见；边界终止行为需运行验证。 |
| M11 | `planning/reflection` 质量策略需验证 | Requires Runtime Only | `src/main/java/com/example/agent/planning/PlannerService.java` `#plan`；`src/main/java/com/example/agent/reflection/ReflectionService.java` `#reflect` | `vendor/Shannon/go/orchestrator/internal/activities/decompose.go` `DecomposeTask`；`vendor/Shannon/go/orchestrator/internal/workflows/patterns/reflection.go` `ReflectOnResult` | 规划与反思实现存在；“质量”与“自修复效果”需运行验证。 |
| M12 | `tools` MCP 本地/远端一致性需验证 | Requires Runtime Only | `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java` `#listTools/#callTool`；`src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java` `#execute`；`src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java` `#execute` | `vendor/Shannon/docs/adding-custom-tools.md`；`vendor/Shannon/go/orchestrator/internal/activities/stream_events.go` | 本地/远端/回退策略代码存在，需外部 MCP 环境做端到端验证。 |
| M13 | `multi-agent` 与主链路耦合需验证 | Found Evidence | `src/main/java/com/example/agent/runtime/step/executor/MultiAgentStepExecutor.java` `#execute` 调用 `src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java` `#coordinate`；`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentEventPublisher.java` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` | 已进入运行时步骤执行链并发布团队事件。 |
| M14 | `reasoning` 深度研究链路有缺口 | Found Evidence | `src/main/java/com/example/agent/runtime/step/executor/ResearchStepExecutor.java` `#execute` 调用 `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java` `#runWithRawRef`；`src/main/java/com/example/agent/reasoning/debate/DebateCoordinator.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 深度研究不再是空壳，已接入运行时并有回退策略。 |
| M15 | `governance` 回放与背压矩阵需验证 | Confirmed Missing | 已有回放：`src/main/java/com/example/agent/governance/replay/ReplayService.java` `#replay`；缺失项检索：`EventType.BACKPRESSURE_APPLIED` 仅见 `src/main/java/com/example/agent/streaming/domain/EventType.java`，未见发布点 | `vendor/Shannon/go/orchestrator/internal/budget/manager.go`（背压）；`vendor/Shannon/go/orchestrator/internal/streaming/manager.go` `ReplaySince` | 回放链路存在，但“背压事件矩阵”静态确认缺失。检索范围：`src/main/java` + `src/test/java`。 |
| M16 | `enterprise` OPA 接入与模型降级需验证 | Confirmed Missing | 模型降级存在：`src/main/java/com/example/agent/capabilities/llm/provider/ModelFallbackPolicy.java` `#evaluate`；OPA 缺失检索：`OPA|opa|opaUrl|policy.NewOPAEngine` 在 `src/main/java` 与 `src/test/java` 均无匹配 | `vendor/Shannon/go/orchestrator/internal/activities/agent.go`（`policy.NewOPAEngine`）; `vendor/Shannon/docs/agent-core-architecture.md` | 模型降级已实现，但 OPA 引擎接入静态确认缺失。 |

### 3.2 旧缺陷条目复核（`VF-*`）

| 旧缺陷编号 | 原结论 | 复核三态 | 证据 |
| --- | --- | --- | --- |
| `VF-P1-001` | 深度研究流程未形成可执行闭环 | Found Evidence | `src/main/java/com/example/agent/runtime/step/executor/ResearchStepExecutor.java` `ResearchStepExecutor#execute` 已接入 `ResearchPipeline#runWithRawRef`；`src/test/java/com/example/agent/research/ResearchPipelineTest.java` 覆盖成功与回退场景 |
| `VF-P1-002` | 未见流式回放实现 | Found Evidence | `src/main/java/com/example/agent/streaming/sse/EventStreamService.java` `EventStreamService#stream/#validateCursor`；`src/test/java/com/example/agent/streaming/EventStreamServiceTest.java` |
| `VF-P2-001` | 未见层次记忆与压缩策略 | Found Evidence | `MemoryLayer` + `RecentMemoryStore` + `SemanticMemoryStore` + `CompressedMemoryStore` + `MemoryStore#compress/#search` |
| `VF-P2-002` | 未见指标与追踪证据 | Found Evidence | `MetricsPublisher` + `TracingPublisher` + `ToolExecutionTracer` + `pom.xml` OTel 桥接依赖 |

## 4. 证据补齐后的缺陷重评级（P0/P1/P2）

### 4.1 P0

#### `V2-P0-001` 终态 `LLM_OUTPUT` 事件发布缺失（Confirmed Missing）

- 本项目证据：
  - `src/main/java/com/example/agent/streaming/domain/EventType.java` 定义 `LLM_OUTPUT`。
  - 全量检索 `EventType.LLM_OUTPUT|LLM_OUTPUT` 仅命中枚举与守卫测试。
  - `src/test/java/com/example/agent/capabilities/llm/architecture/LlmArchitectureGuardTest.java` 明确禁止在 `ModelInvocationService` 恢复 `EventType.LLM_OUTPUT` 发布路径。
- Shannon 证据：
  - `vendor/Shannon/go/orchestrator/internal/activities/stream_events.go` 定义 `StreamEventLLMOutput`。
  - `vendor/Shannon/docs/event-types.md` 将 `LLM_OUTPUT` 定义为最终输出关键事件。
- 关联规格条目：
  - `specs/001-agent-core-spec/spec.md`：`FR-002`、`FR-003`、事件顺序约束与 `LLM_OUTPUT` 说明。
  - `specs/001-agent-core-spec/contracts/openapi.yaml`：`/api/v1/stream/sse`。
  - `specs/001-agent-core-spec/checklists/checklist.md`：`CHK006`、`CHK017`、`CHK021`。

### 4.2 P1

#### `V2-P1-001` `OPA` 策略引擎未接入（Confirmed Missing）

- 本项目证据：
  - `src/main/java/com/example/agent/governance/policy/PolicyEngine.java` 为本地规则评估，不含 OPA 客户端/引擎调用。
  - 全量检索关键词 `OPA|opa|opaUrl|policy.NewOPAEngine`：`src/main/java` 与 `src/test/java` 均无命中。
- Shannon 证据：
  - `vendor/Shannon/go/orchestrator/internal/activities/agent.go` 多处调用 `policy.NewOPAEngine`。
  - `vendor/Shannon/docs/adding-custom-tools.md` 与 `vendor/Shannon/docs/agent-core-architecture.md` 均给出 OPA 治理语义。
- 关联规格条目：
  - `specs/001-agent-core-spec/spec.md`：`FR-024`（企业级安全）。
  - `specs/001-agent-core-spec/contracts/openapi.yaml`：`/api/v1/policy/evaluate`。
  - `specs/001-agent-core-spec/checklists/checklist.md`：`CHK044`。

### 4.3 P2

#### `V2-P2-001` 背压事件矩阵未落地（Confirmed Missing）

- 本项目证据：
  - `src/main/java/com/example/agent/governance/ratelimit/RateLimitService.java` 与 `src/main/java/com/example/agent/governance/circuitbreaker/CircuitBreakerManager.java` 存在限流/熔断控制。
  - `src/main/java/com/example/agent/streaming/domain/EventType.java` 定义 `BACKPRESSURE_APPLIED`，但全量检索 `EventType.BACKPRESSURE_APPLIED|BACKPRESSURE_APPLIED` 仅命中枚举定义，未见发布逻辑。
- Shannon 证据：
  - `vendor/Shannon/go/orchestrator/internal/budget/manager.go` 存在背压阈值与延迟机制。
  - `vendor/Shannon/docs/streaming-api.md` 说明预算阈值与治理事件流关联。
- 关联规格条目：
  - `specs/001-agent-core-spec/spec.md`：`FR-023`（回放/限流/背压/熔断矩阵）。
  - `specs/001-agent-core-spec/contracts/openapi.yaml`：`/api/v1/mcp/tools/call` 错误语义 `MCP_UNAVAILABLE`、`CIRCUIT_OPEN`。
  - `specs/001-agent-core-spec/checklists/checklist.md`：`CHK028`（治理指标覆盖）。

## 5. 关联模块矩阵（证据补齐版）

> 说明：该矩阵已补齐证据链接，不存在空白项；无法静态闭合的项已标注 `Requires Runtime Only` 或 `Confirmed Missing`。

| 模块 | 结论状态 | 本项目证据链接 | Shannon 证据链接 | 关联事件/错误码 | 关联规范 |
| --- | --- | --- | --- | --- | --- |
| gateway | Found Evidence | `src/main/java/com/example/agent/api/http/controller/TaskController.java`；`src/main/java/com/example/agent/security/auth/ApiKeyAuthenticator.java` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go` | `TENANT_MISSING`、`AUTH_FAILED` | `FR-001`、`FR-008`、`CHK046`、`CHK047` |
| orchestrator | Requires Runtime Only | `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java` | `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go` | `WORKFLOW_STARTED`、`WORKFLOW_COMPLETED` | `FR-001`、`FR-003` |
| streaming api | Found Evidence | `src/main/java/com/example/agent/streaming/sse/SseStreamController.java`；`src/main/java/com/example/agent/streaming/sse/EventStreamService.java` | `vendor/Shannon/go/orchestrator/internal/streaming/manager.go`；`vendor/Shannon/docs/streaming-api.md` | `STREAM_GAP`、`INVALID_CURSOR` | `FR-002`、`CHK021`、`CHK024` |
| task history & timeline | Requires Runtime Only | `src/main/java/com/example/agent/history/eventlog/EventLogService.java`；`src/main/java/com/example/agent/history/timeline/TimelineService.java` | `vendor/Shannon/docs/task-history-and-timeline.md` | `REPLAY_NOT_FOUND`（回放场景关联） | `FR-004`、`FR-005`、`CHK008` |
| memory system | Found Evidence | `src/main/java/com/example/agent/capabilities/memory/MemoryStore.java`；`src/main/java/com/example/agent/capabilities/memory/model/MemoryLayer.java` | `vendor/Shannon/docs/memory-system-architecture.md` | `RECENT/SEMANTIC/COMPRESSED` 层 | `FR-006`、`CHK009`、`CHK026` |
| scheduled tasks | Requires Runtime Only | `src/main/java/com/example/agent/scheduler/ScheduleEngine.java`；`src/main/java/com/example/agent/scheduler/ScheduleManager.java` | `vendor/Shannon/docs/scheduled-tasks.md` | `SCHEDULE_TRIGGERED` | `FR-007`、`CHK013` |
| authentication & multitenancy | Found Evidence | `src/main/java/com/example/agent/api/http/filter/TenantContextFilter.java`；`src/main/java/com/example/agent/security/auth/ApiKeyAuthenticator.java` | `vendor/Shannon/docs/authentication-and-multitenancy.md` | `TENANT_MISSING`、`UNAUTHORIZED`、`AUTH_FAILED` | `FR-008`、`CHK040`、`CHK042`、`CHK046`、`CHK047` |
| token budget tracking | Requires Runtime Only | `src/main/java/com/example/agent/budget/token/application/TokenBudgetManager.java`；`src/main/java/com/example/agent/budget/token/event/DefaultBudgetEventPublisher.java` | `vendor/Shannon/go/orchestrator/internal/activities/budget.go`；`vendor/Shannon/docs/token-budget-tracking.md` | `BUDGET_THRESHOLD`、`MODEL_FALLBACK_APPLIED` | `FR-009`、`CHK011`、`CHK023`、`CHK043` |
| observability | Found Evidence | `src/main/java/com/example/agent/streaming/observability/MetricsPublisher.java`；`src/main/java/com/example/agent/streaming/observability/TracingPublisher.java` | `vendor/Shannon/docs/agent-core-architecture.md` | `traceId`、指标计数/时延 | `FR-011`、`CHK028` |
| runtime | Requires Runtime Only | `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java`；`src/main/java/com/example/agent/runtime/engine/StepExecutionCoordinator.java` | `vendor/Shannon/go/orchestrator/internal/activities/agent.go` | `PLAN_GENERATED`、`PLAN_REVISED`、`REFLECTION_*` | `FR-015`、`FR-016`、`FR-017` |
| planning/reflection | Requires Runtime Only | `src/main/java/com/example/agent/planning/PlannerService.java`；`src/main/java/com/example/agent/reflection/ReflectionService.java` | `vendor/Shannon/go/orchestrator/internal/activities/decompose.go`；`vendor/Shannon/go/orchestrator/internal/workflows/patterns/reflection.go` | `PLAN_GENERATED`、`REFLECTION_STARTED`、`REFLECTION_COMPLETED` | `FR-018`、`FR-019` |
| tools | Requires Runtime Only | `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java`；`src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java`；`src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java` | `vendor/Shannon/docs/adding-custom-tools.md`；`vendor/Shannon/go/orchestrator/internal/activities/stream_events.go` | `TOOL_INVOKED`、`TOOL_OBSERVATION`、`TOOL_ERROR`、`MCP_UNAVAILABLE`、`HOOK_BLOCKED` | `FR-020`、`CHK048`、`CHK052` |
| multi-agent | Found Evidence | `src/main/java/com/example/agent/runtime/step/executor/MultiAgentStepExecutor.java`；`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java`；`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentEventPublisher.java` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` | `TEAM_RECRUITED`、`ROLE_ASSIGNED` | `FR-021` |
| reasoning | Found Evidence | `src/main/java/com/example/agent/reasoning/debate/DebateCoordinator.java`；`src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java`；`src/main/java/com/example/agent/runtime/step/executor/ResearchStepExecutor.java` | `vendor/Shannon/docs/agent-core-architecture.md` | `DEBATE_ROUND_COMPLETED`、`RESEARCH_SOURCE_ADDED` | `FR-022` |
| governance | Confirmed Missing | `src/main/java/com/example/agent/governance/replay/ReplayService.java`（已实现）+ `BACKPRESSURE_APPLIED` 发布点检索缺失 | `vendor/Shannon/go/orchestrator/internal/streaming/manager.go`；`vendor/Shannon/go/orchestrator/internal/budget/manager.go` | `REPLAY_STARTED`、`REPLAY_COMPLETED`、`REPLAY_NOT_FOUND`、`BACKPRESSURE_APPLIED`（缺失） | `FR-023`、`CHK051` |
| enterprise | Confirmed Missing | `src/main/java/com/example/agent/governance/policy/PolicyEngine.java`；`src/main/java/com/example/agent/capabilities/tools/sandbox/WasiSandboxExecutor.java`；OPA 检索无命中 | `vendor/Shannon/go/orchestrator/internal/activities/agent.go`（`NewOPAEngine`）；`vendor/Shannon/docs/agent-core-architecture.md` | `POLICY_DENIED`、`SANDBOX_DENIED`、`MODEL_FALLBACK_APPLIED` | `FR-024`、`CHK044`、`CHK045`、`CHK049`、`CHK050` |

## 6. Evidence Index

| 模块 | 类/方法 | 关联事件/错误码 | 关联 Shannon 位置 |
| --- | --- | --- | --- |
| gateway | `TaskController#submitTask` / `ApiKeyAuthenticator#authenticate` | `TENANT_MISSING`、`AUTH_FAILED` | `go/orchestrator/cmd/gateway/internal/handlers/task.go#SubmitTask` |
| orchestrator | `TaskOrchestrator#handleWorkflowRoute` | `WORKFLOW_STARTED`、`WORKFLOW_COMPLETED` | `go/orchestrator/internal/workflows/strategies/dag.go#DAGWorkflow` |
| streaming api | `SseStreamController#stream` / `EventStreamService#stream` | `STREAM_GAP`、`INVALID_CURSOR` | `go/orchestrator/internal/streaming/manager.go#ReplaySince` |
| history/timeline | `EventLogService#onStreamEvent` / `TimelineService#getTimeline` | 事件持久化、时间线摘要 | `docs/task-history-and-timeline.md` |
| memory system | `MemoryStore#search/#compress` / `MemorySearchOrchestrator#search` | `RECENT/SEMANTIC/COMPRESSED` | `docs/memory-system-architecture.md` |
| scheduled tasks | `ScheduleEngine#register/#triggerExecution` | `SCHEDULE_TRIGGERED` | `docs/scheduled-tasks.md` |
| auth & multitenancy | `TenantContextFilter#filter` / `ApiKeyAuthenticator#authenticate` | `TENANT_MISSING`、`UNAUTHORIZED` | `docs/authentication-and-multitenancy.md` |
| token budget | `TokenBudgetManager#recordUsage` / `DefaultBudgetEventPublisher#publishThresholdEvent` | `BUDGET_THRESHOLD`、`MODEL_FALLBACK_APPLIED` | `go/orchestrator/internal/activities/budget.go#ExecuteAgentWithBudget` |
| observability | `MetricsPublisher#increment/#recordTime` / `TracingPublisher#getTraceId` | 指标与 `traceId` | `docs/agent-core-architecture.md` |
| runtime | `AgentRuntime#run` / `StepExecutionCoordinator#executeStep` | `PLAN_*`、`REFLECTION_*` | `go/orchestrator/internal/activities/agent.go#ExecuteAgent` |
| planning/reflection | `PlannerService#plan` / `ReflectionService#reflect` | `PLAN_GENERATED`、`REFLECTION_COMPLETED` | `go/orchestrator/internal/activities/decompose.go#DecomposeTask`、`go/orchestrator/internal/workflows/patterns/reflection.go#ReflectOnResult` |
| tools | `McpToolClient#listTools/#callTool` / `EnforcementGateway#execute` | `TOOL_INVOKED`、`TOOL_OBSERVATION`、`TOOL_ERROR` | `docs/adding-custom-tools.md`、`go/orchestrator/internal/activities/stream_events.go` |
| multi-agent | `MultiAgentCoordinator#coordinate` / `MultiAgentEventPublisher#publishTeamEvents` | `TEAM_RECRUITED`、`ROLE_ASSIGNED` | `docs/multi-agent-workflow-architecture.md` |
| reasoning | `DebateCoordinator` / `ResearchPipeline#runWithRawRef` / `ResearchStepExecutor#execute` | `DEBATE_ROUND_COMPLETED`、`RESEARCH_SOURCE_ADDED` | `docs/agent-core-architecture.md` |
| governance | `ReplayService#replay` / `RateLimitService#allow` / `CircuitBreakerManager#allow` | `REPLAY_*`、`REPLAY_NOT_FOUND`、`BACKPRESSURE_APPLIED`（缺失） | `go/orchestrator/internal/streaming/manager.go#ReplaySince`、`go/orchestrator/internal/budget/manager.go` |
| enterprise | `PolicyEngine#evaluate` / `WasiSandboxExecutor#execute` | `POLICY_DENIED`、`SANDBOX_DENIED`、`OPA`（缺失） | `go/orchestrator/internal/activities/agent.go`（OPA） |

## 7. 结论

1) 本次“证据补齐复核”后，旧报告中 4 个 `VF-*` 缺陷均可静态证据关闭。  
2) 仍需整改的确定性缺口为 3 项：`LLM_OUTPUT` 终态事件缺失（P0）、`OPA` 未接入（P1）、背压事件矩阵未落地（P2）。  
3) 其余“需运行验证”项已从“无实现证据”升级为“有实现但需运行验收”，建议按 `quickstart.md` + `checklist.md` 组织灰度验证。

