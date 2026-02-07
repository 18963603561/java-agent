# DefaultContextBudgetAllocator 上下文预算分配说明

## 1. 作用与定位
`DefaultContextBudgetAllocator` 负责**将“总 token 预算”按比例分配到上下文各个分段**，为后续的裁剪、压缩与提示词装配提供依据。
核心价值：
- **控制上下文大小**，避免提示词过长导致性能下降或上下文溢出。
- **明确优先级**，保证关键分段（如任务意图、工作记忆）优先保留。
- **支撑自动裁剪/压缩**，在预算超限时按策略收敛内容。


## 2. 预算分配流程（代码层面）
### 2.1 启用条件
- `ContextBudgetRequest.enabled == true`
- `agent.context.budget.enabled == true`
否则返回 `null`，后续裁剪/压缩流程不会执行。

### 2.2 总预算来源与上限
`DefaultContextBuilder.fillBudgetRequest(...)` 负责构建预算请求，顺序如下：
1) `context.tokenBudget`（请求上下文覆盖）
2) `agent.context.budget.total-budget-tokens`（默认值 8192）
3) `agent.context.default-token-budget`（默认值 4096）

`DefaultContextBudgetAllocator.resolveTotalTokens(...)` 还会受 `TokenBudgetManager.getThresholdTokens()` 影响，
即**总预算会被全局阈值上限“封顶”**。

### 2.3 分段比例
使用 `ContextBudgetPolicy.sectionRatios` 进行分配，默认比例来自 `ContextBudgetProperties.Ratios`：
- SYSTEM_POLICY 0.05
- DEVELOPER_POLICY 0.05
- USER_INPUT 0.40
- WORKING_MEMORY 0.20
- DOMAIN_KNOWLEDGE 0.10
- LONG_TERM_MEMORY 0.10
- TOOL_SUMMARY 0.05
- TOOL_SCHEMA 0.03
- EVIDENCE_PACK 0.02
- SLACK 0.0

若比例之和 > 1，会自动归一化。

### 2.4 输出结果
返回 `ContextBudgetAllocation`，内含：
- 总预算、保留预算
- 各分段 token 数
- 派生预算字段（如 workingMemoryBudget 等）


## 3. 体现在哪些场景
### 3.1 上下文裁剪（ContextTrimmer）
- `DefaultContextTrimmer` 使用分段预算判断是否超限，超限则按策略裁剪对应分段。
- 典型场景：
  - 记忆召回过多导致 `WORKING_MEMORY` 超预算
  - 证据包/引用列表过多导致 `EVIDENCE_PACK`/`DOMAIN_KNOWLEDGE` 超预算

### 3.2 上下文剪枝（ContextPruner）
- `DefaultContextPruner` 依赖 `ContextBudgetAllocation` 对 `WORKING_MEMORY.summary` 执行预算剪枝。
- 典型场景：长摘要/多证据情况下需要保底压缩。

### 3.3 上下文压缩（ContextCompressionController）
- 压缩触发依据包含预算维度：当估算 token 超预算时触发压缩逻辑。
- 典型场景：长对话/多轮任务导致上下文持续膨胀。

### 3.4 事件与指标
- 分配结果会记录指标与事件，便于监控预算使用与裁剪行为：
  - `context_budget_*` 指标
  - `ContextEventPublisher` 快照事件


## 4. 使用 vLLM 时是否“无预算就无作用”？
### 4.1 结论
**不等于“没有作用”。**
上下文预算是系统内部控制机制，与模型提供方无直接绑定。即使使用 vLLM，本机制仍会按配置生效。

### 4.2 关键点
- vLLM 可能不会提供外部“预算”配置，但系统仍会根据默认预算自动分配与裁剪。
- 只要 `agent.context.budget.enabled=true`，就会执行分配与裁剪。
- **如果你希望完全不使用预算**，需要显式关闭（见下一节）。

### 4.3 注意：totalTokens 为 0 并不等于“无作用”
- 若预算仍启用、但分配结果为 0，`DefaultContextTrimmer` 会判断分段超预算并执行强裁剪。
- 因此“预算为 0”反而可能导致**极端裁剪**。


## 5. 如何关闭或控制预算
### 5.1 全局关闭
- 配置：`agent.context.budget.enabled=false`
- 结果：`DefaultContextBuilder.resolveBudgetRequest(...)` 返回 `null`，预算分配/裁剪/压缩不生效。

### 5.2 调整总预算
- `agent.context.budget.total-budget-tokens`
- 或在请求 `context.tokenBudget` 覆盖

### 5.3 调整分段比例
- 通过 `agent.context.budget.ratios.*` 调整各分段占比


## 6. 适用场景总结
- **长对话/长任务**：自动压缩防止上下文爆炸。
- **多工具调用/证据采集**：控制证据包大小与工具摘要大小。
- **多轮规划**：保持任务意图与工作记忆优先可见。
- **成本/延迟控制**：减少大提示词导致的推理成本与延迟。


## 7. 相关代码位置
- `src/main/java/com/example/agent/budget/DefaultContextBudgetAllocator.java`
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/budget/DefaultContextTrimmer.java`
- `src/main/java/com/example/agent/budget/DefaultContextPruner.java`
- `src/main/java/com/example/agent/budget/ContextCompressionController.java`
- `src/main/java/com/example/agent/budget/ContextBudgetProperties.java`