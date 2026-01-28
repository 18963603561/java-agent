# 上下文工程实现复核结果（202601290947）

## 复核范围
- 文档：`doc/context-engineering-evidence-review-20260128.md`、`doc/context-engineering-interface-draft-20260128.md`、`doc/context-engineering-priority-plan-20260128.md`、`doc/context-engineering-stage1-acceptance-20260128.md`、`doc/context-engineering-stage1-implementation-list-20260128.md`、`doc/context-engineering-stage1-tasks-20260128.md`、`doc/context-engineering-stage2-plan-20260128.md`、`doc/stage3-batch-plan-202601281830.md`、`doc/stage3-context-engineering-plan.md`、`doc/tasks-stage3.md`
- 代码：`src/main/java/com/example/agent/context`、`src/main/java/com/example/agent/model`、`src/main/java/com/example/agent/tools`、`src/main/java/com/example/agent/budget`、`src/main/java/com/example/agent/streaming`、`src/main/java/com/example/agent/memory`、`src/main/java/com/example/agent/runtime`

## 已实现要点（与文档对照）
- 分层上下文模型与快照：`ContextSnapshot`、`RuntimeMeta`、`RoleBoundary`、`TaskIntent`、`WorkingMemory`、`DomainKnowledge`、`LongTermMemory` 由 `DefaultContextBuilder` 生成并注入运行上下文。
- 三段式提示词：`DefaultPromptTemplate` 输出 `SYSTEM` 和 `DEVELOPER`，`DefaultPromptAssembler` 追加 `USER`，`ModelRequest.messages` 已被模型调用链路使用。
- 工具摘要与按需加载：`DefaultToolCatalog` 提供摘要，`ToolCatalogService#getToolSchema` 与 `ModelToolResolver` 支持按需加载并缓存。
- 预算分配、裁剪与压缩：`DefaultContextBudgetAllocator`、`DefaultContextPruner`、`DefaultContextTrimmer`、`ContextCompressionController` 已串联到 `DefaultContextBuilder`。
- 结构化摘要与记忆策略：`CompressedMemoryStore` 生成 `ConversationSummary`/`WorkingMemorySummary`，`MemoryStore` 负责过期过滤与清理；`MemoryWriteService` 与 `MemoryRecallService` 已接入脱敏。
- 事件流与上下文事件：`ContextEventPublisher` 发布 `CONTEXT_SNAPSHOT_CREATED`/`CONTEXT_PRUNED`，包含预算与证据包统计。

## 不足点与优先级
### P0
- 暂未发现阻断主流程的 P0 问题。

### P1（已完成）
- P1-1 装配器与提示词裁剪闭环
  - 状态：已完成
  - commit：未提交（工作区）
  - 关键类：`ContextAssembler`、`DefaultContextAssembler`、`PromptAssemblyInput`、`DefaultPromptAssembler`、`PlannerService`
- P1-2 快照更新与事件审计
  - 状态：已完成
  - commit：未提交（工作区）
  - 关键类：`ContextSnapshotStage`、`ContextSnapshotEventPayload`、`ContextTrimSummary`、`ContextCompressionSummary`、`ContextEventPublisher`、`DefaultContextBuilder`、`PlannerService`、`ToolExecutor`、`ContextCompressionResult`、`EventType`
- P1-3 证据包引用链路（研究引用）
  - 状态：已完成
  - commit：未提交（工作区）
  - 关键类：`EvidencePackService`、`AgentRuntime`、`EvidencePackCitationWritePathTest`

### P2（仍存在的缺口）
- 上下文策略字段未落地到检索与裁剪链路。
  - 证据：`ContextPolicy` 的 `retrievalPriority`/`pruneOrder`/`enableSensitiveMask` 未在检索与裁剪逻辑使用；`ContextBuildRequest` 未注入策略。
  - 影响：策略化装配与裁剪无法生效，配置价值有限。
  - 位置：`src/main/java/com/example/agent/context/ContextPolicy.java`、`src/main/java/com/example/agent/context/ContextBuildRequest.java`、`src/main/java/com/example/agent/budget/DefaultContextPruner.java`。
- 工具摘要字段与筛选条件不完整。
  - 证据：`ToolSummary` 支持成本、时延、授权字段，但 `DefaultToolCatalog` 未赋值；`ToolQuery.allowedScopes`/`locale` 未参与过滤。
  - 影响：工具摘要信息不足，难以支持精细化选型与权限控制。
  - 位置：`src/main/java/com/example/agent/tools/DefaultToolCatalog.java`、`src/main/java/com/example/agent/tools/ToolSummary.java`、`src/main/java/com/example/agent/tools/ToolQuery.java`。
- 记忆可追溯字段与写入门槛仍不完善。
  - 证据：`MemoryRecord` 无来源与置信度字段；`MemoryEvidence.score` 未赋值；`MemoryWriteService` 未实现低价值过滤。
  - 影响：记忆质量与来源难审计，低价值写入可能造成膨胀。
  - 位置：`src/main/java/com/example/agent/memory/MemoryRecord.java`、`src/main/java/com/example/agent/context/MemoryEvidence.java`、`src/main/java/com/example/agent/memory/MemoryWriteService.java`。
- 工具与工作记忆状态字段未被填充。
  - 证据：`WorkingMemory.recentToolCalls`、`ToolState.lastCall`/`retryCount` 等字段未在运行链路中写入。
  - 影响：工作记忆与工具状态难以用于审计与调试。
  - 位置：`src/main/java/com/example/agent/context/WorkingMemory.java`、`src/main/java/com/example/agent/context/ToolState.java`、`src/main/java/com/example/agent/agentcore/ToolExecutor.java`。
- 指标命名与计划存在不一致。
  - 证据：工具注入体量指标未输出 `tools_injected_size`，仅记录 `model_tool_injected_summaries_total`；证据包指标名称与计划不一致。
  - 影响：按计划验收与指标观测存在偏差。
  - 位置：`src/main/java/com/example/agent/model/ModelToolResolver.java`、`src/main/java/com/example/agent/context/EvidencePackService.java`。

## 配置开关清单 + 默认值 + 回滚点
- `agent.prompt.trim.enabled`：默认 `true`，提示词裁剪开关；回滚点为设置 `false`（恢复旧提示词拼装行为）
- `agent.context.budget.enabled`：默认 `true`，上下文预算分配总开关；回滚点为设置 `false`（跳过预算分配/裁剪/压缩）
- `agent.context.default-token-budget`：默认 `4096`，兜底预算值；回滚点为调整为旧值或按环境覆盖
- `agent.context.max-working-summary-chars`：默认 `512`，工作记忆摘要截断长度；回滚点为调整为旧值或按环境覆盖
- `agent.context.budget.total-budget-tokens`：默认 `8192`，预算总量；回滚点为调整为旧值或按环境覆盖
- `agent.context.compression.enabled`：默认 `true`，预算触发压缩总开关；回滚点为设置 `false`
- `agent.context.compression.trigger-over-total-budget`：默认 `true`，总预算超限触发压缩；回滚点为设置 `false`
- `agent.context.compression.trigger-over-section-budget`：默认 `true`，分区预算超限触发压缩；回滚点为设置 `false`
- `agent.context.compression.min-interval-seconds`：默认 `30`，压缩冷却时间；回滚点为调整为旧值或按环境覆盖
