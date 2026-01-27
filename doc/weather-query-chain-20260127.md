# 天气查询入口与链路说明

## 场景
- 用户通过 `POST /api/v1/tasks` 提交自然语言请求，例如“帮我查询今天的天气情况”。
- 请求体为 `TaskRequest`，其中 `query` 为用户问题，`sessionId`、`context` 为可选上下文，`idempotencyKey` 在当前实现中标记为必填。

## 入口与上下文
1. `TenantContextFilter#filter` 解析租户请求头并生成 `TenantContext`，写入 `ServerWebExchange` 属性与 `Reactor Context`。
2. `TaskController#submitTask` 作为入口接口，读取 `TaskRequest` 并调用 `AuthService#authenticate`。
3. `ApiKeyAuthenticator#authenticate` 解析 `API Key` 或 `JWT`，生成 `UserContext` 并写回 `TenantContext`。

## 任务提交与编排
1. `TaskOrchestrator#submitTask` 创建任务与工作流标识，保存任务记录并发布 `WORKFLOW_STARTED` 事件。
2. `WorkflowRouter#route` 将请求路由到运行时入口 `AgentRuntime#run`。
3. `TaskOrchestrator#handleWorkflowRoute` 在运行完成后更新任务状态并发布 `WORKFLOW_COMPLETED` 事件。

## 规划与步骤执行
1. `PlannerService#plan` 生成执行计划。若启用大模型，则通过 `ModelInvocationService#invoke` 获取 `LLM` 规划；否则走 `buildHeuristicPlan` 规则规划。
2. 规划产物为 `PlanResult`，包含步骤列表 `StepRequest`。对该类问题默认包含 `TOOL` 步骤。
3. `AgentRuntime#executeStep` 调用 `StepRuntimeService#startStep` 记录步骤开始，并按 `StepRequest.type` 分发执行。

## 工具调用链路（天气查询关键路径）
1. `AgentRuntime#executeToolStep` 调用 `EnforcementGateway#execute`。
2. `EnforcementGateway#execute` 发布 `TOOL_INVOKED` 事件，构造 `usageId` 后调用 `ToolExecutor#execute`。
3. `ToolExecutor#execute` 通过 `buildArguments` 组装参数（包含 `query` 与 `context`），并根据缓存与重试策略执行工具调用。
4. 工具名称来自 `StepRequest.input.tool` 或 `TaskRequest.context.tool`，未指定时默认 `demo_tool`。
5. `ToolExecutor#execute` 调用 `McpToolClient#callTool`，并根据 `TaskRequest.context.mcpServerId` 选择 `MCP` 服务端。
6. `McpToolClient#callTool` 根据配置选择本地 `ToolRegistry#execute` 或远端 `MCP` 服务调用，并执行限流与熔断控制。
7. `ToolRegistry` 默认仅注册 `demo_tool`，真实天气能力需要在 `ToolRegistry` 或远端 `MCP` 服务中提供对应工具。

## 结果收敛与响应
1. `FinalOutputService#finalizeOutput` 将步骤输出汇总为最终结果，内部通过 `ModelInvocationService#invoke` 生成结构化输出。
2. `TaskOrchestrator#handleWorkflowRoute` 写入任务结果与状态。
3. `TaskController#submitTask` 返回 `TaskResponse`，可通过 `GET /api/v1/tasks/{taskId}` 查询最终状态与结果。

## 事件与可观测性位置
- 任务层：`WORKFLOW_STARTED`、`WORKFLOW_COMPLETED` 由 `TaskOrchestrator` 触发。
- 规划层：`PLAN_GENERATED`、`PLAN_REVISED` 由 `AgentRuntime` 触发。
- 步骤层：`STEP_STARTED`、`STEP_COMPLETED`、`STEP_FAILED` 由 `StepRuntimeService` 触发。
- 工具层：`TOOL_INVOKED`、`TOOL_OBSERVATION`、`TOOL_ERROR` 由 `EnforcementGateway` 触发。
- 模型层：`LLM_PROMPT`、`LLM_OUTPUT` 由 `ModelInvocationService` 触发。
