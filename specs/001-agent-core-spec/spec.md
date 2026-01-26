# Feature Specification: Java Shannon Agent Orchestrator Core Specification

**Feature Branch**: `001-agent-core-spec`  
**Created**: 2026-01-25  
**Status**: Draft  
**Input**: User description: "从零生成一份可落地的 Feature Specification（面向 Java 实现），严格基于以下本地材料，并在 spec 中明确“模块->接口->数据结构->事件流->存储”的落点：输入材料（均为本仓库本地文件）：vendor/Shannon/README.md、vendor/Shannon/ROADMAP.md、vendor/ai-agent-book/zh、vendor/Shannon/docs/（重点阅读：agent-core-architecture、streaming-api、event-types、task-history-and-timeline、memory-system-architecture、scheduled-tasks、authentication-and-multitenancy、token-budget-tracking）、vendor/data/ 下智能体流程图相关文件；输出要求：规格必须包含核心概念与边界、模块划分、关键接口（Java interface）、DTO/事件类型定义、Streaming SSE API 定义、任务历史与时间线模型、Memory 抽象与落盘策略、定时任务模型、多租户/鉴权策略、Token Budget 计量与存储、错误码与异常策略、观测指标（Micrometer/OTel）；规格必须包含 MVP 与迭代版本（V1/V2）拆分，并写清每个版本的验收标准；明确建议的包结构（src/main/java/...）与关键类名（英文命名）"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 任务执行与事件流订阅 (Priority: P1)

平台接收用户任务请求，完成编排与执行，并提供实时事件流订阅。

**Why this priority**: 没有任务执行与事件流，系统不具备最小可用价值。

**Independent Test**: 提交任务后可持续接收事件，并在任务完成后获取最终结果与状态。

**Acceptance Scenarios**:

1. **Given** 用户已鉴权且提供合法任务请求，**When** 提交任务，**Then** 返回可查询与订阅的任务标识。
2. **Given** 任务处于运行中，**When** 订阅事件流，**Then** 按序收到事件并支持断线续传。

---

### User Story 2 - 任务历史与时间线审计 (Priority: P2)

平台提供任务历史、事件审计与时间线查询能力，支持排障与合规审计。

**Why this priority**: 企业级平台必须可追溯，任务历史与时间线是排障与审计基础。

**Independent Test**: 对已完成任务可查询历史事件与时间线摘要，并与结果一致。

**Acceptance Scenarios**:

1. **Given** 任务已完成，**When** 查询任务事件，**Then** 返回持久化事件列表。
2. **Given** 任务已完成，**When** 查询时间线摘要，**Then** 返回关键步骤序列。

---

### User Story 3 - 多租户隔离与预算治理 (Priority: P3)

平台在多租户环境中隔离数据，并对预算使用进行计量与记录。

**Why this priority**: 多租户隔离与预算治理是企业级平台上线前提。

**Independent Test**: 不同租户访问同一任务标识时不可跨租户读取；预算记录可查询。

**Acceptance Scenarios**:

1. **Given** 不同租户用户，**When** 访问非本租户任务，**Then** 返回未找到且不泄漏存在性。
2. **Given** 任务执行完成，**When** 查询预算统计，**Then** 返回可核对的计量与成本数据。

---

### Edge Cases

- 流式订阅使用过期 `last_event_id` 时，如何处理重放与缺失事件。
- 任务执行中断或超时后，如何保证事件与任务状态一致。
- 预算为零或仅工具调用场景下，如何处理计量与记录。
- 多租户上下文缺失时如何拒绝请求并返回统一错误（`HTTP 400` + `TENANT_MISSING`）。

## Requirements *(mandatory)*

### 宪章约束（必填）

- 技术栈必须使用 `Java 17`、`Spring Boot 3`、`WebFlux`，流式输出使用 `SSE`。
- 对外接口与事件输出必须使用统一的输出对象结构与错误语义。
- 日志必须使用 `slf4j`，关键路径有 `info`，异常有 `error` 且包含上下文与堆栈。
- 多租户与鉴权必须预留扩展点（接口、过滤器、上下文）。
- 令牌预算跟踪必须包含计量点、存储接口与可观测性指标。
- 如涉及 `Java` 代码，注释必须为中文，类名、方法名、变量名、包名、配置 `key` 必须为英文。
- 代码结构、模块划分与核心接口命名必须尽量对齐 `vendor/Shannon` 的术语体系。
- 规格必须提供 `Shannon` 源模块/目录/文件到 `Java` 模块/`package`/`interface`/
  `class` 的映射表，并标注差异原因与替代设计。
