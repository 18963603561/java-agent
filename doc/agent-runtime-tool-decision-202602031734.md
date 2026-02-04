# TOOL 步骤是否跳过模型决策的分析

## 1. 背景
在 `AgentRuntime.executeStep` 中，`stepType=TOOL` 会进入 `executeLlmStep`，随后调用 `llmStepService.run`。该方法始终先向模型发起决策请求，再根据决策结果决定是否执行工具。

## 2. 当前实现关键点
- `executeLlmStep` 无条件调用 `llmStepService.run`。
- `llmStepService.run` 在决策阶段会生成 `mode`，只有 `mode=tool_call` 才执行工具；否则直接返回回答。
- 当前已在 `stepType=TOOL` 场景自动补齐 `toolChoice`，用于引导模型选择特定工具，但仍不会跳过决策。

## 3. 仅确认工具存在，是否足以跳过决策
结论：不足以跳过。

原因：
- 工具存在只解决“能否调用”的问题，不能解决“该如何构造参数”的问题。
- `llmStepService.run` 会在决策阶段生成工具参数，缺少该步骤将导致参数为空或不完整。
- `llmStepService.run` 同时负责工具调用的错误映射与重试逻辑，直接绕过会丢失这部分保障。

## 4. 何时可以考虑跳过决策
必须同时满足以下条件，才适合直接调用工具并进入总结：
- 已知工具名称（`stepInput.tool` 或 `stepInput.toolName` 有值）。
- 已知工具参数（`stepInput.arguments` 或平铺参数已完整且可用）。
- 不需要模型参与“是否调用工具”的判断（业务层已明确必须调用）。
- 接受由执行器承担工具重试与错误映射的职责，或在执行器中补齐这些能力。

## 5. 跳过决策的收益与风险
收益：
- 降低一次模型调用的延迟与成本。
- 行为更确定，避免模型返回非工具模式。

风险：
- 参数可能缺失或不完整，导致工具失败。
- 失去 `llmStepService.run` 的工具重试与错误码映射逻辑。
- 失去 `llmStepService.updateToolContext` 的上下文回写能力（需要在执行器补齐）。
- 产出结构与当前 `llmStepService.run` 输出不一致，可能影响后续反思与输出链路。

## 6. 可落地方案（保持 LLM 总结）
若希望跳过决策，但仍保留总结能力，需要新增一条“直达工具 + 总结”的路径：
1) 在 `executeStep` 中，针对 `stepType=TOOL` 增加条件分支：
   - 工具名与参数齐全时，直接调用 `executeToolStep`。
2) 将 `LlmStepService.summarizeToolResult` 抽成可复用方法（或新建公共服务），用于对工具结果做总结。
3) 在直达路径中补齐：
   - 上下文回写（等价于 `updateToolContext` 的行为）。
   - 统一输出结构（包含 `mode`、`tool`、`toolStatus` 等字段）。
   - 工具重试与错误映射逻辑（可复用 `LlmStepService` 的实现或下沉到执行器）。

## 7. 建议
- 若规划阶段能提供完整工具参数，可新增“直达工具”优化路径，减少一次模型决策调用。
- 若参数不完整或存在不确定性，仍应保留当前决策流程，避免工具调用失败。
- 当前阶段最安全的做法是：继续保留 `llmStepService.run`，并通过 `toolChoice` 约束工具选择，确保规划与执行一致。