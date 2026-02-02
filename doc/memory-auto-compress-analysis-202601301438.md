# autoCompressIfNeeded 调用分析报告

## 目标与范围
- 目标：说明 `MemoryStore.autoCompressIfNeeded(sessionId, tenantContext)` 的具体行为、流程、触发原因、落库位置与重复风险。
- 范围：仅基于当前代码实现与数据库初始化脚本的静态分析，不包含运行时行为推断。

## 触发位置
- 保存记忆后触发：`MemoryStore.save(...)` 调用 `autoCompressIfNeeded(record.getSessionId(), tenantContext)`。
- 检索记忆后触发：`MemoryStore.search(...)` 调用 `autoCompressIfNeeded(query != null ? query.getSessionId() : null, tenantContext)`。

## 自动压缩流程
1. **参数校验**：`tenantContext` 为空或 `sessionId` 为空/空白则直接返回，不执行压缩。
2. **拉取会话记忆**：调用 `MemoryRepository.findBySession(tenantId, sessionId)` 获取当前会话全部未过期记忆记录。
   - PostgreSQL 实现会在 SQL 层过滤 `expires_at`（只取未过期记录）。
   - 内存实现会在内存过滤未过期记录。
3. **策略判断**：调用 `MemoryPolicy.shouldCompress(records, now)`。
   - 过滤掉 `layer = "compressed"` 的记录，仅对“非压缩”记录进行判断。
   - 若最近已生成过压缩（小于 `minCompressIntervalSeconds`）则不再触发。
   - 满足以下任一条件则触发：
     - 非压缩记录数量 >= `sizeThreshold`
     - 估算 token 数 >= `tokenThreshold`
     - 最早记录距当前时间 >= `maxAgeSeconds`
4. **生成压缩摘要**：`CompressedMemoryStore.compress(sessionId, records, tenantContext)` 构建结构化摘要与工作记忆摘要。
   - 会排除 `layer = "compressed"` 的记录，避免“压缩包含压缩”。
   - 摘要为空则返回 `null` 并停止。
5. **生成并保存压缩记录**：创建新的 `MemoryRecord`，设置：
   - `memoryId`：新 UUID
   - `sessionId`、`tenantId`
   - `summary`、`conversationSummary`、`workingMemorySummary`
   - `layer = "compressed"`
   - `createdAt` 与 `expiresAt`（若启用过期策略）
6. **持久化**：通过 `MemoryRepository.save(...)` 写入存储，并记录日志。

## 为什么要压缩（代码层面的直接原因）
- **策略驱动**：`MemoryPolicy` 基于数量、token 估算值、会话最老记录年龄等阈值判断是否需要压缩，目的是避免会话记忆过多导致检索与上下文构建成本上升。
- **检索层支持**：检索优先级包含 `SUMMARY` 层，压缩记录用于快速摘要检索。

## 会新增到哪里（表/存储）
- **PostgreSQL 模式**：新增一条 `memory_records` 表记录，`layer = "compressed"`。
- **内存模式**：新增到 `InMemoryMemoryRepository` 的内存 Map 与会话索引中。
- **不涉及的表**：该流程不会写入 `memory_chunks`；也不会在本流程中写入向量存储（`VectorStore` 仅在 `MemoryStore.save(...)` 时触发）。

## 数据会不会重复
- **不会产生相同 `memory_id` 的重复**：压缩记录每次生成新 UUID，`memory_id` 为主键，且保存 SQL 为 `ON CONFLICT (memory_id) DO UPDATE`，因此不会出现相同主键的重复行。
- **可能出现同一会话多条压缩记录**：当达到策略阈值且已超过最小压缩间隔时，会再次生成新的压缩记录。
- **抑制重复生成的机制**：`MemoryPolicy` 会检查是否存在“最近压缩记录”（`minCompressIntervalSeconds`），在该窗口内不再触发。
- **压缩内容不重复叠加**：压缩构建时会排除 `layer = "compressed"` 的记录，避免“摘要叠摘要”。

## 相关代码位置
- `src/main/java/com/example/agent/memory/MemoryStore.java`
- `src/main/java/com/example/agent/memory/MemoryPolicy.java`
- `src/main/java/com/example/agent/memory/CompressedMemoryStore.java`
- `src/main/java/com/example/agent/memory/MemoryRepository.java`
- `src/main/java/com/example/agent/memory/JdbcMemoryRepository.java`
- `src/main/java/com/example/agent/memory/InMemoryMemoryRepository.java`
- `docker/init/001-init.sql`

## 关键默认阈值（便于核对）
- `sizeThreshold = 50`
- `tokenThreshold = 2000`
- `maxAgeSeconds = 3600`
- `minCompressIntervalSeconds = 300`

（以上默认值来源于 `MemoryPolicyProperties`，可被配置覆盖。）