- 技术选型必须优先使用 `Spring` 生态组件，非 `Spring` 组件需给出不可满足原因与风险评估。
- 新增依赖尽量通过 `Spring Boot Starter` 管理版本，避免显式版本锁定。

### Functional Requirements

- **FR-001**: 系统必须接收任务请求并返回唯一任务标识与可订阅的执行标识。
- **FR-002**: 系统必须提供实时事件订阅通道，支持按事件类型过滤与断线续传。
- **FR-003**: 系统必须定义统一事件模型，事件序列具备严格顺序保证。
- **FR-004**: 系统必须持久化关键事件并提供历史查询能力。
- **FR-005**: 系统必须提供时间线生成与查询能力，支持摘要与完整模式。
- **FR-006**: 系统必须提供多层记忆存取与检索接口，支持上下文注入。
- **FR-007**: 系统必须提供定时任务的创建、暂停、恢复、删除与执行历史查询。
- **FR-008**: 系统必须提供多租户隔离与鉴权扩展点，确保跨租户不可访问。
- **FR-009**: 系统必须提供预算计量、记录与成本汇总能力。
- **FR-010**: 系统必须提供统一输出结构与错误码策略。
- **FR-011**: 系统必须输出任务、事件、预算、存储与调度的观测指标。
- **FR-012**: 系统必须给出模块到接口到数据结构到事件流到存储的落点映射。
- **FR-013**: 规格必须提供 `Shannon` 源模块/目录/文件到 `Java` 模块/`package`/
  `interface`/`class` 的映射表，并作为实现阶段唯一设计依据。
- **FR-014**: 对无法 `1:1` 对齐的模块或接口，必须说明差异原因、`Java` 侧替代设计与兼容边界。

### Key Entities *(include if feature involves data)*

- **`Task`**: 任务执行实体，包含请求、状态、结果、预算统计与 `tenantId`。
- **`WorkflowEvent`**: 事件实体，包含 `eventId`、事件类型、序列号、时间戳、`tenantId` 与消息负载。
- **`TaskTimeline`**: 时间线实体，来源于工作流历史或事件汇总，包含 `tenantId`。
- **`MemoryRecord`**: 记忆实体，包含会话上下文、语义向量、压缩摘要与 `tenantId`。
- **`Schedule`**: 定时任务实体，包含 `cron`、预算、执行策略与 `tenantId`。
- **`TokenUsage`**: 预算计量实体，包含输入输出 `token`、成本与 `tenantId`。
- **`TenantContext`**: 多租户上下文，包含租户、用户与权限信息。

### 功能需求验收要点

- FR-001: 提交任务后返回唯一任务标识与可订阅执行标识。
- FR-002: 事件订阅支持类型过滤与断线续传。
- FR-003: 事件序列具备单调递增序列号并可重放。
- FR-004: 关键事件可被查询且包含必要字段。
- FR-005: 时间线可输出摘要与完整模式。
- FR-006: 记忆检索可返回近期与语义结果。
- FR-007: 定时任务支持创建、暂停、恢复、删除与历史查询。
- FR-008: 跨租户访问被拒绝且不泄漏存在性。
- FR-009: 预算计量与成本汇总可查询且无重复记录。
- FR-010: 所有响应遵循统一输出结构与错误码。
- FR-011: 关键指标可在监控系统中观察。
- FR-012: 模块与接口、数据结构、事件流与存储的对应关系清晰可追溯。
- FR-013: 映射表完整覆盖 `Shannon` 源模块并指向对应 `Java` 设计落点。
- FR-014: 差异说明包含原因、替代设计与兼容边界。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95% 的任务在提交后 2 秒内返回任务标识与订阅入口。
  - 统计窗口: `1h`
  - 样本规模: `>= 200` 次任务提交
  - 基准环境假设: 单机 `4C/8G`，`JDK 17`，`Spring Boot 3`，容器化部署，
    `Redis 7.2`，`PostgreSQL 15`，本地千兆网络
  - 指标口径: `P95` 提交耗时 <= 2 秒；成功定义为返回 `taskId` 与订阅入口；
    超时定义为响应耗时 > 2 秒
- **SC-002**: 断线恢复后 5 秒内继续接收事件流。
  - 统计窗口: `1h`
  - 样本规模: `>= 50` 条断线续传连接
  - 基准环境假设: 单机 `4C/8G`，`JDK 17`，`Spring Boot 3`，容器化部署，
    `Redis 7.2`，`PostgreSQL 15`，本地千兆网络
  - 指标口径: `P95` 断线恢复耗时 <= 5 秒；成功定义为续传后接收连续事件；
    超时定义为恢复耗时 > 5 秒
