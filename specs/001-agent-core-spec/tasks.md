---

description: "Task list for Java Shannon Agent Orchestrator Core"
---

# Tasks: Java Shannon Agent Orchestrator Core

**Input**: `specs/001-agent-core-spec/spec.md`、`specs/001-agent-core-spec/plan.md`、`specs/001-agent-core-spec/data-model.md`、
`specs/001-agent-core-spec/contracts/openapi.yaml`、`specs/001-agent-core-spec/research.md`
**Prerequisites**: `specs/001-agent-core-spec/plan.md`（必需）、`specs/001-agent-core-spec/spec.md`（必需）

**Tests**: 未要求强制测试任务；每条任务提供可执行的验证方式。

## `Phase 0`：工程骨架与基础设施

**Purpose**: 建立工程骨架、统一响应与可观测性基础、多租户扩展点

- [x] T0-1 目标/交付: 建立 `Spring Boot` 工程骨架与依赖配置；包/类/接口: `com.example.agent.AgentApplication`；
  输入/输出: `application.yml` 配置 -> 服务可启动；日志/指标: 启动 `info` 日志、`/actuator/health` 与
  `/actuator/metrics`；验证: `./mvnw spring-boot:run` 且 `GET /actuator/health` 返回 `UP`；
  路径: `pom.xml`、`src/main/resources/application.yml`、`src/main/java/com/example/agent/AgentApplication.java`；
  依赖: 无；验收标准: 工程可启动且依赖均通过 `Spring Boot Starter` 管理。
- [x] T0-2 [P] 目标/交付: 定义统一响应结构；包/类/接口: `com.example.agent.common.ApiResponse`、
  `com.example.agent.common.ErrorResponse`；输入/输出: 业务数据或错误 -> 标准响应结构；
  日志/指标: 响应封装不新增日志，调用方按规范记录；验证: 编译通过并可被控制器调用；
  路径: `src/main/java/com/example/agent/common/ApiResponse.java`、
  `src/main/java/com/example/agent/common/ErrorResponse.java`；依赖: `T0-1`；
  验收标准: 字段包含 `code`、`message`、`data`、`traceId`、`requestId` 或 `details`。
- [x] T0-3 [P] 目标/交付: 可观测性发布入口；包/类/接口: `com.example.agent.observability.MetricsPublisher`、
  `com.example.agent.observability.TracingPublisher`；输入/输出: 事件与耗时 -> `Micrometer` 指标发布；
  日志/指标: 指标包含 `task.submit.count`、`event.stream.count`、`budget.tokens.used`；
  验证: `/actuator/metrics` 可查询到上述指标名；
  路径: `src/main/java/com/example/agent/observability/MetricsPublisher.java`、
  `src/main/java/com/example/agent/observability/TracingPublisher.java`；
  依赖: `T0-1`；验收标准: 指标发布类可注入且指标可读。
- [x] T0-4 [P] 目标/交付: 多租户上下文模型；包/类/接口: `com.example.agent.auth.TenantContext`、
  `com.example.agent.auth.UserContext`；输入/输出: 请求头信息 -> `TenantContext`、`UserContext`；
  日志/指标: 不新增日志，解析过程由过滤器记录；验证: 编译通过并可被过滤器引用；
  路径: `src/main/java/com/example/agent/auth/TenantContext.java`、
  `src/main/java/com/example/agent/auth/UserContext.java`；依赖: `T0-1`；
  验收标准: 字段包含 `tenantId`、`userId`、`roles`、`requestId`、`traceId`。
