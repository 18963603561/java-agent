# 上下文分配/裁剪/压缩流程分析（基于现有实现）

生成时间: 2026-01-31 13:29
参考:
- doc/context-snapshot-usage-202601301513.md
- doc/default-context-budget-allocator-analysis-202601301517.md

## 1. 总体流程总览（从构建到压缩）
入口：`DefaultContextBuilder.build(ContextBuildRequest)`

执行顺序（代码顺序）：
1) 构建 `ContextSnapshot` 基础字段（runtimeMeta/roleBoundary/taskIntent/workingMemory/domainKnowledge/longTermMemory/toolState/audit）。
2) 预算分配（`ContextBudgetAllocator.allocate`），产出 `ContextBudgetAllocation`。
3) 裁剪（prune）：`ContextPruner.prune(ContextPruneRequest)`。
4) 裁剪（trim）：`ContextTrimmer.trim(ContextTrimRequest)`。
5) 压缩（compress）：`ContextCompressionController.compressIfNeeded(ContextCompressionRequest)`。
6) 事件发布：裁剪阶段 `CONTEXT_TRIMMED`、压缩阶段 `CONTEXT_COMPRESSED`。

关键说明：
- “压缩包含裁剪功能”的现象来自 **压缩在 trim 之后触发**，且压缩前会基于 trim 结果估算 token。
- **压缩本身并不执行 trim/裁剪逻辑**，而是调用记忆压缩并回填摘要与引用。

相关代码：
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/budget/DefaultContextPruner.java`
- `src/main/java/com/example/agent/budget/DefaultContextTrimmer.java`
- `src/main/java/com/example/agent/budget/ContextCompressionController.java`


## 2. 预算分配（Allocation）
核心职责：将总 token 预算分配到分段，为后续裁剪/压缩提供阈值。

触发条件：
- `ContextBudgetRequest.enabled=true`
- `agent.context.budget.enabled=true`

预算来源顺序（由 `DefaultContextBuilder.fillBudgetRequest` 组装）：
1) `context.tokenBudget`
2) `agent.context.budget.total-budget-tokens`
3) `agent.context.default-token-budget`

分段预算输出：`ContextBudgetAllocation.sectionTokens`，由 `DefaultContextBudgetAllocator` 按比例分配，必要时归一化。

相关代码：
- `src/main/java/com/example/agent/budget/DefaultContextBudgetAllocator.java`
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`（budgetRequest/allocate）


## 3. 裁剪一：Prune（策略裁剪）
位置：`DefaultContextPruner.prune`

特点：
- 依赖策略 `ContextPolicy`，用于“结构性裁剪”（以数量为主）。
- 裁剪顺序可配置（`ContextPolicy.pruneOrder`），否则默认：
  `LONG_TERM_MEMORY -> EVIDENCE_PACK -> DOMAIN_KNOWLEDGE -> WORKING_MEMORY`

典型裁剪行为：
- `maxMemoryCount`：裁剪长期记忆引用数。
- `maxEvidenceCount`：裁剪证据包 items 与引用列表。
- `WORKING_MEMORY.summary`：按预算 token 估算截断。

相关代码：
- `src/main/java/com/example/agent/budget/DefaultContextPruner.java`


## 4. 裁剪二：Trim（预算裁剪）
位置：`DefaultContextTrimmer.trim`

特点：
- 依赖 `ContextBudgetAllocation` 分段预算。
- 同时处理“分段超限”和“总预算超限”。
- 有固定裁剪顺序（可配置），默认按分段逐步收缩内容。

关键机制：
- 先估算各分段 token（`estimateSectionTokens`）。
- 若分段超限：逐分段裁剪（EvidencePack/LongTermMemory/DomainKnowledge/WorkingMemory/ToolSummary/Task+System）。
- 若总预算超限：再次按顺序收缩。

具体裁剪方式示例：
- EvidencePack：先截断 `resultDigest`、`argsDigest`，再删 `toolCalls`、`items`。
- LongTermMemory：按 score/过期时间/摘要长度排序后移除。
- WorkingMemory：截断 summary、keyFacts、planSteps 等。
- ToolSummary：截断工具描述/标签等。

相关代码：
- `src/main/java/com/example/agent/budget/DefaultContextTrimmer.java`


## 5. 压缩（Compression）到底做了什么？
位置：`ContextCompressionController.compressIfNeeded`

触发条件：
- `agent.context.compression.enabled=true`
- 有 `ContextBudgetAllocation`
- 超预算触发：
  - `triggerOverTotalBudget=true` 且 total 超预算
  - 或 `triggerOverSectionBudget=true` 且分段超预算

