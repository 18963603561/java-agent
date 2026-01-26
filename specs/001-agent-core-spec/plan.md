# Implementation Plan: Java Shannon Agent Orchestrator Core

**Branch**: `001-agent-core-spec` | **Date**: 2026-01-25 | **Spec**: `F:\ai-code\java-agent\specs\001-agent-core-spec\spec.md`
**Input**: Feature specification from `F:\ai-code\java-agent\specs\001-agent-core-spec\spec.md`

## Summary

本计划在 `Java 17` + `Spring Boot 3` 体系下，按 `Shannon` 架构与术语对齐落地
`agent core`、`orchestrator`、`streaming api`、`event types`、`task history & timeline`、
`memory system`、`scheduled tasks`、`authentication & multitenancy`、`token budget tracking`，
并补齐 `agent runtime`、`planning/reflection`、`tools`（`MCP`/`Skill`/`Hook`）、`multi-agent`、
`reasoning`、`governance` 与 `enterprise` 能力。
实现路径以规格中的映射表为唯一依据，采用 `Spring` 生态优先选型，形成可执行的分阶段交付。

## Technical Context

**Language/Version**: `Java 17`
**Primary Dependencies**: `Spring Boot 3`（`WebFlux`、`Reactor`、`Spring Data JDBC`、`Spring Data Redis`、
`Spring Scheduling`、`Spring Boot Actuator`、`Micrometer`）
**鉴权链路**: 鉴权链路采用 `WebFilter` + `TenantContextFilter` + `AuthService`，
不引入 `Spring Security` 过滤链；安全边界为所有入口必须经过过滤器链路，禁止绕过。
**Storage**: `PostgreSQL`、`Redis`、`VectorStore`（接口）
**Data Access**: `Spring Data JDBC`，阻塞访问通过 `Reactor` 的 `boundedElastic` 隔离
**Testing**: `JUnit 5`、`Spring Boot Test`、`Reactor Test`
**Target Platform**: 服务端部署（容器化/虚拟机均可）
**Project Type**: 单体服务
**Performance Goals**: 与规格 `SC-001` ~ `SC-006` 一致
**Constraints**: `SSE` 流式输出、统一输出结构、多租户隔离、`token` 计量、`slf4j` 日志、
`Shannon` 对齐、`Spring` 生态优先
**Scale/Scope**: 以规格 `SC-001` ~ `SC-006` 作为规模与负载基线

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- 确认技术栈为 `Java 17` + `Spring Boot 3` + `WebFlux`，流式输出为 `SSE`（已覆盖）
- 统一输出结构与错误语义（已覆盖）
- 关键路径 `slf4j` 日志与异常 `error` 日志（已覆盖）
- 多租户与鉴权扩展点（已覆盖）
- `token` 预算计量点与存储接口（已覆盖）
- 对齐 `vendor/Shannon` 架构与术语（已覆盖）
- 映射表与差异说明完整（已覆盖）
- `Spring` 生态优先选型与依赖管理（已覆盖）

## Project Structure

### Documentation (this feature)

```text
F:/ai-code/java-agent/specs/001-agent-core-spec/
├─ plan.md              # This file (/speckit.plan command output)
├─ research.md          # Phase 0 output (/speckit.plan command)
├─ data-model.md        # Phase 1 output (/speckit.plan command)
├─ quickstart.md        # Phase 1 output (/speckit.plan command)
├─ contracts/           # Phase 1 output (/speckit.plan command)
└─ tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
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
    TaskStreamRequest
  domain/event/
    EventType
    StreamEvent
  history/
    EventLogService
    TimelineService
    EventLogRepository
  memory/
    MemoryStore
    VectorStore
    MemoryRecord
  scheduler/
    ScheduleManager
    ScheduleRepository
    ScheduleExecutionRepository
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

src/test/java/com/example/agent/
  (tests by module)
```

**Structure Decision**: 采用单体服务结构，包路径与规格映射表保持一致。

## Complexity Tracking

无宪章违规项。