- [x] T0-5 目标/交付: 租户解析与上下文过滤；包/类/接口: `com.example.agent.auth.TenantResolver`、
  `com.example.agent.auth.TenantContextFilter`；输入/输出: 请求头 -> `TenantContext` 写入
  `Reactor Context`；日志/指标: 缺失租户记录 `warn` 与 `ERROR_OCCURRED` 事件；
  验证: 访问任一接口时上下文可读取 `tenantId`；路径:
  `src/main/java/com/example/agent/auth/TenantResolver.java`、
  `src/main/java/com/example/agent/auth/TenantContextFilter.java`；依赖: `T0-4`；
  验收标准: 无租户请求返回 `TENANT_MISSING`，`HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》，
  不使用 `NOT_FOUND`。
- [x] T0-6 [P] 目标/交付: 鉴权扩展点定义；包/类/接口: `com.example.agent.auth.AuthService`、
  `com.example.agent.auth.ApiKeyAuthenticator`；输入/输出: `API Key` -> 鉴权结果；
  日志/指标: 接口定义不新增日志，鉴权失败由实现记录；验证: 编译通过且可被注入；
  路径: `src/main/java/com/example/agent/auth/AuthService.java`、
  `src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java`；依赖: `T0-1`；
  验收标准: 方法签名与规格一致，支持后续扩展。
- [x] T0-7 [P] 目标/交付: 全局异常处理器与错误码映射；包/类/接口:
  `com.example.agent.gateway.controller.GlobalExceptionHandler`；输入/输出: 异常 -> `ErrorResponse`；
  日志/指标: 记录 `error` 日志并输出 `traceId`、`requestId`；验证: 触发异常返回统一 `ErrorResponse`；
  路径: `src/main/java/com/example/agent/gateway/controller/GlobalExceptionHandler.java`；依赖: `T0-2`；
  验收标准: 控制器异常统一映射为 `ErrorResponse`。

---

## `Phase 1`：agent-core + event-bus + streaming `SSE` API（`US1`，P1）

**Goal**: 任务提交、编排与实时事件流订阅最小闭环，包含多租户与鉴权最小可用贯通

**Independent Test**: 提交任务后可建立 `SSE` 订阅并接收 `WORKFLOW_STARTED` 等事件

- [x] T1-1 [P] [US1] 目标/交付: 定义事件类型枚举；包/类/接口:
  `com.example.agent.domain.event.EventType`；输入/输出: 事件名称 -> 枚举值；
  日志/指标: 不新增日志；验证: 编译通过且事件类型覆盖规格列表；
  路径: `src/main/java/com/example/agent/domain/event/EventType.java`；依赖: `T0-1`；
  验收标准: 事件类型完整且命名与规格一致。
- [x] T1-2 [P] [US1] 目标/交付: 定义事件流 DTO；包/类/接口:
  `com.example.agent.domain.event.StreamEvent`、`com.example.agent.domain.event.StreamCursor`、
  `com.example.agent.streaming.TaskStreamRequest`；
  输入/输出: 事件序列/订阅请求 -> `SSE` `id`/`event`/`data`；日志/指标: 不新增日志；
  验证: `StreamEvent` 字段包含 `eventId`、`schemaVersion`、`tenantId`、`streamId`、`seq`；
  `TaskStreamRequest` 包含 `workflowId`、`types`、`lastEventId`、`cursor`；
  路径: `src/main/java/com/example/agent/domain/event/StreamEvent.java`、
  `src/main/java/com/example/agent/domain/event/StreamCursor.java`、
  `src/main/java/com/example/agent/streaming/TaskStreamRequest.java`；依赖: `T1-1`；
  验收标准: `eventId` 规则为 `streamId:seq`。
- [x] T1-3 [P] [US1] 目标/交付: 定义任务请求/响应与查询 DTO；包/类/接口:
  `com.example.agent.common.TaskRequest`、`com.example.agent.common.TaskResponse`、
  `com.example.agent.common.TaskStatusResponse`、`com.example.agent.common.TaskQuery`、
  `com.example.agent.common.TaskListResponse`；输入/输出: 请求体或查询参数 -> 任务响应；
  日志/指标: 不新增日志；验证: `TaskRequest` 包含 `idempotencyKey`，`TaskQuery` 包含 `status`、
  `cursor`、`size`，`TaskListResponse.tasks` 为 `TaskStatusResponse` 列表；
  路径: `src/main/java/com/example/agent/common/TaskRequest.java`、
  `src/main/java/com/example/agent/common/TaskResponse.java`、
  `src/main/java/com/example/agent/common/TaskStatusResponse.java`、
  `src/main/java/com/example/agent/common/TaskQuery.java`、
  `src/main/java/com/example/agent/common/TaskListResponse.java`；依赖: `T0-2`；
  验收标准: `TaskListResponse` 包含 `tasks`（`TaskStatusResponse` 列表）、`nextCursor`、
  `hasMore`、`total`。
- [x] T1-4 [US1] 目标/交付: 任务服务接口定义；包/类/接口:
  `com.example.agent.orchestrator.TaskSubmissionService`、
  `com.example.agent.orchestrator.TaskQueryService`；输入/输出: `TaskRequest` +
  `TenantContext` -> `TaskResponse`/`TaskStatusResponse`；`TaskQuery` -> `TaskListResponse`；
  日志/指标: 接口不新增日志；
  验证: 编译通过且与规格签名一致；
  路径: `src/main/java/com/example/agent/orchestrator/TaskSubmissionService.java`、
  `src/main/java/com/example/agent/orchestrator/TaskQueryService.java`；依赖: `T1-3`；
  验收标准: 接口可被 `TaskController` 与 `TaskOrchestrator` 复用。
- [x] T1-5 [US1] 目标/交付: 编排与路由实现；包/类/接口:
  `com.example.agent.orchestrator.TaskOrchestrator`、`com.example.agent.orchestrator.WorkflowRouter`；
  输入/输出: `TaskRequest` -> `TaskResponse` + 事件流；通过 `Spring ApplicationEventPublisher`
  发布 `StreamEvent` 作为内部事件总线输入；日志/指标: 记录编排开始/结束 `info`，
  指标更新 `task.submit.count`、`task.duration.ms`；验证: 提交任务触发
  `WORKFLOW_STARTED` 事件并返回 `workflowId`；
  路径: `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`、
  `src/main/java/com/example/agent/orchestrator/WorkflowRouter.java`；依赖: `T1-4`；
  验收标准: `seq` 以 `workflowId` 单调递增，事件 `eventId` 可重放。
- [x] T1-6 [US1] 目标/交付: `agent core` 执行链路；
  包/类/接口: `com.example.agent.agentcore.EnforcementGateway`、
  `com.example.agent.agentcore.ToolRegistry`、`com.example.agent.agentcore.ToolCache`、
  `com.example.agent.agentcore.ToolExecutor`、`com.example.agent.agentcore.SandboxExecutor`；
  输入/输出: 执行请求 -> `StreamEvent` 负载与 `TokenUsageInput`；日志/指标: 工具调用记录 `info`，
  指标复用 `event.stream.count`；验证: 执行路径可发出 `TOOL_INVOKED` 与 `TOOL_OBSERVATION`；
  路径: `src/main/java/com/example/agent/agentcore/EnforcementGateway.java`、
  `src/main/java/com/example/agent/agentcore/ToolRegistry.java`、
  `src/main/java/com/example/agent/agentcore/ToolCache.java`、
  `src/main/java/com/example/agent/agentcore/ToolExecutor.java`、
  `src/main/java/com/example/agent/agentcore/SandboxExecutor.java`；依赖: `T1-5`；
  验收标准: 输出事件携带 `tenantId` 与 `eventId`，`TokenUsageInput` 携带 `tenantId` 与 `usageId`。
- [x] T1-7 [US1] 目标/交付: 事件流服务实现；
  包/类/接口: `com.example.agent.streaming.EventStreamService`；
  输入/输出: `StreamCursor`/`Last-Event-ID` -> `Flux<StreamEvent>`；
  内部事件: 订阅 `Spring ApplicationEvent` 发布的 `StreamEvent`，写入 `Redis Stream` 并通过
  `Reactor Sinks` 推送在线订阅；
  存储/保留: `Redis Stream` 使用 `MAXLEN=256` 与 `TTL=24h`，超出窗口返回 `STREAM_GAP` 并断开，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  并发隔离: 阻塞式 `Redis` 访问通过 `Schedulers.boundedElastic()`；
  日志/指标: 订阅建立与断开 `info`，`STREAM_GAP` 记录 `warn`，指标更新
  `event.stream.count`、`event.stream.lag.ms`；验证: `Last-Event-ID` 优先级生效；
  路径: `src/main/java/com/example/agent/streaming/EventStreamService.java`；依赖: `T1-2`、`T1-5`；
  验收标准: 事件按 `seq` 有序输出且仅包含当前租户数据。
- [x] T1-8 [US1] 目标/交付: `SSE` 控制器；
  包/类/接口: `com.example.agent.streaming.SseStreamController`；
  输入/输出: `/api/v1/stream/sse` 查询参数 -> `text/event-stream`；
  日志/指标: 连接开始/结束 `info`，指标更新 `event.stream.count`；
  验证: 接口符合 `openapi.yaml` 且鉴权失败返回 `ErrorResponse`；
  路径: `src/main/java/com/example/agent/streaming/SseStreamController.java`；依赖: `T1-7`；
  验收标准: `eventId` 映射到 `SSE` `id`，`type` 映射到 `event`。
- [x] T1-9 [US1] 目标/交付: 任务控制器；
  包/类/接口: `com.example.agent.gateway.controller.TaskController`；
  输入/输出: `/api/v1/tasks` -> `ApiResponse<TaskResponse/TaskStatusResponse/TaskListResponse>`；
  日志/指标: 任务提交与查询记录 `info`，指标更新 `task.submit.count`；
  验证: 重复 `idempotencyKey` 返回相同 `taskId`，列表查询返回 `TaskListResponse`；
  路径: `src/main/java/com/example/agent/gateway/controller/TaskController.java`；依赖: `T1-5`；
  验收标准: 返回结构与错误码符合规格。
- [x] T1-10 [US1] 目标/交付: `TaskOrchestrator` 并发幂等修复；
  涉及文件: `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`；
  修复策略: 使用原子方式创建并缓存 `idempotencyKey` 结果，避免并发重复创建任务；
  对应测试点: 并发提交相同 `idempotencyKey` 仅生成一个 `taskId` 与事件；
  验收标准: 同租户并发请求返回相同 `taskId` 且仅发布一次 `WORKFLOW_STARTED`；
  依赖: `T1-5`。
- [x] T1-11 [US1] 目标/交付: `SSE` 30 秒超时事件序列一致性修复；
  涉及文件: `src/main/java/com/example/agent/streaming/SseStreamController.java`；
  修复策略: 超时事件使用统一序列生成策略，确保 `eventId=streamId:seq` 且 `seq` 单调递增；
  对应测试点: 30 秒无事件返回 `ERROR_OCCURRED` 且 `eventId`/`seq` 合法；
  验收标准: 超时事件不与 `WORKFLOW_STARTED` 冲突，断线续传不触发误判 `STREAM_GAP`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  依赖: `T1-7`、`T1-8`。
- [x] T1-12 [US1] 目标/交付: 断线续传回退 `Redis` 策略；
  涉及文件: `src/main/java/com/example/agent/streaming/EventStreamService.java`；
  修复策略: 索引为空时从 `Redis` 读取窗口校验 `last_event_id` 并重建索引；
  对应测试点: 重启后仍可基于 24 小时窗口续传；
  验收标准: `last_event_id` 在 `Redis` 窗口内时可继续订阅，超出窗口返回 `STREAM_GAP`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  依赖: `T1-7`。
- [x] T1-13 [US1] 目标/交付: `TaskOrchestrator` 内存结构清理策略；
  涉及文件: `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`；
  修复策略: 为缓存结构增加回收策略或容量上限并记录清理日志；
  对应测试点: 长时间运行后缓存不会无限增长；
  验收标准: 清理策略可配置且不影响正常查询与幂等行为；
  依赖: `T1-10`。
- [x] T1-14 [US1] 目标/交付: `types` 参数清洗；
  涉及文件: `src/main/java/com/example/agent/streaming/SseStreamController.java`；
  修复策略: 拆分后对事件类型做 `trim` 并过滤空值；
  对应测试点: `types=WORKFLOW_STARTED, TOOL_INVOKED` 可正确过滤；
  验收标准: 含空格的事件类型仍可正确匹配；
  依赖: `T1-8`。
- [x] T1-15 [P] [US1] 目标/交付: 并发幂等测试；
  涉及文件: `src/test/java/com/example/agent/orchestrator/TaskOrchestratorTest.java`；
  修复策略: 使用并发提交构造幂等场景并断言返回一致；
  对应测试点: 并发相同 `idempotencyKey` 仅返回一个 `taskId`；
  验收标准: 测试稳定通过且覆盖并发条件；
  依赖: `T1-10`。
- [x] T1-16 [P] [US1] 目标/交付: `SSE` 超时与序列一致性测试；
  涉及文件: `src/test/java/com/example/agent/streaming/SseStreamControllerTest.java`；
  修复策略: 模拟 30 秒无事件订阅并校验超时事件字段；
  对应测试点: 超时事件 `eventId`/`seq` 合法且为 `ERROR_OCCURRED`；
  验收标准: 超时场景测试可复现且断线续传不误报；
  依赖: `T1-11`。
- [x] T1-17 [P] [US1] 目标/交付: 断线续传回退测试；
  涉及文件: `src/test/java/com/example/agent/streaming/EventStreamServiceTest.java`；
  修复策略: 构造索引为空但 `Redis` 仍有事件的订阅场景；
  对应测试点: `last_event_id` 仍可通过校验并继续订阅；
  验收标准: 重启后续传行为与 24 小时窗口一致；
  依赖: `T1-12`。
- [x] T1-18 [P] [US1] 目标/交付: 多租户隔离测试；
  涉及文件: `src/test/java/com/example/agent/gateway/controller/TaskControllerTest.java`；
  修复策略: 构造不同租户请求并校验隔离效果；
  对应测试点: 跨租户查询返回 `NOT_FOUND` 且事件过滤有效，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  验收标准: 多租户隔离测试通过且无存在性泄漏；
  依赖: `T1-7`、`T1-9`。
- [x] T1-19 [US1] 目标/交付: 实现 `API Key` 真正校验并返回用户身份；
  涉及文件: `src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java`、
  `src/main/java/com/example/agent/auth/AuthService.java`、
  `src/main/java/com/example/agent/auth/UserContext.java`、
  `src/main/java/com/example/agent/auth/ApiKeyProperties.java`、
  `src/main/resources/application.yml`；
  修复策略: 增加 `application.yml` 中 `api-keys` 映射配置与 `@ConfigurationProperties` 绑定，
  校验 `API Key` 后返回 `UserContext`，无效或缺失返回 `UNAUTHORIZED` 或 `FORBIDDEN`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》并记录 `error` 日志；
  测试点: 鉴权失败时无效 `API Key` 返回 `UNAUTHORIZED` 或 `FORBIDDEN`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；租户缺失时非白名单路径返回
  `TENANT_MISSING`，`HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  分页边界 `size` 超过上限返回 `400` 或被限制到上限；
  验收标准: 有效 `API Key` 返回对应 `userId/roles`，无效 `API Key` 不可通过且不泄漏租户存在性；
  依赖: `T0-6`。
