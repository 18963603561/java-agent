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
- **FR-015**: 系统必须提供 `ReAct` 决策循环（`Think`/`Act`/`Observe`）并支持可配置的终止条件与循环护栏。
- **FR-016**: 系统必须提供 `Step` 模型与状态机，记录每步输入输出、状态迁移与可回放审计。
- **FR-017**: 系统必须定义失败分类与恢复策略矩阵，支持 `retry`/`fallback`/`decompose`/`stop`。
- **FR-018**: 系统必须提供 `Planner` 任务拆分与计划生成能力，输出依赖关系与执行策略。
- **FR-019**: 系统必须提供 `Reflection`/`Self-check` 质量评估与失败自修复能力，避免无限循环。
- **FR-020**: 系统必须支持 `MCP` 工具协议（`tools/list`、`tools/call`）、`Skill` 模型（schema/路由/版本）与 `Hook` 扩展（pre/post tool、pre/post step）。
- **FR-021**: 系统必须支持多智能体编排（`DAG`/`Supervisor`/`Handoff`），并具备角色模型、权限边界与失败传播规则。
- **FR-022**: 系统必须支持高级推理能力（`ToT`/`Debate`/`Deep Research`）并提供可追溯引用结构。
- **FR-023**: 系统必须具备生产治理能力：回放/可重放调试、限流/背压、熔断/超时/重试矩阵与数据层落地。
- **FR-024**: 系统必须提供企业级安全能力：`OPA` 策略评估、`WASI` 沙箱执行与预算触发的模型降级。

### Key Entities *(include if feature involves data)*

- **`Task`**: 任务执行实体，包含请求、状态、结果、预算统计与 `tenantId`。
- **`WorkflowEvent`**: 事件实体，包含 `eventId`、事件类型、序列号、时间戳、`tenantId` 与消息负载。
- **`TaskTimeline`**: 时间线实体，来源于工作流历史或事件汇总，包含 `tenantId`。
- **`MemoryRecord`**: 记忆实体，包含会话上下文、语义向量、压缩摘要与 `tenantId`。
- **`Schedule`**: 定时任务实体，包含 `cron`、预算、执行策略与 `tenantId`。
- **`TokenUsage`**: 预算计量实体，包含输入输出 `token`、成本与 `tenantId`。
- **`TenantContext`**: 多租户上下文，包含租户、用户与权限信息。
- **`StepRecord`**: 运行时步骤记录，包含 `stepId`、`workflowId`、`type`、`status`、`attempt`、`input`、`output`、`errorCode` 与 `tenantId`。
- **`Plan`**: 规划实体，包含任务拆分、依赖关系与执行策略。
- **`ReflectionReport`**: 反思评估实体，包含评分、反馈与是否重试。
- **`ToolInvocation`**: 工具调用记录，包含 `toolName`、参数、耗时与结果摘要。
- **`SkillDefinition`**: 技能定义实体，包含 `name`、`version`、`schema`、`routes` 与约束。
- **`HookRecord`**: Hook 执行记录，包含 `hookType`、阶段、决策与耗时。
- **`AgentRole`**: 多智能体角色模型，包含能力、权限与预算边界。
- **`HandoffRecord`**: 交接记录，包含 `fromAgent`、`toAgent`、上下文与状态。
- **`ReasoningTree`**: 推理树实体，包含分支、评分与剪枝记录。
- **`ResearchCitation`**: 引用实体，包含来源、片段与检索时间。
- **`ReplaySession`**: 回放会话实体，包含 `replayId`、`taskId`、范围与状态。
- **`PolicyDecision`**: 策略决策实体，包含 `policyId`、`decision`、`reason`。
- **`SandboxRun`**: 沙箱执行实体，包含资源限制与执行结果。
- **`ModelFallbackDecision`**: 模型降级记录，包含触发条件与目标模型。

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
- FR-015: 决策循环支持 `Think`/`Act`/`Observe` 且终止条件可配置。
- FR-016: `Step` 状态机与步骤时间线可查询且可回放。
- FR-017: 失败分类与恢复策略矩阵生效且可追溯。
- FR-018: 规划结果包含依赖关系与执行策略并可查询。
- FR-019: 反思评估可触发自修复且不会无限循环。
- FR-020: `MCP` 工具调用、`Skill` 路由版本与 `Hook` 执行可验证。
- FR-021: 多智能体编排支持 `DAG`/`Supervisor`/`Handoff` 且失败传播规则清晰。
- FR-022: 高级推理输出包含可追溯引用结构。
- FR-023: 回放、限流/背压、熔断/超时/重试矩阵与数据层落地可验证。
- FR-024: `OPA` 拒绝、`WASI` 限制与预算触发降级可验证。

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

## Agent Runtime 与决策循环

`Agent Runtime` 负责驱动 `ReAct` 决策循环（`Think`/`Act`/`Observe`），将任务执行拆解为可观测步骤序列，并在运行期应用终止护栏。

