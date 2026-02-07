# Shannon 对齐复核报告（证据补齐复核）- 20260127-v2

## 说明与范围
- 复核对象：`doc/shannon-flow-alignment-20260126.md`、`doc/shannon-alignment-verification-20260127.md`
- 规格权威：`specs/001-agent-core-spec/spec.md`、`specs/001-agent-core-spec/contracts/openapi.yaml`、`specs/001-agent-core-spec/quickstart.md`、`specs/001-agent-core-spec/checklists/checklist.md`
- Shannon 参考：`vendor/Shannon/**`
- 全量检索范围：`src/main/java`、`src/test/java`
- 三态定义：`Found Evidence`=已找到实现证据；`Confirmed Missing`=确认不存在（含检索关键词与范围）；`Requires Runtime Only`=只能运行验证

## 检索关键词清单（全量范围：src/main/java + src/test/java）
| 目的 | 关键词 |
| --- | --- |
| gateway 控制器 | `TaskController`、`SseStreamController`、`TimelineController`、`McpController`、`PolicyController`、`ReplayController`、`ScheduleController`、`MemoryController`、`BudgetController` |
| streaming 断线续传 | `last_event_id`、`Last-Event-ID`、`StreamEvent`、`TaskStreamRequest`、`STREAM_GAP` |
| replay since | `ReplaySince`、`replaySince`、`replay since` |
| memory 分层 | `MemoryStore`、`VectorStore`、`QdrantVectorStore`、`layer`、`compressed`、`recent`、`semantic` |
| observability | `MetricsPublisher`、`TracingPublisher`、`task.submit.count`、`event.stream.count`、`budget.tokens.used`、`traceId`、`requestId` |
| multi-agent DAG/Handoff | `AgentGraphExecutor`、`HandoffService`、`SupervisorCoordinator` |
| backpressure | `BACKPRESSURE_APPLIED`、`backpressure` |
| OPA | `OPA`、`opa` |
| AGENT 事件 | `AGENT_STARTED`、`AGENT_COMPLETED` |

## 证据补齐复核（原报告不确定项逐条处理）
| 编号 | 原结论位置 | 原结论摘要 | 复核结论 | 本项目证据 |
| --- | --- | --- | --- | --- |
| 1 | A.5 | 事件落盘与时间线聚合用于后续查询与回放需运行验证 | `Found Evidence` | `src/main/java/com/example/agent/history/EventLogService.java#onStreamEvent`、`src/main/java/com/example/agent/history/TimelineService.java#getTimeline`、`src/main/java/com/example/agent/governance/ReplayService.java#replay`、`src/main/java/com/example/agent/gateway/controller/TimelineController.java#listEvents` |
| 2 | B.矩阵 gateway | 入口控制器未出现需运行验证 | `Found Evidence` | `src/main/java/com/example/agent/gateway/controller/TaskController.java#submitTask`、`src/main/java/com/example/agent/gateway/controller/ScheduleController.java#create`、`src/main/java/com/example/agent/gateway/controller/McpController.java#listTools` |
| 3 | B.矩阵 streaming api | 未见流式接口实现证据 | `Found Evidence` | `src/main/java/com/example/agent/streaming/SseStreamController.java#stream`、`src/main/java/com/example/agent/streaming/EventStreamService.java#stream`、`src/test/java/com/example/agent/streaming/SseStreamControllerTest.java` |
| 4 | B.矩阵 streaming api | 回放与断线续传对齐需验证 | `Confirmed Missing` | `src/main/java/com/example/agent/streaming/EventStreamService.java#validateCursor` 仅校验游标；`rg "ReplaySince|replaySince|replay since" src/main/java src/test/java` 无命中 |
| 5 | B.矩阵 memory system | 未见层次记忆实现证据 | `Found Evidence` | `src/main/java/com/example/agent/memory/MemoryStore.java#search`（向量语义检索）、`src/main/java/com/example/agent/memory/MemoryStore.java#compress`（`layer=compressed`）、`src/main/java/com/example/agent/memory/JdbcMemoryRepository.java#search`（按 `created_at` 倒序） |
| 6 | B.矩阵 observability | 指标与追踪未见证据 | `Found Evidence` | `src/main/java/com/example/agent/observability/MetricsPublisher.java`、`src/main/java/com/example/agent/observability/TracingPublisher.java`、`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java#submitTask`（`task.submit.count`） |
| 7 | C.1 gateway | 网关控制器与鉴权入口需运行验证 | `Found Evidence` | `src/main/java/com/example/agent/gateway/controller/TaskController.java`、`src/main/java/com/example/agent/auth/TenantContextFilter.java`、`src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java` |
| 8 | C.3 streaming api | 未见流式接口/回放实现 | `Found Evidence` | `src/main/java/com/example/agent/streaming/SseStreamController.java#stream`、`src/main/java/com/example/agent/governance/ReplayService.java#replay` |
| 9 | C.5 memory system | 记忆层次化与压缩策略需运行验证 | `Found Evidence` | `src/main/java/com/example/agent/gateway/controller/MemoryController.java#compress`、`src/main/java/com/example/agent/memory/MemoryStore.java#compress` |
| 10 | C.7 authentication | 鉴权入口与租户上下文需运行验证 | `Found Evidence` | `src/main/java/com/example/agent/auth/TenantResolver.java#resolve`、`src/main/java/com/example/agent/auth/TenantContextFilter.java#filter` |
| 11 | C.9 observability | 指标与追踪需运行验证 | `Found Evidence` | `src/main/java/com/example/agent/streaming/EventStreamService.java#onStreamEvent`（`event.stream.count`）、`src/main/java/com/example/agent/budget/TokenBudgetManager.java#recordUsage`（`budget.tokens.used`） |
| 12 | E.VF-P1-001 | 深度研究流程未形成可执行闭环 | `Found Evidence` | `src/main/java/com/example/agent/research/ResearchPipeline.java#run`、`src/main/java/com/example/agent/runtime/AgentRuntime.java#executeStep`（`RESEARCH` 步骤） |
| 13 | E.VF-P1-002 | 流式回放与断线续传对齐待验证 | `Confirmed Missing` | 同 4 |
| 14 | E.VF-P2-001 | 记忆层次化与压缩策略证据不足 | `Found Evidence` | 同 5 与 9 |
| 15 | E.VF-P2-002 | 可观测性覆盖不足证据 | `Found Evidence` | 同 6 与 11 |