- [x] T1-20 [US1] 目标/交付: 禁止信任 `X-User-Id` 与 `X-Roles`，身份与角色由鉴权结果提供；
  涉及文件: `src/main/java/com/example/agent/auth/TenantResolver.java`、
  `src/main/java/com/example/agent/auth/TenantContext.java`、
  `src/main/java/com/example/agent/auth/AuthService.java`、
  `src/main/java/com/example/agent/auth/UserContext.java`、
  `src/main/resources/application.yml`；
  修复策略: 默认忽略 `X-User-Id` 与 `X-Roles`，由 `AuthService` 鉴权结果填充用户信息；
  仅在开启可信上游模式时读取并进行强校验（例如白名单或签名校验）；
  测试点: 鉴权失败时无效 `API Key` 返回 `UNAUTHORIZED` 或 `FORBIDDEN`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；租户缺失时非白名单路径返回
  `TENANT_MISSING`，`HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  分页边界 `size` 超过上限返回 `400` 或被限制到上限；
  验收标准: 默认模式下请求头不再覆盖用户身份与角色，可信上游模式校验失败时拒绝请求；
  依赖: `T1-19`。
- [x] T1-21 [US1] 目标/交付: 将 `UserContext` 绑定到请求上下文并贯通审计链路；
  涉及文件: `src/main/java/com/example/agent/gateway/controller/TaskController.java`、
  `src/main/java/com/example/agent/auth/UserContext.java`、
  `src/main/java/com/example/agent/auth/TenantContext.java`、
  `src/main/java/com/example/agent/auth/TenantContextFilter.java`；
  修复策略: 鉴权后将 `UserContext` 写入 `ServerWebExchange` 属性与 `Reactor Context`，
  统一由下游读取并用于审计/鉴权判断；
  测试点: 鉴权失败时无效 `API Key` 返回 `UNAUTHORIZED` 或 `FORBIDDEN`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；租户缺失时非白名单路径返回
  `TENANT_MISSING`，`HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  分页边界 `size` 超过上限返回 `400` 或被限制到上限；
  验收标准: 下游组件可从上下文读取到 `userId/roles` 且与鉴权结果一致；
  依赖: `T1-19`。
