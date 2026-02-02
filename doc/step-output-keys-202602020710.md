# 步骤输出字段分析（`LLM`/`TOOL`/`THOUGHT_TREE`/`CHAIN_OF_THOUGHT`/`REACT`）

## 1. 范围与结论
本文分析步骤执行后 `Map<String, Object> output` 中的字段来源与必需性，覆盖 `LLM`、`TOOL`、`THOUGHT_TREE`、`CHAIN_OF_THOUGHT`、`REACT` 五类步骤。
结论要点：
1) 各步骤类型都会产出“专属业务字段”，随后可能被摘要构建器注入通用摘要字段。
2) 最终合并结果阶段有两条路径：
   - 若最后一步是 `LLM` 或 `ANSWER`，直接使用该步输出，必须保证 `answer` 或 `finalAnswer` 之一可用。
   - 否则由最终汇总服务读取步骤摘要，必须保证摘要字段可读；默认由 `buildStepOutputSummary` 补齐。

## 2. 通用摘要字段（跨步骤类型）
### 2.1 摘要构建器注入的字段
`StepOutputSummaryBuilder#build` 会生成以下字段并合并到步骤输出：
- `outputSummary`：输出摘要层，可能包含 `status`、`hasOutput`、`stepId`、`type`、`summary`、`attempt`、`keyFields`、`sample`、`error`。
- `toolResultSummary`：工具结果摘要层，可能包含 `tool`、`resultKeys`、`sample`。
- `stepSummary`：步骤摘要层，可能包含 `stepId`、`type`、`status`、`attempt`、`tool`、`summary`。
- `outputDigest`：输出指纹层，包含 `keyCount`、`keys`、`charCount`、`truncated`。
- `truncated`：摘要是否被截断。

### 2.2 原始输出快照字段
当摘要功能未启用时，`RawOutputSnapshotBuilder` 可生成：
- `rawOutputSnapshot`：包含 `text`、`truncated`，并在有键时包含 `keyCount`、`keys`。

### 2.3 摘要字段的必需性
- 反思与最终汇总阶段都会优先读取 `stepSummary`，其次 `outputSummary`，再退化到 `rawOutputSnapshot` 或 `outputDigest`。
- 在默认实现中，`AgentRuntime#buildStepOutputSummary` 会兜底补齐 `stepSummary`、`outputDigest`、`truncated`，因此最终汇总阶段至少能拿到摘要信息。

## 3. 各步骤类型输出字段与必需性
### 3.1 `LLM`
**字段来源**：`LlmStepService#run` 与 `AgentRuntime#executeLlmStep`。

**主要字段**：
- 直接回答路径：`mode`、`answer`、`reason`、`confidence`、`source`。
- 工具调用路径：模型总结结果（期望包含 `answer`、`highlights`、`confidence`），并额外补充 `mode=tool_call`、`tool`（含 `name`、`arguments`）、`toolStatus`、`toolErrorCode`、`toolErrorMessage`、`source`。
- 工具失败兜底：`mode=tool_call`、`answer`、`highlights`、`confidence`、`toolErrorCode`、`toolDecision`、`source`。
- 空输出兜底：`answer=no_response`、`source=llm_step`。

**必需字段（语义层面）**：
- 若最终合并阶段直接使用该步输出（最后一步为 `LLM` 或 `ANSWER`），则必须保证 `answer` 或 `finalAnswer` 至少存在一个。

**专属字段**：
- `mode`、`reason`、`highlights`、`tool`、`toolStatus`、`toolErrorCode`、`toolErrorMessage`、`toolDecision`、`source`。

### 3.2 `TOOL`
**字段来源**：`EnforcementGateway` -> `ToolExecutor`。

**主要字段**（成功路径固定写入）：
- `tool`：工具名称。
- `result`：工具返回结果，结构由工具自身决定。
- `tokenUsage`：计量记录对象，包含 `recordId`、`usageId`、`taskId`、`agentId`、`model`、`provider`、`inputTokens`、`outputTokens`、`totalTokens`、`costUsd`、`createdAt`、`tenantId`。
- `cacheHit`：是否命中缓存。

**可能的扩展字段**：
- `result.sandbox`、`result.sandboxStatus`（沙箱执行结果）。
- 业务工具自定义字段（位于 `result` 内）。

