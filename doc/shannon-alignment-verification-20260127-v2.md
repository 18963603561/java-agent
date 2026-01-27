# Shannon 对齐复核报告（Evidence-Complete Verification）- 20260127-v2

## 说明与范围
- 约束：仅做分析与产出文档，不修改代码、不执行测试。
- 检索范围：`src/main/java`、`src/test/java` 全量检索。
- 检索方式：使用 `rg` 进行关键词、类名、包名级别搜索（示例关键词：`Controller`、`SseStream`、`EventStreamService`、`Replay`、`MemoryStore`、`VectorStore`、`compress`、`MetricsPublisher`、`traceId`、`requestId`）。

## 1. 证据补齐复核（逐条处理“未见实现证据/需运行验证”结论）
> 结论态：`Found Evidence` / `Confirmed Missing` / `Requires Runtime Only`

| 结论项 | 状态 | 本项目证据 | Shannon 证据 | 说明 |
| --- | --- | --- | --- | --- |
| gateway 控制器（tasks） | `Found Evidence` | `src/main/java/com/example/agent/gateway/controller/TaskController.java#submitTask`、`#getTask`、`#listTasks` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go#SubmitTask`、`#GetTaskStatus` | 网关控制器已实现 |
| gateway 控制器（sse） | `Found Evidence` | `src/main/java/com/example/agent/streaming/SseStreamController.java#stream` | `vendor/Shannon/docs/streaming-api.md` | SSE 订阅接口已实现 |
| gateway 控制器（timeline/steps） | `Found Evidence` | `src/main/java/com/example/agent/gateway/controller/TimelineController.java#listEvents`、`#getTimeline`、`#listSteps` | `vendor/Shannon/docs/task-history-and-timeline.md` | 时间线与步骤查询已实现 |
| gateway 控制器（mcp/tools/list|call） | `Found Evidence` | `src/main/java/com/example/agent/gateway/controller/McpController.java#listTools`、`#callTool` | `vendor/Shannon/docs/adding-custom-tools.md` | MCP 工具列表与调用已实现 |
| gateway 控制器（policy/evaluate） | `Found Evidence` | `src/main/java/com/example/agent/gateway/controller/PolicyController.java#evaluate` | `vendor/Shannon/docs/agent-core-architecture.md` | 策略评估接口已实现 |
| gateway 控制器（replay） | `Found Evidence` | `src/main/java/com/example/agent/gateway/controller/ReplayController.java#replay` | `vendor/Shannon/go/orchestrator/tools/replay/main.go` | 回放接口已实现 |
| streaming api：断线续传（cursor 校验） | `Found Evidence` | `src/main/java/com/example/agent/streaming/SseStreamController.java#stream`、`EventStreamService#validateCursor` | `vendor/Shannon/docs/streaming-api.md` | 支持 `last_event_id` 校验 |
| streaming api：replay since / backfill | `Confirmed Missing` | 全量检索未发现从历史事件回放到 SSE 的实现（关键词：`replay`、`ReplaySince`、`last_event`、`cursor`） | `vendor/Shannon/go/orchestrator/internal/streaming/manager.go#ReplaySince` | 当前只做游标校验，未回放历史事件 |
| memory system：MemoryStore/VectorStore | `Found Evidence` | `src/main/java/com/example/agent/memory/MemoryStore.java`、`VectorStore.java`、`QdrantVectorStore.java` | `vendor/Shannon/docs/memory-system-architecture.md` | 基础记忆存取与向量检索存在 |
| memory system：compressed | `Found Evidence` | `src/main/java/com/example/agent/memory/MemoryStore.java#compress`、`MemoryController.java#compress` | `vendor/Shannon/docs/memory-system-architecture.md` | 提供压缩接口 |
| memory system：recent/semantic 分层 | `Confirmed Missing` | 全量检索未发现显式 `recent/semantic` 层次结构与策略类 | `vendor/Shannon/docs/memory-system-architecture.md` | 仅看到 `compressed` 层 |
| memory system：自动压缩触发 | `Confirmed Missing` | 全量检索未发现自动触发逻辑或阈值策略 | `vendor/Shannon/docs/memory-system-architecture.md` | 目前仅手动接口压缩 |
| observability：指标名（task.submit.count/event.stream.count/budget.tokens.used） | `Found Evidence` | `TaskOrchestrator.java`、`EventStreamService.java`、`TokenBudgetManager.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 指标名存在 |
| observability：traceId/requestId 贯通 | `Found Evidence` | `TenantResolver.java`、`TenantContextFilter.java`、`ApiResponse.java`、`ErrorResponse.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 请求头贯通并写入响应 |
| observability：导出与追踪集成 | `Requires Runtime Only` | `MetricsPublisher.java`、`TracingPublisher.java`（未见使用点） | `vendor/Shannon/docs/agent-core-architecture.md` | 需运行验证导出与 tracing 生效 |
| authentication & multitenancy | `Found Evidence` | `ApiKeyAuthenticator.java#authenticate`、`TenantContextFilter.java#filter`、`TenantResolver.java#resolve` | `vendor/Shannon/docs/authentication-and-multitenancy.md` | 鉴权与租户解析已实现 |
| scheduled tasks | `Found Evidence` | `ScheduleController.java`、`ScheduleManager.java`、`ScheduleEngine.java` | `vendor/Shannon/docs/scheduled-tasks.md` | 调度接口与引擎已实现 |
| history & timeline | `Found Evidence` | `EventLogService.java`、`TimelineService.java`、`TimelineController.java` | `vendor/Shannon/docs/task-history-and-timeline.md` | 事件持久化与时间线聚合已实现 |
| governance：回放 | `Found Evidence` | `ReplayService.java`、`ReplayController.java` | `vendor/Shannon/go/orchestrator/tools/replay/main.go` | 回放逻辑已实现 |
| enterprise：policy/sandbox | `Found Evidence` | `PolicyEngine.java`、`PolicyController.java`、`WasiSandboxExecutor.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 策略与沙箱入口已实现 |

## 2. 16 模块对齐矩阵（补齐证据链接）
| 模块 | 状态 | 本项目证据 | Shannon 证据 | 说明 |
| --- | --- | --- | --- | --- |
| gateway | `Partially` | `TaskController.java`、`McpController.java`、`TimelineController.java` | `vendor/Shannon/go/orchestrator/cmd/gateway/internal/handlers/task.go` | 接口齐全但协议实现差异待对齐 |
| orchestrator | `Partially` | `TaskOrchestrator.java`、`WorkflowRouter.java` | `vendor/Shannon/go/orchestrator/internal/activities/agent.go` | 未见工作流引擎对齐 |
| streaming api | `Partially` | `SseStreamController.java`、`EventStreamService.java` | `vendor/Shannon/docs/streaming-api.md` | 缺少 replay since |
| task history & timeline | `Partially` | `EventLogService.java`、`TimelineService.java` | `vendor/Shannon/docs/task-history-and-timeline.md` | 派生规则差异 |
| memory system | `Partially` | `MemoryStore.java`、`VectorStore.java`、`QdrantVectorStore.java` | `vendor/Shannon/docs/memory-system-architecture.md` | 缺少 recent/semantic 分层 |
| scheduled tasks | `Partially` | `ScheduleManager.java`、`ScheduleEngine.java` | `vendor/Shannon/docs/scheduled-tasks.md` | 与真实调度引擎差异 |
| authentication & multitenancy | `Partially` | `ApiKeyAuthenticator.java`、`TenantContextFilter.java` | `vendor/Shannon/docs/authentication-and-multitenancy.md` | 认证体系差异 |
| token budget tracking | `Partially` | `TokenBudgetManager.java`、`BudgetController.java` | `vendor/Shannon/docs/token-budget-tracking.md` | 计量精度差异 |
| observability | `Partially` | `MetricsPublisher.java`、`TracingPublisher.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 指标与追踪集成不完整 |
| runtime | `Partially` | `AgentRuntime.java`、`StepRuntimeService.java` | `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go` | 状态机差异 |
| planning/reflection | `Partially` | `PlannerService.java`、`ReflectionService.java` | `vendor/Shannon/go/orchestrator/internal/activities/decompose.go`、`workflows/patterns/reflection.go` | 策略差异 |
| tools | `Partially` | `ToolExecutor.java`、`McpToolClient.java`、`EnforcementGateway.java` | `vendor/Shannon/docs/adding-custom-tools.md` | MCP 行为差异 |
| multi-agent | `Partially` | `MultiAgentCoordinator.java` | `vendor/Shannon/docs/multi-agent-workflow-architecture.md` | 编排策略差异 |
| reasoning | `Partially` | `ThoughtTreeService.java`、`DebateCoordinator.java`、`ResearchPipeline.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 深度研究与工具协同差异 |
| governance | `Partially` | `ReplayService.java`、`RateLimitService.java` | `vendor/Shannon/docs/agent-core-architecture.md` | 回放与背压矩阵差异 |
| enterprise | `Partially` | `PolicyEngine.java`、`WasiSandboxExecutor.java` | `vendor/Shannon/docs/agent-core-architecture.md` | OPA 与 WASI 接入差异 |

## 3. Evidence Index（模块 -> 类/方法 -> 事件/错误码 -> Shannon 位置）
| 模块 | 类/方法 | 事件/错误码 | Shannon 位置 |
| --- | --- | --- | --- |
| gateway | `TaskController#submitTask` | `TENANT_MISSING` | `cmd/gateway/internal/handlers/task.go#SubmitTask` |
| gateway | `SseStreamController#stream` | `STREAM_GAP`、`ERROR_OCCURRED` | `docs/streaming-api.md` |
| gateway | `McpController#callTool` | `TOOL_INVOKED`、`TOOL_OBSERVATION`、`TOOL_ERROR`、`MCP_UNAVAILABLE` | `internal/activities/stream_events.go#StreamEventToolInvoked` |
| gateway | `PolicyController#evaluate` | `POLICY_DENIED` | `docs/agent-core-architecture.md` |
| gateway | `ReplayController#replay` | `REPLAY_STARTED`、`REPLAY_COMPLETED`、`REPLAY_NOT_FOUND` | `tools/replay/main.go` |
| streaming api | `EventStreamService#onStreamEvent` | `event.stream.count` | `docs/streaming-api.md` |
| history/timeline | `EventLogService#onStreamEvent` | `event.persist.count` | `docs/task-history-and-timeline.md` |
| memory | `MemoryStore#save` | `MEMORY_SAVED`（事件类型枚举） | `docs/memory-system-architecture.md` |
| scheduled tasks | `ScheduleEngine#run` | `SCHEDULE_TRIGGERED` | `docs/scheduled-tasks.md` |
| auth | `ApiKeyAuthenticator#authenticate` | `UNAUTHORIZED`、`AUTH_FAILED` | `docs/authentication-and-multitenancy.md` |
| budget | `TokenBudgetManager#recordUsage` | `budget.tokens.used`、`BUDGET_THRESHOLD` | `docs/token-budget-tracking.md` |
| observability | `TracingPublisher#currentTraceId` | `traceId` | `docs/agent-core-architecture.md` |
| runtime | `AgentRuntime#run` | `PLAN_GENERATED`、`REFLECTION_COMPLETED`、`LLM_OUTPUT` | `internal/activities/agent.go#ExecuteAgent` |
| planning/reflection | `PlannerService#plan`、`ReflectionService#reflect` | `PLAN_REVISED`、`REFLECTION_STARTED` | `activities/decompose.go#DecomposeTask`、`workflows/patterns/reflection.go#ReflectOnResult` |
| tools | `ToolExecutor#execute` | `tool.call.count` | `docs/adding-custom-tools.md` |
| multi-agent | `MultiAgentCoordinator#coordinate` | `TEAM_RECRUITED`、`ROLE_ASSIGNED` | `docs/multi-agent-workflow-architecture.md` |
| reasoning | `ResearchPipeline#run` | `RESEARCH_SOURCE_ADDED` | `docs/agent-core-architecture.md` |
| governance | `ReplayService#replay` | `replay.count` | `tools/replay/main.go` |
| enterprise | `WasiSandboxExecutor#execute` | `SANDBOX_VIOLATION` | `docs/agent-core-architecture.md` |