## 16 模块矩阵（证据链接补齐）
| 模块 | 状态 | 本项目证据 | Shannon 证据 | 说明 |
| --- | --- | --- | --- | --- |
| gateway | `Partially` | `src/main/java/com/example/agent/gateway/controller/TaskController.java#submitTask`、`src/main/java/com/example/agent/gateway/controller/McpController.java#callTool`、`src/main/java/com/example/agent/gateway/controller/PolicyController.java#evaluate` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go#SubmitTask` | 入口齐备，调用路径与 Shannon 仍有差异 |
| orchestrator | `Partially` | `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java#submitTask`、`src/main/java/com/example/agent/orchestrator/WorkflowRouter.java#route` | `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go#DAGWorkflow` | 同步编排，缺少 Temporal 语义 |
| streaming api | `Partially` | `src/main/java/com/example/agent/streaming/SseStreamController.java#stream`、`src/main/java/com/example/agent/streaming/EventStreamService.java#validateCursor` | `vendor/Shannon/docs/streaming-api.md`、`vendor/Shannon/go/orchestrator/internal/streaming/manager.go#ReplaySince` | 具备游标校验，缺少回放续传 |
| task history & timeline | `Partially` | `src/main/java/com/example/agent/history/EventLogService.java#onStreamEvent`、`src/main/java/com/example/agent/history/TimelineService.java#getTimeline`、`src/main/java/com/example/agent/runtime/StepRuntimeService.java#listSteps` | `vendor/Shannon/docs/task-history-and-timeline.md` | 支持事件与时间线，缺少持久化策略细节 |
| memory system | `Partially` | `src/main/java/com/example/agent/memory/MemoryStore.java#save`、`src/main/java/com/example/agent/memory/VectorStore.java`、`src/main/java/com/example/agent/memory/QdrantVectorStore.java` | `vendor/Shannon/docs/memory-system-architecture.md` | 语义检索与压缩存在，层次化策略不完整 |
| scheduled tasks | `Partially` | `src/main/java/com/example/agent/scheduler/ScheduleManager.java#create`、`src/main/java/com/example/agent/scheduler/ScheduleEngine.java#triggerExecution` | `vendor/Shannon/docs/scheduled-tasks.md` | 本地调度替代 Temporal |
| authentication & multitenancy | `Partially` | `src/main/java/com/example/agent/auth/TenantContextFilter.java#filter`、`src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java#authenticate` | `vendor/Shannon/docs/authentication-and-multitenancy.md` | JWT/角色支持但无数据库隔离 |
| token budget tracking | `Partially` | `src/main/java/com/example/agent/budget/TokenBudgetManager.java#recordUsage`、`src/main/java/com/example/agent/budget/TokenUsageRepository.java` | `vendor/Shannon/docs/token-budget-tracking.md` | 计量链路齐备，模型成本精细化不足 |
| observability | `Partially` | `src/main/java/com/example/agent/observability/MetricsPublisher.java`、`src/main/java/com/example/agent/observability/TracingPublisher.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 指标已落地，OTel 细分未覆盖 |
| runtime | `Partially` | `src/main/java/com/example/agent/runtime/AgentRuntime.java#run`、`src/main/java/com/example/agent/runtime/StepRuntimeService.java#startStep` | `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go#DAGWorkflow` | 未发射 `AGENT_STARTED/AGENT_COMPLETED` |
| planning/reflection | `Partially` | `src/main/java/com/example/agent/planning/PlannerService.java#plan`、`src/main/java/com/example/agent/reflection/ReflectionService.java#reflect` | `vendor/Shannon/go/orchestrator/internal/activities/decompose.go#DecomposeTask`、`vendor/Shannon/go/orchestrator/internal/workflows/patterns/reflection.go#ReflectOnResult` | LLM 与规则并存 |
| tools | `Partially` | `src/main/java/com/example/agent/agentcore/ToolExecutor.java#execute`、`src/main/java/com/example/agent/tools/McpToolClient.java#callTool`、`src/main/java/com/example/agent/tools/hook/HookManager.java#preTool` | `vendor/Shannon/docs/adding-custom-tools.md` | 本地与远端并存，工具选择策略简化 |
| multi-agent | `Partially` | `src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java#coordinate`、`src/main/java/com/example/agent/multiagent/AgentGraphExecutor.java#execute` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` | DAG/Handoff 未接入运行时 |
| reasoning | `Partially` | `src/main/java/com/example/agent/reasoning/ThoughtTreeService.java#buildTree`、`src/main/java/com/example/agent/reasoning/DebateCoordinator.java#debate`、`src/main/java/com/example/agent/research/ResearchPipeline.java#run` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` | 规则与 LLM 混合 |
| governance | `Partially` | `src/main/java/com/example/agent/governance/ReplayService.java#replay`、`src/main/java/com/example/agent/governance/RateLimitService.java#allow`、`src/main/java/com/example/agent/governance/CircuitBreakerManager.java#allow` | `vendor/Shannon/go/orchestrator/tools/replay/main.go` | 回放存在，背压缺失 |
| enterprise | `Partially` | `src/main/java/com/example/agent/policy/PolicyEngine.java#evaluate`、`src/main/java/com/example/agent/sandbox/WasiSandboxExecutor.java#execute`、`src/main/java/com/example/agent/model/ModelFallbackPolicy.java#evaluate` | `vendor/Shannon/docs/agent-core-architecture.md` | OPA 未接入 |