- [x] T1-22 [US1] 目标/交付: `TenantContextFilter` 增加白名单并修正缺失租户返回码；
  涉及文件: `src/main/java/com/example/agent/auth/TenantContextFilter.java`、
  `src/main/resources/application.yml`；
  修复策略: 放行 `/actuator/health`、`/actuator/info` 白名单路径，
  缺失租户头时返回 `TENANT_MISSING`，`HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》，
  不再返回 `NOT_FOUND`；
  测试点: 鉴权失败时无效 `API Key` 返回 `UNAUTHORIZED` 或 `FORBIDDEN`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；租户缺失时非白名单路径返回
  `TENANT_MISSING`，`HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  分页边界 `size` 超过上限返回 `400` 或被限制到上限；
  验收标准: 白名单路径可匿名访问，非白名单缺失租户时返回 `TENANT_MISSING`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  依赖: `T0-5`。
- [x] T1-23 [US1] 目标/交付: 为任务请求与查询参数增加校验与默认上限；
  涉及文件: `src/main/java/com/example/agent/common/TaskRequest.java`、
  `src/main/java/com/example/agent/common/TaskQuery.java`、
  `src/main/java/com/example/agent/gateway/controller/TaskController.java`、
  `pom.xml`；
  修复策略: 增加 `@NotBlank`、`@Min`、`@Max` 等校验并启用校验依赖，
  设置 `size` 必填与上限，超限返回 `400`；
  测试点: 鉴权失败时无效 `API Key` 返回 `UNAUTHORIZED` 或 `FORBIDDEN`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；租户缺失时非白名单路径返回
  `TENANT_MISSING`，`HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  分页边界 `size` 为空返回 `400`，超上限返回 `400`；
  验收标准: 非法请求参数被拦截并返回统一错误结构，分页上限生效；
  依赖: `T1-9`。
- [x] T1-24 [US1] 目标/交付: 角色解析增加 `trim` 与空值过滤；
  涉及文件: `src/main/java/com/example/agent/auth/TenantResolver.java`；
  修复策略: 解析 `X-Roles` 时对每个角色执行 `trim` 并过滤空串；
  测试点: 鉴权失败时无效 `API Key` 返回 `UNAUTHORIZED` 或 `FORBIDDEN`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；租户缺失时非白名单路径返回
  `TENANT_MISSING`，`HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  分页边界 `size` 超过上限返回 `400` 或被限制到上限；
  验收标准: 含空格角色可正确解析且不产生空角色；
  依赖: `T1-20`。