- **SC-003**: 关键事件持久化成功率达到 99.9%。
  - 统计窗口: `1h`
  - 样本规模: `>= 1000` 条关键事件
  - 基准环境假设: 单机 `4C/8G`，`JDK 17`，`Spring Boot 3`，容器化部署，
    `Redis 7.2`，`PostgreSQL 15`，本地千兆网络
  - 指标口径: 成功率 = 成功写入 `PostgreSQL` 的关键事件数 / 总关键事件数；
    失败定义为触发 `EVENT_PERSIST_FAILED`
- **SC-004**: 任务历史查询在 95% 场景下 3 秒内返回。
  - 统计窗口: `1h`
  - 样本规模: `>= 200` 次历史查询
  - 基准环境假设: 单机 `4C/8G`，`JDK 17`，`Spring Boot 3`，容器化部署，
    `Redis 7.2`，`PostgreSQL 15`，本地千兆网络
  - 指标口径: `P95` 查询耗时 <= 3 秒；成功定义为返回 `200` 且包含合法结果；
    超时定义为响应耗时 > 3 秒
- **SC-005**: 越权访问返回未找到或拒绝，零数据泄漏。
  - 统计窗口: `1h`
  - 样本规模: `>= 50` 次跨租户访问
  - 基准环境假设: 单机 `4C/8G`，`JDK 17`，`Spring Boot 3`，容器化部署，
    `Redis 7.2`，`PostgreSQL 15`，本地千兆网络
  - 指标口径: 成功率 = 跨租户请求返回 `NOT_FOUND` 或 `FORBIDDEN` 且不含资源内容；
    超时定义为响应耗时 > 3 秒
- **SC-006**: 预算记录覆盖率达到 100%，不出现重复计量。
  - 统计窗口: `1h`
  - 样本规模: `>= 200` 次计量输入
  - 基准环境假设: 单机 `4C/8G`，`JDK 17`，`Spring Boot 3`，容器化部署，
    `Redis 7.2`，`PostgreSQL 15`，本地千兆网络
  - 指标口径: 覆盖率 = 预算记录数 / 应记录数；成功定义为覆盖率 100%
    且 `usageId` 无重复

## 业务视角摘要

本规格定义了一个可落地的 `Java` 版智能体编排核心能力集，覆盖任务执行、
事件流、审计时间线、记忆系统、定时任务、多租户与预算治理，并提供可观测性。该能力集
面向企业级场景，强调可追溯、可计量与可扩展。

## 核心概念与边界

- **任务**: 用户请求触发的可追踪执行过程。
- **事件流**: 执行过程中的可订阅事件序列。
- **时间线**: 基于工作流历史派生的审计视图。
- **记忆系统**: 会话与语义记忆，用于上下文注入。
- **调度系统**: 定时执行任务并记录执行历史与成本。
- **多租户隔离**: 所有数据与访问按租户隔离。
- **预算治理**: 执行过程的 `token` 与成本计量。

**边界约束**:
- 不包含模型训练与微调能力。
- 不提供终端用户通用界面。
- 不保证覆盖所有第三方工具与外部系统。

## 流程图节点与模块映射

以下映射基于 `vendor/data/` 的流程图节点与阶段，标明在 `Java` 模块中的落点。

- **用户输入/意图识别/抽象层级判断** -> `IntentAnalyzer` 输出 `TaskIntent`。
- **原子性检验/递归分解** -> `TaskDecomposer` 输出 `AtomicTaskList`。
- **任务调度与优先级评分** -> `TaskScheduler`、`PriorityScorer` 输出 `ScheduledTask`。
- **可行性推演与元决策** -> `FeasibilityEvaluator` 输出 `PlanDecision`。
- **规划与执行循环** -> `Planner`、`ExecutionEngine` 输出 `ExecutionPlan`。
- **工具调用与观察** -> `ToolExecutor`、`ObservationCollector` 输出 `ToolResult`。
- **反馈与记忆落盘** -> `MemoryStore` 输出 `MemoryRecord` 并写入存储。
- **审计与观测** -> `EventLogService`、`MetricsPublisher` 输出 `EventLogRecord`。

## 模块划分

1. **网关模块（`gateway`）**: 任务提交、状态查询、事件订阅、时间线与调度接口。
2. **编排核心模块（`orchestrator`）**: 任务编排、事件发布、预算治理与状态管理。
3. **事件流模块（`streaming api`）**: 事件过滤、断线续传与流式发布。
4. **历史与时间线模块（`task history & timeline`）**: 事件持久化与时间线派生。
5. **记忆模块（`memory system`）**: 会话记忆、语义记忆、压缩记忆与检索策略。
6. **调度模块（`scheduled tasks`）**: 定时任务创建、管理与执行历史。
7. **鉴权与租户模块（`authentication & multitenancy`）**: 鉴权入口与租户上下文解析。
8. **预算与成本模块（`token budget tracking`）**: `token` 计量、成本聚合与阈值事件。
9. **观测模块（`observability`）**: 指标、日志与链路追踪。