### 功能需求
- **FR-015**: 系统必须提供 `ReAct` 决策循环，支持 `Think`、`Act`、`Observe` 三阶段与可配置循环护栏（`MaxIterations`、`MinIterations`、`ObservationWindow`、`StepTimeout`、预算阈值、人工中断）。

### 关键规则
- 循环最小步数未满足时不得提前终止，除非触发安全护栏或显式取消。
- `Observe` 结果必须写入步骤记录并进入下一轮 `Think`，禁止跳过观察导致状态漂移。
- 终止后必须输出终止事件并记录终止原因与触发条件。

### 验收口径
- FR-015: 提交任务后可观察到 `Think`/`Act`/`Observe` 步骤序列，终止原因可追溯。
- FR-015: 配置 `MaxIterations` 与 `ObservationWindow` 后，循环在限制内停止并输出对应事件。

## Step 模型与状态机

`Step` 是运行时最小执行单元，用于描述思考、行动、观察以及计划、反思与交接的状态演化。

### 功能需求
- **FR-016**: 系统必须提供 `Step` 模型与状态机，支持 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`SKIPPED`、`CANCELLED`、`WAITING`，并记录输入输出、重试次数、关联工具调用与租户信息。
- **FR-016**: 系统必须提供步骤时间线查询能力（`timeline/steps`），按 `workflowId` 输出有序步骤。

### 关键规则
- `stepSeq` 在同一 `workflowId` 范围内单调递增，支持重放。
- 每次状态迁移必须写入事件：`STEP_STARTED`、`STEP_COMPLETED`、`STEP_FAILED`、`STEP_SKIPPED`。
- `WAITING` 状态只由背压、审批或外部依赖触发，恢复后进入 `RUNNING`。

### 接口定义

#### 步骤时间线查询

方法与路径  
`GET /api/v1/timeline/steps`

请求头  
`X-API-Key`  
`X-Tenant-Id`  
`X-Trace-Id`  
`X-Request-Id`

查询参数  
- `workflowId`：必填，工作流标识。  
- `cursor`：可选，分页游标。  
- `size`：可选，分页大小，范围遵循分页通用约束。

成功响应  
- `ApiResponse<StepTimelineResponse>`  
- `StepTimelineResponse`：`workflowId`、`steps`、`nextCursor`、`hasMore`  
- `StepRecord`：`stepId`、`stepSeq`、`type`、`status`、`attempt`、`errorCode`、`startedAt`、`completedAt`

错误响应  
- `400`：`INVALID_REQUEST`、`INVALID_CURSOR`、`TENANT_MISSING`  
- `403`：`POLICY_DENIED`  
- `404`：`NOT_FOUND`  
- `503`：`CIRCUIT_OPEN`

事件与状态机关系  
- 仅查询，不产生新的 `STEP_*` 事件。  
- 返回的 `StepRecord.status` 必须满足状态机迁移规则。

### 验收口径
- FR-016: 状态机覆盖成功、失败、等待与取消场景，并支持重试计数。
- FR-016: `timeline/steps` 返回步骤序列与状态变更时间。

## 失败策略与恢复

### 功能需求
- **FR-017**: 系统必须定义失败分类与恢复策略矩阵，至少覆盖 `RETRYABLE`、`NON_RETRYABLE`、`POLICY_DENIED`、`SANDBOX_VIOLATION`、`BUDGET_EXCEEDED`、`TIMEOUT`、`DEPENDENCY_ERROR`，并支持 `retry`/`fallback`/`decompose`/`stop`。

### 关键规则
- `retry` 使用指数退避与最大次数限制，重试次数写入步骤记录。
- `fallback` 支持工具替代与模型降级，必须记录触发原因与目标策略。
- `decompose` 触发后进入 `Planner` 重新生成子任务并关联原步骤。
- `stop` 需输出终止事件并保留失败上下文，禁止静默失败。

### 验收口径
- FR-017: 失败分类与策略矩阵配置生效，错误场景触发对应恢复动作。
- FR-017: 失败事件与恢复动作可在事件流与时间线中追溯。

## Planner/Reflection

### 功能需求
- **FR-018**: 系统必须提供 `Planner` 任务拆分与计划生成能力，输出依赖关系、执行策略与预算提示，支持 `DAG`。
- **FR-019**: 系统必须提供 `Reflection`/`Self-check` 质量评估与失败自修复能力，支持反馈重写、阈值判定与最大迭代次数。

### 关键规则
- `Plan` 必须包含 `nodes`、`dependencies`、`expectedOutput`、`budgetHint`，并可持久化查询。
- `Reflection` 失败必须返回可解释原因与改进建议，禁止无限循环（`MaxReflections`）。
- 规划与反思结果必须写入事件并关联步骤。

### 验收口径
- FR-018: 复杂任务可生成包含依赖关系的计划并可查询。
- FR-019: 质量评估触发重写且在阈值或上限达成后稳定结束。

