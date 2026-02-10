# memory 包 P1 具体改造任务清单

- 来源文档：`doc/memory-package-review-202602082040.md`
- 目标：完成 P1 级风险治理，优先降低运行时异常风险与实现语义不一致风险
- 交付方式：小步提交、每项可独立验收

---

## 1. 任务总览

| 任务编号 | 任务名称 | 优先级 | 预估工作量 | 主要风险 | 依赖 |
| --- | --- | --- | --- | --- | --- |
| P1-01 | `MemoryStore` 公开方法参数防御加固 | P1 | 0.5 人天 | 低 | 无 |
| P1-02 | `InMemoryMemoryRepository` 索引去重与语义对齐 | P1 | 1 人天 | 中 | P1-01 |
| P1-03 | `JdbcMemoryRepository` 乱码日志修复与清理日志标准化 | P1 | 0.25 人天 | 低 | 无 |
| P1-04 | 记忆层类型与检索优先级常量化（最小改造版） | P1 | 1 人天 | 中 | P1-01 |

---

## 2. 详细任务清单

### P1-01：`MemoryStore` 公开方法参数防御加固

#### 改造目标

- 消除 `MemoryStore` 入口处潜在 `NPE` 风险。
- 对非法输入快速失败并记录可观测日志。

#### 涉及文件

- `src/main/java/com/example/agent/capabilities/memory/MemoryStore.java`

#### 具体改造点

1. `save(MemoryRecord record, TenantContext tenantContext)`：
   - 增加 `record == null`、`tenantContext == null`、`tenantId` 为空校验。
   - 校验失败时 `warn` 日志并返回 `null`（保持向后兼容，不抛异常）。
2. `search(MemoryQuery query, TenantContext tenantContext, List<String> retrievalPriority)`：
   - 在读取 `query.getLimit()` 前先判空。
   - 对 `tenantContext`、`tenantId` 增加校验，失败时返回空结果 `new MemorySearchResult(List.of())`。
3. `compress(CompressionRequest request, TenantContext tenantContext)`：
   - 增加 `request`、`sessionId`、`tenantContext` 校验。
   - 校验失败时 `warn` 日志并返回 `null`。

#### 验收标准

- 新增单测覆盖：
  - `save` 在 `record=null` 或 `tenantContext=null` 时不抛异常。
  - `search` 在 `query=null` 时返回空结果。
  - `compress` 在 `request=null` 或 `sessionId` 为空时不抛异常。
- 原有 `memory` 相关测试全部通过。

---

### P1-02：`InMemoryMemoryRepository` 索引去重与语义对齐

#### 改造目标

- 修复内存仓储 `save` 重复写入导致 `sessionIndex` 重复项的问题。
- 与 `JdbcMemoryRepository` 的 upsert 语义保持一致。

#### 涉及文件

- `src/main/java/com/example/agent/capabilities/memory/InMemoryMemoryRepository.java`
- `src/test/java/com/example/agent/memory/MemoryStoreTest.java`（或新增专门仓储测试）

#### 具体改造点

1. 在 `save` 前获取旧值：`MemoryRecord previous = records.put(memoryId, record)`。
2. 若 `previous` 不为空：
   - 从旧会话索引中删除旧记录（按 `memoryId`）。
   - 若会话键发生变化（租户/会话变化），确保旧索引清理。
3. 向新会话索引插入时做同 `memoryId` 去重。
4. 可选优化：封装 `removeFromSessionIndex(memoryId, tenantId, sessionId)` 私有方法，降低重复代码。

#### 验收标准

- 新增单测：同一 `memoryId` 连续 `save` 两次，`findBySession` 仅返回 1 条。
- 新增单测：同一 `memoryId` 更新到不同 `sessionId`，旧 session 不再可见，新 session 可见。
- 现有测试通过，行为与 JDBC upsert 语义一致。

---

### P1-03：`JdbcMemoryRepository` 乱码日志修复与清理日志标准化

#### 改造目标

- 修复中文乱码日志，满足“中文不可乱码”约定。
- 统一清理失败日志表达，提升排障可读性。

#### 涉及文件

- `src/main/java/com/example/agent/capabilities/memory/JdbcMemoryRepository.java`

#### 具体改造点

1. 将 `deleteExpired` 异常分支中的乱码文案替换为规范中文。
2. 日志建议补充 `now` 或 `cutoff` 信息（如不影响现有日志格式规范）。

#### 验收标准

- 代码中不再出现乱码文本（可用关键字扫描验证）。
- `deleteExpired` 异常日志包含 `tenantId` 与异常堆栈。

---

### P1-04：记忆层类型与检索优先级常量化（最小改造版）

#### 改造目标

- 降低魔法字符串扩散造成的拼写风险。
- 在不大规模重构前提下，先完成最小集中治理。

#### 涉及文件

- 新增：`src/main/java/com/example/agent/capabilities/memory/MemoryLayer.java`
- 新增：`src/main/java/com/example/agent/capabilities/memory/RetrievalPriority.java`
- 修改：
  - `MemoryStore.java`
  - `MemoryRecallService.java`
  - `RecentMemoryStore.java`
  - `CompressedMemoryStore.java`
  - `SemanticMemoryStore.java`
  - `MemoryPolicy.java`

#### 具体改造点

1. `MemoryLayer`：集中定义 `RECENT`、`COMPRESSED` 常量与匹配方法（如 `isCompressed(String)`）。
2. `RetrievalPriority`：集中定义 `RECENT`、`SEMANTIC`、`SUMMARY` 与规范化方法。
3. 将上述类中的字符串比较替换为常量/工具方法调用。
4. 保持外部上下文入参兼容（仍接受字符串，但内部先归一化）。

#### 验收标准

- `memory` 包内 `"recent"` / `"compressed"` / `"RECENT"|"SEMANTIC"|"SUMMARY"` 的直接散落引用显著减少。
- 原有 `MemoryRecallServicePolicyTest` 等优先级顺序测试继续通过。

---

## 3. 执行顺序建议

1. **先做 P1-01**：先把运行时 NPE 风险兜住。
2. **再做 P1-03**：低成本快速收敛可观测性问题。
3. **再做 P1-02**：处理语义一致性核心问题。
4. **最后做 P1-04**：做常量化收口，减少冲突和回归概率。

---

## 4. 回归验证清单

建议最少执行：

```bash
mvn -q "-Dtest=MemoryStoreTest,MemoryRecallServiceTest,MemoryRecallServicePolicyTest,MemoryWriteServiceTest" test
```

如果时间允许，再补充：

```bash
mvn -q test
```

---

## 5. 交付定义（DoD）

1. 四项 P1 任务全部代码合入。
2. 新增/修改单测通过，且无已知回归。
3. 日志文本无乱码，关键异常路径具备上下文。
4. 任务清单中的验收项全部打勾。

