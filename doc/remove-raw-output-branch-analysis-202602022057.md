# 删除 agent.summary.raw-output 链路的影响与修改点分析

## 1. 目标与范围
- 目标：删除 `agent.summary.raw-output` 相关链路，仅保留 `agent.summary` 摘要分支，完成代码瘦身。
- 范围：配置项、属性类、构建器、运行时摘要链路、最终输出与 ReAct 汇总逻辑。

## 2. 当前 raw-output 链路的作用
### 2.1 配置与属性绑定
- 配置前缀：`agent.summary.raw-output`。
- 属性类：`com.example.agent.runtime.RawOutputProperties`。
- 作用：在摘要关闭时生成“受控原始输出快照”。

### 2.2 构建器与运行时注入
- 构建器：`com.example.agent.runtime.RawOutputSnapshotBuilder`。
- 主要用途：
  - 摘要关闭时生成 `rawOutputSnapshot`。
  - 作为 `lastStepOutput` 的来源。
  - 为 `stepSummary.summary` 的回退文本来源。

### 2.3 下游读取点
- `FinalOutputService`：在 `resolveStepSummaryData(...)` 中用 `rawOutputSnapshot` 作为摘要回退。
- `ReactLoopService`：在汇总步骤摘要时用 `rawOutputSnapshot` 作为回退。
- `ChainOfThoughtService`：优先读 `lastStepSummary`，其次读 `lastStepOutput`（由 raw-output 链路提供）。

## 3. 删除 raw-output 链路需要修改的代码点

### 3.1 配置与属性类
- 删除配置：
  - `src/main/resources/application.yml` 中的 `agent.summary.raw-output` 块。
  - 如有测试或打包产物配置文件同步删除（例如 `target/classes/application.yml`）。
- 删除属性类：
  - `src/main/java/com/example/agent/runtime/RawOutputProperties.java`。

### 3.2 构建器与依赖注入
- 删除构建器类：
  - `src/main/java/com/example/agent/runtime/RawOutputSnapshotBuilder.java`。
- 删除注入与字段：
  - `AgentRuntime` 中的 `rawOutputSnapshotBuilder` 字段与构造参数。

### 3.3 AgentRuntime 相关逻辑
需要移除所有 raw-output 分支：
- `enrichOutputSummaryForReflection(...)`：删除 `shouldUseRawOutputSnapshot()` 分支。
- `hasRawOutputField(...)` / `shouldUseRawOutputSnapshot(...)`：删除方法。
- `buildStepOutputSummary(...)`：
  - 不再写入 `rawOutputSnapshot`。
  - 不再通过 `RawOutputSnapshotBuilder.resolveText(...)` 生成摘要回退。
- `updateRuntimeContext(...)`：
  - 移除 `lastStepOutput` 的写入逻辑（当前仅来自 `rawOutputSnapshot`）。

### 3.4 FinalOutputService 相关逻辑
- `resolveStepSummaryData(...)`：
  - 删除 `RawOutputSnapshotBuilder.resolveText(...)` 回退分支。
  - 若 `stepSummary/outputSummary/outputDigest` 均无内容，则保持 `"(summary disabled)"`。

### 3.5 ReactLoopService 相关逻辑
- 汇总输出时的回退逻辑中包含 `RawOutputSnapshotBuilder.resolveText(...)`：
  - 删除 raw-output 回退分支。
  - 仅保留 `stepSummary/outputSummary/outputDigest` 等摘要来源。

### 3.6 其他引用清理
- 清理 imports 与未使用常量：
  - `RawOutputSnapshotBuilder.RAW_OUTPUT_KEY` 的引用删除。
- 若存在测试用例验证 `rawOutputSnapshot` 字段：
  - 需要同步移除或调整断言。

## 4. 行为变化与影响评估
### 4.1 摘要开启时
- `agent.summary.enable=true`：
  - 主摘要逻辑仍然有效。
  - raw-output 删除不会影响正常摘要输出。

### 4.2 摘要关闭时
- `agent.summary.enable=false`：
  - 不再有 `rawOutputSnapshot` 兜底。
  - `lastStepOutput` 不再写入。
  - `lastStepSummary.summary` 可能退化为 `"(summary disabled)"`。
- 影响点：
  - `ChainOfThoughtService` 的输入摘要变弱。
  - `FinalOutputService` 的步骤摘要可能为空或退化。
  - `ReactLoopService` 的摘要展示变弱。

## 5. 建议方案
### 方案一：保留“摘要必须开启”的约束（最小改动）
- 删除 raw-output 后，强制要求 `agent.summary.enable=true` 作为默认配置。
- 优点：不引入新分支，逻辑清晰。
- 风险：若用户关闭摘要，后续步骤上下文质量会显著下降。

### 方案二：引入最小摘要回退（不再依赖 raw-output）
- 当摘要关闭时，生成轻量级 `stepSummary.summary`（例如仅使用 `answer/finalAnswer` 字段）。
- 注意：这属于“摘要分支”，不再是 raw-output。
- 优点：保证下游最小可读性。

### 方案三：删除 raw-output 后同步约束相关链路
- 调整 `ChainOfThoughtService`、`FinalOutputService`、`ReactLoopService`：
  - 明确不再读取 `lastStepOutput` 或 raw-output。
  - 将“摘要关闭”视为完全不提供上下文摘要。

## 6. 结论
- `agent.summary.raw-output` 与 `summary` 在语义上相近，但 raw-output 提供了“摘要关闭时的兜底快照”。
- 删除该链路需要同步清理运行时、最终输出与 ReAct 汇总等多个调用点。
- 若删除 raw-output，建议默认开启 summary，或提供最小摘要回退以避免上下游退化。
