# inputSummary 出现在 sample 中的原因与方案说明

## 一、问题确认
问题属实：`inputSummary` 会出现在 `outputSummary.sample` 中。

## 二、原因分析
`StepOutputSummaryBuilder.build` 在生成 `outputSummary.sample` 时，对“完整 output 对象”做快照。当前 output 已被合并了摘要字段（包含 `inputSummary` / `inputDigest` / `outputSummary` / `toolResultSummary` / `stepSummary` / `outputDigest` / `truncated`），因此快照会把这些摘要字段再次序列化进 sample。

结论：不是 inputSummary 自己“进入同级”，而是 sample 的生成源是“含摘要的 output”，导致摘要字段被样本化。

## 三、建议实现方案
### 方案A（推荐，改动小）
在 `StepOutputSummaryBuilder` 生成快照前过滤摘要字段：
- 过滤字段：`outputSummary`、`toolResultSummary`、`stepSummary`、`outputDigest`、`truncated`、`inputSummary`、`inputDigest`。
- 仅用于生成快照，不影响原始 output 结构。

优点：
- 不改变外部输出结构，低风险。
- 立即消除 sample 中摘要字段的嵌套噪声。

### 方案B（结构调整，改动中）
在调用链路中保留“原始 output”用于生成摘要，避免摘要合并后再做快照：
- 生成摘要时传入原始输出；
- 合并摘要只发生在最终输出。

优点：
- 不需在摘要构建器内过滤字段。

缺点：
- 需要多处调用点协同改造，侵入面较大。

### 方案C（结构解耦，改动大）
将摘要与输出彻底分离：
- `steps[i].output` 只保留业务输出；
- `steps[i].summary` 保存摘要。

优点：
- 从结构上彻底避免摘要递归污染。

缺点：
- 改动范围大，涉及持久化与下游兼容。

## 四、涉及文件（按方案A）
- `src/main/java/com/example/agent/runtime/StepOutputSummaryBuilder.java`
  - 在构建快照时过滤摘要字段（仅影响 sample 和 keyFields）。

## 五、结论
当前现象是由“对含摘要的 output 进行快照”导致的副作用。若仅需避免 sample 嵌套噪声，推荐采用方案A。