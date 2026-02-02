# ContextCompressionController.compressIfNeeded 方法说明

## 1. 方法职责概述
`compressIfNeeded(...)` 的职责是：**在上下文预算超限时触发记忆压缩，并将压缩摘要回填到当前上下文快照**，从而降低后续提示词长度与运行成本。

它相当于“预算超限时的收敛阀门”，保证长对话或复杂任务不会无限膨胀。


## 2. 主要执行流程（按代码顺序）
1) **基础校验与开关判断**
- `request` 或 `snapshot` 为空直接返回。
- `ContextCompressionProperties.enabled=false` 或 `allocation` 为空，直接返回。

2) **获取裁剪后 token 统计**
- 优先使用 `trimReport` 的 `sectionTokensAfter` 与 `totalAfterTokens`。
- 若无裁剪报告，调用 `estimateSectionTokens(snapshot)` 自行估算。
- 写入 `result.beforeTokens` 与 `result.afterTrimTokens`。

3) **判断是否需要触发压缩**
- `resolveTriggerReason(...)` 检查：
  - 是否超过**总预算**（`OVER_TOTAL`）
  - 是否超过**分段预算**（`OVER_SECTION`）
- 两者都不满足则不触发压缩，直接返回。

4) **冷却窗口控制**
- 以 `workflowId` 或 `sessionId` 为 key 做“压缩冷却”，避免短时间重复压缩。
- 若在冷却期内：标记 `skippedCooldown=true` 并返回。

5) **触发记忆压缩**
- 要求 `sessionId` 必须存在，否则跳过。
- 调用 `memoryStore.compress(...)` 生成压缩记忆（本质是生成结构化摘要）。

6) **回填压缩摘要**
- `applyCompressedSummary(...)` 将压缩结果写入：
  - `WorkingMemory.summary` / `keyFacts`
  - `WorkingMemory` 结构化摘要版本与统计字段
  - `LongTermMemory` 加入新的 `MemoryRef`

7) **评估压缩后是否仍超预算**
- 重新估算 token，写入 `afterCompressTokens`。
- 若仍超限：记录指标并输出日志。

8) **记录结果与日志**
- 标记 `result.triggered=true`、记录触发原因、耗时等。
- 记录统计指标与日志。


## 3. 为什么要做压缩
### 3.1 控制上下文成本
- 上下文越长，模型推理开销越高，响应变慢且成本上升。
- 压缩将历史内容转为结构化摘要，保留关键信息，减少 token 消耗。

### 3.2 防止对话持续膨胀
- 长会话或多轮任务会不断积累：
  - 记忆召回
  - 工具证据
  - 长期记忆引用
- 在预算超限时必须收敛，否则无法继续执行。

### 3.3 与裁剪配合形成“软硬双保险”
- `ContextTrimmer`：先做“裁剪”与“缩短”。
- `ContextCompressionController`：当裁剪仍不足时，再做“压缩摘要化”。


## 4. 触发条件（触发点）
由 `resolveTriggerReason(...)` 决定，依赖：
- **总预算超限**：`totalTokens > allocation.totalTokens`
- **分段预算超限**：某 `ContextSection` 超过分配预算

是否触发还受开关影响：
- `ContextCompressionProperties.triggerOverTotalBudget`
- `ContextCompressionProperties.triggerOverSectionBudget`


## 5. 关键依赖与影响
### 5.1 依赖
- `MemoryStore.compress(...)`：负责生成压缩记忆与结构化摘要。
- `TokenEstimator`：用于估算各分段 token。
- `ContextBudgetAllocation`：提供预算基线。

### 5.2 影响
- **WorkingMemory**：被新的压缩摘要覆盖/更新。
- **LongTermMemory**：加入 `compressed` 记忆引用。
- **事件链路**：后续上下文事件会带上压缩结果统计。


## 6. 典型场景
### 场景 A：长对话压缩
- 会话多轮、记忆召回很多，`WORKING_MEMORY` 超预算。
- 裁剪后仍超限 → 触发压缩，将历史对话转换为摘要。

### 场景 B：检索与证据过多
- `EVIDENCE_PACK` 或 `DOMAIN_KNOWLEDGE` 超限。
- 触发压缩后，保留结构化摘要，减少证据细节。

### 场景 C：持续工具调用
- 近期工具调用记录膨胀，导致总预算超限。
- 压缩后只保留摘要与关键事实。


## 7. 注意事项
- **必须有 sessionId**：否则无法回到记忆层进行压缩。
- **冷却期避免频繁压缩**：由 `minIntervalSeconds` 控制。
- **压缩后仍可能超预算**：会记录指标，但不会进一步强制处理。
- **压缩结果依赖 MemoryStore**：若记忆存储不可用，会直接跳过。


## 8. 相关代码位置
- `src/main/java/com/example/agent/budget/ContextCompressionController.java`
- `src/main/java/com/example/agent/memory/MemoryStore.java`
- `src/main/java/com/example/agent/memory/CompressedMemoryStore.java`
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`