## 4. 缺陷清单（基于证据补齐后的重新评级）
### P0
- 无已确认 P0

### P1
#### VF-P1-001：SSE 断线续传缺少 replay since
- 复现路径：`/api/v1/stream/sse` 带 `last_event_id` 订阅
- 影响范围：`streaming api`、客户端事件一致性
- 本项目证据：`SseStreamController#stream` 仅做游标校验；`EventStreamService#stream` 未回放历史事件
- Shannon 证据：`vendor/Shannon/go/orchestrator/internal/streaming/manager.go#ReplaySince`
- 关联规范：`specs/001-agent-core-spec/spec.md` 事件流模块；`specs/001-agent-core-spec/contracts/openapi.yaml`
- 建议修复：补齐基于 `last_event_id` 的历史回放或与 `replay` 结合的流式重放机制，并补充 SSE 回放测试用例。

### P2
#### VF-P2-001：记忆分层缺少 recent/semantic 层与自动压缩触发
- 复现路径：`/api/v1/memory/save`、`/api/v1/memory/search`、`/api/v1/memory/compress`
- 影响范围：`memory system`；长上下文质量
- 本项目证据：`MemoryStore#compress` 仅手动压缩，未见 recent/semantic 分层策略类
- Shannon 证据：`vendor/Shannon/docs/memory-system-architecture.md`
- 关联规范：`specs/001-agent-core-spec/spec.md`
- 建议修复：补齐分层记忆策略与自动压缩触发条件，并补测压缩触发与检索分层行为。

#### VF-P2-002：追踪组件存在但未见实际调用
- 复现路径：运行任务后查看 traceId 与 tracing 关联（需运行验证）
- 影响范围：`observability`
- 本项目证据：`TracingPublisher.java` 未见调用点；`TenantResolver` 仅从请求头写入 traceId
- Shannon 证据：`vendor/Shannon/docs/agent-core-architecture.md`
- 关联规范：`specs/001-agent-core-spec/spec.md`
- 建议修复：在关键路径绑定 tracing span 并统一注入 traceId，补测 traceId 与日志/事件一致性。

## 5. 闭环判断（更新版）
结论：本项目具备 `planner → step → tool → observation → reflection → finalize` 的闭环实现证据，但流式回放（replay since）与记忆分层策略仍与 Shannon 存在差距，导致一致性与可追溯性不足。
