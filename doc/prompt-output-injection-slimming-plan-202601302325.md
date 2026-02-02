# 提示词中输出全量注入扫描与精简方案（基于 reflection-llm-prompt-output-analysis-202601301749.md）

> 结论先行：工程内至少 6 处存在“将运行期输出/上下文全量注入提示词”的实现，其中反思与最终输出汇总对体量最敏感；ReAct 与多智能体也存在潜在“工具结果全量灌入”风险。建议以“白名单字段 + 体量上限 + 摘要/指纹”替代全量注入，逐步落地并配套指标与回归测试。

## 1. 全量注入位置总览（同类相似 output 注入）

### 1.1 反思提示词（高风险）
- 文件：`src/main/java/com/example/agent/reflection/ReflectionService.java`
- 方法：`buildReflectionPrompt`
- 标记：`REFLECTION_CONTEXT_JSON`
- 注入内容：
  - `stepType`
  - `attempt`
  - `output`（**完整步骤输出**）
- 风险：
  - 输出体量不可控，可能包含大对象（工具原始响应、上下文快照、证据包等）
  - 影响反思质量（噪声过大）与 JSON 解析稳定性
  - 增加 PII/敏感信息进入模型提示的概率

### 1.2 最终输出汇总（高风险）
- 文件：`src/main/java/com/example/agent/runtime/FinalOutputService.java`
- 方法：`buildFinalPrompt`
- 标记：`FINAL_CONTEXT_JSON`
- 注入内容：
  - `query`
  - `planSummary`
  - `steps`（**完整步骤输出列表**）
- 风险：
  - 步骤输出体量随执行步数/工具输出增长
  - 同时承担“总结 + 格式化输出”两类目标，容易被噪声拖累

### 1.3 ReAct 决策提示词（中高风险）
- 文件：`src/main/java/com/example/agent/runtime/ReactLoopService.java`
- 方法：`buildThinkPrompt`
- 标记：`REACT_CONTEXT_JSON`
- 注入内容：
  - `query`
  - `iteration`
  - `observations`（**观测列表，通常含工具输出**）
- 风险：
  - observation.content 可能为原始工具响应，体量不可控
  - 多轮累计导致提示膨胀

### 1.4 多智能体协同（中风险）
- 文件：`src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java`
- 方法：`buildPrompt`
- 标记：`MULTI_AGENT_CONTEXT_JSON`
- 注入内容：
  - `step.getInput()`（**完整步骤输入**，常含 `context`、`query`、`toolChoice` 等）
- 风险：
  - 输入中可能包含 `contextSnapshot`/`contextBudget` 等大对象
  - 规划信息与执行信息混杂，提示语义不清

### 1.5 链式推理（中风险）
- 文件：`src/main/java/com/example/agent/reasoning/ChainOfThoughtService.java`
- 方法：`buildPrompt`
- 标记：`COT_CONTEXT_JSON`
- 注入内容：
  - `question`
  - `memorySummary`
  - `recentObservation`
  - `previousSteps`
  - `stepIndex`/`maxSteps`
- 风险：
  - `previousSteps` 可能包含过长文本
  - 迭代次数多时增长明显

### 1.6 规划提示词（中风险）
- 文件：`src/main/java/com/example/agent/planning/PlannerService.java`
- 方法：`buildPlanPrompt`
- 标记：`PLAN_CONTEXT_JSON`
- 注入内容：
  - `query`
  - `context`（**全量上下文**，包含 `contextSnapshot`/`contextBudget`/`evidencePack` 等）
- 风险：
  - 上下文对象结构深、体量大
  - 与模型规划输出的结构无强约束，容易发生格式不稳定

### 1.7 研究/辩论（低风险）
- `ResearchPipeline.buildPrompt` 仅注入 `query`
- `DebateCoordinator.buildPrompt` 仅注入 `topic`
- 风险：当前可接受

## 2. 可精简性分析

总体判断：**可精简且有明显收益**。
- 模型需要的是“决定/总结”所需的关键信息，而非完整对象。
- 全量注入带来的收益有限，风险（体量、噪声、敏感信息）更高。
- 结构化裁剪后能更稳定地产生 JSON 格式输出，降低反思/总结失败率。

