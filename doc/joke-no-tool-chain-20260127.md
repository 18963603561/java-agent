# 笑话请求无工具链路说明

## 场景
- 用户通过 `POST /api/v1/tasks` 提交自然语言请求，例如“请给我讲个笑话”，要求不走工具调用。
- 请求体为 `TaskRequest`，其中 `query` 为用户问题，`sessionId`、`context` 为可选上下文，`idempotencyKey` 在当前实现中标记为必填。

## 入口与上下文
1. `TenantContextFilter#filter` 解析租户请求头并生成 `TenantContext`，写入 `ServerWebExchange` 属性与 `Reactor Context`。
2. `TaskController#submitTask` 作为入口接口，读取 `TaskRequest` 并调用 `AuthService#authenticate`。
3. `ApiKeyAuthenticator#authenticate` 解析 `API Key` 或 `JWT`，生成 `UserContext` 并写回 `TenantContext`。

## 任务提交与编排
1. `TaskOrchestrator#submitTask` 创建任务与工作流标识，保存任务记录并发布 `WORKFLOW_STARTED` 事件。
2. `WorkflowRouter#route` 将请求路由到运行时入口 `AgentRuntime#run`。
3. `TaskOrchestrator#handleWorkflowRoute` 在运行完成后更新任务状态并发布 `WORKFLOW_COMPLETED` 事件。

## 无工具执行的规划前提
- `PlannerService#plan` 在启用 `agent.planner.llm-enabled=true` 时可使用 `LLM` 规划步骤。
- 若启用兜底规则规划（`agent.planner.fallback-enabled=true`），`buildHeuristicPlan` 会固定追加 `TOOL` 步骤，因此无法保证无工具执行。
- 要满足“无工具执行”，需要 `LLM` 规划结果中的 `StepRequest.type` 不包含 `TOOL`，并保证步骤非空，否则运行时会直接返回且不会生成最终输出。

## 规划与步骤执行（不走工具）
1. `PlannerService#plan` 生成 `PlanResult`，步骤类型可能包含 `THOUGHT_TREE`、`MULTI_AGENT`、`DEBATE`、`RESEARCH` 等。
2. `AgentRuntime#executeStep` 调用 `StepRuntimeService#startStep` 记录步骤开始，并根据 `StepRequest.type` 分支执行。
3. `THOUGHT_TREE` 走 `ThoughtTreeService#buildTree`，该实现为规则化扩展，不依赖 `LLM`。
4. `MULTI_AGENT` 走 `MultiAgentCoordinator#coordinate`，内部通过 `ModelInvocationService#invoke` 调用 `LLM` 生成团队配置。
5. `DEBATE` 走 `DebateCoordinator#debate`，内部通过 `ModelInvocationService#invoke` 生成结论。
6. `RESEARCH` 走 `ResearchPipeline#run`，内部通过 `ModelInvocationService#invoke` 生成引用列表。
7. 每个步骤执行后都会调用 `ReflectionService#reflect`，根据配置决定使用 `LLM` 或规则策略进行质量评估与重试判断。

## 结果收敛与响应
1. `FinalOutputService#finalizeOutput` 汇总步骤输出，通过 `ModelInvocationService#invoke` 生成最终回答。
2. `TaskOrchestrator#handleWorkflowRoute` 写入任务结果与状态。
3. `TaskController#submitTask` 返回 `TaskResponse`，可通过 `GET /api/v1/tasks/{taskId}` 查询最终状态与结果。

## 不会经过的工具链路
- 当规划结果不包含 `TOOL` 类型步骤时，不会触发 `EnforcementGateway#execute`、`ToolExecutor#execute` 与 `McpToolClient#callTool`。

## 事件与可观测性位置
- 任务层：`WORKFLOW_STARTED`、`WORKFLOW_COMPLETED` 由 `TaskOrchestrator` 触发。
- 规划层：`PLAN_GENERATED`、`PLAN_REVISED` 由 `AgentRuntime` 触发。
- 步骤层：`STEP_STARTED`、`STEP_COMPLETED`、`STEP_FAILED` 由 `StepRuntimeService` 触发。
- 模型层：`LLM_PROMPT`、`LLM_OUTPUT` 由 `ModelInvocationService` 触发。
