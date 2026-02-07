# 缺陷修复记录
- 日期：2026-01-27

## G-007 鉴权与租户隔离缺失
- 现象：鉴权模块缺少 `JWT` 支持与租户范围校验，且 `AuthService` 无实现导致鉴权链路不完整。
- 复现步骤：
  1. 启动应用后调用任一受保护接口，未加载 `AuthService` 实现会导致启动失败或鉴权流程异常。
  2. 使用 `Authorization: Bearer <token>` 访问接口时无法解析或校验 `JWT`。
- 期望/实际：
  - 期望：支持 `API Key` 与 `JWT` 两种鉴权路径，具备租户范围校验并输出一致错误码。
  - 实际：无 `JWT` 鉴权实现，租户范围无法限制，部分鉴权链路缺失。
- 根因：`AuthService` 实现缺失，缺少 `JWT` 解析与签名校验逻辑。
- 修复：
  - 新增 `ApiKeyAuthenticator`，支持 `API Key`、`JWT` 与可信上游鉴权路径。
  - 增加租户范围校验，租户不匹配返回 `FORBIDDEN`。
  - 失败日志统一记录 `AUTH_FAILED` 与请求上下文字段。
- 影响范围：`gateway`、`streaming` 与所有依赖 `AuthService` 的控制器。
- 关联 Shannon 参考：`vendor/Shannon/docs/authentication-and-multitenancy.md`
- 关联规范与验收：
  - `specs/001-agent-core-spec/checklists/checklist.md`：`CHK046`、`CHK047`
- 回归测试：
  - `src/test/java/com/example/agent/gateway/controller/SecurityValidationTest.java#jwtAuthUsesClaims`
  - `src/test/java/com/example/agent/gateway/controller/SecurityValidationTest.java#jwtTenantScopeMismatchReturnsForbidden`

## T-001 记忆模块缺少嵌入服务导致测试上下文失败
- 现象：启动测试上下文时提示缺少 `EmbeddingService`，导致 `OpenApiContractDriftTest` 等测试无法运行。
- 复现步骤：运行 `mvn test`，观察应用上下文加载失败。
- 期望/实际：
  - 期望：无外部嵌入服务时应使用兜底逻辑继续启动。
  - 实际：`MemoryStore` 强依赖 `EmbeddingService` 导致上下文失败。
- 根因：`MemoryStore` 直接注入 `EmbeddingService`，缺少可选依赖处理。
- 修复：`MemoryStore` 改为注入 `ObjectProvider<EmbeddingService>` 并在缺失时记录告警与走文本检索兜底。
- 影响范围：`memory` 模块与测试上下文加载。
- 关联规范与验收：无
- 回归测试：`mvn test`

## T-002 SSE 超时关闭导致订阅阻塞
- 现象：`agent.sse.timeoutSeconds=0` 时订阅接口无法及时返回或首条事件为空。
- 复现步骤：运行 `McpToolEventTest` 或 `QuickstartFlowTest`。
- 期望/实际：
  - 期望：关闭超时后 SSE 应立即建立连接并继续等待事件。
  - 实际：连接阻塞或首条事件为空导致断言失败。
- 根因：无首条事件时响应未提交，测试阻塞等待。
- 修复：
  - 关闭超时时注入一次注释心跳以提交响应。
  - 测试中过滤无数据事件后再断言。
- 影响范围：`streaming` 模块与 SSE 集成测试。
- 关联规范与验收：`specs/001-agent-core-spec/quickstart.md`
- 回归测试：
  - `src/test/java/com/example/agent/gateway/controller/McpToolEventTest.java#mcpToolCallEmitsToolEvents`
  - `src/test/java/com/example/agent/gateway/controller/QuickstartFlowTest.java#quickstartFlowCoversCoreEndpoints`

## VF-P1-001 SSE 断线续传缺少 replay since
- 现象：带 `last_event_id` 订阅仅做游标校验，不回放历史事件，导致断线后丢失事件。
- 复现：调用 `/api/v1/stream/sse?workflow_id=...&last_event_id=...`，断线后再次订阅只收到新事件。
- 根因：`EventStreamService#stream` 未从 Redis 回放 `last_event_id` 之后的事件。
- 修复：
  - 在 `EventStreamService#stream` 增加历史回放逻辑：从 Redis 读取并筛选 `seq > lastSeq` 的事件后再拼接实时流。
  - 保留游标校验与类型过滤语义。
- 影响面：`streaming api`、SSE 客户端断线续传体验。
- 回归测试：`src/test/java/com/example/agent/streaming/EventStreamServiceTest.java#resumeReplaysEventsAfterCursor`
- 关联 CHK/接口/错误码：
  - 接口：`/api/v1/stream/sse`
  - 错误码：`STREAM_GAP`、`INVALID_CURSOR`
- Shannon 对齐证据：`vendor/Shannon/go/orchestrator/internal/streaming/manager.go#ReplaySince`

## VF-P2-001 记忆分层与自动压缩缺失
- 现象：记忆仅提供单层存取，缺少 recent/semantic/compressed 分层与自动压缩触发策略。
- 复现：调用 `/api/v1/memory/save` 多次写入后，检索只返回单层记录，未触发自动压缩。
- 根因：缺少分层存取与策略组件，压缩仅能手动调用。
- 修复：
  - 新增 `RecentMemoryStore`、`SemanticMemoryStore`、`CompressedMemoryStore` 与 `MemoryPolicy`。
  - 引入 size/token/time 触发策略，保存与检索时自动触发压缩。
  - 检索按层级聚合：语义 -> recent -> compressed。
- 影响面：`memory system` 分层存取与压缩策略。
- 回归测试：
  - `src/test/java/com/example/agent/memory/MemoryStoreTest.java#autoCompressTriggeredBySizeThreshold`
  - `src/test/java/com/example/agent/memory/MemoryStoreTest.java#autoCompressTriggeredByTimePolicy`
  - `src/test/java/com/example/agent/memory/MemoryStoreTest.java#searchAggregatesRecentAndCompressed`
- 关联 CHK/接口/错误码：
  - 接口：`/api/v1/memory/save`、`/api/v1/memory/search`、`/api/v1/memory/compress`
- Shannon 对齐证据：`vendor/Shannon/docs/memory-system-architecture.md`

## VF-P2-002 Tracing 接入不足导致 traceId 不贯通
- 现象：traceId 未统一注入关键日志、事件载荷与指标标签，导致观测链路不完整。
- 复现：带 `X-Trace-Id` 请求调用任务与工具相关接口，事件 payload 与指标标签缺少 traceId。
- 根因：缺少 TracingPublisher 注入与 traceId 透传逻辑。
- 修复：
  - 在 Gateway/Orchestrator/Runtime/ToolExecutor 注入 `TracingPublisher`。
  - 事件 payload 补齐 `traceId/requestId`，指标计数与耗时增加 `traceId` 标签。
  - 关键日志补齐 `traceId` 字段。
- 影响面：`gateway`、`orchestrator`、`runtime`、`tools`、`observability`。
- 回归测试：
  - `src/test/java/com/example/agent/orchestrator/TaskOrchestratorTest.java#concurrentIdempotencyUsesSingleTask`
  - `src/test/java/com/example/agent/agentcore/ToolExecutorTest.java#retriesOnRetryableError`
- 关联 CHK/接口/错误码：
  - 接口：`/api/v1/tasks`、`/api/v1/stream/sse`、`/api/v1/mcp/tools/call`
  - 错误码：`TENANT_MISSING`、`UNAUTHORIZED`
- Shannon 对齐证据：`vendor/Shannon/docs/agent-core-architecture.md`