---

## `Phase 2`：task-history / timeline + memory-system（`US2`，P2）

**Goal**: 事件持久化、历史查询与时间线生成，记忆存取与检索

**Independent Test**: 任务完成后可查询事件日志与时间线摘要，记忆检索返回结果

- [ ] T2-1 [P] [US2] 目标/交付: 历史与时间线 DTO；
  包/类/接口: `com.example.agent.history.EventLogRecord`、`com.example.agent.history.EventLogPage`、
  `com.example.agent.history.EventQuery`、`com.example.agent.history.TimelineRecord`、
  `com.example.agent.history.TimelineRequest`、`com.example.agent.history.TimelineResponse`；
  输入/输出: 事件查询/时间线请求 -> 事件分页与时间线结构；
  日志/指标: 不新增日志；验证: `EventLogRecord` 包含 `eventId`、`workflowId`、`tenantId`；
  `EventQuery` 包含 `workflowId`、`types`、`cursor`、`size`；
  路径: `src/main/java/com/example/agent/history/EventLogRecord.java`、
  `src/main/java/com/example/agent/history/EventLogPage.java`、
  `src/main/java/com/example/agent/history/EventQuery.java`、
  `src/main/java/com/example/agent/history/TimelineRecord.java`、
  `src/main/java/com/example/agent/history/TimelineRequest.java`、
  `src/main/java/com/example/agent/history/TimelineResponse.java`；依赖: `T1-2`；
  验收标准: DTO 与规格字段一致。
- [ ] T2-2 [US2] 目标/交付: 事件日志仓储；
  包/类/接口: `com.example.agent.history.EventLogRepository`；
  输入/输出: `EventLogRecord` -> `event_logs` 持久化；
  日志/指标: 失败记录 `error`，指标更新 `event.persist.count`；
  验证: 写入与查询按 `tenantId` 过滤；
  路径: `src/main/java/com/example/agent/history/EventLogRepository.java`；依赖: `T2-1`；
  验收标准: `eventId` 幂等写入生效。
- [ ] T2-3 [US2] 目标/交付: 事件日志服务；
  包/类/接口: `com.example.agent.history.EventLogService`；
  输入/输出: 事件追加/查询 -> 事件列表；
  并发隔离: `EventLogRepository` 的阻塞访问通过 `Schedulers.boundedElastic()`；
  日志/指标: 持久化失败记录 `ERROR_OCCURRED` 与 `EVENT_PERSIST_FAILED`；
  验证: 失败重试后仍失败时记录错误事件；
  路径: `src/main/java/com/example/agent/history/EventLogService.java`；依赖: `T2-2`；
  验收标准: 查询结果与 `SSE` 事件一致，允许短暂延迟。
- [ ] T2-4 [US2] 目标/交付: 时间线生成服务；
  包/类/接口: `com.example.agent.history.TimelineService`；
  输入/输出: `workflowId` -> `TimelineResponse`（`summary`/`full`）；
  日志/指标: 生成耗时记录 `info`，指标更新 `event.persist.count`；
  验证: 时间线仅基于持久化事件生成；
  路径: `src/main/java/com/example/agent/history/TimelineService.java`；依赖: `T2-3`；
  验收标准: `persist=true` 时写入 `event_logs`。
- [ ] T2-5 [US2] 目标/交付: 事件与时间线接口；
  包/类/接口: `com.example.agent.gateway.controller.TimelineController`；
  输入/输出: `/api/v1/events`、`/api/v1/timeline` -> `ApiResponse`；
  日志/指标: 查询记录 `info`，指标更新 `event.persist.count`；
  验证: `tenantId` 过滤生效且跨租户返回 `NOT_FOUND`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  路径: `src/main/java/com/example/agent/gateway/controller/TimelineController.java`；依赖: `T2-3`；
  验收标准: 返回字段与 `openapi.yaml` 一致。
- [ ] T2-6 [P] [US2] 目标/交付: 记忆 DTO；
  包/类/接口: `com.example.agent.memory.MemoryRecord`、`com.example.agent.memory.MemoryChunk`、
  `com.example.agent.memory.MemoryQuery`、`com.example.agent.memory.MemorySearchResult`、
  `com.example.agent.memory.CompressionRequest`；
  输入/输出: 记忆请求 -> 记忆结果；
  日志/指标: 不新增日志；验证: 字段包含 `tenantId` 与 `layer`；
  路径: `src/main/java/com/example/agent/memory/MemoryRecord.java`、
  `src/main/java/com/example/agent/memory/MemoryChunk.java`、
  `src/main/java/com/example/agent/memory/MemoryQuery.java`、
  `src/main/java/com/example/agent/memory/MemorySearchResult.java`、
  `src/main/java/com/example/agent/memory/CompressionRequest.java`；依赖: `T0-1`；
  验收标准: `layer` 仅允许 `recent`、`semantic`、`compressed`。