## MCP/Skills/Hooks

### 功能需求
- **FR-020**: 系统必须支持 `MCP` 工具协议（`tools/list`、`tools/call`），并提供 `Skill` 模型（schema/路由/版本）与 `Hook` 扩展（pre/post tool、pre/post step）。

### 关键规则
- `MCP` 调用必须支持域名白名单、超时、熔断与响应大小限制，默认拒绝未知域。
- `Skill` 注册需包含 `name`、`version`、`schema`、`routes`、`constraints`，支持灰度与降级。
- `Hook` 支持阻断/放行策略与超时降级，执行顺序可配置并写入审计记录。

### 接口定义

#### MCP 工具列表

方法与路径  
`POST /api/v1/mcp/tools/list`

请求头  
`X-API-Key`  
`X-Tenant-Id`  
`X-Trace-Id`  
`X-Request-Id`

请求体  
`McpToolListRequest`  
- `serverId`：必填，目标 `MCP` 服务标识。  
- `cursor`：可选，分页游标。  
- `size`：可选，分页大小，范围遵循分页通用约束。

成功响应  
- `ApiResponse<McpToolListResponse>`  
- `McpToolListResponse`：`tools`、`nextCursor`、`hasMore`  
- `McpToolDefinition`：`name`、`version`、`description`、`inputSchema`、`outputSchema`、`tags`

错误响应  
- `400`：`INVALID_REQUEST`、`TENANT_MISSING`  
- `403`：`POLICY_DENIED`  
- `404`：`NOT_FOUND`  
- `503`：`MCP_UNAVAILABLE`、`CIRCUIT_OPEN`

事件与状态机关系  
- 启用 `Hook` 时记录 `HOOK_PRE_TOOL` 与 `HOOK_POST_TOOL`。  
- 成功可记录 `TOOL_OBSERVATION`，失败记录 `TOOL_ERROR`。  
- 仅查询工具清单，不推进 `Step` 状态机。

#### MCP 工具调用

方法与路径  
`POST /api/v1/mcp/tools/call`

请求头  
`X-API-Key`  
`X-Tenant-Id`  
`X-Trace-Id`  
`X-Request-Id`

请求体  
`McpToolCallRequest`  
- `callId`：必填，调用幂等键。  
- `serverId`：必填，目标 `MCP` 服务标识。  
- `toolName`：必填，工具名称。  
- `arguments`：可选，工具参数。  
- `timeoutMs`：可选，超时毫秒。

成功响应  
- `ApiResponse<McpToolCallResponse>`  
- `McpToolCallResponse`：`callId`、`status`、`result`、`error`

错误响应  
- `400`：`INVALID_REQUEST`、`TENANT_MISSING`  
- `403`：`POLICY_DENIED`  
- `404`：`NOT_FOUND`  
- `409`：`HOOK_BLOCKED`  
- `503`：`MCP_UNAVAILABLE`、`CIRCUIT_OPEN`

事件与状态机关系  
- 调用开始记录 `TOOL_INVOKED`，成功记录 `TOOL_OBSERVATION`，失败记录 `TOOL_ERROR`。  
- 启用 `Hook` 时记录 `HOOK_PRE_TOOL` 与 `HOOK_POST_TOOL`。  
- 在 `Step` 内调用时，失败会驱动对应步骤进入 `FAILED` 并遵循失败恢复策略。

### 验收口径
- FR-020: `tools/list` 可返回可用工具清单，`tools/call` 可返回执行结果或错误。
- FR-020: `Skill` 路由与版本匹配生效，`Hook` 在 pre/post 阶段被触发并记录审计。

## Multi-agent 编排

### 功能需求
- **FR-021**: 系统必须支持多智能体编排，包括 `DAG` 执行、`Supervisor` 调度与 `Handoff` 机制，并具备角色模型、权限边界与失败传播规则。

### 关键规则
- `Supervisor` 负责角色分配、预算拆分与失败阈值决策，并记录团队生命周期事件。
- `Handoff` 需记录上下文传递与权限校验，支持顺序与并行交接。
- 失败传播策略应支持 `fail-fast` 与部分成功两种模式，默认不隐藏失败原因。

### 验收口径
- FR-021: `DAG` 依赖执行与并行策略可配置且事件可追溯。
- FR-021: `Handoff` 事件与权限校验记录完整，失败传播符合配置。

## Advanced Reasoning

### 功能需求
- **FR-022**: 系统必须支持 `ToT`、`Debate` 与 `Deep Research` 等高级推理模式，并输出可追溯引用结构。

### 关键规则
- `ToT` 最小实现包含多分支候选生成、评分、剪枝与停止条件（`maxDepth`、`beamWidth`）。
- `Debate` 支持立场生成、论辩轮次、共识检测与最终综合，允许可选启用。
- `Deep Research` 使用 `web-search`/`web-fetch` 工具链，输出 `citations` 列表（来源、片段、检索时间、可信度）。