## Phase 0：工程骨架与基础设施

### 1. 目标说明
- 建立 `Spring Boot 3` 工程骨架与基础依赖管理
- 固化统一输出结构、错误码、日志与可观测性基线
- 暴露 `OpenAPI` 文档并提供契约导出与漂移校验基线（`springdoc` 导出 + 对比测试）
- 建立多租户上下文传递与鉴权扩展点框架
- 明确阻塞访问隔离与幂等控制的基础策略
- 建立全局异常处理与错误码映射

### 2. 新增/修改的 Java package 列表
- `com.example.agent.common`
- `com.example.agent.observability`
- `com.example.agent.auth`
- `com.example.agent.gateway.controller`
- `com.example.agent.domain.event`
- `com.example.agent.contracts`

### 3. 关键接口 / 核心类清单
- `ApiResponse`：统一响应封装
- `ErrorResponse`：统一错误结构
- `GlobalExceptionHandler`：全局异常映射
- `OpenApiConfig`：`OpenAPI` 文档暴露与元信息配置
- `OpenApiContractDriftTest`：契约漂移校验测试
- `MetricsPublisher`：指标发布入口
- `TracingPublisher`：链路追踪入口
- `TenantResolver`：租户解析扩展点
- `TenantContextFilter`：租户上下文传递
- `AuthService`：鉴权扩展点
- `EventType`：事件类型枚举

### 4. Spring 组件选型说明
- 基础依赖采用 `Spring Boot Starter` 管理
- Web 端采用 `Spring WebFlux`
- 配置采用 `Spring Boot Configuration Properties`
- 日志采用 `slf4j` + `Spring Boot Actuator`
- 监控采用 `Micrometer`

### 5. 关键事件类型 / DTO 清单
- `ApiResponse`、`ErrorResponse`
- `TenantContext`、`UserContext`
- `EventType`

### 6. 存储与状态管理策略
- 仅落地连接配置与数据源占位，不写入业务数据
- `PostgreSQL` 与 `Redis` 连接配置准备
- 阻塞式数据访问使用 `boundedElastic` 隔离线程池
- 幂等键缓存策略预留至 `Redis`，按 `tenantId` 维度隔离

### 7. 日志与可观测性要点
- 请求入口与过滤器记录 `info` 日志（含 `traceId`、`requestId`）
- 全局异常处理记录 `error` 日志与上下文
- 暴露基础健康检查与指标端点
- 关键日志统一输出 `tenantId` 与 `eventId`（如可用）

### 8. 验收标准
- 工程可编译启动
- 统一输出结构对外可用
- 日志与指标可在本地观测
- `OpenAPI` 文档可访问且契约导出与对比校验通过

### 9. 风险与依赖说明
- 依赖基础中间件可用性（`PostgreSQL`、`Redis`）
- 依赖统一错误码字典冻结以避免后续反复修改

## Phase 1：agent-core + event-bus + streaming SSE API

### 1. 目标说明
- 实现 `agent core` 与 `orchestrator` 的任务接收与编排入口
- 建立内部事件总线与 `SSE` 实时事件流
- 完成任务提交、查询与事件订阅最小闭环
- 落地多租户与鉴权最小可用能力并贯通上下文

### 2. 新增/修改的 Java package 列表
- `com.example.agent.orchestrator`
- `com.example.agent.agentcore`
- `com.example.agent.streaming`
- `com.example.agent.domain.event`
- `com.example.agent.gateway.controller`
- `com.example.agent.common`
- `com.example.agent.observability`
- `com.example.agent.auth`（鉴权与多租户 `MVP`）

### 3. 关键接口 / 核心类清单
- `TaskOrchestrator`：任务编排与驱动入口
- `WorkflowRouter`：工作流路由与分派
- `EnforcementGateway`：策略与隔离门
- `ToolRegistry`：工具注册
- `ToolCache`：工具缓存
- `ToolExecutor`：工具执行
- `SandboxExecutor`：沙箱执行扩展点
- `EventStreamService`：事件流输出
- `SseStreamController`：`SSE` 订阅控制器
- `TaskSubmissionService`：任务提交接口
- `TaskQueryService`：任务查询接口
- `AuthService`：鉴权服务
- `ApiKeyAuthenticator`：`API Key` 鉴权
- `TenantResolver`：租户解析
- `TenantContextFilter`：上下文过滤器

