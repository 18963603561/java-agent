# stepOutputs 中 sample 重复字段原因分析

## 一、问题现象
`stepOutputs[i].output` 的 `outputSummary.sample` 与 `toolResultSummary.sample` 中出现了 `outputSummary`、`toolResultSummary`、`stepSummary`、`outputDigest`、`truncated` 等字段的重复嵌套，导致样本文本体积膨胀，且难以阅读。

## 二、直接原因
`StepOutputSummaryBuilder.build` 在生成 `sample` 时，会对**完整的输出对象**做快照。只要输出对象中已经包含摘要字段，快照就会把这些摘要字段再次序列化进入 `sample`。

造成输出对象包含摘要字段的典型路径：
1. `AgentRuntime.enrichOutputSummaryForReflection` 在反思前将摘要合并进输出。
2. `StepRuntimeService.completeStep` 再次对输出生成摘要并合并。
3. 若输出本身来自上游 LLM 或工具结果，且已包含摘要字段，也会触发重复。

因此重复并非随机错误，而是“对已包含摘要的输出再次做摘要”的必然结果。

## 三、是否属于设计问题
- 从稳定性角度看：当前实现是可预期的，但会导致 `sample` 嵌套膨胀与噪声增加。
- 从可读性角度看：该行为不符合“摘要应只描述业务输出”的直觉。
- 结论：属于**设计上的副作用**，建议优化以提升摘要可读性。

## 四、是否应去除这些字段
建议去除。`outputSummary`、`toolResultSummary`、`stepSummary`、`outputDigest`、`truncated` 属于摘要元数据，不应再次进入 `sample` 的业务样本文本。

## 五、可选改造方案
### 方案A：在 `StepOutputSummaryBuilder` 内部过滤摘要字段（推荐）
- 在生成快照前，若输出为 `Map`，先移除以下字段再快照：
  - `outputSummary`、`toolResultSummary`、`stepSummary`、`outputDigest`、`truncated`、`inputSummary`、`inputDigest`
- 优点：调用方不变，控制点集中。
- 风险：若业务输出本身确实包含这些同名字段，会被过滤；需要确认命名冲突概率。

### 方案B：调用方提供“原始输出”
- 在合并摘要前，保留一份不含摘要的原始输出用于生成快照。
- 优点：不改动业务输出结构。
- 风险：需要修改调用点，侵入范围较方案A更大。

### 方案C：摘要与输出解耦
- 将摘要放在独立字段（如 `metadata.summary`），输出字段只保留业务结果。
- 优点：彻底避免递归摘要。
- 风险：涉及响应结构调整，兼容性成本高。

## 六、推荐结论
优先采用方案A：在 `StepOutputSummaryBuilder` 内部过滤摘要字段，使 `sample` 仅反映真实业务输出内容，避免重复与递归膨胀。