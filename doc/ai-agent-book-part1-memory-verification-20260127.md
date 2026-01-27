# Part1 记忆能力复核报告（补充确认）-20260127

## 背景与范围
- 复核对象：`doc/ai-agent-book-part1-coverage-20260127-v2.md` 中“记忆能力”的两条说明。
- 目标：确认“记忆保存/检索/压缩/向量索引接入”以及“运行时自动写入与调用记忆”的实现证据。
- 检索范围：`src/main/java` 与 `src/test/java`。

## 结论摘要
- 记忆保存、检索、压缩与向量索引接入已有明确实现证据，且提供了自动压缩触发与可配置的召回/写入开关。
- 运行时存在自动召回与自动写入链路：在任务执行前召回，任务结束后写入。
- “短期与长期记忆调度未体现”的结论需要细化：存在 recent/semantic/compressed 分层与压缩策略，但缺少独立的长期记忆调度器或显式优先级策略；目前是通过合并检索与压缩阈值策略实现的“弱调度”。

## 证据清单与链路复核

### 1) 记忆保存、检索、压缩与向量索引接入
- 保存与索引接入：`src/main/java/com/example/agent/memory/MemoryStore.java` 的 `save`。
  - 保存到 recent：`RecentMemoryStore.save`。
  - 向量接入：`VectorStore` + `EmbeddingService`，可用时调用 `vectorStore.upsert`。
  - 自动压缩触发：`autoCompressIfNeeded`。
- 检索：`src/main/java/com/example/agent/memory/MemoryStore.java` 的 `search`。
  - semantic：`SemanticMemoryStore.search`。
  - recent：`RecentMemoryStore.search`。
  - compressed：`CompressedMemoryStore.search`。
  - 合并策略：`mergeRecords` 按上限合并。
- 压缩：`src/main/java/com/example/agent/memory/CompressedMemoryStore.java` 与 `MemoryStore.compress`。
  - 压缩触发策略：`src/main/java/com/example/agent/memory/MemoryPolicy.java` 的 `shouldCompress`。
- 向量存储实现：`src/main/java/com/example/agent/memory/QdrantVectorStore.java`，通过 `agent.memory.vector.enabled` 控制启用。
- 相关配置：
  - 写入：`src/main/java/com/example/agent/memory/MemoryWriteProperties.java`。
  - 召回：`src/main/java/com/example/agent/memory/MemoryRecallProperties.java`。
  - 压缩：`src/main/java/com/example/agent/memory/MemoryPolicyProperties.java`。
  - 向量：`src/main/java/com/example/agent/memory/MemoryVectorProperties.java`。
- 测试覆盖：
  - `src/test/java/com/example/agent/memory/MemoryStoreTest.java`。
  - `src/test/java/com/example/agent/memory/MemoryWriteServiceTest.java`。
  - `src/test/java/com/example/agent/memory/MemoryRecallServiceTest.java`。

### 2) 运行时自动召回与自动写入链路
- 运行时自动召回：`src/main/java/com/example/agent/runtime/AgentRuntime.java` 的 `run`。
  - 调用 `memoryRecallService.recall`。
  - `applyMemoryContext` 将召回结果写入 `runtimeContext`，并进入 `buildRequestWithContext`。
- 运行时自动写入：`src/main/java/com/example/agent/runtime/AgentRuntime.java` 的 `persistMemorySafely`。
  - 在任务结束与空计划路径均调用 `memoryWriteService.saveTaskMemory`。
  - 失败不阻断主流程，记录 error 日志。

### 3) 自动写入与召回的触发条件与“调度”边界
- 自动召回开关与阈值控制：`src/main/java/com/example/agent/memory/MemoryRecallService.java`。
  - `memoryRecallEnabled`、`memoryRecallMinQueryLength`、`memoryRecallLimit` 等上下文覆盖字段。
  - 需要 `sessionId` 与非空 `query`。
- 自动写入开关与内容选择：`src/main/java/com/example/agent/memory/MemoryWriteService.java`。
  - `memoryWriteEnabled` 可在上下文覆盖。
  - 只写入“用户输入”和“最终输出”（可配置开关）。
- 分层与压缩策略：`src/main/java/com/example/agent/memory/MemoryPolicy.java`。
  - 依据记录数量、token 估算、最老记录年龄触发压缩。
  - 以 compressed 作为“长记忆浓缩层”，但未见独立的“长期记忆调度器”。

## 对原说明的修订建议
- “具备记忆保存、检索、压缩与向量索引接入”结论成立，证据充足。
- “运行时未见自动写入与调用记忆的链路，短期与长期记忆调度未体现”需修订为：
  - 运行时存在自动召回与自动写入链路（AgentRuntime.run -> MemoryRecallService.recall；AgentRuntime.persistMemorySafely -> MemoryWriteService.saveTaskMemory）。
  - 记忆分层与压缩策略存在，但缺少独立的“短期/长期调度器”或显式优先级策略；当前为“分层检索 + 压缩阈值”的弱调度。

## 需要进一步确认的点（非阻断）
- 向量存储接入依赖配置 `agent.memory.vector.enabled` 与外部向量库可用性，未在主流程中强制启用。
- 压缩策略的触发效果依赖 `MemoryPolicyProperties` 的阈值配置与实际运行数据。