### 4. Spring 组件选型说明
- `Spring WebFlux` 作为控制器与 `SSE` 输出框架
- 事件总线采用 `Spring ApplicationEvent` + `Reactor Sinks`
- 外部调用优先使用 `Spring WebClient`

### 5. 关键事件类型 / DTO 清单
- `TaskRequest`、`TaskResponse`、`TaskStatusResponse`、`TaskQuery`、`TaskListResponse`
- `TaskStreamRequest`、`StreamCursor`
- `StreamEvent`、`EventType`
- `StreamEvent` 必须包含 `eventId`、`schemaVersion`、`tenantId`、`streamId`、`seq`
- `TaskRequest` 必须包含 `idempotencyKey`
- 事件类型：`WORKFLOW_STARTED`、`WORKFLOW_COMPLETED`、`AGENT_STARTED`、
  `AGENT_COMPLETED`、`ERROR_OCCURRED`、`LLM_PARTIAL`、`LLM_OUTPUT`、
  `TOOL_INVOKED`、`TOOL_OBSERVATION`、`TOOL_ERROR`

### 6. 存储与状态管理策略
- `task_executions` 表存储任务主数据与状态
- `Redis Stream` 保存全量事件并支持断线续传
- `last_event_id` 与 `seq` 按事件序列单调递增
- `eventId` 使用 `streamId:seq` 作为唯一键并映射到 `SSE` 的 `id`
- `Last-Event-ID` Header 优先于 `last_event_id` 参数
- 幂等键缓存用于任务重复提交去重
- 事件发布采用至少一次语义，消费端以 `eventId` 去重

### 7. 日志与可观测性要点
- 任务提交、事件发布、订阅建立记录 `info` 日志
- 事件流延迟与订阅数量输出指标
- 任务完成耗时与失败计数输出指标
- 事件日志必须包含 `tenantId`、`eventId` 与 `workflowId`

### 8. 验收标准
- 提交任务返回任务标识与订阅入口
- `SSE` 订阅支持类型过滤与断线续传
- 事件序列有序且可重放
- 关键指标可观测
- 同一 `idempotencyKey` 重复提交返回相同 `taskId`
- `Last-Event-ID` 优先级校验通过
- `SSE` 订阅仅返回当前租户事件
- 超出保留窗口时返回 `STREAM_GAP` 并关闭连接

### 9. 风险与依赖说明
- 依赖 Phase 0 统一响应结构与鉴权扩展点
- 事件流量较大时需关注 `Redis Stream` 压力

### 宪章复核（Phase 1 后）
- 技术栈、统一输出、日志、`Shannon` 对齐与 `Spring` 生态优先：通过
- 多租户与预算扩展点：已保留

## Phase 1.5：Agent Runtime

### 1. 目标说明
- 落地 `ReAct` 决策循环与 `Step` 状态机，支持终止条件与循环护栏
- 定义失败分类与恢复策略（`retry`/`fallback`/`decompose`/`stop`）
- 引入 `Planner`/`Reflection` 自修复能力，避免无限迭代
- 支持 `MCP` 工具协议、`Skill` 路由版本与 `Hook` 扩展
- 补齐 `LlmClient`/`ModelProvider` 抽象与多模型配置，提供 `ModelRegistry`/`ModelRouter` 路由能力

### 2. 新增/修改的 Java package 列表
- `com.example.agent.runtime`
- `com.example.agent.planning`
- `com.example.agent.reflection`
- `com.example.agent.tools`
- `com.example.agent.model`