### 2.1 反思提示词
- **现状问题**：`output` 全量注入，包含深层结构与大字段。
- **建议**：
  - 仅注入：`outputKeys`、`outputSize`、`status`、`errorCode`、`errorMessage`、`answer`/`finalAnswer`（若有）
  - 附加：`outputDigest`（字段名+长度摘要）
  - 可选：`outputSample`（最长 500~1000 字符）
- **收益**：减少噪声，反思更聚焦“是否重试”。

### 2.2 最终输出汇总
- **现状问题**：`steps` 全量注入，多步骤/大输出易溢出。
- **建议**：
  - 仅保留每步：`stepId`、`type`、`status`、`toolName`、`summary`、`answer/finalAnswer`、`errorCode`
  - 统一字段：若 `output` 中有 `answer` 则提升为 `stepAnswer`
  - 若超过阈值，仅保留最后 N 步（建议 N=3~5）

### 2.3 ReAct 决策
- **现状问题**：`observations` 可能包含原始工具大输出。
- **建议**：
  - 对 `observation.content` 截断（例如 500~800 字）
  - 只保留最后 N 条观测
  - 增加 `observationDigest`（长度、关键词、是否含 error）

### 2.4 多智能体协同
- **现状问题**：`step.getInput()` 全量注入，实际需要的是“任务目标与角色需求”。
- **建议**：
  - 构造白名单上下文：`query`、`goal`、`constraints`、`toolsNeeded`、`skillName`
  - 避免注入 `contextSnapshot`/`budget`/`evidencePack` 等

### 2.5 链式推理
- **现状问题**：`previousSteps` 列表增长不可控。
- **建议**：
  - 仅保留最近 N 个 stepSummary
  - stepSummary 长度限制（例如 200 字）
  - 若有 `memorySummary`，优先使用并限制长度

### 2.6 规划提示词
- **现状问题**：上下文全量注入，结构杂乱。
- **建议**：
  - 规划上下文白名单字段：`mode`、`strategy`、`toolChoice`、`skillName`、`constraints`、`budgetHint` 等
  - 将 `contextSnapshot` 精简为 `snapshotId`、`tokenBudget`、`allowedTools`、`locale`

## 3. 建议方案（可落地）

### 方案 A：轻量精简（优先级高，改动小）
1) 新增工具方法 `PromptContextReducer`：
   - `reduceForReflection(output)`
   - `reduceForFinalOutput(stepOutputs)`
   - `reduceObservations(observations)`
2) 每个提示词构建处替换“全量对象 → 结构化缩减对象”。
3) 增加通用配置：
   - `prompt.maxContextChars`（默认 8000）
   - `prompt.maxListItems`（默认 5）
   - `prompt.maxFieldChars`（默认 1000）

### 方案 B：分层摘要（推荐）
1) 执行阶段产生“轻量摘要字段”：
   - `outputSummary`、`toolResultSummary`、`stepSummary`
2) 反思/最终输出仅注入摘要字段：
   - 原始大对象仅保留 `digest` 或 `snapshotId`
3) 增加结构化摘要生成：
   - `StepRuntimeService.completeStep` 时生成摘要字段

### 方案 C：体量预算驱动（中等复杂度）
1) 引入 `PromptBudgetAllocator`：
   - 按模块分配上下文预算
2) 超限后自动裁剪（FIFO/重要度排序）

## 4. 实施步骤建议

1) **反思/最终输出优先**：
   - 先替换 `ReflectionService.buildReflectionPrompt` 与 `FinalOutputService.buildFinalPrompt`
2) **ReAct 与多智能体**：
   - 对 `observations` 与 `stepInput` 加白名单与截断
3) **规划提示词**：
   - 增加 `PlanPromptContextBuilder`，只取必要字段
4) **指标与告警**：
   - 记录 `promptContextSize`、`truncatedFields`、`prunedItems`

## 5. 兼容性与风险
- 反思/最终输出输出格式不会改变，只改变输入上下文
- 若某些逻辑依赖原始字段，需要评估并保留最关键字段
- 建议引入配置开关 `prompt.reduce-context.enabled`，便于灰度

## 6. 测试建议
- 单测：
  - `PromptContextReducerTest`（字段保留与截断）
  - `ReflectionServiceTest`（输出 JSON 正确）
  - `FinalOutputServiceTest`（最终输出一致性）
- 回归：
  - 同步/异步任务链路
  - LLM 反思重试路径

---

如需，我可以继续给出“实际代码改动清单 + 参考实现骨架”。