### 模块边界与职责约束
- 内部事件总线采用 `Spring ApplicationEvent` + `Reactor Sinks`，由 `TaskOrchestrator` 发布 `StreamEvent`，
  `EventStreamService` 与 `EventLogService` 订阅并桥接到 `SSE` 与持久化。

- `orchestrator` 仅负责任务编排与状态机推进，生成事件序列号并驱动流程，不直接执行工具调用。
- `agent core` 负责工具调用、策略拦截与沙箱扩展，不负责任务调度或历史持久化。
- `streaming api` 仅负责实时事件分发与断线续传，不负责事件持久化与时间线生成。
- `task history & timeline` 仅负责事件持久化与时间线派生，时间线仅基于持久化事件生成。
- `memory system` 仅负责会话/语义/压缩记忆，不保存任务事件历史。
- `authentication & multitenancy` 仅负责鉴权与租户上下文解析，禁止嵌入业务流程判断。
- `token budget tracking` 仅负责计量、聚合与阈值事件发布，不负责鉴权与事件分发。

## 模块到接口到数据结构到事件流到存储落点

| 模块 | 接口 | 数据结构 | 事件流 | 存储落点 |
| --- | --- | --- | --- | --- |
| 网关模块 | `TaskSubmissionService` | `TaskRequest`、`TaskResponse` | `WORKFLOW_STARTED` | `task_executions` |
| 事件流模块 | `EventStreamService` | `StreamEvent`、`StreamCursor` | `LLM_PARTIAL`、`LLM_OUTPUT` | `Redis Stream` |
| 历史与时间线模块 | `EventLogService` | `EventLogRecord`、`TimelineRecord` | `TOOL_INVOKED`、`ERROR_OCCURRED` | `event_logs` |
| 记忆模块 | `MemoryStore` | `MemoryRecord`、`MemoryChunk` | `MEMORY_SAVED` | `sessions`、`vector_store` |
| 调度模块 | `ScheduleManager` | `ScheduleSpec`、`ScheduleExecutionRecord` | `SCHEDULE_TRIGGERED` | `scheduled_tasks` |
| 预算与成本模块 | `TokenBudgetManager` | `TokenUsageRecord`、`TokenUsageSummary` | `BUDGET_THRESHOLD` | `token_usage` |
| 鉴权与租户模块 | `AuthService` | `TenantContext`、`UserContext` | `AUTH_FAILED` | `auth.tenants` |

## `Shannon` 模块对齐映射表

| `Shannon` 源模块/目录/文件 | `Java` 模块/`package`/`interface`/`class` | 对齐说明 |
| --- | --- | --- |
| `vendor/Shannon/README.md` | `com.example.agent.orchestrator` / `TaskOrchestrator`、`WorkflowRouter` | 对齐 `orchestrator` 任务编排能力 |
| `vendor/Shannon/docs/agent-core-architecture.md` | `com.example.agent.agentcore` / `EnforcementGateway`、`ToolRegistry`、`ToolCache`、`ToolExecutor`、`SandboxExecutor` | 对齐 `agent core` 执行与工具体系 |
| `vendor/Shannon/docs/streaming-api.md` | `com.example.agent.streaming` / `EventStreamService`、`SseStreamController`、`StreamEvent` | 对齐 `streaming api` 与 `SSE` |
| `vendor/Shannon/docs/event-types.md` | `com.example.agent.domain.event` / `EventType`、`StreamEvent` | 对齐 `event types` |
| `vendor/Shannon/docs/task-history-and-timeline.md` | `com.example.agent.history` / `EventLogService`、`TimelineService`、`EventLogRepository` | 对齐 `task history & timeline` |
| `vendor/Shannon/docs/memory-system-architecture.md` | `com.example.agent.memory` / `MemoryStore`、`VectorStore`、`MemoryRecord`、`MemoryQuery` | 对齐 `memory system` |
| `vendor/Shannon/docs/scheduled-tasks.md` | `com.example.agent.scheduler` / `ScheduleManager`、`ScheduleRepository`、`ScheduleExecutionRepository` | 对齐 `scheduled tasks` |
| `vendor/Shannon/docs/authentication-and-multitenancy.md` | `com.example.agent.auth` / `AuthService`、`ApiKeyAuthenticator`、`TenantResolver`、`TenantContextFilter` | 对齐 `authentication & multitenancy` |
| `vendor/Shannon/docs/token-budget-tracking.md` | `com.example.agent.budget` / `TokenBudgetManager`、`TokenUsageRepository`、`CostCalculator` | 对齐 `token budget tracking` |