### 3. 关键接口 / 核心类清单
- `StepRuntimeService`：步骤驱动与状态机推进
- `StepStateMachine`：状态迁移规则
- `AgentRuntime`：`ReAct` 决策循环驱动
- `PlannerService`：任务拆分与计划生成
- `ReflectionService`：质量评估与反馈重写
- `LlmClient`：模型调用客户端抽象
- `ModelProvider`：模型供应商适配器
- `ModelRegistry`：多模型注册与检索
- `ModelRouter`：模型路由与选型策略
- `ModelConfigProperties`：多模型配置绑定
- `McpToolClient`：`MCP` 工具列表与调用
- `SkillRegistry`：`Skill` 注册表与版本路由
- `HookManager`：pre/post tool、pre/post step 执行器

### 4. Spring 组件选型说明
- `Spring WebFlux` + `WebClient` 用于 `MCP` 调用与异步观察
- `Spring Boot Configuration Properties` 管理循环护栏与 `Skill`/`Hook` 配置
- 多模型配置使用 `Spring Boot Configuration Properties` 绑定 `application.yml`
- 超时与退避使用 `Reactor` 操作符实现，治理矩阵在 Phase 3 补齐

### 5. 关键事件类型 / DTO 清单
- `StepRecord`、`StepRequest`、`StepResponse`、`StepQuery`、`StepTimelineResponse`
- `PlanRequest`、`PlanResult`
- `ReflectionRequest`、`ReflectionResult`
- `McpToolListRequest`、`McpToolListResponse`、`McpToolCallRequest`、`McpToolCallResponse`
- `SkillDefinition`、`HookContext`、`HookDecision`
- 事件类型：`STEP_STARTED`、`STEP_COMPLETED`、`STEP_FAILED`、`PLAN_GENERATED`、`REFLECTION_COMPLETED`、
  `HOOK_PRE_TOOL`、`HOOK_POST_TOOL`、`HOOK_PRE_STEP`、`HOOK_POST_STEP`

### 6. 存储与状态管理策略
- `task_steps` 持久化 `StepRecord` 用于步骤时间线与回放
- `plans` 与 `reflection_reports` 持久化规划与反思结果
- `tool_registry`、`skill_registry`、`hook_records` 记录工具与扩展配置
- 关键状态按 `tenantId` 过滤，步骤序列按 `workflowId` 单调递增

### 7. 日志与可观测性要点
- 步骤状态迁移记录 `info` 日志，包含 `stepId`、`stepSeq`、`status`
- `MCP` 调用与 `Hook` 决策记录 `info`/`warn` 日志
- 指标输出 `step.count`、`step.duration.ms`、`tool.call.count`、`hook.block.count`

### 8. 验收标准
- `ReAct` 循环按护栏终止，终止原因可追溯
- `Step` 状态机可查询并生成步骤时间线
- `Planner` 输出依赖关系与执行策略
- `Reflection` 触发自修复且无无限循环
- `MCP` `tools/list` 与 `tools/call` 可用，`Skill` 路由与 `Hook` 执行生效
- 多模型配置可加载且路由结果可追溯

### 9. 风险与依赖说明
- 依赖 Phase 1 事件序列与 `SSE` 基础链路
- `MCP` 服务可用性与工具权限配置影响稳定性
- `Hook` 执行链过长可能引入延迟

### 宪章复核（Phase 1.5 后）
- 决策循环与 `Step` 状态机遵循统一事件与日志规范：通过
- `MCP`/`Skill`/`Hook` 扩展点满足安全与可观测要求：通过

## Phase 2：task-history / timeline + memory-system

### 1. 目标说明
- 完成关键事件持久化与历史查询
- 生成任务时间线（摘要与完整）
- 建立多层记忆存储与检索能力
- 明确历史、时间线与记忆的职责边界与一致性策略

### 2. 新增/修改的 Java package 列表
- `com.example.agent.history`
- `com.example.agent.memory`
- `com.example.agent.gateway.controller`
- `com.example.agent.domain.event`
- `com.example.agent.common`

