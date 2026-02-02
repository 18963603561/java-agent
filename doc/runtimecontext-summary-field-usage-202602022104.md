# runtimeContext 与 summary 字段使用分析

## 1. 分析范围
- 关注字段：
  - `runtimeContext`：`lastStepId`、`lastStepType`、`lastStepSummary`、`lastOutputSize`、`lastStepOutput`。
  - `summary` 对象字段：`outputSummary`、`toolResultSummary`、`stepSummary`、`outputDigest`、`truncated`，以及 `stepSummary` 内的 `stepId/type/status/attempt`。
- 重点回答：这些字段在哪些流程中被使用，缺失时会发送什么或产生什么影响。

## 2. 字段生成与注入流程概览
### 2.1 运行时上下文写入
- 位置：`AgentRuntime.updateRuntimeContext(...)`。
- 行为：
  - 写入 `lastStepId`、`lastStepType`。
  - 通过 `buildStepOutputSummary(record, output)` 生成 `summary`，写入 `lastStepSummary`。
  - 如果 `summary` 内含 `rawOutputSnapshot`，则写入 `lastStepOutput`，否则清理。
  - 输出不为空时写入 `lastOutputSize`。

### 2.2 summary 对象构建
- 位置：`AgentRuntime.buildStepOutputSummary(...)`。
- 行为：
  - 从 `output` 中复制已有 `outputSummary/toolResultSummary/stepSummary/outputDigest/truncated`。
  - 无摘要时补齐 `stepSummary`（写入 `stepId/type/status/attempt`，并补 `summary` 文本）。
  - `outputDigest` 不存在时写入最小 digest（如 `keyCount`）。
  - `truncated` 缺失时写入 `false`。
- summary 会被：
  - 写入 `runtimeContext.lastStepSummary`。
  - 作为 `recordStepOutput(...)` 的输出内容写入 `stepOutputs` 列表。

## 3. 各字段使用位置与用途

### 3.1 runtimeContext 字段使用
- `lastStepSummary`：
  - 位置：`ChainOfThoughtService`。
  - 用途：作为“上一步摘要/观察”的主要来源，生成链式推理观察文本。
- `lastStepOutput`：
  - 位置：`ChainOfThoughtService`。
  - 用途：当 `lastStepSummary` 缺失时的兜底来源。
- `lastStepId` / `lastStepType` / `lastOutputSize`：
  - 直接读取位置：当前代码内未发现显式读取。
  - 间接影响：这些字段会进入 `stepInput`（`mergeStepInput` 合并 `runtimeContext`），可能被 LLM 提示词或工具调用使用，但目前代码中未出现显式读取。

### 3.2 summary 字段使用
- `outputSummary`：
  - `ReflectionService.buildOutputSummaryContext(...)` 用于构建反思输入。
  - `ReactLoopService.resolveObservationSummary(...)` 用于观察摘要与状态。
  - `FinalOutputService.resolveStepSummaryData(...)` 用于推导步骤状态与摘要文本。
- `toolResultSummary`：
  - `ReactLoopService.resolveObservationSummary(...)` 优先使用其 `summary/sample` 生成摘要。
- `stepSummary`：
  - `FinalOutputService`：优先使用 `stepSummary.summary` 生成最终摘要文本。
  - `ReactLoopService`：作为摘要与状态的回退来源。
  - `StepRuntimeService`：在日志中读取 `stepSummary.tool` 与 `stepSummary` 结构用于摘要记录。
- `outputDigest`：
  - `ReflectionService`/`ReactLoopService`/`FinalOutputService`：当摘要缺失时用于构建 digest 文本。
- `truncated`：
  - `ReactLoopService`：用于标记摘要是否截断。
  - `StepRuntimeService`：用于摘要生成日志中的 `truncated`。
- `stepSummary.stepId/type/status/attempt`：
  - 主要用于“补齐摘要元数据”与日志观测。
  - `FinalOutputService` 与 `ReactLoopService` 会读取 `status` 作为步骤状态。

## 4. 缺失时的影响与“发送内容”变化

### 4.1 runtimeContext 字段缺失
- `lastStepSummary` 缺失：
  - `ChainOfThoughtService` 无法生成观察摘要，导致链式推理提示中缺少上一步摘要信息。
  - 发送给模型的观察摘要字段为空或缺失，推理连贯性下降。
- `lastStepOutput` 缺失：
  - 当 `lastStepSummary` 也缺失时，没有兜底内容，观察摘要为空。
- `lastStepId/lastStepType/lastOutputSize` 缺失：
  - 当前代码无直接依赖，运行流程不报错。
  - 仅在需要日志/提示词/工具侧读取时会造成可观测信息缺失。

### 4.2 summary 字段缺失
- `outputSummary` 缺失：
  - `ReflectionService` 会尝试从 `rawOutputSnapshot` 或 `outputDigest` 构建摘要；若仍无，则写入 `"(summary disabled)"`。
  - `ReactLoopService` 将回退到 `stepSummary` 或 `outputDigest`，再不行则 `"(summary disabled)"`。
- `toolResultSummary` 缺失：
  - `ReactLoopService` 直接回退到 `outputSummary/stepSummary`。
- `stepSummary` 缺失：
  - `FinalOutputService` 无法获取 `stepSummary.summary`，将回退到 `outputSummary`、`rawOutputSnapshot` 或 `outputDigest`。
  - `ReactLoopService` 无法从 `stepSummary` 获取状态与摘要，状态可能为空。
- `outputDigest` 缺失：
  - `ReflectionService`/`ReactLoopService`/`FinalOutputService` 的 digest 回退失效，摘要更容易退化为 `"(summary disabled)"`。
- `truncated` 缺失：
  - `ReactLoopService` 的 `truncated` 标记缺失，观测摘要不会标识截断。
  - `StepRuntimeService` 日志中 `truncated` 为空。
- `stepSummary.stepId/type/status/attempt` 缺失：
  - `FinalOutputService` 与 `ReactLoopService` 无法输出步骤状态或类型信息。
  - 运行日志与诊断信息会变弱，但流程仍可继续。

## 5. 结论与建议
- `lastStepSummary/lastStepOutput` 是链式推理的重要输入，缺失会直接削弱后续步骤的上下文质量。
- `outputSummary/stepSummary/outputDigest` 是反思与最终输出的核心摘要来源，缺失会导致“摘要退化”为 `"(summary disabled)"` 或空字符串。
- `lastStepId/lastStepType/lastOutputSize` 当前仅提供运行期可观测性，不影响主流程，但建议保留以便排查问题与支持未来扩展。

---

## 6. 关键参考位置
- `AgentRuntime.updateRuntimeContext(...)`：写入 `lastStep*` 与 `summary`。
- `AgentRuntime.buildStepOutputSummary(...)`：生成 `outputSummary/stepSummary/outputDigest/truncated`。
- `ChainOfThoughtService`：读取 `lastStepSummary/lastStepOutput`。
- `ReflectionService`：读取 `outputSummary/outputDigest`。
- `FinalOutputService`：读取 `stepSummary/outputSummary/outputDigest`。
- `ReactLoopService`：读取 `toolResultSummary/outputSummary/stepSummary/outputDigest/truncated`。