## 对齐差异与替代设计

- `Shannon` 为多语言分层架构（`Go`/`Rust`/`Python`），`Java` 版本在单进程内以
  模块化方式承载 `orchestrator` 与 `agent core`，跨语言边界转为模块内接口调用。
- `WASI` 沙箱在 `Java` 侧保留 `SandboxExecutor` 扩展点，默认不内置运行时；
  若引入外部沙箱必须在规格中补充兼容边界。
- `gRPC` 与 `HTTP` 边界在 `Java` 侧以 `WebFlux` 接口与 `Reactor` 流式抽象统一，
  仍保持事件类型与持久化语义一致。
- 任何偏离映射表的新增抽象必须先更新本规格并说明原因。

## 关键接口（`Java` `interface`）

```java
public interface TaskSubmissionService {
    TaskResponse submitTask(TaskRequest request, TenantContext tenantContext);
}

public interface TaskQueryService {
    TaskStatusResponse getTask(TaskId taskId, TenantContext tenantContext);
    TaskListResponse listTasks(TaskQuery query, TenantContext tenantContext);
}

public interface EventStreamService {
    Flux<StreamEvent> stream(TaskStreamRequest request, TenantContext tenantContext);
}

public interface EventLogService {
    void append(EventLogRecord record, TenantContext tenantContext);
    EventLogPage query(EventQuery query, TenantContext tenantContext);
}

public interface TimelineService {
    TimelineResponse buildTimeline(TimelineRequest request, TenantContext tenantContext);
}

public interface MemoryStore {
    void save(MemoryRecord record, TenantContext tenantContext);
    MemorySearchResult search(MemoryQuery query, TenantContext tenantContext);
    void compress(CompressionRequest request, TenantContext tenantContext);
}

public interface ScheduleManager {
    ScheduleResponse create(ScheduleSpec spec, TenantContext tenantContext);
    ScheduleResponse update(ScheduleSpec spec, TenantContext tenantContext);
    void pause(ScheduleId scheduleId, TenantContext tenantContext);
    void resume(ScheduleId scheduleId, TenantContext tenantContext);
    void delete(ScheduleId scheduleId, TenantContext tenantContext);
    SchedulePage list(ScheduleQuery query, TenantContext tenantContext);
}

public interface TokenBudgetManager {
    void recordUsage(TokenUsageInput input, TenantContext tenantContext);
    TokenUsageSummary aggregate(TaskId taskId, TenantContext tenantContext);
}
```

## `DTO` 与事件类型定义

- **`StreamEvent`**
  - `eventId`、`schemaVersion`、`workflowId`、`type`、`agentId`、`message`、`timestamp`、`seq`、
    `streamId`、`tenantId`、`payload`
- **`StreamCursor`**
  - `streamId`、`seq`、`eventId`
- **`TaskStreamRequest`**
  - `workflowId`、`types`、`lastEventId`、`cursor`
- **`TaskRequest`**
  - `query`、`sessionId`、`context`、`idempotencyKey`
- **`TaskResponse`**
  - `taskId`、`workflowId`、`status`
- **`TaskStatusResponse`**
  - `taskId`、`workflowId`、`status`、`updatedAt`
- **`TaskQuery`**
  - `status`、`cursor`、`size`
- **`TaskListResponse`**
  - `tasks`（列表为 `TaskStatusResponse`）、`nextCursor`、`hasMore`、`total`
- **`EventLogRecord`**
  - `eventId`、`workflowId`、`type`、`timestamp`、`tenantId`、`payload`
- **`EventQuery`**
  - `workflowId`、`types`、`cursor`、`size`、`from`、`to`
- **`EventLogPage`**
  - `events`、`nextCursor`、`hasMore`、`total`
- **`TimelineRecord`**
  - `eventId`、`type`、`timestamp`、`payload`
- **`TimelineRequest`**
  - `workflowId`、`mode`、`persist`
- **`TimelineResponse`**
  - `workflowId`、`mode`、`events`、`stats`
- **`ScheduleSpec`**
  - `scheduleId`、`cron`、`timezone`、`idempotencyKey`
- **`ScheduleResponse`**
  - `scheduleId`、`status`、`nextRunAt`
- **`ScheduleExecutionRecord`**
  - `executionId`、`scheduleId`、`status`、`startedAt`、`completedAt`、`tenantId`