### 验收口径
- FR-022: `ToT` 能生成多候选并依据评分选择结果。
- FR-022: `Deep Research` 输出报告包含引用结构且引用可追溯。

## Production 治理

### 功能需求
- **FR-023**: 系统必须具备回放/可重放调试、限流/背压、熔断/超时/重试矩阵与数据层落地能力。

### 关键规则
- 回放以 `StepRecord` 与事件日志为基础，支持指定范围重放与差异对比。
- 限流/背压按租户与任务维度配置，触发时进入 `WAITING` 并记录 `BACKPRESSURE_APPLIED`。
- 熔断/超时/重试矩阵需覆盖外部调用（`HTTP`/`DB`/`MQ`/文件系统/`MCP`），并支持按工具类别配置。
- 数据层必须提供表结构、迁移脚本与读写职责分层，避免业务逻辑与持久化耦合。

### 接口定义

#### 任务回放

方法与路径  
`POST /api/v1/replay`

请求头  
`X-API-Key`  
`X-Tenant-Id`  
`X-Trace-Id`  
`X-Request-Id`

请求体  
`ReplayRequest`  
- `taskId`：必填，任务标识。  
- `fromStepId`：可选，回放起始步骤。  
- `toStepId`：可选，回放结束步骤。  
- `mode`：可选，回放模式。

成功响应  
- `ApiResponse<ReplayResponse>`  
- `ReplayResponse`：`replayId`、`status`、`startedAt`、`completedAt`

错误响应  
- `400`：`INVALID_REQUEST`、`TENANT_MISSING`  
- `403`：`POLICY_DENIED`  
- `404`：`REPLAY_NOT_FOUND`  
- `503`：`CIRCUIT_OPEN`

事件与状态机关系  
- 回放开始记录 `REPLAY_STARTED`，结束记录 `REPLAY_COMPLETED`。  
- 回放仅基于历史重建，不产生新的 `STEP_*` 状态迁移。

### 验收口径
- FR-023: 重放可复现步骤序列并输出一致的事件轨迹。
- FR-023: 限流/背压与熔断规则生效且可观测。

## Enterprise 安全

### 功能需求
- **FR-024**: 系统必须提供 `OPA` 策略评估、`WASI` 沙箱执行与预算触发模型降级能力。

### 关键规则
- `OPA` 默认拒绝，评估输入包含租户、用户、动作、资源与上下文，结果写入审计日志。
- `WASI` 沙箱限制文件系统、网络、环境变量与资源配额（CPU、内存、超时），并记录违规原因。
- 预算触发降级需支持模型分层与回退记录，确保降级不绕过策略。

### 接口定义

#### 策略评估

方法与路径  
`POST /api/v1/policy/evaluate`

请求头  
`X-API-Key`  
`X-Tenant-Id`  
`X-Trace-Id`  
`X-Request-Id`

请求体  
`PolicyRequest`  
- `policyId`：必填，策略标识。  
- `action`：必填，动作名称。  
- `resource`：必填，资源标识。  
- `input`：可选，上下文输入。

成功响应  
- `ApiResponse<PolicyDecision>`  
- `PolicyDecision`：`policyId`、`decision`、`reason`、`evaluationId`、`matchedRules`

错误响应  
- `400`：`INVALID_REQUEST`、`TENANT_MISSING`  
- `403`：`POLICY_DENIED`  
- `404`：`NOT_FOUND`  
- `503`：`CIRCUIT_OPEN`

事件与状态机关系  
- 评估成功记录 `POLICY_EVALUATED`，拒绝记录 `POLICY_DENIED`。  
- 拒绝可驱动运行时步骤进入 `FAILED` 并记录失败分类 `POLICY_DENIED`。

