# applyStructuredSummary 中 WorkingMemorySummary 与 ConversationSummary 的优先级说明

## 1. 结论摘要
`DefaultContextBuilder.applyStructuredSummary(...)` 的优先级**不是单一方向**，而是“字段级别”的混合策略：
- **summary 文本**：`ConversationSummary` 先写入，`WorkingMemorySummary` 仅在 summary 仍为空时补充。
- **keyFacts 列表**：`ConversationSummary` 先写入（仅在为空时），但 `WorkingMemorySummary.items` 会**直接覆盖**。
- **summaryVersion / summaryChars**：优先取 `ConversationSummary`，无值时才用 `WorkingMemorySummary`。
- **workingMemoryItems**：优先取 `WorkingMemorySummary.itemCount`，无值才用 `ConversationSummary.bulletCount`。

因此，不能简单理解为“前面的覆盖后面”或“WorkingMemorySummary 完全优先”。优先级随字段不同而不同。


## 2. 方法位置
- 文件：`src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- 方法：`applyStructuredSummary(WorkingMemory memory, StructuredSummary structuredSummary)`


## 3. 具体优先级规则（按字段拆解）
### 3.1 summary（文本摘要）
1) **先尝试 ConversationSummary**：
   - `summaryText = firstNonBlank(conversationSummary.getSummary(), conversationSummary.toLegacyText())`
   - 若有值，直接写入 `memory.summary`
2) **再处理 WorkingMemorySummary**：
   - 仅当 `memory.summary` 仍为空时，才写入 `workingSummary` 的 `summary/legacyText`

**结论**：summary 的优先级是 **ConversationSummary > WorkingMemorySummary（仅兜底）**。

### 3.2 keyFacts（要点列表）
1) **ConversationSummary.bullets**：
   - 若 `memory.keyFacts` 为空，则用 `bullets` 填充。
2) **WorkingMemorySummary.items**：
   - 若 `items` 非空，则**直接覆盖** `memory.keyFacts`（不检查是否已有值）。

**结论**：keyFacts 的最终优先级是 **WorkingMemorySummary.items > ConversationSummary.bullets**。

### 3.3 summaryVersion
- `firstNonBlank(conversationSummary.version, workingSummary.version)`

**结论**：`ConversationSummary.version` 优先。

### 3.4 summaryChars
- `firstNonNull(conversationSummary.summaryChars, workingSummary.summaryChars)`

**结论**：`ConversationSummary.summaryChars` 优先。

### 3.5 workingMemoryItems
- `firstNonNull(workingSummary.itemCount, conversationSummary.bulletCount)`

**结论**：`WorkingMemorySummary.itemCount` 优先。


## 4. 为什么会是“混合优先级”
### 4.1 语义差异
- `ConversationSummary` 偏“全局稳定概括”，适合默认摘要与版本信息。
- `WorkingMemorySummary` 偏“近期执行要点”，适合 keyFacts 与条目统计。

### 4.2 设计意图
- 保留“全局摘要”的稳定性（summary/version/summaryChars）
- 强化“当前任务要点”的可执行性（keyFacts/items/workingMemoryItems）


## 5. 结合代码顺序的正确理解
虽然方法里先处理 `ConversationSummary`，再处理 `WorkingMemorySummary`，但只有部分字段会被后者覆盖：
- summary：不覆盖
- keyFacts：覆盖
- summaryVersion/summaryChars：不覆盖
- workingMemoryItems：覆盖

所以“前面覆盖后面”的理解是**不准确的**，应按字段理解。


## 6. 适用场景解释
- **需要整体会话摘要**时：依赖 ConversationSummary.summary。
- **需要当前任务执行要点**时：依赖 WorkingMemorySummary.items。
- **需要统计信息/监控**时：summaryChars 与 workingMemoryItems 各自取优先级字段。


## 7. 相关代码位置
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/memory/ConversationSummary.java`
- `src/main/java/com/example/agent/memory/WorkingMemorySummary.java`