### 3. 关键接口 / 核心类清单
- `EventLogService`：事件持久化与查询
- `TimelineService`：时间线生成
- `EventLogRepository`：事件日志存储
- `MemoryStore`：记忆存取接口
- `VectorStore`：向量存储接口
- `MemoryRecord`：记忆实体

### 4. Spring 组件选型说明
- `Spring Data JDBC` 管理事件日志与记忆元数据
- `Spring Data Redis` 作为缓存与事件流水存储

### 5. 关键事件类型 / DTO 清单
- `EventLogRecord`、`EventLogPage`、`EventQuery`、`TimelineRecord`、`TimelineRequest`、`TimelineResponse`
- `MemoryRecord`、`MemoryChunk`、`MemoryQuery`、`MemorySearchResult`
- 事件类型：`TOOL_INVOKED`、`ERROR_OCCURRED`、`MEMORY_SAVED`

### 6. 存储与状态管理策略
- `Redis` 保存全量事件，默认 `TTL` 与容量阈值
- `PostgreSQL` 持久化关键事件（排除高频 `LLM_PARTIAL`）
- 记忆元数据入库，向量数据通过 `VectorStore` 接口落地
- 事件写入顺序为先写 `Redis` 后异步补写 `PostgreSQL`，失败需重试并输出 `ERROR_OCCURRED`
- `EventLogRecord` 以 `eventId` 为幂等键，重复写入去重
- 时间线仅基于持久化事件生成，允许与实时流存在短暂延迟
- 历史与时间线查询必须按 `tenantId` 过滤

### 7. 日志与可观测性要点
- 事件持久化成功率与失败计数指标
- 时间线生成耗时与查询耗时指标
- 记忆检索延迟与命中率指标
- 持久化失败时记录 `eventId`、`tenantId` 与重试次数

### 8. 验收标准
- 事件日志可查询且与 `SSE` 输出一致
- 时间线支持摘要与完整模式
- 记忆检索返回近期与语义结果
- 时间线结果仅基于持久化事件生成

### 9. 风险与依赖说明
- 依赖 Phase 1 事件序列一致性
- 向量存储实现需保持接口兼容与替换边界

## Phase 2.5：Multi-agent + Advanced Reasoning

### 1. 目标说明
- 引入多智能体编排（`Supervisor`/`DAG`/`Handoff`）
- 支持并行/串行/混合执行与失败传播
- 落地 `ToT`/`Debate`/`Deep Research`，输出可追溯引用结构
- 建立 `web-search`/`web-fetch` 工具链与引用追踪
- 补齐 `AgentProfile`/`AgentConfig` 与 `DeepResearchWorkflow` 配置层

### 2. 新增/修改的 Java package 列表
- `com.example.agent.multiagent`
- `com.example.agent.reasoning`
- `com.example.agent.research`

### 3. 关键接口 / 核心类清单
- `MultiAgentCoordinator`：多智能体编排入口
- `SupervisorCoordinator`：角色分配与调度
- `HandoffService`：交接与上下文传递
- `AgentGraphExecutor`：`DAG` 执行器
- `AgentProfile`：智能体角色与能力画像
- `AgentConfig`：角色配置与权限边界
- `ThoughtTreeService`：`ToT` 分支搜索
- `DebateCoordinator`：辩论流程协调
- `ResearchPipeline`：研究链路与引用汇总
- `DeepResearchWorkflowConfig`：研究流程配置与约束

### 4. Spring 组件选型说明
- 并行执行基于 `Reactor` 并发调度与背压机制
- 研究链路外部调用使用 `WebClient`，引用解析使用 `Jackson`
- 配置层使用 `Spring Boot Configuration Properties` 绑定 `application.yml`

### 5. 关键事件类型 / DTO 清单
- `AgentRole`、`HandoffRecord`、`HandoffRequest`、`HandoffResult`
- `ThoughtNode`、`DebateRound`、`ResearchCitation`
- 事件类型：`HANDOFF_REQUESTED`、`HANDOFF_COMPLETED`、`THOUGHT_EXPANDED`、
  `DEBATE_ROUND_COMPLETED`、`RESEARCH_SOURCE_ADDED`