**必需字段**：
- 成功路径下 `tool`、`result`、`tokenUsage`、`cacheHit` 固定存在。

**专属字段**：
- `tool`、`result`、`tokenUsage`、`cacheHit` 以及 `result` 内工具自定义字段。

### 3.3 `THOUGHT_TREE`
**字段来源**：`AgentRuntime#executeThoughtTree`。

**主要字段**（固定写入）：
- `bestSolution`：最优方案文本。
- `confidence`：置信度。
- `totalThoughts`：总节点数。
- `treeDepth`：树深度。
- `nodes`：展开后的节点列表，节点字段包含 `nodeId`、`content`、`score`、`parentId`、`depth`、`terminal`、`explanation`、`tokensUsed`、`children`。

**必需字段**：
- 上述五个字段固定写入，但值可能为空。

**专属字段**：
- `bestSolution`、`totalThoughts`、`treeDepth`、`nodes`。

### 3.4 `CHAIN_OF_THOUGHT`
**字段来源**：`AgentRuntime#executeChainOfThought`。

**主要字段**（固定写入）：
- `finalAnswer`、`stepsCount`、`confidence`、`stopReason`、`status`（`COMPLETED` 或 `STOPPED`）。

**必需字段**：
- 上述字段固定写入，但值可能为空。

**专属字段**：
- `finalAnswer`、`stepsCount`、`stopReason`、`status`。

### 3.5 `REACT`
**字段来源**：`AgentRuntime#executeReactLoop`。

**主要字段**（固定写入）：
- `iterations`、`completed`、`stopReason`、`finalAnswer`、`observations`、`status`（`COMPLETED` 或 `UNRESOLVED`）。
- `observations` 内部元素为 `ReactObservation`，包含 `content`、`tool`、`timestamp`。

**必需字段**：
- 上述字段固定写入，但值可能为空。

**专属字段**：
- `iterations`、`completed`、`observations`、`status`。

## 4. 最终合并结果阶段的必需字段
最终合并存在两条路径：

### 4.1 直接使用最后一步输出
`AgentRuntime#resolveFinalOutputFromSteps` 在最后一步类型为 `LLM` 或 `ANSWER` 时直接返回输出。
- 必需字段：`answer` 或 `finalAnswer`。
- 若输出不是 `Map`，会转成 `answer=output.toString()`。

### 4.2 使用汇总服务生成最终结果
`FinalOutputService#buildStepSummaries` 会从步骤输出中提取摘要。
- 优先级顺序：`stepSummary.summary` → `outputSummary.summary` → `rawOutputSnapshot.text` → `outputDigest`。
- 为保证摘要可用，至少需要上述链路中的一个字段存在。
- 默认实现中，`AgentRuntime#buildStepOutputSummary` 会补齐 `stepSummary`、`outputDigest`、`truncated`，因此不会出现摘要缺失。

## 5. 关键来源文件
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`：步骤类型分发、各步骤输出字段、摘要补齐、最终输出直返逻辑。
- `src/main/java/com/example/agent/runtime/LlmStepService.java`：`LLM` 步骤输出结构与工具调用路径。
- `src/main/java/com/example/agent/agentcore/ToolExecutor.java`、`src/main/java/com/example/agent/agentcore/EnforcementGateway.java`：`TOOL` 输出结构。
- `src/main/java/com/example/agent/runtime/StepOutputSummaryBuilder.java`：通用摘要字段结构。
- `src/main/java/com/example/agent/runtime/RawOutputSnapshotBuilder.java`：原始输出快照字段结构。
- `src/main/java/com/example/agent/runtime/FinalOutputService.java`：最终合并阶段摘要读取规则。
- `src/main/java/com/example/agent/runtime/ReactLoopResult.java`、`src/main/java/com/example/agent/runtime/ReactObservation.java`：`REACT` 输出结构。
- `src/main/java/com/example/agent/reasoning/ChainOfThoughtResult.java`：`CHAIN_OF_THOUGHT` 结果结构。
- `src/main/java/com/example/agent/reasoning/ThoughtTreeResult.java`、`src/main/java/com/example/agent/reasoning/ThoughtNode.java`：`THOUGHT_TREE` 结果结构。