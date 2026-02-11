# ai-agent-book-part5 旧版缺陷复核重评与补充方案（2026-02-11 07:26）

## 1. 复核目标与输入

- 复核对象：
  - `doc/old/ai-agent-book-part5-coverage-20260127.md`
  - `doc/old/ai-agent-book-part5-coverage-20260127-v2.md`
- 对齐基线（总体复核）：
  - `doc/shannon-alignment-evidence-recheck-v2-202602102356.md`
  - `doc/功能复核实现改造文件影响与包设计分级-202602110003.md`
- 对照源码：
  - 本项目：`src/main/java`、`src/test/java`
  - Shannon：`vendor/Shannon`（只读）
- 规格依据：
  - `specs/001-agent-core-spec/spec.md`
  - `specs/001-agent-core-spec/contracts/openapi.yaml`
  - `specs/001-agent-core-spec/quickstart.md`
  - `specs/001-agent-core-spec/checklists/checklist.md`
- 执行约束：仅静态分析与文档产出；未修改代码，未执行测试。

## 2. 全量检索方法与覆盖证明

### 2.1 检索范围

- 主代码：`src/main/java`
- 测试代码：`src/test/java`
- 两个目录均执行了包名、类名、关键词三级检索。

### 2.2 包名级检索结论

| 检索项 | `main` 命中 | `test` 命中 | 结论 |
| --- | ---: | ---: | --- |
| `package com.example.agent.orchestration.multiagent` | 9 | 0 | 当前多智能体主实现位于 `orchestration.multiagent` |
| `package com.example.agent.multiagent` | 0 | 1 | 旧包仅在测试包名残留，不是主实现路径 |

### 2.3 类名级检索结论（与旧报告强相关）

| 类名/对象 | `main` 命中 | `test` 命中 | 结论 |
| --- | ---: | ---: | --- |
| `MultiAgentCoordinator` | 8 | 10 | 已实现 |
| `MultiAgentStepExecutor` | 2 | 0 | 已接入步骤执行路由 |
| `StepExecutorRouter` | 7 | 9 | 已实现步骤类型路由 |
| `AgentGraph` / `AgentGraphExecutor` | 0 | 2 / 1 | 主代码缺失，仅在守卫测试字符串中出现 |
| `SupervisorCoordinator` | 0 | 0 | 主测代码均缺失 |
| `HandoffService` / `HandoffRequest` / `HandoffResult` / `HandoffRecord` | 0 | 1 / 1 / 1 / 1 | 主代码缺失，仅在守卫测试字符串中出现 |

### 2.4 关键词级检索结论（与 FR-021/023/024 相关）

| 关键词 | `main` 命中 | `test` 命中 | 结论 |
| --- | ---: | ---: | --- |
| `DAG` | 0 | 0 | 未见 DAG 执行实现证据 |
| `topological` | 0 | 0 | 未见拓扑排序实现证据 |
| `supervisor` | 0 | 0 | 未见监督者工作流实现证据 |
| `handoff` | 0 | 0 | 未见交接服务/协议实现证据 |
| `dependsOn` | 25 | 2 | 仅见规划/输入结构字段，不等于 DAG 调度器 |
| `TEAM_RECRUITED` | 2 | 0 | 事件枚举 + 发布点存在 |
| `ROLE_ASSIGNED` | 2 | 0 | 事件枚举 + 发布点存在 |
| `HANDOFF_REQUESTED` | 1 | 0 | 仅事件枚举存在，未见发布 |
| `HANDOFF_COMPLETED` | 1 | 0 | 仅事件枚举存在，未见发布 |
| `MESSAGE_SENT` / `MESSAGE_RECEIVED` / `WORKSPACE_UPDATED` / `TEAM_STATUS` | 1 / 1 / 1 / 1 | 0 | 仅事件枚举存在，未见发布 |
| `LLM_OUTPUT` | 1 | 1 | 仅事件枚举 + 守卫测试，不存在发布实现 |
| `OPA` | 0 | 0 | 未见 OPA 接入实现 |
| `BACKPRESSURE_APPLIED` | 1 | 0 | 仅事件枚举存在，未见发布实现 |

## 3. 两份旧报告缺陷逐项复核（当前工程视角）

### 3.1 结论说明