- [ ] T2-7 [US2] 目标/交付: 记忆存取实现；
  包/类/接口: `com.example.agent.memory.VectorStore`、`com.example.agent.memory.MemoryStore`；
  输入/输出: 保存/检索请求 -> 记忆结果；
  并发隔离: 阻塞式存取通过 `Schedulers.boundedElastic()`；
  日志/指标: 检索耗时记录 `info`，指标更新 `storage.db.latency.ms`、
  `storage.redis.hit`；验证: `memoryId` + `tenantId` 幂等去重；
  路径: `src/main/java/com/example/agent/memory/VectorStore.java`、
  `src/main/java/com/example/agent/memory/MemoryStore.java`；依赖: `T2-6`；
  验收标准: 嵌入不可用时返回空结果且不阻断任务。
- [ ] T2-8 [US2] 目标/交付: 记忆接口落地；
  包/类/接口: `com.example.agent.gateway.controller.TaskController`；
  输入/输出: `/api/v1/memory/save`、`/api/v1/memory/search`、`/api/v1/memory/compress` -> `ApiResponse`；
  日志/指标: 请求记录 `info`，指标复用 `storage.db.latency.ms`；
  验证: 返回结构与 `openapi.yaml` 一致；
  路径: `src/main/java/com/example/agent/gateway/controller/TaskController.java`；依赖: `T1-9`、`T2-7`；
  验收标准: 仅返回当前租户记忆结果。

---

## `Phase 3`：scheduled-tasks + authentication / multitenancy + token-budget-tracking（`US3`，P3）

**Goal**: 定时任务、鉴权增强与预算计量的完整闭环

**Independent Test**: 不同租户访问同一任务被拒绝，预算汇总可查询且无重复计量

- [ ] T3-1 [US3] 目标/交付: 鉴权与多租户策略增强（基于 `Phase 1` `MVP`）；
  包/类/接口: `com.example.agent.auth.AuthService`、`com.example.agent.auth.ApiKeyAuthenticator`、
  `com.example.agent.auth.TenantResolver`、`com.example.agent.auth.TenantContextFilter`；
  输入/输出: `API Key` + 可信上游开关 -> `UserContext` 与审计字段；
  日志/指标: 失败记录 `error` 与 `AUTH_FAILED`，补充审计字段记录；
  验证: 策略开关与可信上游校验生效，默认路径保持 `Phase 1` 行为；
  路径: `src/main/java/com/example/agent/auth/AuthService.java`、
  `src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java`、
  `src/main/java/com/example/agent/auth/TenantResolver.java`、
  `src/main/java/com/example/agent/auth/TenantContextFilter.java`；依赖: `T1-19`、`T1-20`、`T1-22`；
  验收标准: 鉴权增强不影响既有 `MVP` 能力，审计字段可追溯且配置可控。
- [ ] T3-2 [P] [US3] 目标/交付: 调度 DTO；
  包/类/接口: `com.example.agent.scheduler.ScheduleSpec`、
  `com.example.agent.scheduler.ScheduleResponse`、
  `com.example.agent.scheduler.ScheduleExecutionRecord`、
  `com.example.agent.scheduler.ScheduleQuery`、
  `com.example.agent.scheduler.SchedulePage`；
  输入/输出: 调度请求/查询 -> 执行记录与分页结果；
  日志/指标: 不新增日志；
  验证: `ScheduleSpec` 字段包含 `scheduleId`、`cron`、`timezone`、`tenantId`；
  `ScheduleQuery` 包含 `status`、`cursor`、`size`；
  路径: `src/main/java/com/example/agent/scheduler/ScheduleSpec.java`、
  `src/main/java/com/example/agent/scheduler/ScheduleResponse.java`、
  `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java`、
  `src/main/java/com/example/agent/scheduler/ScheduleQuery.java`、
  `src/main/java/com/example/agent/scheduler/SchedulePage.java`；依赖: `T0-1`；
  验收标准: 支持 `idempotencyKey` 幂等请求。
- [ ] T3-3 [US3] 目标/交付: 调度仓储；
  包/类/接口: `com.example.agent.scheduler.ScheduleRepository`、
  `com.example.agent.scheduler.ScheduleExecutionRepository`；
  输入/输出: `ScheduleSpec` -> `scheduled_tasks`；
  日志/指标: 执行失败记录 `error`，指标更新 `schedule.run.fail.count`；
  验证: 按 `tenantId` 查询；
  路径: `src/main/java/com/example/agent/scheduler/ScheduleRepository.java`、
  `src/main/java/com/example/agent/scheduler/ScheduleExecutionRepository.java`；依赖: `T3-2`；
  验收标准: 执行记录与成本字段齐全。
- [ ] T3-4 [US3] 目标/交付: 调度管理实现；
  包/类/接口: `com.example.agent.scheduler.ScheduleManager`；
  输入/输出: 创建/暂停/恢复/删除 -> 执行计划；
  并发隔离: 调度存储的阻塞访问通过 `Schedulers.boundedElastic()`；
  日志/指标: 触发记录 `info`，指标更新 `schedule.run.count`；
  验证: 缺失 `tenantId` 的执行记录失败并返回 `AUTH_FAILED`；
  路径: `src/main/java/com/example/agent/scheduler/ScheduleManager.java`；依赖: `T3-3`；
  验收标准: `scheduleId` + `idempotencyKey` 去重生效。
