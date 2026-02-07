# 模型链路分析报告

## 验证结论
本次已通过接口提交任务进行验证，模型链路连通且有内容返回。

请求与返回摘要如下。

请求地址
- `http://localhost:8080/api/v1/tasks`

请求体
```json
{
  "query": "告诉我你是谁",
  "idempotencyKey": "idem-llm-verify-<guid>",
  "sessionId": "session-verify-1",
  "context": {
    "mode": "chain_of_thought"
  }
}
```

返回摘要
- `taskId`: `d88bdc80-64f6-4834-9af5-050687b46da2`
- `workflowId`: `01b6e63a-6b47-41e9-9d67-f053e3a904f5`
- `status`: `COMPLETED`
- `modelId`: `deepseek-chat`
- `planSummary`: `strategy=sequential, cognitive=simple, complexity=0.13, steps=1`
- `answer`: 我是智能体运行时执行器，遵循安全边界与多租户隔离约束。
- `highlights`: 已回答用户问题，无需进一步步骤
- `confidence`: `0.95`

## 任务链路详细流程
以下为从请求进入到模型调用再到返回的完整链路。

1. `TaskController.submitTask` 接收请求并鉴权与写入租户上下文。
2. `TaskOrchestrator.submitTask` 创建任务记录并发布 `WORKFLOW_STARTED` 事件。
3. `WorkflowRouter.route` 将请求路由到 `AgentRuntime.run`。
4. `PlannerService.plan` 生成计划。
   - `agent.planner.llm-enabled` 当前为 `false`，因此使用 `buildHeuristicPlan`。
   - 当 `context.mode` 为 `chain_of_thought` 时，计划生成 `CHAIN_OF_THOUGHT` 步骤。
5. `AgentRuntime.executeChainOfThought` 调用 `ChainOfThoughtService.run`。
   - `ChainOfThoughtService` 构造 `COT_CONTEXT_JSON` 并调用 `ModelInvocationService.invoke`。
   - `ModelInvocationService` 通过 `DefaultLlmClient` 与 `DefaultModelProvider.invokeOpenAiCompatible` 发起模型请求，调用 `POST /chat/completions`。
6. `FinalOutputService.finalizeOutput` 汇总步骤结果并再次调用模型生成 `finalOutput`。
7. `TaskOrchestrator` 写入任务结果并发布 `WORKFLOW_COMPLETED` 事件。
8. `TaskController.getTask` 返回 `TaskStatusResponse`。

## 关于“没有访问大模型”的疑问说明
结论是请求已经访问模型。

理由如下。
- `finalOutput.modelId` 为 `deepseek-chat`，该字段仅在模型调用返回后写入。
- `ModelInvocationService` 在模型调用阶段会记录日志并发布 `LLM_PROMPT` 与 `LLM_OUTPUT` 事件，属于模型调用链路的直接证据。
- 本次验证返回了结构化的模型输出，说明模型调用链路完整走通。

如果出现任务失败且没有 `finalOutput`，通常是因为工具步骤提前失败导致流程中断，而不是模型未调用。

## 关于“缺少输入问题”反馈的原因分析
出现“缺少输入问题”属于模型输出内容本身，并不等价于没有模型调用。

可能原因如下。
1. 模型对提示词语境理解偏差。
   - `FinalOutputService` 提示词要求模型输出 `JSON`，模型可能将“缺少输入问题”作为固定模板式回答。
2. 提示词裁剪或上下文缺失。
   - `DefaultPromptAssembler` 可能在预算裁剪时截断 `userText`，导致模型看到的输入不足。
3. 上下文构建阶段传入的 `query` 为空。
   - `PlannerService` 与 `ChainOfThoughtService` 的 `prompt` 均依赖 `request.query` 或 `stepInput`，若上游未写入或被覆盖，会触发该类提示。

定位建议。
- 通过订阅 `SSE` 查看 `LLM_PROMPT` 事件中的 `prompt` 内容，确认是否包含 `query`。
- 检查 `promptAssemblyInput.userText` 是否为空或被裁剪。
- 查看 `agent.context.budget` 的配置与裁剪日志，确认是否触发裁剪。

## 默认规划为何走 `demo_tool` 以及 `MCP_UNAVAILABLE` 触发流程
默认规划流程由 `PlannerService.buildHeuristicPlan` 控制，当未指定 `context.mode` 或 `react` 等策略时，会生成 `TOOL` 步骤，并默认使用 `demo_tool`。

触发链路如下。
1. 规划阶段生成 `TOOL` 步骤，工具名称为 `demo_tool`。
2. 执行阶段进入 `AgentRuntime.executeToolStep`。
3. `ToolExecutor.execute` 构造请求并调用 `McpToolClient.callTool`。
4. `agent.mcp.remote-enabled` 当前为 `true`，因此会调用远端 `MCP` 服务。
5. 远端工具集中不存在 `demo_tool` 时返回错误，最终转化为 `MCP_UNAVAILABLE` 或“工具不存在”。

这意味着默认路径并不会直接调用本地 `ToolRegistry` 内置工具，而是优先走远端 `MCP` 工具集。

## 建议与可选修复方向
1. 若希望默认请求不依赖工具，继续使用 `context.mode=chain_of_thought` 或开启 `react`。
2. 若希望模型规划，请启用 `agent.planner.llm-enabled`，但仍需确保工具可用。
3. 若希望使用工具，请在远端 `MCP` 工具集注册 `demo_tool`，或显式指定已有工具名称。
4. 对链路验证建议固定开启 `LLM_PROMPT` 与 `LLM_OUTPUT` 事件订阅，用于排查提示词内容。