- 本节“是否缺陷”按当前工程代码基线判断。
- 三态含义：`Found Evidence`（已实现）、`Confirmed Missing`（确认缺失）、`Requires Runtime Only`（仅能运行验证）。

### 3.2 条目复核表

| 来源 | 原条目 | 当前判定 | 三态 | 结论 |
| --- | --- | --- | --- | --- |
| 20260127 | “多智能体编排核心机制覆盖完整，整体满足目标要求” | 不成立 | `Confirmed Missing` | 角色分配已实现，但 `DAG/Supervisor/Handoff` 核心能力仍缺失 |
| 20260127 | “状态模型与一致性规则缺少系统说明” | 部分成立 | `Found Evidence` + `Confirmed Missing` | 已有 `StepStateMachine` 与 `RuntimeContext`（通用状态），但缺少多智能体交接状态模型 |
| 20260127-v2 | 多智能体角色分配与团队组建 | 成立 | `Found Evidence` | `MultiAgentCoordinator#coordinate` + `MultiAgentEventPublisher#publishTeamEvents` |
| 20260127-v2 | DAG 依赖执行与循环依赖检测缺失 | 成立 | `Confirmed Missing` | 仅有 `dependsOn` 字段解析与透传，无 DAG 调度/拓扑/环检测执行器 |
| 20260127-v2 | 监督者工作流与失败阈值治理缺失 | 成立 | `Confirmed Missing` | 当前无 `Supervisor` 工作流；无失败阈值传播策略 |
| 20260127-v2 | 交接上下文透传（旧文档指向 `HandoffService`） | 旧证据失效，替代能力部分存在 | `Found Evidence`（通用步间） | `RuntimeContextUpdateService` 完成步间上下文回写，但不是 FR-021 的 `HandoffService` |
| 20260127-v2 | 交接统一状态模型与一致性规则缺失 | 成立 | `Confirmed Missing` | 无 `HandoffRecord` 生命周期、版本冲突与恢复策略 |
| 20260127-v2 | 交接消息协商与工作空间同步缺失 | 成立 | `Confirmed Missing` | `MESSAGE_*`、`WORKSPACE_UPDATED` 仅在枚举存在，无发布链路 |
| 20260127-v2 | 占位类证据（`AgentGraph*`、`Handoff*`） | 旧证据失效 | `Confirmed Missing`（主代码） | 当前由 `OrchestrationArchitectureGuardTest` 显式禁止占位类落回根包 |

## 4. 与总体复核合并后的最终缺陷清单（重评级）

> 说明：以下重评级同时吸收旧报告复核结果与总体复核结果。

### 4.1 P0

#### P0-01 `LLM_OUTPUT` 终态事件缺失（仍成立）

- 本项目证据：
  - `src/main/java/com/example/agent/streaming/domain/EventType.java` 仅定义 `LLM_OUTPUT`
  - `src/main/java/com/example/agent/capabilities/llm/client/LlmEventPublisher.java` 仅发布 `LLM_PROMPT`、`LLM_PARSE`
  - `src/test/java/com/example/agent/capabilities/llm/architecture/LlmArchitectureGuardTest.java` 明确禁止在 `ModelInvocationService` 回流 `LLM_OUTPUT`
- Shannon 证据：
  - `vendor/Shannon/docs/event-types.md` 定义 `LLM_OUTPUT`
  - `vendor/Shannon/docs/event-types.md` 将 `LLM_OUTPUT` 作为最终输出关键事件
- 规格关联：
  - `spec.md` 事件清单要求 `LLM_OUTPUT`（`spec.md:832`）
  - `spec.md` `SSE` 映射要求 `thread.message.completed -> LLM_OUTPUT`（`spec.md:864`）
  - `openapi.yaml` 仅定义 `SSE` 通道，不保证语义自动成立
- 结论：缺陷成立，保持 `P0`。

### 4.2 P1

#### P1-01 FR-021 的 DAG 执行与环检测缺失（仍成立）

- 本项目证据：
  - `PlanParser#buildStepSpec` 可解析 `dependsOn`（`src/main/java/com/example/agent/planning/parser/PlanParser.java`）
  - `AgentRuntime` 采用 `for (StepSpec step : plan.getSteps())` 顺序执行（`src/main/java/com/example/agent/runtime/engine/AgentRuntime.java`）
  - `OrchestrationArchitectureGuardTest` 仅保留对 `AgentGraph*` 占位类的禁止检查（无运行实现）