- [ ] T3-5 [US3] 目标/交付: 调度接口；
  包/类/接口: `com.example.agent.gateway.controller.ScheduleController`；
  输入/输出: `/api/v1/schedules` -> `ApiResponse<ScheduleResponse>`；
  日志/指标: 请求记录 `info`，指标复用 `schedule.run.count`；
  验证: 返回结构符合 `openapi.yaml`；
  路径: `src/main/java/com/example/agent/gateway/controller/ScheduleController.java`；依赖: `T3-4`；
  验收标准: 跨租户访问返回 `NOT_FOUND`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》。
- [ ] T3-6 [P] [US3] 目标/交付: 预算 DTO；
  包/类/接口: `com.example.agent.budget.TokenUsageInput`、
  `com.example.agent.budget.TokenUsageRecord`、
  `com.example.agent.budget.TokenUsageSummary`；
  输入/输出: 计量输入 -> 计量记录；
  日志/指标: 不新增日志；
  验证: `TokenUsageInput` 包含 `usageId`；
  路径: `src/main/java/com/example/agent/budget/TokenUsageInput.java`、
  `src/main/java/com/example/agent/budget/TokenUsageRecord.java`、
  `src/main/java/com/example/agent/budget/TokenUsageSummary.java`；依赖: `T0-1`；
  验收标准: 字段与规格一致且包含 `tenantId`。
- [ ] T3-7 [US3] 目标/交付: 预算仓储与成本计算；
  包/类/接口: `com.example.agent.budget.TokenUsageRepository`、
  `com.example.agent.budget.CostCalculator`；
  输入/输出: `TokenUsageInput` -> `token_usage`；
  并发隔离: `TokenUsageRepository` 阻塞访问通过 `Schedulers.boundedElastic()`；
  日志/指标: 失败记录 `error`，指标更新 `budget.tokens.used`、`budget.cost.usd`；
  验证: 同一 `usageId` 去重；
  路径: `src/main/java/com/example/agent/budget/TokenUsageRepository.java`、
  `src/main/java/com/example/agent/budget/CostCalculator.java`；依赖: `T3-6`；
  验收标准: `totalTokens` 计算一致。
- [ ] T3-8 [US3] 目标/交付: 预算管理与事件触发；
  包/类/接口: `com.example.agent.budget.TokenBudgetManager`；
  输入/输出: 计量输入 -> 聚合与 `BUDGET_THRESHOLD` 事件；
  日志/指标: 超限记录 `warn`，指标更新 `budget.exceeded.count`；
  验证: 预算阈值触发事件并写入 `token_usage`；
  路径: `src/main/java/com/example/agent/budget/TokenBudgetManager.java`；依赖: `T3-7`、`T1-5`；
  验收标准: 聚合按 `taskId` + `tenantId` 维度一致。
- [ ] T3-9 [US3] 目标/交付: 预算计量链路集成；
  包/类/接口: `com.example.agent.agentcore.ToolExecutor`、`com.example.agent.budget.TokenBudgetManager`；
  输入/输出: `TokenUsageInput` -> `TokenBudgetManager.recordUsage` -> `TokenUsageRecord`；
  日志/指标: 计量调用记录 `info`，失败记录 `error`，指标复用 `budget.tokens.used`；
  验证: 每次工具调用仅记录一次 `usageId`，重复 `usageId` 不重复计量；
  路径: `src/main/java/com/example/agent/agentcore/ToolExecutor.java`；
  依赖: `T1-6`、`T3-6`、`T3-8`；
  验收标准: 计量调用覆盖所有工具执行分支且不影响主流程。

---

## `Phase 4`：收尾与跨阶段一致性

**Purpose**: 契约与文档同步，确保交付一致性

- [ ] T4-1 [P] 目标/交付: 同步接口契约；
  包/类/接口: `openapi` 定义；
  输入/输出: 规格与实现字段 -> `openapi.yaml`；
  日志/指标: 不新增日志；
  验证: `StreamEvent`、`TaskRequest.idempotencyKey`、`TokenUsageInput.usageId` 字段齐全；
  路径: `specs/001-agent-core-spec/contracts/openapi.yaml`；依赖: `T1-2`、`T1-3`、`T3-6`；
  需求映射: `maintenance`（非需求任务）。
  验收标准: 契约与 `spec.md` 字段一致。
- [ ] T4-2 [P] 目标/交付: 更新快速验证指引；
  包/类/接口: 快速启动文档；
  输入/输出: 新增参数 -> 验证步骤；
  日志/指标: 不新增日志；
  验证: 手动执行 `quickstart.md` 步骤可完成一次任务提交与订阅；
  路径: `specs/001-agent-core-spec/quickstart.md`；依赖: `T1-9`、`T1-8`；
  需求映射: `maintenance`（非需求任务）。
  验收标准: 文档步骤可复现且与接口一致。
- [ ] T4-3 [P] 目标/交付: 补齐模块映射表与差异说明（`FR-012`~`FR-014`）；
  包/类/接口: 规格映射表与差异说明；
  输入/输出: `Shannon` 模块 -> `Java` 模块/`package`/`interface`/`class` 映射与差异说明；
  日志/指标: 不新增日志；
  验证: 对齐表覆盖 `agent core`、`orchestrator`、`streaming api`、`event types`、
  `task history & timeline`、`memory system`、`scheduled tasks`、`authentication & multitenancy`、
  `token budget tracking`，并标注无法 `1:1` 对齐的原因与替代设计；
  路径: `specs/001-agent-core-spec/spec.md`；依赖: `T0-1`；
  需求映射: `FR-012`、`FR-013`、`FR-014`；
  验收标准: 映射表可追溯且差异说明覆盖全部未对齐项。