### 6. 存储与状态管理策略
- `agent_runs`、`agent_handoffs` 持久化编排与交接记录
- `reasoning_runs`、`research_sources`、`research_reports` 持久化推理与引用
- 引用按 `tenantId`、`taskId` 分区，支持回溯

### 7. 日志与可观测性要点
- 编排开始/结束、交接事件记录 `info`
- 分支数量与剪枝结果输出指标 `reasoning.tot.branch.count`
- 引用数量输出 `research.citation.count`

### 8. 验收标准
- `DAG` 依赖执行、`Supervisor` 角色分配与 `Handoff` 事件可追溯
- `ToT` 最小实现可生成多候选并基于评分剪枝
- `Deep Research` 输出包含 `citations` 且可追溯来源
- `AgentProfile`/`AgentConfig` 与 `DeepResearchWorkflow` 配置可加载且可被编排引用

### 9. 风险与依赖说明
- 依赖 Phase 1.5 `Step` 状态机与 `Hook` 基础
- 外部搜索与抓取工具可用性影响研究稳定性
- 分支爆炸需配合 `beamWidth` 与预算限制

## Phase 3：scheduled-tasks + authentication / multitenancy + token-budget-tracking + production / enterprise

### 1. 目标说明
- 完成定时任务管理与执行历史
- 基于 `Phase 1` 最小可用能力，增强鉴权与多租户策略（`RBAC`、审计、可信上游开关）
- 实现 `token` 预算计量、聚合与阈值事件
- 完成租户隔离在调度、事件传播与预算链路中的落地
- 补齐生产治理能力（回放/可重放调试、限流/背压、熔断/超时/重试矩阵）
- 落地企业安全能力（`OPA` 策略评估、`WASI` 沙箱执行、预算触发模型降级）

### 2. 新增/修改的 Java package 列表
- `com.example.agent.scheduler`
- `com.example.agent.auth`（鉴权增强）
- `com.example.agent.budget`
- `com.example.agent.governance`
- `com.example.agent.policy`
- `com.example.agent.sandbox`
- `com.example.agent.model`
- `com.example.agent.gateway.controller`
- `com.example.agent.common`
- `com.example.agent.observability`

### 3. 关键接口 / 核心类清单
- `ScheduleManager`：定时任务管理
- `ScheduleRepository`：定时任务存储
- `ScheduleExecutionRepository`：执行记录存储
- `AuthService`：鉴权服务
- `ApiKeyAuthenticator`：`API Key` 鉴权
- `TenantResolver`：租户解析
- `TenantContextFilter`：上下文过滤器
- `TokenBudgetManager`：预算管理
- `TokenUsageRepository`：预算记录存储
- `CostCalculator`：成本计算
- `ReplayService`：回放与可重放调试
- `RateLimitService`：限流与背压控制
- `CircuitBreakerManager`：熔断器管理
- `PolicyEngine`：`OPA` 策略评估
- `WasiSandboxExecutor`：`WASI` 沙箱执行
- `ModelFallbackPolicy`：模型降级策略

### 4. Spring 组件选型说明
- 调度采用 `Spring Scheduling`，复杂场景可扩展 `Quartz`
- 鉴权采用 `WebFilter` + `TenantContextFilter` 组合，不引入 `Spring Security` 过滤链；
  原因：已有 `WebFlux` 过滤器链满足 `MVP`，避免双重链路；安全边界：所有入口强制经过过滤器链路。
- 存储采用 `Spring Data JDBC` 与 `Spring Data Redis`
- 生产治理优先采用 `resilience4j` 的 `Spring Boot` 集成实现限流与熔断
- `OPA` 策略评估通过 `WebClient` 调用或嵌入式引擎适配器接入
- `WASI` 运行时通过可插拔适配层接入并暴露资源限制配置