- Shannon 证据：
  - `vendor/Shannon/go/orchestrator/internal/workflows/strategies/dag.go` 存在 `DAGWorkflow`
  - 同文件存在 `ValidateDAGDependencies`、`ExecuteSequential`、`ExecuteHybrid`
- 规格关联：
  - `spec.md` FR-021 明确要求 `DAG`（`spec.md:408`）
  - `spec.md` 验收要求可配置并行策略与可追溯事件（`spec.md:416`）
  - `openapi.yaml` 无多智能体专用接口定义（关键词检索无命中）
- 结论：缺陷成立，维持 `P1`。

#### P1-02 FR-021 的 Supervisor 调度与失败传播缺失（仍成立）

- 本项目证据：
  - `orchestration.multiagent` 仅含 `MultiAgentCoordinator`、`MultiAgentRoleResolver`、`MultiAgentEventPublisher`
  - 未检索到 `SupervisorCoordinator`/`supervisor` 实现
  - 仅有 `TEAM_RECRUITED`、`ROLE_ASSIGNED` 发布，无失败阈值治理
- Shannon 证据：
  - `vendor/Shannon/go/orchestrator/internal/workflows/supervisor_workflow.go` 存在 `SupervisorWorkflow`
  - 同文件存在 `maxFailures`、`mailbox_v1`、`listTeamAgents` 等团队治理机制
- 规格关联：
  - `spec.md` Supervisor 关键规则（`spec.md:411`）
  - `spec.md` 失败传播策略要求（`spec.md:413`）
- 结论：缺陷成立，维持 `P1`。

#### P1-03 FR-021 的 Handoff 机制缺失（仍成立）

- 本项目证据：
  - 主代码不存在 `HandoffService` / `HandoffRequest` / `HandoffResult` / `HandoffRecord`
  - `EventType` 虽定义 `HANDOFF_REQUESTED`、`HANDOFF_COMPLETED`，但无发布点
  - 旧 v2 文档中的 `com.example.agent.multiagent.HandoffService` 证据路径已失效
- Shannon 证据：
  - `vendor/Shannon/go/orchestrator/internal/workflows/supervisor_workflow.go` 存在 mailbox、topic 协调、workspace 同步
  - `vendor/Shannon/docs/p2p-coordination.md` 说明 `produces/consumes` + workspace 交互
- 规格关联：
  - `spec.md` FR-021 Handoff 规则（`spec.md:412`、`spec.md:417`）
  - `spec.md` 实体要求 `HandoffRecord`（`spec.md:122`）
  - `spec.md` 接口草案含 `handoff(HandoffRequest)`（`spec.md:704`、`spec.md:706`）
- 结论：缺陷成立，维持 `P1`。

#### P1-04 FR-024 的 OPA 接入缺失（来自总体复核，仍成立）

- 本项目证据：
  - `src/main/java/com/example/agent/governance/policy/PolicyEngine.java` 为本地规则判断
  - `src/main/java`、`src/test/java` 检索 `OPA`/`Opa` 均无实现命中
- Shannon 证据：
  - `vendor/Shannon` 侧存在 `OPA` 引擎接入实现（总体复核已对齐）
- 规格关联：
  - `spec.md` FR-024 要求 OPA（`spec.md:485`、`spec.md:488`、`spec.md:527`）
  - `checklist.md` CHK044 明确 OPA 验证项（`checklist.md:81`）
- 结论：缺陷成立，维持 `P1`。

### 4.3 P2

#### P2-01 多智能体交接状态一致性规则缺失（仍成立）

- 本项目证据：
  - 已有通用步骤状态机 `StepStateMachine` 与 `StepRuntimeService`（可证明“通用状态管理”）
  - 但不存在 `HandoffRecord` 生命周期、版本、冲突处理、恢复策略
- Shannon 证据：
  - `supervisor_workflow.go` 在消息、topic、workspace 上有状态等待与推进机制
  - `p2p-coordination.md` 明确依赖等待与数据交换流程