- **`ScheduleQuery`**
  - `status`、`cursor`、`size`、`from`、`to`
- **`SchedulePage`**
  - `schedules`、`nextCursor`、`hasMore`、`total`
- **`TokenUsageRecord`**
  - `recordId`、`taskId`、`agentId`、`model`、`provider`、`inputTokens`、`outputTokens`、`totalTokens`、
    `costUsd`、`tenantId`
- **`TokenUsageInput`**
  - `usageId`、`taskId`、`agentId`、`model`、`provider`、`inputTokens`、`outputTokens`、`totalTokens`、
    `costUsd`、`tenantId`

**事件类型（按类别）**:

- 核心流程: `WORKFLOW_STARTED`、`WORKFLOW_COMPLETED`、`AGENT_STARTED`、`AGENT_COMPLETED`、`ERROR_OCCURRED`
- LLM: `LLM_PROMPT`、`LLM_PARTIAL`、`LLM_OUTPUT`
- 工具: `TOOL_INVOKED`、`TOOL_OBSERVATION`、`TOOL_ERROR`
- 多智能体: `DELEGATION`、`TEAM_RECRUITED`、`TEAM_RETIRED`、`MESSAGE_SENT`、`MESSAGE_RECEIVED`、`ROLE_ASSIGNED`
- 进度与状态: `PROGRESS`、`DATA_PROCESSING`、`WAITING`、`TEAM_STATUS`、`WORKSPACE_UPDATED`
- 人机交互: `APPROVAL_REQUESTED`、`APPROVAL_DECISION`

### 事件模型与顺序约束

- `eventId` 为事件唯一键，格式为 `streamId:seq`，用于 `SSE` 的 `id` 字段与去重。
- `seq` 以 `workflowId` 为范围单调递增，由 `orchestrator` 生成并保证唯一。
- `schemaVersion` 必填，默认 `v1`，用于事件演进兼容。
- 所有事件必须携带 `tenantId`，事件查询与订阅按 `tenantId` 过滤。
- 生命周期顺序：`WORKFLOW_STARTED` 先于任何业务事件，`WORKFLOW_COMPLETED` 作为最终事件。
- `ERROR_OCCURRED` 可插入任何阶段，但必须在最终事件前出现。
- 同一 `agentId` 的 `LLM_PARTIAL` 必须在对应 `LLM_OUTPUT` 之前出现。

## 流式 `SSE` 接口定义

- **GET** `/api/v1/stream/sse?workflow_id={id}&types={csv}&last_event_id={cursor}`
- 支持 `Last-Event-ID` Header 断线续传，优先级高于 `last_event_id` 参数。
- `last_event_id` 支持 `eventId` 或 `streamId:seq`；仅提供数值 `seq` 时以 `workflow_id` 为范围解析。
- `SSE` 事件 `id` 使用 `eventId`，`event` 使用 `type`，`data` 为 `StreamEvent` 序列化结果。
- 鉴权失败返回 `401/403` 与 `ErrorResponse`，不建立流。
- `LLM_PARTIAL` 映射 `thread.message.delta`，字段包含 `delta`。
- `LLM_OUTPUT` 映射 `thread.message.completed`，字段包含 `response` 与 `metadata`。
- 连接启动 30 秒内无事件时，发送 `ERROR_OCCURRED` 并关闭连接。
- 事件过滤参数 `types` 支持多个事件类型，未知类型不返回事件。
- 当 `last_event_id` 超出保留窗口时返回 `STREAM_GAP` 错误并关闭连接。
- 订阅仅返回当前租户事件，跨租户事件不得出现在同一流中。

## 任务历史与时间线模型

- **事件持久化策略**:
  - `Redis` 保存全量事件，默认 24 小时 `TTL`，容量默认 256。
  - `PostgreSQL` 持久化关键事件，排除 `LLM_PARTIAL` 与心跳类事件。
  - 持久化与查询必须按 `tenantId` 过滤。
- **时间线生成**:
  - 基于工作流历史生成摘要与完整模式。
  - 支持 `persist=true` 异步写入 `event_logs`。
  - 时间线事件包含 `WF_`、`ACT_`、`SIG_` 等来源前缀。
- **一致性与职责边界**:
  - `streaming api` 只负责实时分发，`task history & timeline` 负责持久化与派生。
  - 事件写入顺序为先写 `Redis` 后异步补写 `PostgreSQL`，持久化失败需重试并记录
    `ERROR_OCCURRED`，`errorCode` 使用 `EVENT_PERSIST_FAILED`。
  - `EventLogRecord` 以 `eventId` 作为幂等键，重复写入需去重。
  - 时间线仅基于已持久化事件生成，与实时流允许存在短暂延迟。

