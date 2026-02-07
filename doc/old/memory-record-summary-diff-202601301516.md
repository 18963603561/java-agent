# MemoryRecord 中 ConversationSummary 与 WorkingMemorySummary 区别说明

## 1. 结论摘要
- **ConversationSummary**：面向“整段会话”的稳定概括，强调长期/全局要点（bullets）。
- **WorkingMemorySummary**：面向“当前任务”的近期要点，强调最近/可执行事项（items）。
- 二者都属于**压缩结果的结构化摘要**，优先用于上下文构建与裁剪压缩，而不是直接落库存储。


## 2. 结构与字段差异
### 2.1 ConversationSummary
- **核心字段**：`summary` + `bullets`
- **语义**：对整段会话的关键事实抽取，适合长期记忆与总览。
- **统计字段**：`summaryChars`、`bulletCount`
- **降级输出**：`toLegacyText()` 优先使用 `summary`，否则拼接 `bullets`。

### 2.2 WorkingMemorySummary
- **核心字段**：`summary` + `items`
- **语义**：面向当前任务的工作记忆条目（近期、可执行、可追踪）。
- **统计字段**：`summaryChars`、`itemCount`
- **降级输出**：`toLegacyText()` 优先使用 `summary`，否则拼接 `items`。


## 3. 生成逻辑与差异来源
### 3.1 生成入口
- `MemoryStore.autoCompressIfNeeded(...)` -> `CompressedMemoryStore.compress(...)`
- 压缩时写入 `MemoryRecord`：
  - `conversationSummary`
  - `workingMemorySummary`
  - `summary`（兼容旧逻辑）

### 3.2 ConversationSummary 的构建方式
- `CompressedMemoryStore.buildConversationSummary(...)`
- **取样范围**：从会话记录“前部”抽取若干条（通过 `collectItems`）。
- **摘要形式**：`summary` 使用“逗号分隔”的句段拼接，`bullets` 保存条目列表。
- **长度/数量限制**：
  - 条目数：`MAX_BULLET_COUNT = 8`
  - 单条长度：`MAX_ITEM_CHARS = 120`
  - 总摘要长度：`MAX_SUMMARY_CHARS = 800`

### 3.3 WorkingMemorySummary 的构建方式
- `CompressedMemoryStore.buildWorkingMemorySummary(...)`
- **取样范围**：从会话记录“尾部”抽取若干条（通过 `collectTailItems`）。
- **摘要形式**：`summary` 使用“ | ”拼接，`items` 保存条目列表。
- **长度/数量限制**：
  - 条目数：`MAX_ITEM_COUNT = 6`
  - 单条长度：`MAX_ITEM_CHARS = 120`
  - 总摘要长度：`MAX_SUMMARY_CHARS = 800`

> 结论：ConversationSummary 更偏“历史全局”，WorkingMemorySummary 更偏“近期与执行态”。


## 4. 使用位置与作用
### 4.1 上下文构建（核心用途）
- `DefaultContextBuilder.resolveStructuredSummary(...)` 会从记忆记录中优先取结构化摘要，填充 `WorkingMemory`：
  - `ConversationSummary` -> `WorkingMemory.summary` / `keyFacts`
  - `WorkingMemorySummary` -> `WorkingMemory.summary` / `keyFacts`
- 当存在结构化摘要时，会标记 `usedStructuredSummary=true`、`summaryVersion` 等统计字段。

### 4.2 预算压缩场景
- `ContextCompressionController.applyCompressedSummary(...)` 直接使用压缩记录的
  `conversationSummary` 与 `workingMemorySummary` 来写入当前快照的 `WorkingMemory` 与 `LongTermMemory`。

### 4.3 兼容旧逻辑
- `CompressedMemoryStore.resolveLegacySummary(...)` 的优先级：
  1) `conversationSummary.toLegacyText()`
  2) `workingMemorySummary.toLegacyText()`
  3) 非结构化 `summary` 拼接


## 5. 在数据库中的表现
- `ConversationSummary` 与 `WorkingMemorySummary` **不是** `memory_records` 表字段，
  在默认 JDBC 落库结构中**不持久化**。
- 该类对象主要用于**内存态、上下文构建与事件链路**，落库只保留 `summary/content` 等基础字段。


## 6. 场景示例
### 场景 A：长期会话总结
- **需求**：需要给用户“整体会话要点”。
- **优先使用**：`ConversationSummary.bullets` + `summary`。
- **输出形式**：要点列表 + 总结段落。

### 场景 B：多步任务执行中
- **需求**：模型需要记住“最近做了什么、下一步做什么”。
- **优先使用**：`WorkingMemorySummary.items`。
- **输出形式**：条目化行动清单。

### 场景 C：上下文压缩触发
- **需求**：预算不足时保留“全局概括 + 当前要点”。
- **方式**：`ConversationSummary` 作为长程摘要，`WorkingMemorySummary` 作为短程摘要。


## 7. 建议用法
1) **展示层区分**：
   - ConversationSummary 用于“总体回顾”。
   - WorkingMemorySummary 用于“当前任务状态/下一步”。
2) **优先结构化摘要**：
   - 如果结构化摘要存在，避免再拼接长文本，减少 token 成本。
3) **注意落库差异**：
   - 若需要持久化结构化摘要，需扩展 `memory_records` 或单独表。


## 8. 相关代码位置
- `src/main/java/com/example/agent/memory/MemoryRecord.java`
- `src/main/java/com/example/agent/memory/ConversationSummary.java`
- `src/main/java/com/example/agent/memory/WorkingMemorySummary.java`
- `src/main/java/com/example/agent/memory/CompressedMemoryStore.java`
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/budget/ContextCompressionController.java`