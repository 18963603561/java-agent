# 上下文事件载荷细化与阶段二范围确认

## 输入依据
```
doc/context-engineering-interface-draft-20260128.md
doc/context-engineering-stage1-implementation-list-20260128.md
doc/context-engineering-stage1-acceptance-20260128.md
```

## 事件载荷字段细化

### 基础字段
- `eventId`：事件唯一标识，与事件流一致。
- `eventType`：事件类型名称，对应上下文相关事件。
- `snapshotId`：上下文快照标识。
- `traceId`：链路追踪标识，用于跨模块关联。
- `requestId`：请求标识，用于接口级回溯。
- `eventTime`：事件发生时间。

### 变更摘要 `delta`
- `changedSections`：发生变化的上下文分段名称列表。
- `summary`：变更摘要说明。
- `removedCount`：被移除条目数量。
- `removedItemTypes`：被移除条目类型统计。

### 快照统计 `snapshotSummary`
- `sections`：快照包含的上下文分段名称列表。
- `taskId`：任务标识。
- `inputSize`：任务输入长度。
- `constraintCount`：约束条件数量。
- `approvalRequired`：是否需要审批。
- `riskLevel`：风险等级标识。
- `workingSummarySize`：工作记忆摘要长度。
- `keyFactCount`：关键事实数量。
- `planStepCount`：计划步骤数量。
- `evidenceCount`：证据条目数量。
- `citationCount`：领域引用数量。
- `memoryCount`：长期记忆引用数量。
- `toolCount`：可用工具数量。
- `selectedToolCount`：已选工具数量。
- `recentToolCallCount`：最近工具调用数量。
- `allowedToolCount`：允许工具数量。

### 预算摘要 `budgetSummary`
- `totalTokens`：总预算令牌数。
- `reservedTokens`：预留令牌数。
- `sectionTokens`：分段预算明细。

### 裁剪摘要 `pruneSummary`
- `removedCount`：被移除条目数量。
- `removedItemTypes`：被移除条目类型统计。
- `summary`：裁剪摘要说明。

### 关联状态与指标
- `budgetState`：预算状态，包含分配、使用与剩余信息。
- `toolState`：工具状态，包含可用工具、已选工具与最近调用信息。
- `buildMetrics`：构建指标，包含耗时与检索统计。
- `auditMetadata`：审计元数据，包含版本、来源与时间。

### 载荷约束
- 载荷默认只输出计数、标识与摘要，不直接输出完整业务内容。
- 对于可能包含敏感内容的字段，优先输出长度与数量统计。

## 阶段二范围确认

### 记忆过期
范围覆盖：
- 记忆数据增加过期字段与过期策略，包含持久化与查询过滤。
- 记忆召回阶段过滤过期数据，避免过期记忆进入上下文。
- 记忆清理支持定时清理或按查询时清理。

涉及组件：
- `MemoryRecord`：新增过期时间字段。
- `MemoryStore`、`MemoryRepository`：存储与查询支持过期筛选。
- `MemoryRecallService`：召回结果过滤过期记录。
- `MemoryWriteService`：写入时计算过期时间。
- `MemoryPolicyProperties`、`MemoryWriteProperties`：补充过期策略配置。

不包含：
- 跨租户的记忆共享与迁移。
- 外部数据仓库级别的统一过期治理。

### 记忆脱敏
范围覆盖：
- 写入前脱敏，避免敏感信息进入记忆层。
- 召回后脱敏，避免敏感信息进入上下文。
- 脱敏规则支持可配置与可扩展。

涉及组件：
- 新增脱敏执行器，例如 `SensitiveMasker` 或同等组件。
- `MemoryWriteService`、`MemoryRecallService`：接入脱敏能力。
- `ContextBuilder`：将脱敏标记写入上下文或摘要。
- 配置项：脱敏开关、规则集合与忽略列表。

不包含：
- 复杂的敏感信息识别模型接入。
- 多语种或跨区域法规适配。

### 结构化摘要
范围覆盖：
- 生成结构化摘要产物并写入上下文或记忆层。
- 支持会话摘要、工作记忆摘要与证据包的结构化输出。
- 摘要产物支持压缩触发与版本化记录。

涉及组件：
- `CompressionRequest`、`MemoryStore#compress`：输出结构化摘要。
- `ContextBuilder`：消费结构化摘要并填充 `WorkingMemory` 与 `EvidencePack`。
- 新增摘要数据结构，例如 `ConversationSummary`、`WorkingMemorySummary`。
- 事件载荷中输出摘要统计信息。

不包含：
- 端到端摘要评测体系与评分自动化。
- 多模型对比与摘要质量自动迭代。

## 阶段二优先落地顺序建议
1. 记忆过期：先保障数据正确性与召回稳定性。
2. 记忆脱敏：建立可配置规则与双阶段脱敏链路。
3. 结构化摘要：在稳定的记忆输入基础上输出结构化产物。