## 记忆抽象与落盘策略

- **层次结构**:
  - 近期记忆（会话上下文）
  - 语义记忆（向量检索）
  - 压缩摘要（历史归并）
- **职责边界与一致性**:
  - 记忆仅服务上下文与语义检索，不保存任务事件历史与时间线数据。
  - `memoryId` 与 `tenantId` 组合为幂等键，重复写入必须去重。
- **落盘策略**:
  - 会话与任务元数据写入 `PostgreSQL`。
  - 活跃会话状态与预算使用缓存到 `Redis`。
  - 向量记忆通过 `VectorStore` 接口接入，参考 `Qdrant`。
  - 若 `VectorStore` 采用非 `Spring` 组件，必须补充不可满足原因与风险评估。
- **降级策略**:
  - 嵌入模型不可用时返回空记忆结果，任务仍可继续执行。

## 定时任务模型

- 支持 `cron` 表达式与时区。
- 支持最大数量、最小间隔与单次预算限制。
- 每次执行记录到 `scheduled_task_executions`，包含成本与状态。
- 创建与更新请求需携带 `scheduleId` 与可选 `idempotencyKey`，用于幂等控制。

## 多租户与鉴权策略

- 支持 `API Key` 与 `JWT` 扩展点。
- 每个请求必须解析 `TenantContext` 并贯穿执行链路。
- 所有查询必须按 `tenant_id` 过滤，跨租户访问返回未找到。
- 缺失租户头时必须返回 `HTTP 400` 且错误码为 `TENANT_MISSING`，不使用 `NOT_FOUND`。
- `SSE` 订阅仅能读取当前租户事件，`StreamEvent` 必须携带 `tenantId`。
- 定时任务执行必须携带 `tenantId`，缺失时记录 `AUTH_FAILED` 并终止执行。
- 事件传播链路必须在 `streaming`、`history` 与 `budget` 模块进行租户校验。

## 预算计量与存储（`Token Budget`）

- 预算启用时由预算活动记录 `token` 使用。
- 未启用预算时由流程模式记录 `token` 使用。
- 记录必须具备幂等键，保证同一执行仅记录一次。
- 仅工具调用且 `token` 为零时可选择跳过记录或记录为零成本。
- 计量点位于 `ToolExecutor` 与模型调用处，产出 `TokenUsageInput` 并发送至 `TokenBudgetManager`。
- 传播路径为 `agent core` -> `orchestrator` -> `token budget tracking`，聚合按 `taskId` 与 `tenantId` 维度。
- `usageId` 推荐使用 `taskId:seq` 作为幂等键，避免重复计量。
- 预算阈值触发 `BUDGET_THRESHOLD` 事件并写入 `token_usage`。

## 统一输出对象结构

- 统一响应结构 `ApiResponse<T>`：`code`、`message`、`data`、`traceId`、`requestId`。
- 统一错误结构 `ErrorResponse`：`code`、`message`、`details`、`traceId`。

## 错误码与异常策略

- 错误结构必须包含 `code`、`message`、`traceId`、`details`。
- 必须通过全局异常处理器统一映射为 `ErrorResponse`。
- 错误码示例：`BAD_REQUEST`、`INVALID_REQUEST`、`INVALID_CURSOR`、`UNAUTHORIZED`、`FORBIDDEN`、
  `TENANT_MISSING`、`NOT_FOUND`、`BUDGET_EXCEEDED`、`STREAM_TIMEOUT`、`STREAM_GAP`、
  `EVENT_PERSIST_FAILED`、`IDEMPOTENCY_CONFLICT`、`INTERNAL_ERROR`。
- 缺失租户头返回 `TENANT_MISSING`，统一映射 `HTTP 400`。
- 跨租户访问必须返回 `NOT_FOUND`，避免存在性泄漏。
- 错误码与 `HTTP` 状态映射表如下（任务与测试以该表为准）：