### 验收口径
- FR-024: `OPA` 拒绝时返回一致错误码并记录审计日志。
- FR-024: `WASI` 资源限制可通过测试用例验证。
- FR-024: 预算阈值触发模型降级且事件可追溯。

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
- **决策循环与步骤状态机** -> `AgentRuntime`、`StepEngine` 输出 `StepRecord`。
- **规划与反思** -> `PlannerService`、`ReflectionService` 输出 `Plan` 与 `ReflectionReport`。
- **多智能体编排与交接** -> `SupervisorCoordinator`、`HandoffService` 输出 `HandoffRecord`。
- **高级推理与研究** -> `ThoughtTreeService`、`DebateCoordinator`、`ResearchPipeline` 输出 `ReasoningTree` 与 `ResearchCitation`。
- **生产治理与回放** -> `ReplayService`、`RateLimitService`、`CircuitBreakerManager` 输出 `ReplaySession`。
- **企业策略与沙箱** -> `PolicyEngine`、`WasiSandboxExecutor` 输出 `PolicyDecision` 与 `SandboxRun`。

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
10. **运行时模块（`runtime`）**: 决策循环、`Step` 状态机与终止条件控制。
11. **规划与反思模块（`planning`/`reflection`）**: 任务拆分、计划生成与质量自检。
12. **工具扩展模块（`tools`）**: `MCP` 工具接入、`Skill` 注册表与 `Hook` 执行。
13. **多智能体编排模块（`multi-agent`）**: `Supervisor` 调度、`DAG` 执行与 `Handoff`。
14. **高级推理模块（`reasoning`）**: `ToT`、`Debate` 与 `Deep Research` 流程。
15. **生产治理模块（`governance`）**: 回放、限流/背压、熔断/超时/重试矩阵。
16. **企业安全模块（`enterprise`）**: `OPA` 策略、`WASI` 沙箱与模型降级。

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
- `runtime` 仅负责步骤状态机推进与终止判定，步骤持久化通过 `history` 模块落盘。
- `planning`/`reflection` 仅负责计划与自检，不直接执行工具调用。
- `tools` 仅负责工具注册、调用与 `Hook` 扩展，不管理任务生命周期。
- `multi-agent` 仅负责编排、交接与失败传播，不直接执行业务工具。
- `governance` 仅负责限流/背压/熔断/回放治理，不侵入业务规则。
- `enterprise` 仅负责策略评估与沙箱隔离，拒绝结果不得绕过租户与预算约束。

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
| 运行时模块 | `StepRuntimeService` | `StepRecord`、`StepQuery` | `STEP_STARTED`、`STEP_COMPLETED` | `task_steps` |
| 规划与反思模块 | `PlannerService`、`ReflectionService` | `Plan`、`ReflectionReport` | `PLAN_GENERATED`、`REFLECTION_COMPLETED` | `plans`、`reflection_reports` |
| 工具扩展模块 | `McpToolClient`、`SkillRegistry`、`HookManager` | `McpToolDefinition`、`SkillDefinition`、`HookRecord` | `MCP_TOOL_LISTED`、`HOOK_EXECUTED` | `tool_registry`、`skill_registry` |
| 多智能体编排模块 | `SupervisorCoordinator`、`HandoffService` | `AgentRole`、`HandoffRecord` | `HANDOFF_REQUESTED`、`HANDOFF_COMPLETED` | `agent_runs`、`agent_handoffs` |
| 高级推理模块 | `ThoughtTreeService`、`ResearchPipeline` | `ReasoningTree`、`ResearchCitation` | `THOUGHT_EXPANDED`、`RESEARCH_SOURCE_ADDED` | `reasoning_runs`、`research_sources` |
| 生产治理模块 | `ReplayService`、`RateLimitService`、`CircuitBreakerManager` | `ReplaySession`、`RateLimitRule`、`CircuitState` | `REPLAY_STARTED`、`CIRCUIT_OPENED`、`BACKPRESSURE_APPLIED` | `replay_sessions`、`runtime_limits` |
| 企业安全模块 | `PolicyEngine`、`WasiSandboxExecutor` | `PolicyDecision`、`SandboxRun`、`ModelFallbackDecision` | `POLICY_DENIED`、`SANDBOX_VIOLATION`、`MODEL_FALLBACK_APPLIED` | `policy_audit`、`sandbox_runs`、`model_fallbacks` |

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
- `WASI` 沙箱在 `Java` 侧提供 `WasiSandboxExecutor` 落地实现，运行时通过可配置适配层接入；
  必须在规格中给出资源限制、隔离范围与兼容边界。
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

public interface StepRuntimeService {
    StepResponse runStep(StepRequest request, TenantContext tenantContext);
    StepTimelineResponse querySteps(StepQuery query, TenantContext tenantContext);
}

public interface PlannerService {
    PlanResult plan(PlanRequest request, TenantContext tenantContext);
}

public interface ReflectionService {
    ReflectionResult reflect(ReflectionRequest request, TenantContext tenantContext);
}

public interface HookManager {
    HookDecision preTool(HookContext context, TenantContext tenantContext);
    HookDecision postTool(HookContext context, TenantContext tenantContext);
    HookDecision preStep(HookContext context, TenantContext tenantContext);
    HookDecision postStep(HookContext context, TenantContext tenantContext);
}

public interface SkillRegistry {
    SkillDefinition resolve(SkillQuery query, TenantContext tenantContext);
}

public interface McpToolClient {
    McpToolList list(McpToolListRequest request, TenantContext tenantContext);
    McpToolResult call(McpToolCallRequest request, TenantContext tenantContext);
}

public interface MultiAgentCoordinator {
    AgentGraphResult executeGraph(AgentGraphRequest request, TenantContext tenantContext);
    HandoffResult handoff(HandoffRequest request, TenantContext tenantContext);
}

public interface ReplayService {
    ReplayResponse replay(ReplayRequest request, TenantContext tenantContext);
}

public interface PolicyEngine {
    PolicyDecision evaluate(PolicyRequest request, TenantContext tenantContext);
}