- 规格关联：
  - `spec.md` Handoff 记录实体定义（`spec.md:122`）
  - `spec.md` Handoff 事件与权限校验完整性要求（`spec.md:417`）
- 结论：缺陷成立，维持 `P2`。

#### P2-02 `BACKPRESSURE_APPLIED` 事件矩阵缺失（来自总体复核，仍成立）

- 本项目证据：
  - `EventType` 定义 `BACKPRESSURE_APPLIED`
  - `EnforcementGateway`、`ToolExecutor`、`RateLimitService`、`CircuitBreakerManager` 未发布该事件
- Shannon 证据：
  - Shannon 生产治理链路包含背压/熔断可观测事件
- 规格关联：
  - `spec.md` FR-023 与关键规则要求背压事件（`spec.md:436`、`spec.md:440`）
  - 验收要求“限流/背压与熔断可观测”（`spec.md:480`）
- 结论：缺陷成立，维持 `P2`。

## 5. 如何补充：设计建议与涉及文件

### 5.1 针对旧报告缺陷（FR-021）

#### A. DAG 执行器补齐（建议优先级：P1，复杂度：L4）

- 建议设计：
  - 在 `orchestration.multiagent` 下新增 `dag` 子包，避免回到根包占位类模式。
  - 将“依赖解析”与“执行调度”分离：`DagCycleDetector` + `DagExecutionCoordinator`。
  - 保留 `PlanParser` 的 `dependsOn`，增加执行前依赖图校验与错误码映射。
- 涉及文件（建议）：
  - 修改：`src/main/java/com/example/agent/runtime/engine/AgentRuntime.java`
  - 修改：`src/main/java/com/example/agent/runtime/step/executor/MultiAgentStepExecutor.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/dag/DagCycleDetector.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/dag/DagExecutionCoordinator.java`
  - 新增测试：`src/test/java/com/example/agent/orchestration/multiagent/dag/DagCycleDetectorTest.java`
  - 新增测试：`src/test/java/com/example/agent/orchestration/multiagent/dag/DagExecutionCoordinatorTest.java`

#### B. Supervisor 与失败传播补齐（建议优先级：P1，复杂度：L4）

- 建议设计：
  - 新增 `supervisor` 子包，实现角色调度、失败阈值、失败传播策略。
  - 明确 `fail-fast` 与“部分成功”两种策略并可配置。
- 涉及文件（建议）：
  - 修改：`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/supervisor/SupervisorCoordinator.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/supervisor/FailurePropagationPolicy.java`
  - 新增测试：`src/test/java/com/example/agent/orchestration/multiagent/supervisor/SupervisorCoordinatorTest.java`

#### C. Handoff + 消息协商 + 工作区同步补齐（建议优先级：P1，复杂度：L4）

- 建议设计：
  - 新增 `handoff` 子包，独立交接协议对象与服务。
  - 将 `HANDOFF_REQUESTED`、`HANDOFF_COMPLETED`、`MESSAGE_SENT`、`MESSAGE_RECEIVED`、`WORKSPACE_UPDATED` 事件发布落到统一发布器。
- 涉及文件（建议）：
  - 修改：`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentEventPublisher.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/handoff/HandoffService.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/handoff/HandoffRequest.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/handoff/HandoffResult.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/handoff/HandoffRecord.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/handoff/WorkspaceSyncService.java`
  - 新增测试：`src/test/java/com/example/agent/orchestration/multiagent/handoff/HandoffServiceTest.java`

#### D. 交接状态一致性规则补齐（建议优先级：P2，复杂度：L3）

- 建议设计：
  - 为 `HandoffRecord` 增加状态机（`PENDING/WAITING/RUNNING/SUCCEEDED/FAILED/CANCELLED`）与版本字段。
  - 增加冲突检测与幂等键策略。
