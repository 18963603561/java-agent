# 证据补齐复核报告（`Shannon` 对照版）- 20260127

## 说明与范围
- 约束：只做分析与文档产出，不修改代码，不运行测试。
- 复核对象：`doc/ai-agent-book-part3-coverage-20260127.md`、`doc/shannon-flow-alignment-20260126.md`、`doc/shannon-alignment-verification-20260127.md`。
- 对照材料：`vendor/Shannon` 源码；`specs/001-agent-core-spec/spec.md`、`specs/001-agent-core-spec/contracts/openapi.yaml`、`specs/001-agent-core-spec/quickstart.md`、`specs/001-agent-core-spec/checklists/checklist.md`。
- 结论输出三态：`Found Evidence` / `Confirmed Missing` / `Requires Runtime Only`。

## 方法与检索
- 全量检索范围：`src/main/java`、`src/test/java`。
- 关键词集合：`context_window`/`context window`/`truncate`/`compression`/`summary`/`backpressure`；`session`/`conversation`/`history`/`message`；`memory`/`embedding`/`vector`/`qdrant`/`semantic`；`mmr`/`dedup`/`duplicate`/`similarity`；`pii`/`redact`/`mask`/`anonym`/`privacy`；`LLM_OUTPUT`/`PLAN_GENERATED`/`TOOL_INVOKED`/`STREAM_GAP`；`jwt`/`tenant`/`auth`；`opa`。
- 检索工具：`rg`。

## 证据补齐复核清单

### A. `doc/shannon-alignment-verification-20260127.md` 对应项

