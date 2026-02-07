# raw-output 与 summary 同时关闭的影响分析

## 1. 背景与问题范围
- 配置位置：`F:\ai-code\java-agent\target\classes\application.yml`。
- 关注点：在 `AgentRuntime.executeStep(...)` 中，执行完成后调用
  - `updateRuntimeContext(runtimeContext, record, output)`
  - `recordStepOutput(stepOutputs, record, output)`
  当 `summary.enable=false` 且 `summary.raw-output.enable=false` 时，最终结果的变化与后续步骤如何取到上一步输出。

## 2. 相关配置开关与语义
### 2.1 summary 开关
- `summary.enable=false`：禁用步骤输出摘要生成（`StepOutputSummaryBuilder.isEnabled()` 为 false）。
- 直接影响：
  - `StepOutputSummaryBuilder.build(...)` 不执行摘要生成。
  - `StepRuntimeService.completeStep(...)` 不往输出中注入摘要字段（`outputSummary/stepSummary/outputDigest`）。

### 2.2 raw-output 开关
- `summary.raw-output.enable=false`：禁用原始输出快照（`RawOutputSnapshotBuilder.isEnabled()` 为 false）。
- 直接影响：
  - `RawOutputSnapshotBuilder.build(...)` 不生成 `rawOutputSnapshot`。
  - `AgentRuntime.shouldUseRawOutputSnapshot()` 返回 false。

## 3. executeStep 输出链路与数据流
### 3.1 关键链路
1) `executeStep(...)` 产生 `output`。
2) `enrichOutputSummaryForReflection(...)`：
   - `summary.enable=false` 且 `raw-output.enable=false` 时，直接返回原 `output`（不注入摘要/快照）。
3) `StepRuntimeService.completeStep(...)`：
   - 摘要关闭时，不往 `output` 追加摘要字段。
4) `updateRuntimeContext(runtimeContext, record, output)`：
   - 内部调用 `buildStepOutputSummary(record, output)` 生成“运行时摘要结构”。
5) `recordStepOutput(stepOutputs, record, output)`：
   - 也通过 `buildStepOutputSummary(...)` 生成记录结构，写入 `stepOutputs`。

### 3.2 buildStepOutputSummary 在摘要/快照关闭时的实际结果
在 `summary` 与 `raw-output` 均关闭时：
- 不会生成 `rawOutputSnapshot`。
- 仍会生成 `stepSummary` 与 `outputDigest`（但内容极简）：
  - `stepSummary.summary` 会落为 `"(summary disabled)"`。
  - `outputDigest` 仅保留 `keyCount`，`charCount` 等字段通常缺失。
- `updateRuntimeContext` 行为：
  - `lastStepSummary` 仍会写入（摘要占位）。
  - `lastStepOutput` 被清理，且无法重新写入（因为缺少 `rawOutputSnapshot`）。
  - `lastOutputSize` 仍会写入（输出 Map 的 size）。
- `recordStepOutput` 行为：
  - `stepOutputs` 记录仅包含摘要占位与简单 digest。

## 4. 对最终结果的影响
### 4.1 对后续步骤的影响（上一步输出来源）
后续步骤的输入构造为：
- `mergeStepInput(step, runtimeContext)`
- 该方法将 `runtimeContext` 作为基座，并用 `step.getInput()` 覆盖同名键。
- 因此：
  - 后续步骤获取上一步结果的主要来源是 `runtimeContext`。
  - 关键字段是：`lastStepSummary`、`lastStepOutput`。

在当前配置下：
- `lastStepOutput` 不存在。
- `lastStepSummary.summary` 为 `"(summary disabled)"`。
- 结果：后续步骤无法获取上一阶段的可读结果，仅能获得占位信息。

### 4.2 对 ChainOfThought 的影响
`ChainOfThoughtService` 会按顺序从以下字段提取上一步摘要：
1) `observationsSummary`
2) `lastStepSummary`
3) `lastStepOutput`

在摘要与原始快照均禁用时：
- `lastStepSummary` 有值但仅是 `"(summary disabled)"`。
- `lastStepOutput` 不存在。
- 结果：链式推理输入信息不足，影响质量与可解释性。

### 4.3 对最终输出的影响
`AgentRuntime.run(...)` 在所有步骤完成后：
- 会先调用 `resolveFinalOutputFromSteps(...)`。
- 若该方法返回非空，则不会调用 `FinalOutputService.finalizeOutput(...)`。

而 `resolveFinalOutputFromSteps(...)` 使用的 `stepOutputs` 来自 `recordStepOutput(...)`。
- 当前配置下 `stepOutputs` 只有摘要占位与 digest。
- 可能导致最终输出仅包含摘要占位内容，缺少真实答案信息。

## 5. 结论
1) `summary=false` 且 `raw-output=false` 时：
   - 运行期上下文中的“上一步输出”被剥离，仅剩占位摘要。
   - 后续步骤无法可靠读取上一步输出内容。
2) 最终结果可能退化为摘要占位输出，无法体现真实执行结果。
3) 该配置组合对链式推理与最终总结均有明显负面影响。

## 6. 建议方案
### 方案一（推荐）：保留最小“内部摘要”能力
- 即便 summary 关闭，也保留极简摘要生成（仅用于运行时上下文与 final prompt）。
- 规则：
  - 只从 `output` 中提取 `answer/finalAnswer` 等可读字段，截断到固定长度（例如 200 字）。
  - 严格避免输出敏感字段或原始结构。
- 优点：保持默认行为不变，但避免完全失去上一步结果。

### 方案二：保留原始快照但标记为“内部不可外显”
- 当 `summary=false && raw-output=false` 时：
  - 仍可在 `runtimeContext` 内部写入 `lastStepOutput`（仅供后续步骤使用）。
  - 不将其写入 `stepOutputs`，避免外部可见。
- 优点：不影响上下游步骤联动；风险在于内部数据量增大。

### 方案三：当摘要关闭时强制走 FinalOutputService
- 修改 `resolveFinalOutputFromSteps(...)`：
  - 如果 `stepOutputs` 仅包含占位摘要（`"(summary disabled)"`），则返回 `null`。
  - 迫使流程走 `FinalOutputService`，由模型汇总步骤。
- 注意：`FinalOutputService` 仍依赖 `stepOutputs`，但至少避免直接返回占位结果。

---

## 7. 关键参考位置
- `AgentRuntime.executeStep(...)`：执行完成后更新上下文与输出记录。
- `AgentRuntime.updateRuntimeContext(...)`：更新 `lastStepSummary/lastStepOutput`。
- `AgentRuntime.recordStepOutput(...)`：写入 `stepOutputs`。
- `ChainOfThoughtService`：读取 `lastStepSummary/lastStepOutput`。
- `FinalOutputService`：依赖 `stepOutputs` 构建最终输出。