- 涉及文件（建议）：
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/handoff/HandoffStateMachine.java`
  - 新增：`src/main/java/com/example/agent/orchestration/multiagent/handoff/HandoffRepository.java`
  - 新增测试：`src/test/java/com/example/agent/orchestration/multiagent/handoff/HandoffStateMachineTest.java`

### 5.2 针对总体缺陷

#### E. `LLM_OUTPUT` 终态事件补齐（建议优先级：P0，复杂度：L3）

- 建议设计：
  - 在运行时收口层发布 `LLM_OUTPUT`，避免违反 `ModelInvocationService` 守卫约束。
- 涉及文件（建议）：
  - 修改：`src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java`
  - 修改：`src/main/java/com/example/agent/runtime/output/FinalOutputService.java`
  - 修改：`src/main/java/com/example/agent/runtime/engine/RuntimeEventDispatchService.java`
  - 新增/修改测试：`src/test/java/com/example/agent/runtime/FinalOutputServiceTest.java`

#### F. OPA 策略引擎补齐（建议优先级：P1，复杂度：L4）

- 涉及文件（建议）：
  - 修改：`src/main/java/com/example/agent/governance/policy/PolicyEngine.java`
  - 新增：`src/main/java/com/example/agent/governance/policy/spi/PolicyDecisionProvider.java`
  - 新增：`src/main/java/com/example/agent/governance/policy/provider/opa/OpaPolicyDecisionProvider.java`
  - 新增：`src/main/java/com/example/agent/governance/policy/provider/opa/OpaClient.java`
  - 修改：`src/main/resources/application.yml`
  - 新增测试：`src/test/java/com/example/agent/governance/OpaPolicyDecisionProviderTest.java`

#### G. 背压事件矩阵补齐（建议优先级：P2，复杂度：L2~L3）

- 涉及文件（建议）：
  - 修改：`src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java`
  - 修改：`src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java`
  - 可选修改：`src/main/java/com/example/agent/governance/ratelimit/RateLimitService.java`
  - 可选修改：`src/main/java/com/example/agent/governance/circuitbreaker/CircuitBreakerManager.java`

### 5.3 规格与文档同步（必须）

- 修改建议：
  - `specs/001-agent-core-spec/contracts/openapi.yaml`：补齐 FR-021 相关契约（若采用独立接口）
  - `specs/001-agent-core-spec/quickstart.md`：补齐多智能体 `DAG/Supervisor/Handoff` 验证步骤
  - `specs/001-agent-core-spec/checklists/checklist.md`：新增 FR-021 检查项（当前无命中）
  - `doc/old/ai-agent-book-part5-coverage-20260127-v2.md`：标注“证据路径已过时”历史声明

## 6. 包设计合理性评估（当前与目标）

### 6.1 当前等级

- 总体等级：`B`
- 主要问题：
  - `orchestration.multiagent` 仅有角色招募与事件基础能力，缺少 `dag/supervisor/handoff` 子域实现。
  - 规划层已输出依赖语义，但运行时缺少相应执行器，存在“语义有、执行无”断层。
  - 事件枚举覆盖较全，但发布链路不完整（`LLM_OUTPUT`、`BACKPRESSURE_APPLIED`、`HANDOFF_*`、`MESSAGE_*`）。

### 6.2 目标等级（完成 P0+P1 后）

- 预期等级：`A-`
- 达成条件：
  - 多智能体三子域能力闭环（`dag/supervisor/handoff`）
  - 关键事件语义闭环（`LLM_OUTPUT`、`HANDOFF_*`、`MESSAGE_*`、`WORKSPACE_UPDATED`）
  - 策略域 SPI 化并接入 OPA

## 7. Evidence Index

| 模块 | 本项目类/方法 | 关联事件/错误码 | Shannon 对照位置 |
| --- | --- | --- | --- |
| 多智能体角色分配 | `MultiAgentCoordinator#coordinate`、`MultiAgentEventPublisher#publishTeamEvents` | `TEAM_RECRUITED`、`ROLE_ASSIGNED` | `supervisor_workflow.go`（团队角色与事件） |
| 多智能体步骤接入 | `MultiAgentStepExecutor#supports/#execute`、`StepExecutorRouter#execute` | `MULTI_AGENT` 步骤路由 | `orchestrator_router.go`（工作流路由） |
| DAG 依赖元数据（仅规划） | `PlanParser#buildStepSpec`、`StepSpec#setDependsOn` | `dependsOn` | `strategies/dag.go`（真实 DAG 执行） |
| DAG 执行与环检测 | `Confirmed Missing`（运行时） | 无 | `strategies/dag.go`（`DAGWorkflow` + `ValidateDAGDependencies`） |
| Supervisor 调度 | `Confirmed Missing`（运行时） | 无 | `supervisor_workflow.go`（`SupervisorWorkflow`、`maxFailures`） |
| Handoff 协议对象与服务 | `Confirmed Missing`（主代码） | `HANDOFF_REQUESTED`、`HANDOFF_COMPLETED`（仅枚举） | `p2p-coordination.md`、`supervisor_workflow.go` |
| 消息与工作区协同 | `Confirmed Missing`（发布链路） | `MESSAGE_SENT`、`MESSAGE_RECEIVED`、`WORKSPACE_UPDATED`（仅枚举） | `event-types.md`、`p2p-coordination.md` |
| 通用步间上下文透传 | `RuntimeContextUpdateService#mergeStepInput/#updateRuntimeContext` | 上一步摘要/原始引用透传 | `p2p-coordination.md`（依赖数据传递理念） |
| 终态输出事件 | `Confirmed Missing`（`LLM_OUTPUT`） | `LLM_OUTPUT`（仅枚举） | `event-types.md`（最终输出事件） |
| 企业策略 | `PolicyEngine#evaluate`（本地规则） | `POLICY_DENIED` | Shannon OPA 路径（总体复核基线） |
| 生产治理背压 | `RateLimitService`、`CircuitBreakerManager`（无事件发布） | `BACKPRESSURE_APPLIED`（仅枚举） | Shannon 生产治理事件链路 |

