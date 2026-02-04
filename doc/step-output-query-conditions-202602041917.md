# stepOutputs 查询条件摘要落地方案（方案B）

## 一、结论
- `stepOutputs` 现有的 `outputSummary`、`toolResultSummary`、`stepSummary`、`outputDigest`、`truncated` 不包含查询条件。
- 采用方案B已落地：扩展 `StepOutputSummaryBuilder.build` 入参，新增 `inputSummary` 与 `inputDigest`，并允许 `stepInput` 覆盖 `record.getInput()`。
- `inputSummary.source` 用于标识输入来源：传入 `stepInput` 时为 `stepInput`，否则为 `record`。

## 二、落地后的结构
在 `stepOutputs[i].output` 中新增：
- `inputSummary`：输入条件摘要，提取 `query`、`question`、`topic`、`tool`、`arguments`、`filters`、`timeRange` 等字段。
- `inputDigest`：输入规模与截断信息，包含 `keyCount`、`keys`、`charCount`、`truncated`。

## 三、字段补齐与默认行为
- `inputSummary`：
  - 默认按白名单字段提取；未命中字段时不输出。
  - `tool` 优先使用显式传入的 `toolName`，否则从输入读取 `tool` 或 `toolName`。
  - `source` 默认值为 `record`，只有显式传入 `stepInput` 时才为 `stepInput`。
- `inputDigest`：
  - 当输入为空且无法生成快照时不输出该字段。
  - `truncated` 为 `true` 表示摘要生成过程中发生截断。
- 现有 `outputSummary`、`toolResultSummary`、`stepSummary`、`outputDigest`、`truncated` 保持不变。

## 四、调用点调整
- `AgentRuntime.enrichOutputSummaryForReflection`：调用 `build(record, stepInput, output, toolName, null)`。
- `StepRuntimeService.completeStep`：调用 `build(record, null, output, null, null)`，使用 `record` 作为输入来源。

## 五、摘要生成规则
- 结构化字段（如 `arguments`、`filters`、`timeRange`）会做深度与条目数限制，并对文本做长度截断。
- 避免将完整 `context` 或敏感字段写入摘要，仅保留白名单字段。