public interface WasiSandboxExecutor {
    SandboxResult execute(SandboxRequest request, TenantContext tenantContext);
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
- **`StepRecord`**
  - `stepId`、`workflowId`、`stepSeq`、`type`、`status`、`attempt`、`input`、`output`、`errorCode`、`startedAt`、
    `completedAt`、`tenantId`
- **`StepRequest`**
  - `workflowId`、`type`、`input`、`context`、`idempotencyKey`
- **`StepResponse`**
  - `stepId`、`status`、`output`、`errorCode`
- **`StepQuery`**
  - `workflowId`、`status`、`cursor`、`size`
- **`StepTimelineResponse`**
  - `workflowId`、`steps`、`nextCursor`、`hasMore`
- **`PlanRequest`**
  - `taskId`、`query`、`context`、`constraints`
- **`PlanResult`**
  - `planId`、`nodes`、`dependencies`、`strategy`
- **`ReflectionRequest`**
  - `taskId`、`content`、`criteria`、`maxIterations`
- **`ReflectionResult`**
  - `score`、`feedback`、`retry`、`iteration`
- **`SkillDefinition`**
  - `name`、`version`、`schema`、`routes`、`constraints`
- **`HookContext`**
  - `hookType`、`stepId`、`toolName`、`payload`
- **`HookDecision`**
  - `action`、`reason`
- **`McpToolListRequest`**
  - `serverId`、`cursor`、`size`
- **`McpToolListResponse`**
  - `tools`、`nextCursor`、`hasMore`
- **`McpToolDefinition`**
  - `name`、`version`、`description`、`inputSchema`、`outputSchema`、`tags`
- **`McpToolCallRequest`**
  - `callId`、`serverId`、`toolName`、`arguments`、`timeoutMs`
- **`McpToolCallResponse`**
  - `callId`、`status`、`result`、`error`
- **`HandoffRequest`**
  - `fromAgent`、`toAgent`、`context`、`permissions`
- **`HandoffResult`**
  - `handoffId`、`status`
- **`AgentGraph`**
  - `nodes`、`edges`、`strategy`
- **`ThoughtNode`**
  - `nodeId`、`parentId`、`content`、`score`、`depth`
- **`DebateRound`**
  - `round`、`positions`、`scores`
- **`ResearchCitation`**
  - `sourceId`、`url`、`title`、`snippet`、`retrievedAt`、`confidence`
- **`ReplayRequest`**
  - `taskId`、`fromStepId`、`toStepId`、`mode`
- **`ReplayResponse`**
  - `replayId`、`status`、`startedAt`、`completedAt`
- **`PolicyRequest`**
  - `policyId`、`action`、`resource`、`input`
- **`PolicyDecision`**
  - `decision`、`reason`、`policyId`、`evaluationId`、`matchedRules`
- **`SandboxRequest`**
  - `toolName`、`input`、`limits`
- **`SandboxResult`**
  - `status`、`output`、`error`

**事件类型（按类别）**:

- 核心流程: `WORKFLOW_STARTED`、`WORKFLOW_COMPLETED`、`AGENT_STARTED`、`AGENT_COMPLETED`、`ERROR_OCCURRED`
- LLM: `LLM_PROMPT`、`LLM_PARTIAL`、`LLM_OUTPUT`
- 工具: `TOOL_INVOKED`、`TOOL_OBSERVATION`、`TOOL_ERROR`
- 运行时步骤: `STEP_STARTED`、`STEP_COMPLETED`、`STEP_FAILED`、`STEP_SKIPPED`、`STEP_WAITING`
- 规划与反思: `PLAN_GENERATED`、`PLAN_REVISED`、`REFLECTION_STARTED`、`REFLECTION_COMPLETED`
- Hooks: `HOOK_PRE_TOOL`、`HOOK_POST_TOOL`、`HOOK_PRE_STEP`、`HOOK_POST_STEP`
- 多智能体: `DELEGATION`、`TEAM_RECRUITED`、`TEAM_RETIRED`、`MESSAGE_SENT`、`MESSAGE_RECEIVED`、`ROLE_ASSIGNED`
- 进度与状态: `PROGRESS`、`DATA_PROCESSING`、`WAITING`、`TEAM_STATUS`、`WORKSPACE_UPDATED`
- 人机交互: `APPROVAL_REQUESTED`、`APPROVAL_DECISION`
- 高级推理: `THOUGHT_EXPANDED`、`THOUGHT_PRUNED`、`DEBATE_ROUND_STARTED`、`DEBATE_ROUND_COMPLETED`、`RESEARCH_SOURCE_ADDED`、`RESEARCH_SYNTHESIZED`
- 生产治理: `REPLAY_STARTED`、`REPLAY_COMPLETED`、`BACKPRESSURE_APPLIED`、`CIRCUIT_OPENED`、`CIRCUIT_HALF_OPEN`
- 策略与沙箱: `POLICY_EVALUATED`、`POLICY_DENIED`、`SANDBOX_VIOLATION`、`MODEL_FALLBACK_APPLIED`

### 事件模型与顺序约束

- `eventId` 为事件唯一键，格式为 `streamId:seq`，用于 `SSE` 的 `id` 字段与去重。
- `seq` 以 `workflowId` 为范围单调递增，由 `orchestrator` 生成并保证唯一。
- `schemaVersion` 必填，默认 `v1`，用于事件演进兼容。
- 所有事件必须携带 `tenantId`，事件查询与订阅按 `tenantId` 过滤。
- 生命周期顺序：`WORKFLOW_STARTED` 先于任何业务事件，`WORKFLOW_COMPLETED` 作为最终事件。
- `ERROR_OCCURRED` 可插入任何阶段，但必须在最终事件前出现。
- 同一 `agentId` 的 `LLM_PARTIAL` 必须在对应 `LLM_OUTPUT` 之前出现。
- 同一 `stepId` 的事件顺序必须满足 `STEP_STARTED` -> `STEP_COMPLETED`/`STEP_FAILED`，禁止跳序。
- `stepSeq` 在同一 `workflowId` 范围内单调递增，用于步骤时间线与回放重建。

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
  - `task_steps` 持久化 `StepRecord`，用于步骤时间线与回放重建。
  - 持久化与查询必须按 `tenantId` 过滤。
- **时间线生成**:
  - 基于工作流历史生成摘要与完整模式。
  - 步骤时间线基于 `StepRecord` 生成，支持 `timeline/steps` 查询。
  - 支持 `persist=true` 异步写入 `event_logs`。
  - 时间线事件包含 `WF_`、`ACT_`、`SIG_` 等来源前缀。
- **一致性与职责边界**:
  - `streaming api` 只负责实时分发，`task history & timeline` 负责持久化与派生。
  - 事件写入顺序为先写 `Redis` 后异步补写 `PostgreSQL`，持久化失败需重试并记录
    `ERROR_OCCURRED`，`errorCode` 使用 `EVENT_PERSIST_FAILED`。
  - `EventLogRecord` 以 `eventId` 作为幂等键，重复写入需去重。
  - 时间线仅基于已持久化事件生成，与实时流允许存在短暂延迟。
  - 回放仅基于持久化事件与步骤记录重建，回放结果允许与实时流存在短暂延迟。

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
- 关键入口在执行前进行 `OPA` 策略评估，拒绝时返回 `POLICY_DENIED` 并记录审计。
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
- 预算阈值可触发模型分层降级，记录 `MODEL_FALLBACK_APPLIED` 并写入 `model_fallbacks`。

## 统一输出对象结构

- 统一响应结构 `ApiResponse<T>`：`code`、`message`、`data`、`traceId`、`requestId`。
- 统一错误结构 `ErrorResponse`：`code`、`message`、`details`、`traceId`。

## 错误码与异常策略

- 错误结构必须包含 `code`、`message`、`traceId`、`details`。
- 必须通过全局异常处理器统一映射为 `ErrorResponse`。
- 错误码示例：`BAD_REQUEST`、`INVALID_REQUEST`、`INVALID_CURSOR`、`UNAUTHORIZED`、`FORBIDDEN`、
  `TENANT_MISSING`、`NOT_FOUND`、`BUDGET_EXCEEDED`、`STREAM_TIMEOUT`、`STREAM_GAP`、
  `EVENT_PERSIST_FAILED`、`IDEMPOTENCY_CONFLICT`、`POLICY_DENIED`、`SANDBOX_DENIED`、
  `RATE_LIMITED`、`CIRCUIT_OPEN`、`MCP_UNAVAILABLE`、`REPLAY_NOT_FOUND`、`HOOK_BLOCKED`、`INTERNAL_ERROR`。
- 命名兼容：`POLICY_DENY` 统一映射为 `POLICY_DENIED`，`SANDBOX_DENY` 统一映射为 `SANDBOX_DENIED`，对外输出以统一名称为准。
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
| `POLICY_DENIED` | `403` | 策略评估拒绝 | 否 |
| `SANDBOX_DENIED` | `403` | 沙箱执行被拒绝或资源限制触发 | 否 |
| `NOT_FOUND` | `404` | 资源不存在或跨租户隐藏 | 否 |
| `REPLAY_NOT_FOUND` | `404` | 回放会话不存在 | 否 |
| `STREAM_TIMEOUT` | `408` | 流式订阅超时无事件 | 是（可重连） |
| `STREAM_GAP` | `409` | 事件序列缺口或窗口过期 | 否（需重置游标） |
| `IDEMPOTENCY_CONFLICT` | `409` | 幂等键冲突或结果不一致 | 否 |
| `HOOK_BLOCKED` | `409` | Hook 阻断执行 | 否 |
| `BUDGET_EXCEEDED` | `429` | 预算超限 | 否 |
| `RATE_LIMITED` | `429` | 限流或背压触发 | 是（可重试） |
| `EVENT_PERSIST_FAILED` | `500` | 事件持久化失败 | 是（退避重试） |
| `CIRCUIT_OPEN` | `503` | 熔断器打开 | 是（等待半开） |
| `MCP_UNAVAILABLE` | `503` | MCP 工具不可用 | 是（退避重试） |
| `INTERNAL_ERROR` | `500` | 未预期内部错误 | 是（退避重试） |

## 并发、幂等、重试与失败恢复

- `TaskRequest.idempotencyKey` 在同一租户范围内唯一，重复提交需返回相同 `taskId`。
- 定时任务创建与更新使用 `scheduleId` + `idempotencyKey` 去重，避免重复调度。
- 事件发布采用至少一次语义，消费端以 `eventId` 去重。
- 关键持久化与预算记录失败采用指数退避重试，达到上限后记录 `ERROR_OCCURRED` 并输出指标。
- 任务失败时必须发出 `ERROR_OCCURRED`，最终以 `WORKFLOW_COMPLETED` 标记终态。
- 失败恢复遵循 `retry`/`fallback`/`decompose`/`stop` 策略矩阵，禁止无限循环。
- 预算触发降级时优先采用模型分层 `fallback`，并记录 `MODEL_FALLBACK_APPLIED` 事件。

## 观测指标（`Micrometer`/`OpenTelemetry`）

- 任务：`task.submit.count`、`task.complete.count`、`task.duration.ms`
- 事件：`event.stream.count`、`event.persist.count`、`event.stream.lag.ms`
- 预算：`budget.tokens.used`、`budget.cost.usd`、`budget.exceeded.count`
- 存储：`storage.redis.hit`、`storage.db.latency.ms`
- 调度：`schedule.run.count`、`schedule.run.fail.count`
- 运行时：`step.count`、`step.duration.ms`、`step.failure.count`
- 工具与 Hook：`tool.call.count`、`tool.call.latency.ms`、`hook.block.count`
- 多智能体：`agent.handoff.count`、`agent.parallel.count`、`agent.fail.count`
- 高级推理：`reasoning.tot.branch.count`、`reasoning.debate.round.count`、`research.citation.count`
- 生产治理：`replay.count`、`rate.limit.count`、`circuit.open.count`
- 企业安全：`policy.deny.count`、`sandbox.violation.count`、`model.fallback.count`

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

### 迭代版本（`V3`）

**范围**:
- `Agent Runtime` 决策循环与 `Step` 状态机。
- `Planner`/`Reflection` 自修复与计划能力。
- `MCP` 工具协议、`Skill`/`Hook` 扩展。
- 多智能体编排与高级推理（`ToT`/`Debate`/`Deep Research`）。
- 生产治理与企业安全（回放、限流/背压、熔断、`OPA`、`WASI`、模型降级）。

**验收标准**:
- 决策循环可配置终止条件，`Step` 时间线可查询。
- `MCP` 工具调用、`Hook` 执行与 `Skill` 路由可验证。
- 多智能体编排与高级推理输出可追溯引用。
- 回放、限流/背压与熔断生效，`OPA` 与 `WASI` 安全校验可通过。

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
  runtime/
    StepRuntimeService
    StepRecord
    StepRequest
    StepResponse
    StepQuery
    StepTimelineResponse
    StepState
    StepStateMachine
    AgentRuntime
  planning/
    PlannerService
    Plan
    PlanRequest
    PlanResult
  reflection/
    ReflectionService
    ReflectionReport
    ReflectionRequest
    ReflectionResult
  tools/
    McpToolClient
    McpToolDefinition
    McpToolListRequest
    McpToolCallRequest
    HookManager
    HookRecord
    HookContext
    HookDecision
    SkillRegistry
    SkillDefinition
    SkillRoute
    SkillVersion
  multiagent/
    SupervisorCoordinator
    MultiAgentCoordinator
    AgentGraphExecutor
    HandoffService
    AgentRole
    AgentGraph
    HandoffRequest
    HandoffResult
    HandoffRecord
  reasoning/
    ThoughtTreeService
    ThoughtNode
    DebateCoordinator
    DebateRound
  research/
    ResearchPipeline
    ResearchCitation
  governance/
    ReplayService
    ReplaySession
    ReplayRequest
    ReplayResponse
    RateLimitService
    RateLimitRule
    CircuitBreakerManager
  policy/
    PolicyEngine
    PolicyDecision
    PolicyRequest
  sandbox/
    WasiSandboxExecutor
    SandboxRun
    SandboxRequest
    SandboxResult
  model/
    ModelFallbackPolicy
    ModelFallbackDecision
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
- `OPA` 策略引擎通过独立服务或嵌入式运行时接入，策略文件由运维侧管理并支持热更新。
- `WASI` 运行时可用且具备资源限制能力，`WASM` 模块来源可信并支持校验。
- `web-search`/`web-fetch` 工具链可用且具备域名白名单与超时配置。
