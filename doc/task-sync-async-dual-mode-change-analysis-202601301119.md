# 同时支持同步与异步执行的改动分析

## 背景
现有实现中 `POST /api/v1/tasks` 走同步执行链路，但对外只返回 `taskId/workflowId/status`。若要同时支持同步与异步执行，需要在请求层区分执行模式，并在执行层实现异步队列与执行器，同时保证高可用与避免阻塞、死锁、假死。

## 目标与约束
- 同步模式：在限定时间内返回最终结果或明确超时结果。
- 异步模式：快速返回任务标识，结果通过查询或事件流获取。
- 高可用：异步执行不因单机线程耗尽、队列堆积或阻塞而长期不可用。
- 避免死锁与假死：不在同一线程池内相互等待，不在事件循环线程阻塞等待执行结果。

## 设计要点（高可用与无阻塞）
1) 执行线程池隔离
- 控制器线程与执行线程池隔离，避免阻塞 `WebFlux` 事件循环。
- 异步执行使用独立线程池，队列长度可配置，超出即快速失败并返回可恢复错误码。

2) 队列与持久化
- 异步任务进入持久化队列（如 `Redis`/数据库队列表），避免单机内存队列丢失与假死。
- Worker 从队列拉取任务并更新状态，支持重试与失败恢复。

3) 超时与降级
- 同步等待设置超时，超时即返回可查询的任务状态，避免长时间阻塞请求。
- 异步执行设置执行超时与心跳，防止任务卡死。

4) 幂等与并发控制
- 幂等键只在创建阶段加锁，避免跨线程持锁等待。
- 对幂等键的复用与状态更新采用原子更新或乐观锁，避免重复执行。

5) 事件发布与观测
- 事件发布与持久化使用异步化或隔离线程池，避免慢监听拖垮执行线程。
- 增加队列长度、拒绝次数、执行耗时、超时次数等指标。

## 需要改动的文件清单
### 接口与请求模型
- `src/main/java/com/example/agent/common/TaskRequest.java`
  - 增加执行模式字段（如 `executionMode` 或 `waitForResult`）与同步等待超时字段。
- `src/main/java/com/example/agent/common/TaskResponse.java`
  - 扩展返回结构，区分同步与异步响应（例如增加 `mode`、`result` 或 `accepted` 标记）。
- `src/main/java/com/example/agent/orchestrator/TaskSubmissionService.java`
  - 增加同步等待或异步提交的接口方法，明确返回类型与时序。

### 控制器层
- `src/main/java/com/example/agent/gateway/controller/TaskController.java`
  - 解析执行模式并选择同步或异步路径。
  - 同步模式使用异步等待链路（例如 `Mono` + 超时），避免阻塞事件循环线程。

### 编排与执行层
- `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
  - 拆分“任务创建”与“任务执行”。
  - 异步模式：创建后入队并返回；同步模式：触发执行并等待结果（带超时）。
- `src/main/java/com/example/agent/orchestrator/WorkflowRouter.java`
  - 作为执行入口由 Worker 调用，避免控制器直接同步调用。

### 异步执行与队列
- 新增：`src/main/java/com/example/agent/orchestrator/TaskExecutionService.java`
  - 负责任务入队、出队与执行调度。
- 新增：`src/main/java/com/example/agent/orchestrator/TaskExecutionWorker.java`
  - Worker 拉取任务并调用执行入口，更新状态与事件。
- 新增：`src/main/java/com/example/agent/orchestrator/TaskQueueRepository.java`
  - 队列抽象接口，支持多实现（内存/Redis/数据库）。
- 新增：`src/main/java/com/example/agent/orchestrator/RedisTaskQueueRepository.java` 或 `JdbcTaskQueueRepository.java`
  - 高可用队列实现，支持重试与可观测。

### 状态与持久化
- `src/main/java/com/example/agent/orchestrator/TaskRecord.java`
  - 增加执行模式、入队时间、开始时间、超时字段或心跳字段。
- `src/main/java/com/example/agent/orchestrator/TaskRepository.java`
  - 增加原子状态变更方法（如 `markRunning`、`markCompleted`、`markTimeout`）。
- 若引入数据库队列：`src/main/resources/db/migration/*.sql`
  - 增加队列表或字段，支持持久化队列与执行状态。

### 配置与可用性保障
- 新增：`src/main/java/com/example/agent/orchestrator/TaskExecutionConfig.java`
  - 定义执行线程池、队列大小、拒绝策略、调度线程池。
- `src/main/resources/application.yml`
  - 增加异步执行相关配置项（线程池大小、队列长度、超时阈值）。

### 事件与异常处理
- `src/main/java/com/example/agent/streaming/EventStreamService.java`
  - 评估事件发布是否需异步化或隔离线程池，避免慢监听阻塞执行线程。
- `src/main/java/com/example/agent/gateway/controller/GlobalExceptionHandler.java`
  - 增加队列满、同步超时等错误码映射。

### 测试
- `src/test/java/com/example/agent/gateway/controller/TaskControllerTest.java`
  - 覆盖同步与异步模式的返回语义与超时行为。
- 新增：`src/test/java/com/example/agent/orchestrator/TaskExecutionServiceTest.java`
  - 覆盖队列满、超时、重试与幂等复用等场景。

### 规格与示例文档
- `specs/001-agent-core-spec/spec.md`
  - 补充执行模式语义与同步等待超时规则。
- `specs/001-agent-core-spec/contracts/openapi.yaml`
  - 补充请求参数与响应字段定义。
- `specs/001-agent-core-spec/quickstart.md`
  - 更新示例，展示同步与异步的调用方式。

## 备注
- 若不引入持久化队列，仅使用内存队列，需要在文档中明确单机失效风险与恢复策略。
- 同步等待必须采用非阻塞方式实现，避免在 `WebFlux` 事件循环线程上 `block` 或 `get`。