# 摘要关闭时保留原始数据的可行方案分析

## 1. 背景与需求
- 现状：`summary` 关闭后，`lastStepSummary` 退化为占位，`lastStepOutput` 通常缺失，反思/链式推理/最终输出的摘要能力下降。
- 需求：摘要关闭时仍能“直接使用原始数据”，且不影响其他流程（尤其避免原始数据进入提示词或最终输出）。

## 2. 风险与约束
- 原始输出体量大：直接进入 `stepInput` 或提示词会导致 token 暴涨。
- 原始输出可能包含敏感字段：需要隔离或限制可见性。
- `stepInput` 默认合并 `runtimeContext`：若不做过滤，原始数据可能被后续步骤“隐式带入”。

## 3. 可选方案

### 方案 A：运行时上下文内部保留 + 白名单注入（推荐）
**核心思路**：保留原始数据，但默认不进入 `stepInput`，仅特定流程显式读取。

1) 在 `AgentRuntime.updateRuntimeContext(...)` 写入内部字段：
- 新增键：`lastStepRawOutput`（或 `_internal.lastStepRawOutput`）。
- 仅在 `summary` 关闭时写入，或可配置开关控制。

2) 在 `AgentRuntime.mergeStepInput(...)` 增加过滤：
- 默认从 `runtimeContext` 中移除 `lastStepRawOutput`，避免进入提示词装配。
- 仅在特定步骤类型（如链式推理）需要时显式注入。

3) 在 `ChainOfThoughtService` 增加兜底读取：
- 当 `lastStepSummary/lastStepOutput` 缺失时，读取 `lastStepRawOutput`（需做长度与字段裁剪）。

**优点**：
- 最小化对现有流程的影响。
- 不污染 `stepOutputs` 与最终输出摘要。

**风险**：
- 需要新增“内部键过滤机制”，否则仍可能泄漏到提示词。

---

### 方案 B：仅持久化到 StepRecord（不进入 runtimeContext）
**核心思路**：把原始输出仅写入 `StepRecord` 或持久化存储，后续需要时再查询。

**优点**：
- 完全不影响运行时提示词或 `stepInput`。

**缺点**：
- 当次运行内难以复用，需额外查询接口。
- 变更范围较大（存储结构与查询接口）。

---

### 方案 C：写入 stepOutputs 但标记为内部不可用
**核心思路**：仍写入 `stepOutputs`，但在 `FinalOutputService` / `ReactLoopService` 中显式过滤。

**缺点**：
- 需要改动多个汇总逻辑，容易遗漏。
- 风险更高，可能影响最终输出。

## 4. 推荐落地方案（A）细化

### 4.1 建议新增的配置项（示例）
- `agent.summary.raw-retain.enable`：是否在摘要关闭时保留原始输出。
- `agent.summary.raw-retain.max-chars`：原始输出最大字符数。
- `agent.summary.raw-retain.allow-keys`：允许保留的字段白名单。

### 4.2 建议修改点
1) `AgentRuntime.updateRuntimeContext(...)`
- 写入 `lastStepRawOutput`（遵循配置与长度限制）。
- 不写入 `lastStepOutput`（继续保持现有摘要逻辑）。

2) `AgentRuntime.mergeStepInput(...)`
- 默认过滤 `lastStepRawOutput`，避免进入提示词。
- 对需要原始数据的步骤类型（如 COT）显式注入一个“裁剪后的文本”。

3) `ChainOfThoughtService`
- 新增读取 `lastStepRawOutput` 的回退逻辑，但必须做长度与字段裁剪。

### 4.3 预期行为
- 摘要关闭时：
  - `runtimeContext` 内部仍保留原始输出，可供特定流程使用。
  - 其他流程不感知该字段，提示词与最终输出保持原样。

## 5. 结论
- 摘要关闭后保留原始数据是可行的，但必须与提示词装配隔离。
- 推荐采用“内部保留 + 白名单注入”的方式，既能提供原始数据，又能避免影响其他流程。

---

## 6. 涉及的潜在修改文件（参考）
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
- `src/main/java/com/example/agent/reasoning/ChainOfThoughtService.java`
- （可选）新增配置属性类与配置项