| 编号 | 原结论 | 复核结果 | 本项目证据 | `Shannon` 证据 | 说明 |
| --- | --- | --- | --- | --- | --- |
| SV-01 | 入口控制器未出现，需运行验证 | `Found Evidence` | `src/main/java/com/example/agent/gateway/controller/TaskController.java`；类 `TaskController`；方法 `submitTask`、`getTask`、`listTasks` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go`；方法 `SubmitTask`、`GetTaskStatus` | 入口已实现 |
| SV-02 | 编排存在但与工作流持久化一致性需验证 | `Found Evidence` | `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`；方法 `submitTask`、`handleWorkflowRoute`；`src/main/java/com/example/agent/orchestrator/JdbcTaskRepository.java` | `vendor/Shannon/go/orchestrator/internal/activities/agent.go` | 持久化实现存在，一致性仍需运行验证 |
| SV-03 | 未见流式接口/回放实现，需运行验证 | `Found Evidence` | `src/main/java/com/example/agent/streaming/SseStreamController.java`；方法 `stream`；`src/main/java/com/example/agent/streaming/EventStreamService.java`；方法 `stream`、`validateCursor`、`loadHistoryEvents` | `vendor/Shannon/docs/streaming-api.md`；`vendor/Shannon/go/orchestrator/internal/streaming/manager.go` | 断线续传与回放逻辑已实现，需运行校验 |
| SV-04 | 事件派生规则需验证 | `Found Evidence` | `src/main/java/com/example/agent/history/EventLogService.java`；方法 `onStreamEvent`；`src/main/java/com/example/agent/history/TimelineService.java`；方法 `getTimeline` | `vendor/Shannon/docs/task-history-and-timeline.md` | 派生逻辑存在，规则一致性需运行验证 |
| SV-05 | 未见层次记忆实现，需运行验证 | `Found Evidence` | `src/main/java/com/example/agent/memory/MemoryStore.java`；方法 `search`、`compress`；`RecentMemoryStore`、`SemanticMemoryStore`、`CompressedMemoryStore`；`MemoryPolicy.shouldCompress` | `vendor/Shannon/docs/memory-system-architecture.md` | 分层与压缩已实现 |
| SV-06 | 定时任务与历史一致性需验证 | `Found Evidence` | `src/main/java/com/example/agent/scheduler/ScheduleManager.java`；方法 `create`、`pause`、`resume`；`src/main/java/com/example/agent/scheduler/ScheduleEngine.java`；方法 `register`、`triggerExecution` | `vendor/Shannon/docs/scheduled-tasks.md` | 调度链路存在，持久化一致性需运行验证 |
| SV-07 | 多租户上下文与日志字段需验证 | `Found Evidence` | `src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java`；方法 `authenticate`、`authenticateJwt`、`validateTenantScope`；`src/main/java/com/example/agent/auth/TenantContextFilter.java`；方法 `filter` | `vendor/Shannon/docs/authentication-and-multitenancy.md` | 多租户链路已实现，日志一致性需运行验证 |
| SV-08 | 预算阈值事件与聚合需验证 | `Found Evidence` | `src/main/java/com/example/agent/budget/TokenBudgetManager.java`；方法 `recordUsage`、`summarize`、`publishThresholdEvent` | `vendor/Shannon/docs/token-budget-tracking.md` | 阈值事件与聚合实现存在 |
| SV-09 | 指标与追踪未见证据 | `Found Evidence` | `src/main/java/com/example/agent/observability/MetricsPublisher.java`；`TracingPublisher.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 指标与追踪类已实现 |
| SV-10 | 步骤状态机与终止条件需验证 | `Found Evidence` | `src/main/java/com/example/agent/runtime/StepRuntimeService.java`；方法 `startStep`、`completeStep`、`failStep`；`src/main/java/com/example/agent/runtime/ReactLoopService.java`；方法 `run` | `vendor/Shannon/docs/agent-core-architecture.md` | 状态机与终止护栏存在 |
| SV-11 | 计划质量自检与分解策略需验证 | `Found Evidence` | `src/main/java/com/example/agent/planning/PlannerService.java`；方法 `tryLlmPlan`；`src/main/java/com/example/agent/reflection/ReflectionService.java`；方法 `tryLlmReflection` | `vendor/Shannon/go/orchestrator/internal/activities/decompose.go`；`vendor/Shannon/go/orchestrator/internal/workflows/patterns/reflection.go` | `LLM` 规划与反思已实现 |
| SV-12 | `MCP` 远端/本地一致性需验证 | `Found Evidence` | `src/main/java/com/example/agent/agentcore/ToolExecutor.java`；方法 `execute`；`src/main/java/com/example/agent/tools/McpToolClient.java`；方法 `listTools`、`callTool` | `vendor/Shannon/docs/adding-custom-tools.md` | 远端开关与本地执行均存在，需运行验证 |
| SV-13 | 多智能体与主链路耦合需验证 | `Found Evidence` | `src/main/java/com/example/agent/runtime/AgentRuntime.java`；方法 `executeStep`；`src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java`；方法 `coordinate` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` | 主链路中已接入多智能体步骤 |
| SV-14 | 深度研究流程存在缺口 | `Found Evidence` | `src/main/java/com/example/agent/reasoning/ThoughtTreeService.java`；方法 `buildTree`；`src/main/java/com/example/agent/research/ResearchPipeline.java`；方法 `run` | `vendor/Shannon/docs/agent-core-architecture.md` | 基础流程已实现，能力深度见缺陷清单 |
| SV-15a | 回放能力需验证 | `Found Evidence` | `src/main/java/com/example/agent/governance/ReplayService.java`；方法 `replay` | `vendor/Shannon/go/orchestrator/tools/replay/main.go` | 回放链路已实现 |
| SV-15b | 背压矩阵需验证 | `Confirmed Missing` | 未发现实现 | `vendor/Shannon/docs/agent-core-architecture.md` | 检索范围：`src/main/java`、`src/test/java`；关键词：`BACKPRESSURE_APPLIED|backpressure`；结果：仅命中事件枚举 |
| SV-16a | `OPA` 集成需验证 | `Confirmed Missing` | 未发现实现 | `vendor/Shannon/docs/agent-core-architecture.md` | 检索范围：`src/main/java`、`src/test/java`；关键词：`opa`；结果：未命中 |
| SV-16b | 模型降级策略需验证 | `Found Evidence` | `src/main/java/com/example/agent/budget/TokenBudgetManager.java`；方法 `publishFallbackEvent`；`src/main/java/com/example/agent/model/ModelFallbackPolicy.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 模型降级事件已实现 |
| SV-17 | 事件落盘与时间线接口契约一致性需验证 | `Requires Runtime Only` | `src/main/java/com/example/agent/history/EventLogService.java`；方法 `listEvents`；`src/main/java/com/example/agent/history/TimelineService.java`；方法 `getTimeline` | `vendor/Shannon/docs/task-history-and-timeline.md` | 依赖存储与接口返回行为，需运行验证 |