关键步骤：
1) 计算 token（优先用 `ContextTrimReport`；否则重新估算）。
2) 判断超限原因 `OVER_TOTAL` / `OVER_SECTION` / 二者。
3) 防抖：根据 workflowId/sessionId 做最小间隔限制。
4) 调用记忆压缩：`MemoryStore.compress(CompressionRequest)`。
5) 将压缩结果回填到 `ContextSnapshot`：
   - `WorkingMemory.summary/keyFacts` 更新为压缩摘要
   - `WorkingMemory.usedStructuredSummary=true`
   - `LongTermMemory.memoryRefs` 追加一条 `compressed` 引用（source=budget_compress）

**注意**：压缩不会像 Trimmer 那样逐字段删减；它是生成“摘要”并替换工作记忆内容。

相关代码：
- `src/main/java/com/example/agent/budget/ContextCompressionController.java`
- `src/main/java/com/example/agent/memory/MemoryStore.java`
- `src/main/java/com/example/agent/memory/CompressedMemoryStore.java`


## 6. 压缩“具体怎么压缩”
压缩的本质是“生成结构化摘要”，并不调用模型或外部服务。生成逻辑在 `CompressedMemoryStore`：

摘要构成：
- `ConversationSummary`：收集若干条 bullet（最多 8 条），拼接为摘要文本。
- `WorkingMemorySummary`：收集最近 N 条记录（最多 6 条）作为 items。
- `summary`（legacy）：从结构化摘要派生，长度上限 800 字符。

生成来源：
- 从 `MemoryRecord` 的 `summary` 或 `content` 拼装。
- 会跳过 layer=compressed 的记录，避免递归。

关键限制：
- bullets/item/summaries 均有长度与数量限制（MAX_BULLET_COUNT/MAX_ITEM_COUNT/MAX_ITEM_CHARS/MAX_SUMMARY_CHARS）。

相关代码：
- `src/main/java/com/example/agent/memory/CompressedMemoryStore.java`


## 7. 为什么“看起来压缩包含裁剪”？
实际原因：
- 压缩在 trim 之后执行，trim 已经先做过强裁剪。
- 压缩阶段会根据 trim report 计算 token，以“trim 后的状态”触发压缩。
- 压缩成功后会替换 workingMemory.summary/keyFacts，并新增 compressed 引用，看起来像“二次裁剪”。

结论：
- **裁剪（prune/trim）是结构性删减与截断**。
- **压缩是生成摘要并替换内容**，不等价于“裁剪”。


## 8. 是否存在其他压缩方案？
从现有代码看，压缩方案主要有三类：
1) **上下文压缩（ContextCompressionController）**：预算触发，将会话记忆压缩为摘要回填到 `ContextSnapshot`。
2) **自动记忆压缩（MemoryStore.autoCompressIfNeeded）**：写入/检索时按 `MemoryPolicy` 条件触发，对会话记录生成 compressed 记忆。
3) **手工压缩接口**：`MemoryController.compress` 调用 `MemoryStore.compress` 手动触发。

**未发现**其他“多策略/多模型”的压缩实现或替代方案（例如 LLM 摘要、多层级压缩、分段摘要等）。

相关代码：
- `src/main/java/com/example/agent/memory/MemoryStore.java`
- `src/main/java/com/example/agent/gateway/controller/MemoryController.java`


## 9. 关键事件与指标（可观测性）
- 裁剪：`ContextSnapshotStage.CONTEXT_TRIMMED`，指标 `context_trim_*`。
- 压缩：`ContextSnapshotStage.CONTEXT_COMPRESSED`，指标 `context_compression_*`。

相关代码：
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/streaming/ContextEventPublisher.java`


## 10. 你可以重点关注的代码点（定位压缩细节）
- `ContextCompressionController.applyCompressedSummary(...)`
  - 真正执行“回填摘要、更新 workingMemory、追加 compressed 引用”的地方。
- `CompressedMemoryStore.buildConversationSummary/buildWorkingMemorySummary(...)`
  - 结构化摘要生成逻辑。
- `DefaultContextTrimmer.trimEvidencePack/trimWorkingMemory/...`
  - 裁剪与截断的具体规则。


## 11. 结论摘要
- **分配**：由 `DefaultContextBudgetAllocator` 完成分段预算。
- **裁剪**：分两阶段（Prune + Trim），分别负责“数量裁剪”和“预算裁剪”。
- **压缩**：并非裁剪，而是基于记忆记录生成结构化摘要，回填到 `ContextSnapshot`，并添加 compressed 引用。
- **压缩触发**：预算超限 + 冷却间隔 + 有会话与可用 memoryStore。
- **其他压缩方案**：目前仅有会话记忆压缩（自动/手工）与上下文压缩，未见多模型或多策略压缩。

