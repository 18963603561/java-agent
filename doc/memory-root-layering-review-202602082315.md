# memory 根包分层完整性复核报告

- 复核路径：`src/main/java/com/example/agent/capabilities/memory`
- 复核时间：2026-02-08 23:15
- 复核目标：确认 `memory` 根包分层是否已经彻底完成

---

## 1. 结论

**结论：根包分层“明显改善但仍不彻底”，当前状态属于“部分完成”。**

### 结论等级

- 分层完成度：**B（中上）**
- 是否“彻底分层”：**否**

### 一句话判断

`config/repository/vector/write/recall/store/support` 子包已经建立，但 `memory` 根包仍保留较多不同职责类型的类，尚未收敛为“仅门面与少量对外入口”。

---

## 2. 结构快照（客观数据）

### 2.1 文件规模

- `memory` 下 Java 总数：`50`
- 根包 Java 数：`18`
- 根包占比：`36%`

### 2.2 子包分布

- `config`：5
- `repository`：3
- `vector`：4
- `write`：7
- `recall`：8
- `store`：3
- `support`：2

### 2.3 根包现有类（18 个）

- 服务/编排相关：
  - `MemoryStore`
  - `MemoryRecallService`
  - `MemoryWriteService`
  - `MemoryPolicy`
  - `MemoryExpirationService`
  - `RecentMemoryStore`
  - `SemanticMemoryStore`
  - `CompressedMemoryStore`
  - `TokenEstimator`
- 模型/DTO/枚举相关：
  - `MemoryRecord`
  - `MemoryQuery`
  - `MemorySearchResult`
  - `MemoryRecallResult`
  - `CompressionRequest`
  - `ConversationSummary`
  - `WorkingMemorySummary`
  - `MemoryLayer`
  - `RetrievalPriority`

---

## 3. 为什么判断“还不彻底”

## 3.1 根包职责仍混合

根包同时包含：

1. 业务服务（`MemoryWriteService`、`MemoryRecallService`）
2. 存取层实现（`RecentMemoryStore`、`SemanticMemoryStore`、`CompressedMemoryStore`）
3. 策略与规则（`MemoryPolicy`、`MemoryExpirationService`、`TokenEstimator`）
4. 数据模型（`MemoryRecord`、`MemoryQuery` 等）

这说明“按职责分层”尚未完全落实到物理目录层。

## 3.2 根包尚未收敛为“薄入口”

如果目标是彻底分层，通常根包应主要保留：

- 门面/对外服务入口（例如 `MemoryStore`）
- 少量必要对外契约

当前根包仍保留多类实现细节，意味着后续维护时仍会在根包发生较多横向改动。

---

## 4. 影响评估

### 已达成的正向效果

1. 关键基础层已抽离：配置、仓储、向量、写入子能力均已独立。
2. 架构可读性相比早期版本已显著提升。

### 仍存在的工程性代价

1. 根包导航成本偏高（职责混杂）。
2. 新增能力时，容易继续“往根包堆类”。
3. 分层边界的团队认知不够强约束，长周期可能再次回流。

---

## 5. 建议（如继续推进彻底分层）

可按以下顺序做下一轮收口：

1. 新建 `model` 子包，迁移 `MemoryRecord/MemoryQuery/MemorySearchResult/MemoryRecallResult` 等纯模型类。
2. 将 `RecentMemoryStore/SemanticMemoryStore/CompressedMemoryStore` 归并到 `store`（或单独 `access`）子包，根包不再放存取实现。
3. 将 `MemoryPolicy/MemoryExpirationService/TokenEstimator` 归并到 `policy`（或 `lifecycle`）子包。
4. 根包最终仅保留 `MemoryStore` 及极少数外部入口类。

---

## 6. 最终确认

针对你的问题“`memory` 根包是不是分层还不彻底”：

**是，当前仍不彻底；属于已完成关键分层、但未完成最终收口的状态。**

