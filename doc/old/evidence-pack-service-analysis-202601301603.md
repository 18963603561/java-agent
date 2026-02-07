# EvidencePackService 作用与场景说明

## 1. 核心职责
`EvidencePackService` 的主要作用是：**聚合与维护“证据包（EvidencePack）”，用于追踪任务执行过程中用到的工具调用、记忆召回与外部引用**，并生成可审计的证据统计信息。

它解决的问题：
- **可追溯性**：记录“模型结论来自哪些工具/记忆/引用”。
- **审计与回放**：为流程回放或合规检查提供证据线索。
- **轻量统计**：生成证据规模统计，支持监控与预算裁剪。


## 2. 主要功能点（基于 EvidencePackService.java）
### 2.1 创建或获取证据包
- `getOrCreatePack(context, tenantId, workflowId, snapshotId)`
- 若 `context` 中已存在 `evidencePack`，则复用；否则创建新包并写回 `context`。
- 自动补齐 `tenantId`、`workflowId`、`snapshotId`。

### 2.2 记录工具调用证据
- `addToolCall(pack, evidence, tenantId, workflowId)`
- 添加 `ToolCallEvidence`（包含 toolName、参数摘要、结果摘要、耗时、状态等）。

### 2.3 记录记忆引用证据
- `addMemoriesUsed(pack, memories, tenantId, workflowId)`
- 记录 `MemoryEvidence`（记忆 ID、召回分数、过期时间、摘要版本）。

### 2.4 记录外部引用证据
- `addCitations(pack, citations, tenantId, workflowId, stage)`
- `addResearchCitations(pack, researchCitations, tenantId, workflowId, stage)`
- 统一转为 `Citation` 并记录统计。

### 2.5 证据包统计与归档
- `finalizePack(pack, tenantId, workflowId)`
- 计算并刷新 `EvidenceStats`（工具调用数、记忆数、引用数、估算字符规模）。


## 3. 典型使用场景
### 场景 A：工具调用链路追踪
- `ToolExecutor.appendToolEvidence(...)` 会把每次工具调用摘要写入证据包。
- 适用于：需要复盘“模型为什么得出结论”的场景。

### 场景 B：记忆召回证据记录
- `MemoryRecallService.appendMemoryEvidence(...)` 召回记忆时写入 `MemoryEvidence`。
- 适用于：确认回答是否正确引用历史上下文。

### 场景 C：研究引用与外部证据
- `ResearchPipeline` 生成引用后，写入证据包并打标阶段。
- 适用于：需要保留外部来源证据链。

### 场景 D：审计与回放
- `ContextEventPublisher` 会读取 `EvidencePack` 统计并输出事件日志。
- 适用于：审计、回放、监控场景。


## 4. 数据结构与关键字段
### 4.1 EvidencePack
包含：
- `toolCalls`：工具调用证据
- `memoriesUsed`：记忆引用证据
- `citations`：引用证据
- `items`：旧版兼容条目
- `stats`：汇总统计

### 4.2 EvidenceStats
- 记录证据数量与估算规模（approxChars），用于预算裁剪与监控。


## 5. 使用示例（伪流程）
### 示例 1：工具调用记录
```java
EvidencePack pack = evidencePackService.getOrCreatePack(context, tenantId, workflowId, snapshotId);
ToolCallEvidence evidence = new ToolCallEvidence();
evidence.setToolName("web.search");
evidence.setArgsDigest("query=...");
evidence.setResultDigest("found 5 items");
evidence.setStatus("SUCCESS");
evidencePackService.addToolCall(pack, evidence, tenantId, workflowId);
evidencePackService.finalizePack(pack, tenantId, workflowId);
```

### 示例 2：记忆召回记录
```java
MemoryEvidence memoryEvidence = new MemoryEvidence();
memoryEvidence.setMemoryId("m-123");
memoryEvidence.setScore(0.87);
pack = evidencePackService.getOrCreatePack(context, tenantId, workflowId, snapshotId);
evidencePackService.addMemoriesUsed(pack, List.of(memoryEvidence), tenantId, workflowId);
```

### 示例 3：研究引用记录
```java
Citation citation = new Citation();
citation.setSource("example.com");
citation.setSnippet("关键结论...");
evidencePackService.addCitations(pack, List.of(citation), tenantId, workflowId, "research");
```


## 6. 注意事项
- `context` 可能是不可变 Map，写入证据包时需要容错（代码已捕获 `UnsupportedOperationException`）。
- 证据包会被写入 `WorkingMemory`（`ToolExecutor.syncSnapshotEvidence`），影响上下文大小。
- 建议只记录摘要字段，不要直接写入大段原文，避免 token 膨胀。


## 7. 相关代码位置
- `src/main/java/com/example/agent/context/EvidencePackService.java`
- `src/main/java/com/example/agent/context/EvidencePack.java`
- `src/main/java/com/example/agent/context/ToolCallEvidence.java`
- `src/main/java/com/example/agent/context/MemoryEvidence.java`
- `src/main/java/com/example/agent/context/Citation.java`
- `src/main/java/com/example/agent/agentcore/ToolExecutor.java`
- `src/main/java/com/example/agent/memory/MemoryRecallService.java`
- `src/main/java/com/example/agent/streaming/ContextEventPublisher.java`