### 5. 关键事件类型 / DTO 清单
- `ScheduleSpec`、`ScheduleResponse`、`ScheduleExecutionRecord`、`ScheduleQuery`、`SchedulePage`
- `TokenUsageRecord`、`TokenUsageSummary`、`TokenUsageInput`
- `ReplayRequest`、`ReplayResponse`、`ReplaySession`
- `PolicyRequest`、`PolicyDecision`
- `SandboxRequest`、`SandboxResult`
- `ModelFallbackDecision`、`RateLimitRule`、`CircuitState`
- `TenantContext`、`UserContext`
- `TokenUsageInput` 必须包含 `usageId`
- 事件类型：`SCHEDULE_TRIGGERED`、`AUTH_FAILED`、`BUDGET_THRESHOLD`、`REPLAY_STARTED`、
  `BACKPRESSURE_APPLIED`、`CIRCUIT_OPENED`、`POLICY_DENIED`、`SANDBOX_VIOLATION`、`MODEL_FALLBACK_APPLIED`

### 6. 存储与状态管理策略
- `scheduled_tasks` 与 `scheduled_task_executions` 持久化
- `token_usage` 记录预算与成本
- `replay_sessions`、`runtime_limits` 落地回放与治理配置
- `policy_audit` 记录策略评估与拒绝结果
- `sandbox_runs` 记录沙箱执行与资源限制
- `model_fallbacks` 记录模型降级触发与目标模型
- `auth.tenants` 保存租户配置与鉴权信息
- `TokenUsageInput` 由 `ToolExecutor` 产出并调用 `TokenBudgetManager.recordUsage` 进入计量链路
- 预算记录使用 `usageId` 去重，推荐 `taskId:seq` 作为幂等键
- 定时任务执行必须携带 `tenantId`，缺失时标记失败并记录原因

### 7. 日志与可观测性要点
- 定时任务触发与失败记录 `info`/`error` 日志
- 鉴权失败计数与拒绝原因指标
- 预算使用量与超阈值事件指标
- 预算事件记录 `tenantId`、`taskId` 与 `usageId`
- 回放、限流/背压与熔断记录 `info`/`warn` 日志
- 策略拒绝与沙箱违规记录 `error` 日志并输出审计指标
- 指标覆盖 `replay.count`、`rate.limit.count`、`circuit.open.count`、`policy.deny.count`、`sandbox.violation.count`

### 8. 验收标准
- 定时任务支持创建、暂停、恢复、删除与历史查询
- 多租户隔离校验通过且无跨租户数据泄漏
- `token` 预算聚合结果可查询并可触发阈值事件
- 相同 `usageId` 不重复入库且聚合结果一致
- 回放可复现步骤序列且事件轨迹一致
- `OPA` 拒绝返回 `POLICY_DENIED` 并记录审计
- `WASI` 资源限制与超时行为可验证
- 预算阈值触发模型降级并记录 `MODEL_FALLBACK_APPLIED`

### 9. 风险与依赖说明
- 依赖 Phase 1 事件总线用于阈值事件通知
- 依赖 Phase 0 统一错误码以保证鉴权响应一致
- `OPA` 服务可用性与策略热更新影响拒绝策略一致性
- `WASI` 运行时性能与资源限制配置影响工具执行稳定性

## Phase 4：文档与验证收尾

### 1. 目标说明
- 不新增核心能力，仅完成文档对齐、验证基线与维护项收尾
- 补齐 `Shannon` 映射表与差异说明，形成可追溯对齐结果
- 完成性能与可靠性基线验证，沉淀可复现口径
- 同步接口契约与快速验证指引

### 2. 产出清单
- `spec.md` 映射表与差异说明更新
- `contracts/openapi.yaml` 对齐更新
- `quickstart.md` 快速验证指引更新
- 性能与安全验证结果记录

### 3. 验收标准
- 文档与契约一致性校验通过
- 性能与可靠性基线记录可追溯且符合规格口径
- 维护类任务不引入新增功能

### 4. 风险与依赖说明
- 依赖 Phase 1~3 的实现与测试结果