## 重点链路证据补齐
### gateway 控制器链路
- 任务相关：`src/main/java/com/example/agent/gateway/controller/TaskController.java#submitTask`、`src/main/java/com/example/agent/gateway/controller/TaskController.java#getTask`、`src/main/java/com/example/agent/gateway/controller/TaskController.java#listTasks`
- 流式订阅：`src/main/java/com/example/agent/streaming/SseStreamController.java#stream`
- 时间线与步骤：`src/main/java/com/example/agent/gateway/controller/TimelineController.java#listEvents`、`src/main/java/com/example/agent/gateway/controller/TimelineController.java#listSteps`
- MCP：`src/main/java/com/example/agent/gateway/controller/McpController.java#listTools`、`src/main/java/com/example/agent/gateway/controller/McpController.java#callTool`
- 策略评估：`src/main/java/com/example/agent/gateway/controller/PolicyController.java#evaluate`
- 回放：`src/main/java/com/example/agent/gateway/controller/ReplayController.java#replay`

### streaming 断线续传与 replay since
- 续传游标：`src/main/java/com/example/agent/streaming/SseStreamController.java#stream`（`Last-Event-ID` 优先）  
  `src/main/java/com/example/agent/streaming/EventStreamService.java#validateCursor`（`STREAM_GAP` 校验）
- 缺口：未发现 `ReplaySince` 或历史事件回放逻辑；`rg "ReplaySince|replaySince|replay since" src/main/java src/test/java` 无命中  
  结论：`Confirmed Missing`（详见缺陷 `V2-P1-001`）