## 8. 最终结论

1. 两份旧报告中“多智能体总体满足”的判断在当前工程语义下不成立，属于“结论高估 + 证据路径老化”。
2. 当前工程“已实现”与“未实现”边界明确：
   - 已实现：多智能体角色招募与 `MULTI_AGENT` 步骤接入、通用步间上下文透传。
   - 未实现：FR-021 要求的 `DAG/Supervisor/Handoff` 核心执行闭环。
3. 与总体复核合并后，仍需优先处理三类确定性缺口：
   - `P0`：`LLM_OUTPUT` 终态事件。
   - `P1`：FR-021 多智能体核心执行闭环 + OPA 接入。
   - `P2`：交接一致性规则与背压事件矩阵。

## 9. P2落地状态更新（2026-02-11 14:04）

### 9.1 完成项

- 已完成 `Handoff` 状态一致性改造：
  - 新增 `HandoffStatus`、`HandoffStateMachine`、`HandoffRepository`、`InMemoryHandoffRepository`。
  - `HandoffRecord/HandoffResult/HandoffRequest` 增加 `version`、`idempotencyKey`、`errorCode` 等字段与幂等语义。
  - `HandoffService` 接入状态机迁移、版本更新与幂等命中逻辑。
  - `SupervisorCoordinator` 接入 `WAITING/RUNNING/FAILED/SUCCEEDED` 生命周期推进与终态守卫。

- 已完成背压事件矩阵改造：
  - `RateLimitService` 与 `CircuitBreakerManager` 输出结构化决策对象。
  - `McpToolClient` 在限流/熔断场景抛出 `GovernanceRejectionException` 并携带结构化 context。
  - `EnforcementGateway` 统一发布治理事件：
    - `RATE_LIMITED` -> `BACKPRESSURE_APPLIED`
    - `CIRCUIT_OPEN` -> `CIRCUIT_OPENED` + `BACKPRESSURE_APPLIED`
  - `ToolExecutor` 在可重试退避前发布 `WAITING`，并在限流/熔断重试时补发 `BACKPRESSURE_APPLIED`。
  - 预算链路在阈值命中时新增 `BACKPRESSURE_APPLIED`（不替代 `BUDGET_THRESHOLD`）。

### 9.2 测试结果

- 关键新增测试：
  - `HandoffStateMachineTest`
  - `HandoffRepositoryTest`
  - `BackpressureEventMatrixTest`
  - `ToolExecutorTest`（新增等待/背压重试断言）

- 全流程回归：
  - 已执行三轮完整 `mvn test`，全部通过。
  - 最近一次结果：`Tests run: 605, Failures: 0, Errors: 0, Skipped: 0`。

### 9.3 剩余风险

- 当前 `HandoffRepository` 为内存实现，后续需按生产落地替换为持久化实现。
- `Supervisor` 生命周期记录目前复用 `handoff` 领域对象，后续可考虑独立生命周期模型以简化语义边界。