- [ ] T4-4 [P] 目标/交付: 任务提交与鉴权开销基线验证（`SC-001`）；
  包/类/接口: 指标与质量门禁记录；
  输入/输出: 任务提交压测 -> `taskId` 返回时延与鉴权开销基线；
  日志/指标: 使用 `task.duration.ms` 与鉴权耗时日志；
  验证: 通过 `/actuator/metrics/task.duration.ms` 获取 `p95`；
  路径: `specs/001-agent-core-spec/checklists/checklist.md`；依赖: `T1-5`、`T1-19`；
  需求映射: `SC-001`；
  验收标准: 按 `spec.md` `SC-001` 口径完成基线记录并满足阈值。
- [ ] T4-5 [P] 目标/交付: `SSE` 稳定性与断线恢复、`Redis` 回退耗时基线验证（`SC-002`）；
  包/类/接口: 流式订阅与恢复校验记录；
  输入/输出: 断线恢复与回退场景 -> 恢复耗时与回退耗时基线；
  日志/指标: 使用 `event.stream.lag.ms` 与回退耗时日志；
  验证: 断线恢复时间通过测试日志与 `SSE` 事件 `eventId`/`seq` 校验；
  路径: `specs/001-agent-core-spec/checklists/checklist.md`；依赖: `T1-11`、`T1-12`；
  需求映射: `SC-002`；
  验收标准: 按 `spec.md` `SC-002` 口径完成基线记录并满足阈值。
- [ ] T4-6 [P] 目标/交付: 事件吞吐与持久化成功率基线验证（`SC-003`）；
  包/类/接口: 事件持久化与吞吐校验记录；
  输入/输出: 事件流压测 -> 吞吐基线与持久化成功率；
  日志/指标: 使用 `event.persist.count` 与失败日志统计；
  验证: 通过指标与日志计算成功率与吞吐基线；
  路径: `specs/001-agent-core-spec/checklists/checklist.md`；依赖: `T2-3`；
  需求映射: `SC-003`；
  验收标准: 按 `spec.md` `SC-003` 口径完成基线记录并满足阈值。
- [ ] T4-7 [P] 目标/交付: 任务历史查询延迟基线验证（`SC-004`）；
  包/类/接口: 历史查询性能校验记录；
  输入/输出: 历史查询压测 -> 延迟基线；
  日志/指标: 使用 `storage.db.latency.ms` 与查询日志；
  验证: 通过 `/actuator/metrics/storage.db.latency.ms` 获取 `p95`；
  路径: `specs/001-agent-core-spec/checklists/checklist.md`；依赖: `T2-5`；
  需求映射: `SC-004`；
  验收标准: 按 `spec.md` `SC-004` 口径完成基线记录并满足阈值。
- [ ] T4-8 [P] 目标/交付: 越权访问与存在性泄漏验证（`SC-005`）；
  包/类/接口: 多租户隔离验证记录；
  输入/输出: 跨租户请求 -> 拒绝结果与错误码；
  日志/指标: 记录拒绝原因与 `tenantId`；
  验证: 复用多租户隔离测试并校验返回 `NOT_FOUND`，
  `HTTP` 状态映射参见 `spec.md` 章节《错误码与异常策略》；
  路径: `specs/001-agent-core-spec/checklists/checklist.md`；依赖: `T1-18`；
  需求映射: `SC-005`；
  验收标准: 按 `spec.md` `SC-005` 口径完成验证并满足阈值。
- [ ] T4-9 [P] 目标/交付: 预算覆盖与内存增长/清理基线验证（`SC-006`）；
  包/类/接口: 预算计量与缓存清理校验记录；
  输入/输出: 预算计量场景 + 长时间运行 -> 覆盖率与内存增长基线；
  日志/指标: 使用 `budget.tokens.used` 与清理日志；
  验证: 预算记录对照任务与工具调用统计，缓存清理日志可观测；
  路径: `specs/001-agent-core-spec/checklists/checklist.md`；依赖: `T3-9`、`T1-13`；
  需求映射: `SC-006`；
  验收标准: 按 `spec.md` `SC-006` 口径完成基线记录并满足阈值。

---

## Dependencies & Execution Order

### Phase Dependencies

- **`Phase 0`**: 无依赖
- **`Phase 1`**: 依赖 `Phase 0`
- **`Phase 2`**: 依赖 `Phase 1`
- **`Phase 3`**: 依赖 `Phase 1` 与 `Phase 2` 的核心能力
- **`Phase 4`**: 依赖所有实现任务完成

### User Story Dependencies

- **`US1` (P1)**: 依赖 `Phase 0`
- **`US2` (P2)**: 依赖 `US1` 的事件模型与 `SSE` 可用
- **`US3` (P3)**: 依赖 `US1` 的编排事件与 `US2` 的持久化能力

### Parallel Opportunities

- `Phase 0` 中标记 `[P]` 的任务可并行
- `Phase 1` 中 `T1-1`、`T1-2`、`T1-3` 可并行
- `Phase 2` 中 `T2-1`、`T2-6` 可并行
- `Phase 3` 中 `T3-2` 与 `T3-6` 可并行
- `Phase 4` 中 `T4-1`~`T4-9` 可并行

---

## Parallel Example: `US1`

```text
T1-1 事件类型枚举
T1-2 事件流 DTO
T1-3 任务请求/响应 DTO
```

---

## Parallel Example: `US2`

```text
T2-1 事件日志/时间线 DTO
T2-6 记忆 DTO
```

---

## Parallel Example: `US3`

```text
T3-2 调度 DTO
T3-6 预算 DTO
```

---

## Implementation Strategy

### `MVP` 先行（`US1`）

1. 完成 `Phase 0`
2. 完成 `Phase 1`
3. 验证 `US1` 独立可用（任务提交 + `SSE` 订阅）

### 增量交付

1. `US1` 完成后进入 `US2`（历史与时间线）
2. `US2` 完成后进入 `US3`（多租户、调度、预算）
3. 最后同步契约与文档（`Phase 4`）
