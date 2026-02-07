# AgentRuntime 工具调用流程分析与处理建议

## 1. 现状流程（从规划到执行）
- `PlannerService` 生成 `PlanResult`，`steps` 中每个 `StepRequest` 包含 `stepType` 和 `input`。
- `AgentRuntime.run` 调用 `plannerService.plan` 得到 `plan.getSteps()`，逐步进入 `executeStep`。
- `executeStep` 合并 `runtimeContext` 与 `step.input` 得到 `stepInput`，用于执行与审批。
- 当 `stepType` 为 `LLM`、`ANSWER`、`TOOL` 时进入 `executeLlmStep`。
- `executeLlmStep` 调用 `LlmStepService.run`，由模型决策是否调用工具。

## 2. 工具信息来源与存放位置
- 规划阶段：`PlannerService.parsePlan` 把 `steps[*].tool` 写入 `StepRequest.input` 的 `tool` 字段；`PlannerService.buildHeuristicPlan` 也会写 `tool`。
- 运行时工具决策：`LlmStepService` 主要读取 `toolChoice`、`disableTools` 与技能约束，默认不会直接使用 `tool` 或 `toolName`。
- 单步内工具调用输出：`LlmStepService.run` 的返回 `output` 包含 `mode=tool_call`、`tool.name`、`tool.arguments`、`toolStatus`、`toolErrorCode`、`toolErrorMessage`。
- 步骤记录：`StepRecord.output` 保存上述 `output`；`stepOutputs` 列表保存摘要（`outputSummary`、`toolResultSummary`）。
- 上下文回写：`LlmStepService.updateToolContext` 写入 `request.getContext()` 与 `stepInput.context`，关键字段包括：
  - `lastToolResult`
  - `lastToolError`
  - `selectedTools`

## 3. 潜在冲突与紊乱风险
- 请求级工具选择覆盖规划步骤：`ModelToolResolver` 优先读取 `stepInput.toolChoice`，其次读取 `request.toolChoice` 与 `request.context.toolChoice`。当请求携带 `SPECIFIED` 时，可能覆盖规划步骤期望的工具。
- 规划步骤的 `tool` 字段未被执行器直接使用：当前 `LlmStepService` 不读取 `stepInput.tool` 或 `stepInput.toolName`，导致规划层与执行层可能出现“工具不同”的情况。
- 运行时上下文未同步新工具结果：`updateToolContext` 写入 `request.context`，但 `runtimeContext` 不会回灌，后续 `mergeStepInput` 可能拿不到最新的工具回写字段（除非下游从 `request.context` 读取）。

## 4. 建议的解决路径（按侵入性从低到高）
方案 A（最小改动，保持 LLM 决策）
- 规划阶段显式写入 `toolChoice`：当 `stepType=TOOL` 时，在 `StepRequest.input` 中写入 `toolChoice={mode:specified, toolName:...}`，保证 `ModelToolResolver` 能强制选择目标工具。
- 或在 `executeStep` 中检测 `stepType=TOOL` 且存在 `tool` 或 `toolName` 时自动补齐 `toolChoice`，再调用 `executeLlmStep`。

方案 B（执行器强制工具调用）
- `stepType=TOOL` 直接走 `executeToolStep`，使用 `tool` 或 `toolName` 与参数执行工具；随后可复用 `LlmStepService` 的总结逻辑输出。

方案 C（上下文一致性增强）
- 每次步骤完成后，将 `request.getContext()` 的新增键同步回 `runtimeContext`，保证后续步骤能够读取到 `lastToolResult` 等字段。

## 5. 本次代码调整点
- `executeStep` 对 `stepType=TOOL` 明确走 `executeLlmStep` 分支，避免落入默认分支的“未指定工具”路径。
- `executeStep` 在 `stepType=TOOL` 且步骤输入存在 `tool`/`toolName` 时自动补齐 `toolChoice`，让 `ModelToolResolver` 在 LLM 决策阶段识别规划指定的工具。