### memory system
- 语义检索：`src/main/java/com/example/agent/memory/MemoryStore.java#search` + `src/main/java/com/example/agent/memory/VectorStore.java`
- 压缩层：`src/main/java/com/example/agent/memory/MemoryStore.java#compress`（`layer=compressed`）
- 近期记忆：`src/main/java/com/example/agent/memory/JdbcMemoryRepository.java#search`（按 `created_at` 倒序）
- 缺口：未见 `recent/semantic` 明确层级标识与自动压缩触发（详见缺陷 `V2-P2-001`）

### observability
- 指标：`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java#submitTask`（`task.submit.count`）  
  `src/main/java/com/example/agent/streaming/EventStreamService.java#onStreamEvent`（`event.stream.count`）  
  `src/main/java/com/example/agent/budget/TokenBudgetManager.java#recordUsage`（`budget.tokens.used`）
- 追踪字段贯通：`src/main/java/com/example/agent/auth/TenantResolver.java#resolve`、`src/main/java/com/example/agent/common/ApiResponse.java`、`src/main/java/com/example/agent/gateway/controller/GlobalExceptionHandler.java`

## 缺陷清单（证据补齐后重评级）
### P0
- 无

### P1
#### V2-P1-001 `SSE` 断线续传缺少事件回放
- 本项目证据：`src/main/java/com/example/agent/streaming/EventStreamService.java#stream` 仅订阅实时 `sink`；`src/main/java/com/example/agent/streaming/EventStreamService.java#validateCursor` 仅校验游标；`rg "ReplaySince|replaySince|replay since" src/main/java src/test/java` 无命中
- Shannon 证据：`vendor/Shannon/go/orchestrator/internal/streaming/manager.go#ReplaySince`、`vendor/Shannon/docs/streaming-api.md`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-002/FR-003 与 `SSE` 断线续传规则）  
  `specs/001-agent-core-spec/contracts/openapi.yaml`（`/api/v1/stream/sse`）  
  `specs/001-agent-core-spec/checklists/checklist.md`（CHK007、CHK021、CHK024）

#### V2-P1-002 `OPA` 策略引擎未接入
- 本项目证据：`src/main/java/com/example/agent/policy/PolicyEngine.java#evaluate` 为本地规则判断；`rg "OPA|opa" src/main/java src/test/java` 无命中
- Shannon 证据：`vendor/Shannon/config/opa/`、`vendor/Shannon/docs/agent-core-architecture.md`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-024）  
  `specs/001-agent-core-spec/contracts/openapi.yaml`（`/api/v1/policy/evaluate`）  
  `specs/001-agent-core-spec/checklists/checklist.md`（CHK044、CHK049）

### P2
#### V2-P2-001 记忆层次化标识与自动压缩触发缺失
- 本项目证据：`src/main/java/com/example/agent/memory/MemoryStore.java#compress` 仅在显式调用时压缩；`rg "recent|semantic" src/main/java src/test/java` 无命中
- Shannon 证据：`vendor/Shannon/docs/memory-system-architecture.md`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-006）  
  `specs/001-agent-core-spec/contracts/openapi.yaml`（`/api/v1/memory/search`、`/api/v1/memory/compress`）  
  `specs/001-agent-core-spec/checklists/checklist.md`（CHK009、CHK026）

#### V2-P2-002 多智能体 DAG/Supervisor/Handoff 未接入运行时
- 本项目证据：`src/main/java/com/example/agent/runtime/AgentRuntime.java#executeStep` 仅调用 `MultiAgentCoordinator`；`rg "HandoffService|AgentGraphExecutor|SupervisorCoordinator" src/main/java src/test/java` 仅命中类定义
- Shannon 证据：`vendor/Shannon/docs/multi-agent-workflow-architecture.md`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-021）  
  `specs/001-agent-core-spec/contracts/openapi.yaml`（无对应接口）  
  `specs/001-agent-core-spec/checklists/checklist.md`（CHK006、CHK017）

#### V2-P2-003 背压事件未落地
- 本项目证据：`rg "BACKPRESSURE_APPLIED|backpressure" src/main/java src/test/java` 仅命中 `src/main/java/com/example/agent/domain/event/EventType.java`
- Shannon 证据：`vendor/Shannon/docs/agent-core-architecture.md`
- 关联规范：`specs/001-agent-core-spec/spec.md`（FR-023）  
  `specs/001-agent-core-spec/contracts/openapi.yaml`（无对应接口）  
  `specs/001-agent-core-spec/checklists/checklist.md`（无对应条目）

