# AgentRuntime.executeStep 反思摘要为空原因与方案

生成时间
2026-02-01 13:50

## 结论概要

`executeStep` 中调用 `reflectWithEvents` 时摘要为空，核心原因是反思发生在摘要生成之前。
步骤摘要由 `StepRuntimeService.completeStep` 触发 `StepOutputSummaryBuilder` 生成，
而当前流程在反思完成后才调用 `completeStep`，因此 `outputSummary`/`outputDigest` 尚未写入。

## 触发链路与时序

1. `AgentRuntime.executeStep` 执行具体步骤，得到 `output`。
2. 立即调用 `reflectWithEvents(step, ..., output, attempt)`。
3. `ReflectionService.buildOutputSummaryContext` 只读取 `outputSummary`/`outputDigest`。
4. 因为摘要尚未生成，`outputSummary`/`outputDigest` 为空，最终写入 "(summary disabled)"。
5. 之后才执行 `stepRuntimeService.completeStep`，摘要生成晚于反思。

## 关键代码位置

- 反思调用位置
  - `src/main/java/com/example/agent/runtime/AgentRuntime.java`：`executeStep` 内 `reflectWithEvents(...)`

- 摘要生成位置
  - `src/main/java/com/example/agent/runtime/StepRuntimeService.java`：`completeStep` 内调用 `StepOutputSummaryBuilder.build(...)`

- 反思摘要构建
  - `src/main/java/com/example/agent/reflection/ReflectionService.java`：`buildOutputSummaryContext` 只读 `outputSummary`/`outputDigest`

## 不是因为“未执行摘要”的典型场景

- 即使 `agent.summary.enable=true`，反思仍可能拿不到摘要，因为摘要在反思之后生成。
- 如果 `agent.summary.enable=false`，摘要完全不会生成，反思一定为空。

## 修改方案

方案一（推荐）：反思前生成临时摘要
- 在 `executeStep` 反思前，使用 `StepOutputSummaryBuilder` 生成临时摘要并合并进 `output`。
- 不改变步骤完成语义，不影响后续 `completeStep` 的持久化行为。
- 需要新增一个“摘要预生成”方法或将 `StepOutputSummaryBuilder` 注入 `AgentRuntime`。

伪流程
1) output = 执行步骤
2) output = merge(临时摘要(output))
3) reflection = reflectWithEvents(..., output, ...)
4) completeStep(record, output, ...)

方案二：反思时直接从原始输出构建摘要
- 修改 `ReflectionService.buildOutputSummaryContext`，当 `outputSummary`/`outputDigest` 为空时，直接从 `output` 构建 digest。
- 避免改动执行流程，但会让反思逻辑变得更重。

方案三：调整时序（不推荐）
- 将 `completeStep` 前置到反思之前。
- 会导致步骤状态过早变为 `COMPLETED`，不利于反思触发重试或重规划。

## 影响范围与文件改动建议

代码改动
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
  - 在 `reflectWithEvents` 前补充摘要生成或注入摘要构建器

- `src/main/java/com/example/agent/runtime/StepRuntimeService.java`
  - 可选：提供 `buildSummarySnapshot` 方法供反思调用

- `src/main/java/com/example/agent/reflection/ReflectionService.java`
  - 可选：支持直接从 `output` 生成 digest 作为兜底

配置改动（视需求）
- `src/main/resources/application.yml`
  - 确认 `agent.summary.enable=true`

文档改动
- `doc/summary-disabled-analysis-202602011332.md`
  - 补充“反思阶段摘要为空”场景与时序说明

## 验证建议

- 用开启/关闭 `agent.summary.enable` 两种配置验证反思摘要行为。
- 验证反思前临时摘要是否可用，并确保 `completeStep` 仍能生成最终摘要。
- 验证反思触发重试时步骤状态未被提前标记为 `COMPLETED`。