### B. `doc/ai-agent-book-part3-coverage-20260127.md` 对应项

| 编号 | 原结论 | 复核结果 | 本项目证据 | `Shannon` 证据 | 说明 |
| --- | --- | --- | --- | --- | --- |
| AB-01 | 上下文窗口管理与截断策略未见实现 | `Found Evidence` | `src/main/java/com/example/agent/runtime/ObservationWindowBuffer.java`；方法 `add`、`snapshot`；`src/main/java/com/example/agent/runtime/ReactRuntimeProperties.java`；字段 `observationWindow`；`src/main/java/com/example/agent/memory/MemoryRecallService.java`；方法 `trimRecords` | `vendor/Shannon/docs/agent-core-architecture.md` | 观察窗口与文本裁剪已实现 |
| AB-02 | 记忆检索未与运行时决策链路集成 | `Found Evidence` | `src/main/java/com/example/agent/memory/MemoryRecallService.java`；方法 `recall`；`src/main/java/com/example/agent/runtime/AgentRuntime.java`；方法 `applyMemoryContext`、`buildRequestWithContext` | `vendor/Shannon/docs/memory-system-architecture.md` | 运行时已注入记忆上下文 |
| AB-03 | 分层记忆未见实现 | `Found Evidence` | `src/main/java/com/example/agent/memory/MemoryStore.java`；方法 `search`；`RecentMemoryStore`、`SemanticMemoryStore`、`CompressedMemoryStore` | `vendor/Shannon/docs/memory-system-architecture.md` | 分层检索已实现 |
| AB-04 | 相似度去重与多样性重排序未见实现 | `Confirmed Missing` | 未发现实现 | `vendor/Shannon/docs/memory-system-architecture.md` | 检索范围：`src/main/java`、`src/test/java`；关键词：`mmr|dedup|duplicate|similarity`；结果：未命中相似度去重逻辑 |
| AB-05 | 基于阈值的压缩自动触发未见实现 | `Found Evidence` | `src/main/java/com/example/agent/memory/MemoryPolicy.java`；方法 `shouldCompress`；`src/main/java/com/example/agent/memory/MemoryStore.java`；方法 `autoCompressIfNeeded` | `vendor/Shannon/docs/memory-system-architecture.md` | 自动压缩已实现 |
| AB-06 | 多轮对话与会话持久化未见实现 | `Found Evidence` | `src/main/java/com/example/agent/memory/MemoryWriteService.java`；方法 `saveTaskMemory`；`src/main/java/com/example/agent/memory/MemoryRepository.java`；方法 `findBySession`；`src/main/java/com/example/agent/common/TaskRequest.java`；方法 `getSessionId` | `vendor/Shannon/docs/memory-system-architecture.md` | 会话持久化已具备，但缺少独立对话管理层 |
| AB-07 | 隐私脱敏未见实现 | `Confirmed Missing` | 未发现实现 | `vendor/Shannon/docs/authentication-and-multitenancy.md` | 检索范围：`src/main/java`、`src/test/java`；关键词：`pii|redact|mask|anonym|privacy`；结果：未命中 |

### C. `doc/shannon-flow-alignment-20260126.md` 补充项