| 错误码 | `HTTP` 状态码 | 错误码语义 | 是否可重试 |
| --- | --- | --- | --- |
| `BAD_REQUEST` | `400` | 请求体解析失败或协议不合法 | 否 |
| `INVALID_REQUEST` | `400` | 参数校验失败或字段缺失 | 否 |
| `INVALID_CURSOR` | `400` | 游标或分页参数无效 | 否 |
| `TENANT_MISSING` | `400` | 缺失租户上下文 | 否 |
| `UNAUTHORIZED` | `401` | 未认证或凭证无效 | 是（需重新鉴权） |
| `FORBIDDEN` | `403` | 已认证但无权限或被拒绝 | 否 |
| `NOT_FOUND` | `404` | 资源不存在或跨租户隐藏 | 否 |
| `STREAM_TIMEOUT` | `408` | 流式订阅超时无事件 | 是（可重连） |
| `STREAM_GAP` | `409` | 事件序列缺口或窗口过期 | 否（需重置游标） |
| `IDEMPOTENCY_CONFLICT` | `409` | 幂等键冲突或结果不一致 | 否 |
| `BUDGET_EXCEEDED` | `429` | 预算超限 | 否 |
| `EVENT_PERSIST_FAILED` | `500` | 事件持久化失败 | 是（退避重试） |
| `INTERNAL_ERROR` | `500` | 未预期内部错误 | 是（退避重试） |

## 并发、幂等、重试与失败恢复

- `TaskRequest.idempotencyKey` 在同一租户范围内唯一，重复提交需返回相同 `taskId`。
- 定时任务创建与更新使用 `scheduleId` + `idempotencyKey` 去重，避免重复调度。
- 事件发布采用至少一次语义，消费端以 `eventId` 去重。
- 关键持久化与预算记录失败采用指数退避重试，达到上限后记录 `ERROR_OCCURRED` 并输出指标。
- 任务失败时必须发出 `ERROR_OCCURRED`，最终以 `WORKFLOW_COMPLETED` 标记终态。

## 观测指标（`Micrometer`/`OpenTelemetry`）

- 任务：`task.submit.count`、`task.complete.count`、`task.duration.ms`
- 事件：`event.stream.count`、`event.persist.count`、`event.stream.lag.ms`
- 预算：`budget.tokens.used`、`budget.cost.usd`、`budget.exceeded.count`
- 存储：`storage.redis.hit`、`storage.db.latency.ms`
- 调度：`schedule.run.count`、`schedule.run.fail.count`

## `MVP` 与迭代版本拆分

### `MVP`（`V1`）

**范围**:
- 任务提交、状态查询、事件流订阅。
- 关键事件持久化与历史查询。
- 多租户上下文与 `API Key` 鉴权扩展点。
- 预算计量与成本聚合基础链路。

**验收标准**:
- 可提交任务并获得任务标识与事件订阅入口。
- 事件流支持断线续传与类型过滤。
- 任务事件可查询且包含关键事件类型。
- 预算统计可在任务完成后查询。
- 多租户隔离校验通过。

### 迭代版本（`V2`）

**范围**:
- 时间线摘要与完整模式。
- 记忆分层与向量检索接入。
- 定时任务模型与执行历史。
- 扩展事件类型与预算阈值事件。

**验收标准**:
- 时间线查询可返回摘要与完整记录。
- 记忆检索可返回近期与语义结果。
- 定时任务可创建、暂停、恢复并记录执行历史。
- 扩展事件可订阅并按类型过滤。

## 推荐包结构与关键类名

```
src/main/java/com/example/agent/
  gateway/
    controller/TaskController
    controller/ScheduleController
    controller/TimelineController
  orchestrator/
    TaskOrchestrator
    WorkflowRouter
  agentcore/
    EnforcementGateway
    ToolRegistry
    ToolCache
    ToolExecutor
    SandboxExecutor
  streaming/
    EventStreamService
    SseStreamController
    StreamEvent
    TaskStreamRequest
  history/
    EventLogService
    TimelineService
    EventLogRepository
    EventLogRecord
    EventLogPage
    EventQuery
    TimelineRecord
    TimelineRequest
    TimelineResponse
  memory/
    MemoryStore
    VectorStore
    MemoryRecord
  scheduler/
    ScheduleManager
    ScheduleRepository
    ScheduleExecutionRepository
    ScheduleSpec
    ScheduleResponse
    ScheduleExecutionRecord
    ScheduleQuery
    SchedulePage
  auth/
    AuthService
    ApiKeyAuthenticator
    TenantResolver
    TenantContextFilter
  budget/
    TokenBudgetManager
    TokenUsageRepository
    CostCalculator
  observability/
    MetricsPublisher
    TracingPublisher
  common/
    ApiResponse
    ErrorResponse
```

## 依赖与假设

- 存在可替换的工作流执行引擎，默认参考 `Temporal`。
- 存储层可用 `PostgreSQL` 与 `Redis`，向量存储可通过 `VectorStore` 接口替换。
- 预算计量依赖模型返回 `token` 统计，无法获取时使用可配置的近似策略。
- 在 `WebFlux` 场景下，阻塞式数据访问需通过专用线程池隔离；如改用 `R2DBC` 必须更新规格。
