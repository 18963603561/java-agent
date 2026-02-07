# `Redis`、`PostgreSQL` 与向量使用情况分析

## 结论摘要
- 项目明确使用 `Redis`、`PostgreSQL` 与向量检索能力（`VectorStore` + `Qdrant`），三者可同时启用。
- 互斥点主要在持久化仓储层：`agent.storage.mode` 在 `memory` 与 `postgres` 之间二选一。
- `Redis` 与向量能力均为可选增强，关闭或不可用时会回退到内存或跳过语义检索，不影响主流程。

## 启用配置与默认值
- 默认配置：`src/main/resources/application.yml`
  - `agent.storage.mode=memory`，并通过 `spring.autoconfigure.exclude` 禁用数据源自动配置。
  - `agent.idempotency.redis-enabled=false`、`agent.tool.cache.redis-enabled=false`、`agent.memory.vector.enabled=false`。
- 容器配置：`src/main/resources/application-docker.yml`
  - `agent.storage.mode=postgres`，开启 `spring.datasource` 与 `spring.data.redis`。
  - `agent.idempotency.redis-enabled=true`、`agent.tool.cache.redis-enabled=true`、`agent.memory.vector.enabled=true`。
- 依赖编排：`docker/compose.yaml` 同时启动 `postgres`、`redis`、`qdrant`。

## 互斥与组合关系
- 持久化仓储：`agent.storage.mode` 仅选择一种实现。
  - `postgres`：启用 `Jdbc*Repository` 系列持久化仓储。
  - `memory`：启用 `InMemory*Repository` 系列内存仓储。
- `Redis` 功能不依赖 `agent.storage.mode`，可与 `postgres` 或 `memory` 组合。
- 向量能力不依赖 `agent.storage.mode`，可与 `postgres` 或 `memory` 组合；关闭时语义检索直接返回空结果。

## `Redis` 流程
- 事件流与断线续传：`EventStreamService`
  - 监听应用事件 `StreamEvent`，写入 `Redis Stream`，键为 `stream:<workflowId>`。
  - 每次追加后执行 `trim` 与 `expire`，窗口大小来自 `agent.sse.max-stream-size`（最小 64），`TTL` 固定 24 小时。
  - `SSE` 订阅时先校验游标：内存索引缺失时从 `Redis` 重新加载；仍找不到则抛出 `STREAM_GAP`。
  - 断线重连会读取 `Redis` 历史事件，再拼接实时事件流。
- 幂等控制：`TaskOrchestrator`
  - 当 `agent.idempotency.redis-enabled=true` 且 `StringRedisTemplate` 可用时，
    使用 `idempotency:task:<tenantId>:<idempotencyKey>` 缓存任务标识。
  - 不可用时回退到 `TaskRepository` 的幂等键查询。
- 工具缓存：`ToolCache`
  - 当 `agent.tool.cache.redis-enabled=true` 且 `StringRedisTemplate` 可用时，
    使用 `agent.tool.cache.redis-key-prefix` 与 `agent.tool.cache.ttl-seconds` 做结果缓存。
  - 不可用时回退到进程内 `ConcurrentHashMap`。

## `PostgreSQL` 流程
- 仓储实现由 `agent.storage.mode=postgres` 驱动：
  - 任务：`JdbcTaskRepository`，由 `TaskOrchestrator` 写入与查询。
  - 事件日志：`JdbcEventLogRepository`，由 `EventLogService` 监听事件并持久化，`EventType.LLM_PARTIAL` 被过滤。
  - 记忆：`JdbcMemoryRepository`，由 `MemoryStore` 与其下游存取层写入与检索。
  - 步骤记录：`JdbcStepRecordRepository`，由步骤运行服务写入与查询。
  - 预算：`JdbcTokenUsageRepository`，用于记录模型消耗。
  - 调度：`JdbcScheduleRepository` 与 `JdbcScheduleExecutionRepository`，用于计划任务与执行记录。
- `docker/init/001-init.sql` 定义了上述表结构与索引，包含 `memory_records`、`event_logs`、`tasks` 等。

## 向量流程
- 向量接口：`VectorStore`
  - 通过 `MemoryStore.save` 触发：记录写入后生成向量并调用 `VectorStore.upsert`。
  - 通过 `SemanticMemoryStore.search` 触发：查询生成向量后调用 `VectorStore.search`。
- 向量实现：`QdrantVectorStore`
  - 由 `agent.memory.vector.enabled=true` 激活，启动时检查并创建集合。
  - 使用 `agent.memory.vector.base-url`、`collection`、`dimension` 等配置调用 `Qdrant`。
- 嵌入服务：`EmbeddingService`
  - 若无外部实现，则使用 `HashEmbeddingService` 生成轻量向量。
- 记忆召回组合：`MemoryStore.search`
  - 语义检索结果 + `recent` 检索结果 + `compressed` 检索结果合并返回。
  - 向量不可用时，语义检索为空，但 `recent`/`compressed` 检索仍可用。

## 组合场景结论
- `application-docker.yml` + `docker/compose.yaml` 表明目标运行模式是三者同时启用。
- 仅当 `agent.storage.mode=memory` 时 `PostgreSQL` 不参与主流程；
  但 `Redis` 与向量能力仍可按配置独立启用或关闭。
  