#### V2-P2-004 `AGENT_STARTED/AGENT_COMPLETED` 未发射
- 本项目证据：`rg "AGENT_STARTED|AGENT_COMPLETED" src/main/java src/test/java` 仅命中 `src/main/java/com/example/agent/domain/event/EventType.java`
- Shannon 证据：`vendor/Shannon/docs/event-types.md`
- 关联规范：`specs/001-agent-core-spec/spec.md`（事件类型定义）  
  `specs/001-agent-core-spec/contracts/openapi.yaml`（无对应接口）  
  `specs/001-agent-core-spec/checklists/checklist.md`（CHK006、CHK017）

## Evidence Index
| 模块 | 本项目类/方法 | 关联事件/错误码 | Shannon 位置 |
| --- | --- | --- | --- |
| gateway | `src/main/java/com/example/agent/gateway/controller/TaskController.java#submitTask` | `TENANT_MISSING`、`UNAUTHORIZED` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go#SubmitTask` |
| orchestrator | `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java#createTask` | `WORKFLOW_STARTED`、`WORKFLOW_COMPLETED` | `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go#DAGWorkflow` |
| streaming api | `src/main/java/com/example/agent/streaming/EventStreamService.java#validateCursor` | `STREAM_GAP`、`ERROR_OCCURRED` | `vendor/Shannon/go/orchestrator/internal/streaming/manager.go#ReplaySince` |
| task history & timeline | `src/main/java/com/example/agent/history/EventLogService.java#onStreamEvent` | `event.persist.count`、`NOT_FOUND` | `vendor/Shannon/docs/task-history-and-timeline.md` |
| memory system | `src/main/java/com/example/agent/memory/MemoryStore.java#search` | 无事件发射 | `vendor/Shannon/docs/memory-system-architecture.md` |
| scheduled tasks | `src/main/java/com/example/agent/scheduler/ScheduleEngine.java#triggerExecution` | `SCHEDULE_TRIGGERED` | `vendor/Shannon/docs/scheduled-tasks.md` |
| authentication & multitenancy | `src/main/java/com/example/agent/auth/TenantContextFilter.java#filter` | `TENANT_MISSING`、`UNAUTHORIZED` | `vendor/Shannon/docs/authentication-and-multitenancy.md` |
| token budget tracking | `src/main/java/com/example/agent/budget/TokenBudgetManager.java#recordUsage` | `BUDGET_THRESHOLD`、`MODEL_FALLBACK_APPLIED` | `vendor/Shannon/docs/token-budget-tracking.md` |
| observability | `src/main/java/com/example/agent/observability/MetricsPublisher.java` | `task.submit.count`、`event.stream.count`、`budget.tokens.used` | `vendor/Shannon/docs/agent-core-architecture.md` |
| runtime | `src/main/java/com/example/agent/runtime/StepRuntimeService.java#startStep` | `STEP_STARTED`、`STEP_COMPLETED`、`STEP_FAILED` | `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go#DAGWorkflow` |
| planning/reflection | `src/main/java/com/example/agent/planning/PlannerService.java#plan` | `PLAN_GENERATED`、`PLAN_REVISED`、`REFLECTION_COMPLETED` | `vendor/Shannon/go/orchestrator/internal/activities/decompose.go#DecomposeTask` |
| tools | `src/main/java/com/example/agent/agentcore/ToolExecutor.java#execute` | `TOOL_INVOKED`、`TOOL_OBSERVATION`、`HOOK_BLOCKED` | `vendor/Shannon/docs/adding-custom-tools.md` |
| multi-agent | `src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java#coordinate` | `TEAM_RECRUITED`、`ROLE_ASSIGNED` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` |
| reasoning | `src/main/java/com/example/agent/reasoning/ThoughtTreeService.java#buildTree` | `THOUGHT_EXPANDED`、`DEBATE_ROUND_COMPLETED`、`RESEARCH_SOURCE_ADDED` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` |
| governance | `src/main/java/com/example/agent/governance/ReplayService.java#replay` | `REPLAY_STARTED`、`REPLAY_COMPLETED` | `vendor/Shannon/go/orchestrator/tools/replay/main.go` |
| enterprise | `src/main/java/com/example/agent/policy/PolicyEngine.java#evaluate` | `POLICY_DENIED`、`SANDBOX_DENIED` | `vendor/Shannon/docs/agent-core-architecture.md` |