| 编号 | 原结论 | 复核结果 | 本项目证据 | `Shannon` 证据 | 说明 |
| --- | --- | --- | --- | --- | --- |
| SF-01 | 未生成 `LLM_OUTPUT` 事件与最终输出聚合 | `Found Evidence` | `src/main/java/com/example/agent/model/ModelInvocationService.java`；方法 `publishOutputEvent`；`src/main/java/com/example/agent/runtime/FinalOutputService.java`；方法 `finalizeOutput`；`src/main/java/com/example/agent/domain/event/EventType.java` | `vendor/Shannon/go/orchestrator/internal/activities/stream_events.go`；`vendor/Shannon/docs/event-types.md` | 事件与总结链路已实现 |
| SF-02 | 缺少 `LLM` 规划与反思闭环 | `Found Evidence` | `src/main/java/com/example/agent/planning/PlannerService.java`；方法 `tryLlmPlan`；`src/main/java/com/example/agent/reflection/ReflectionService.java`；方法 `tryLlmReflection` | `vendor/Shannon/go/orchestrator/internal/activities/decompose.go`；`vendor/Shannon/go/orchestrator/internal/workflows/patterns/reflection.go` | `LLM` 规划与反思已实现 |

## 16 模块对齐矩阵（`v2`）

| 模块 | 状态 | 本项目证据 | `Shannon` 证据 | 说明 |
| --- | --- | --- | --- | --- |
| `gateway` | `Partially` | `src/main/java/com/example/agent/gateway/controller/TaskController.java`；类 `TaskController` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go` | 入口形态为本地调用，未见 `gRPC` 入口 |
| `orchestrator` | `Partially` | `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`；方法 `handleWorkflowRoute`；`JdbcTaskRepository` | `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go` | 未接入 `Temporal` 工作流 |
| `streaming api` | `Partially` | `src/main/java/com/example/agent/streaming/SseStreamController.java`；方法 `stream`；`EventStreamService.stream` | `vendor/Shannon/docs/streaming-api.md` | 仅 `SSE`，无 `WebSocket`/`gRPC` |
| `task history & timeline` | `Partially` | `src/main/java/com/example/agent/history/EventLogService.java`；`TimelineService.getTimeline` | `vendor/Shannon/docs/task-history-and-timeline.md` | 未见基于决定性历史的派生语义 |
| `memory system` | `Partially` | `src/main/java/com/example/agent/memory/MemoryStore.java`；`RecentMemoryStore`、`SemanticMemoryStore`、`CompressedMemoryStore` | `vendor/Shannon/docs/memory-system-architecture.md` | 相似度去重缺失 |
| `scheduled tasks` | `Partially` | `src/main/java/com/example/agent/scheduler/ScheduleManager.java`；`ScheduleEngine.register` | `vendor/Shannon/docs/scheduled-tasks.md` | 使用本地调度引擎 |
| `authentication & multitenancy` | `Partially` | `src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java`；`TenantContextFilter` | `vendor/Shannon/docs/authentication-and-multitenancy.md` | 角色与租户体系为轻量实现 |
| `token budget tracking` | `Partially` | `src/main/java/com/example/agent/budget/TokenBudgetManager.java`；方法 `recordUsage` | `vendor/Shannon/docs/token-budget-tracking.md` | `token` 估算基于长度 |
| `observability` | `Partially` | `src/main/java/com/example/agent/observability/MetricsPublisher.java`；`TracingPublisher.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 覆盖面需运行验证 |
| `runtime` | `Partially` | `src/main/java/com/example/agent/runtime/AgentRuntime.java`；`StepRuntimeService`；`ReactLoopService` | `vendor/Shannon/docs/agent-core-architecture.md` | 与 `Temporal` 执行模型存在差异 |
| `planning/reflection` | `Partially` | `src/main/java/com/example/agent/planning/PlannerService.java`；`ReflectionService.java` | `vendor/Shannon/go/orchestrator/internal/activities/decompose.go`；`vendor/Shannon/go/orchestrator/internal/workflows/patterns/reflection.go` | 质量门控策略差异 |
| `tools` | `Partially` | `src/main/java/com/example/agent/agentcore/ToolExecutor.java`；`McpToolClient.callTool` | `vendor/Shannon/docs/adding-custom-tools.md` | 远端 `MCP` 依赖运行配置 |
| `multi-agent` | `Partially` | `src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` | 未见 `DAG`/`Handoff` 全流程 |
| `reasoning` | `Partially` | `src/main/java/com/example/agent/reasoning/ThoughtTreeService.java`；`DebateCoordinator.java`；`ResearchPipeline.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 深度研究引用链路不足 |
| `governance` | `Partially` | `src/main/java/com/example/agent/governance/ReplayService.java`；`RateLimitService`；`CircuitBreakerManager` | `vendor/Shannon/docs/agent-core-architecture.md` | 背压矩阵缺失 |
| `enterprise` | `Partially` | `src/main/java/com/example/agent/policy/PolicyEngine.java`；`WasiSandboxExecutor.java`；`TokenBudgetManager.publishFallbackEvent` | `vendor/Shannon/docs/agent-core-architecture.md` | `OPA` 接入缺失 |

## Evidence Index

| 模块 | 类/方法 | 关联事件/错误码 | 关联 `Shannon` 位置 |
| --- | --- | --- | --- |
| `gateway` | `TaskController.submitTask` | `TENANT_MISSING` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go` |
| `orchestrator` | `TaskOrchestrator.handleWorkflowRoute` | `WORKFLOW_STARTED`、`WORKFLOW_COMPLETED` | `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go` |
| `streaming api` | `SseStreamController.stream`；`EventStreamService.stream` | `STREAM_GAP`、`INVALID_CURSOR`、`STREAM_TIMEOUT` | `vendor/Shannon/docs/streaming-api.md` |
| `task history & timeline` | `EventLogService.onStreamEvent`；`TimelineService.getTimeline` | 无 | `vendor/Shannon/docs/task-history-and-timeline.md` |
| `memory system` | `MemoryStore.search`；`MemoryPolicy.shouldCompress` | 无 | `vendor/Shannon/docs/memory-system-architecture.md` |
| `scheduled tasks` | `ScheduleEngine.triggerExecution` | `SCHEDULE_TRIGGERED` | `vendor/Shannon/docs/scheduled-tasks.md` |
| `authentication & multitenancy` | `ApiKeyAuthenticator.authenticateJwt`；`TenantContextFilter.filter` | `UNAUTHORIZED`、`TENANT_MISSING` | `vendor/Shannon/docs/authentication-and-multitenancy.md` |
| `token budget tracking` | `TokenBudgetManager.recordUsage` | `BUDGET_THRESHOLD`、`MODEL_FALLBACK_APPLIED` | `vendor/Shannon/docs/token-budget-tracking.md` |
| `observability` | `MetricsPublisher.increment`；`TracingPublisher.currentTraceId` | 无 | `vendor/Shannon/docs/agent-core-architecture.md` |
| `runtime` | `StepRuntimeService.startStep`；`ReactLoopService.run` | `STEP_STARTED`、`STEP_COMPLETED`、`REACT_STOPPED` | `vendor/Shannon/docs/agent-core-architecture.md` |
| `planning/reflection` | `PlannerService.tryLlmPlan`；`ReflectionService.tryLlmReflection` | `PLAN_GENERATED`、`REFLECTION_COMPLETED` | `vendor/Shannon/go/orchestrator/internal/activities/decompose.go` |
| `tools` | `ToolExecutor.execute`；`EnforcementGateway.execute`；`McpToolClient.callTool` | `TOOL_INVOKED`、`TOOL_OBSERVATION`、`MCP_UNAVAILABLE` | `vendor/Shannon/docs/adding-custom-tools.md` |
| `multi-agent` | `MultiAgentCoordinator.coordinate` | `TEAM_RECRUITED`、`ROLE_ASSIGNED` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` |
| `reasoning` | `ThoughtTreeService.buildTree`；`DebateCoordinator.debate`；`ResearchPipeline.run` | `THOUGHT_EXPANDED`、`DEBATE_ROUND_COMPLETED`、`RESEARCH_SOURCE_ADDED` | `vendor/Shannon/docs/agent-core-architecture.md` |
| `governance` | `ReplayService.replay`；`RateLimitService.allow`；`CircuitBreakerManager.allow` | `REPLAY_STARTED`、`REPLAY_COMPLETED`、`RATE_LIMITED`、`CIRCUIT_OPEN` | `vendor/Shannon/docs/agent-core-architecture.md` |
| `enterprise` | `PolicyEngine.evaluate`；`WasiSandboxExecutor.execute`；`TokenBudgetManager.publishFallbackEvent` | `POLICY_DENIED`、`SANDBOX_DENIED`、`MODEL_FALLBACK_APPLIED` | `vendor/Shannon/docs/agent-core-architecture.md` |

## 缺陷清单（证据补齐后）

### `P0`
无。

### `P1`

#### `VF-P1-003` `OPA` 策略引擎对接缺失
本项目证据：`src/main/java/com/example/agent/policy/PolicyEngine.java`；方法 `evaluate` 仅为本地规则；检索范围 `src/main/java`、`src/test/java` 关键词 `opa` 未命中。  
`Shannon` 证据：`vendor/Shannon/docs/agent-core-architecture.md`。  
关联规范：`specs/001-agent-core-spec/spec.md` `FR-024`；`specs/001-agent-core-spec/contracts/openapi.yaml` `/api/v1/policy/evaluate`；`specs/001-agent-core-spec/checklists/checklist.md` `CHK044`。

### `P2`

#### `VF-P2-003` 背压矩阵与事件未落地
本项目证据：`src/main/java/com/example/agent/domain/event/EventType.java` 仅定义 `BACKPRESSURE_APPLIED`；`src/main/java`、`src/test/java` 未发现背压触发实现。  
`Shannon` 证据：`vendor/Shannon/docs/agent-core-architecture.md`。  
关联规范：`specs/001-agent-core-spec/spec.md` `FR-023`；`specs/001-agent-core-spec/contracts/openapi.yaml` `/api/v1/stream/sse`；`specs/001-agent-core-spec/checklists/checklist.md` `CHK028`。

#### `VF-P2-004` 深度研究引用链路不足
本项目证据：`src/main/java/com/example/agent/research/ResearchPipeline.java`；方法 `run` 仅基于模型输出与本地兜底 `buildFallbackCitations`，未见外部检索链路。  
`Shannon` 证据：`vendor/Shannon/docs/agent-core-architecture.md`。  
关联规范：`specs/001-agent-core-spec/spec.md` `FR-022`；`specs/001-agent-core-spec/contracts/openapi.yaml` `/api/v1/tasks`；`specs/001-agent-core-spec/checklists/checklist.md` `CHK006`。

#### `VF-P2-005` 记忆去重与多样性重排序缺失
本项目证据：`src/main/java/com/example/agent/memory/MemoryStore.java` 仅基于 `memoryId` 去重；检索范围 `src/main/java`、`src/test/java` 关键词 `mmr|dedup|duplicate|similarity` 未命中相似度去重逻辑。  
`Shannon` 证据：`vendor/Shannon/docs/memory-system-architecture.md`。  
关联规范：`specs/001-agent-core-spec/spec.md` `FR-006`；`specs/001-agent-core-spec/contracts/openapi.yaml` `/api/v1/memory/search`；`specs/001-agent-core-spec/checklists/checklist.md` `CHK009`、`CHK026`。

## 已关闭或转为运行验证项
- `VF-P1-001` 深度研究流程未形成闭环：已由 `ResearchPipeline.run`、`ModelInvocationService.invoke` 覆盖，调整为 `VF-P2-004`。  
- `VF-P1-002` 流式回放与断线续传对齐待验证：`SseStreamController.stream`、`EventStreamService.stream`、`ReplayService.replay` 已具备实现，转为运行验证项。  
- `VF-P2-001` 记忆层次化与压缩策略证据不足：`MemoryStore`、`MemoryPolicy`、`CompressedMemoryStore` 已实现，关闭。  
- `VF-P2-002` 指标与追踪证据不足：`MetricsPublisher`、`TracingPublisher` 已实现，关闭。
