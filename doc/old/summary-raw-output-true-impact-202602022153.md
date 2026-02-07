# summary.raw-output=true 的影响分析

## 1. 结论摘要
- `summary.raw-output=true` 只在 `summary.enable=false` 时生效。
- 生效后会向 `runtimeContext` 与 `stepOutputs` 注入受控原始输出快照，影响链式推理、反思与最终输出的“摘要兜底来源”。
- 摘要开启时（`summary.enable=true`），`summary.raw-output=true` 基本不生效。

## 2. 生效条件与触发路径
### 2.1 生效条件
- `AgentRuntime.shouldUseRawOutputSnapshot()` 返回 true：
  - `summary.enable=false`
  - 且 `raw-output.enable=true`

### 2.2 触发路径
- `AgentRuntime.enrichOutputSummaryForReflection(...)`：
  - 摘要关闭时生成 `rawOutputSnapshot`，写入输出。
- `AgentRuntime.buildStepOutputSummary(...)`：
  - 当 summary 中缺少 `rawOutputSnapshot` 时，会补齐。
- `AgentRuntime.updateRuntimeContext(...)`：
  - 若 summary 内含 `rawOutputSnapshot`，写入 `runtimeContext.lastStepOutput`。

## 3. 对功能的影响

### 3.1 对链式推理（COT）
- `ChainOfThoughtService` 读取顺序：
  - `lastStepSummary` → `lastStepOutput`。
- 开启 raw-output 后：
  - 当摘要为空时仍能通过 `lastStepOutput` 兜底，提升推理连贯性。

### 3.2 对反思流程
- `ReflectionService.buildOutputSummaryContext(...)`：
  - 当 `outputSummary` 为空时，会读取 `rawOutputSnapshot` 作为摘要文本。
- 开启 raw-output 后：
  - 反思输入具备原始输出兜底，减少“summary disabled”。

### 3.3 对最终输出汇总
- `FinalOutputService.resolveStepSummaryData(...)`：
  - `stepSummary` / `outputSummary` 为空时，会尝试从 `rawOutputSnapshot` 取文本。
- 开启 raw-output 后：
  - 最终汇总更容易拿到可读摘要。

### 3.4 对 ReAct 观察摘要
- `ReactLoopService.resolveObservationSummary(...)`：
  - 在 `toolResultSummary/outputSummary/stepSummary` 为空时，会用 `rawOutputSnapshot` 作为摘要来源。
- 开启 raw-output 后：
  - 观察摘要更完整，减少“summary disabled”。

## 4. 可能的副作用与风险

### 4.1 体量与成本
- `rawOutputSnapshot` 仍是原始输出的“受控快照”，但体量比摘要大。
- 会影响：
  - `stepOutputs` 体积
  - `runtimeContext` 大小
  - 序列化与日志成本

### 4.2 信息泄露风险
- raw-output 包含更多细节，虽然有 allow/mask 白名单，但仍可能引入敏感字段。
- 若下游未严格隔离，可能进入提示词或日志。

### 4.3 行为差异
- `summary.enable=false` 时：
  - 开启 raw-output 会改变链式推理/反思/最终输出的摘要来源，从“空/占位”变为“原始快照”。
- `summary.enable=true` 时：
  - raw-output 分支不生效，功能基本不变。

## 5. 结论
- `summary.raw-output=true` 会影响部分功能，但仅在 `summary.enable=false` 的场景生效。
- 主要影响：
  - 提升链式推理、反思、最终输出与 ReAct 的摘要兜底质量。
  - 可能带来体量与敏感信息风险。

---

## 6. 参考位置
- `AgentRuntime.shouldUseRawOutputSnapshot(...)`
- `AgentRuntime.enrichOutputSummaryForReflection(...)`
- `AgentRuntime.buildStepOutputSummary(...)`
- `AgentRuntime.updateRuntimeContext(...)`
- `ReflectionService.buildOutputSummaryContext(...)`
- `FinalOutputService.resolveStepSummaryData(...)`
- `ReactLoopService.resolveObservationSummary(...)`
- `ChainOfThoughtService`（读取 `lastStepOutput`）
