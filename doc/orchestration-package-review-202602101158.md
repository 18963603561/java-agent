# `orchestration` 包设计与代码坏味道评审报告

## 1. 评审范围

- 评审目录：`src/main/java/com/example/agent/orchestration`
- 覆盖子包：
  - `orchestration.task`
  - `orchestration.multiagent`
  - `orchestration.workflow`
- 评审方式：静态结构分析 + 代码阅读（未执行运行时压测）。

## 2. 总体结论

### 2.1 总体等级

- **总体等级：C+（可用，但结构性债务较重）**

### 2.2 分项等级

- 分层清晰度：**B-**
  - 已按 `task / multiagent / workflow` 做语义分包，方向基本合理。
  - 但 `orchestration` 直接依赖 `api.http.dto`，出现跨层耦合。
- 可维护性：**C-**
  - `TaskOrchestrator` 体量过大（`932` 行），承担过多职责。
  - `MultiAgentCoordinator` 体量偏大（`418` 行），解析、修复、事件、提示词拼装耦合在一起。
- 可靠性：**C**
  - 存在多处“吞异常后继续返回成功形态”的实现，容易掩盖故障。
  - 多智能体提示词与解析契约不一致，影响结果稳定性。
- 可观测性：**B**
  - 关键链路有较完整日志与指标埋点。
  - 但部分异常被静默处理，削弱排障可见性。
- 可扩展性：**C**
  - 存在明显占位实现与未消费对象，扩展时容易误入不稳定路径。

## 3. 包设计合理性分析

### 3.1 合理点

- 分包语义基本正确：
  - `task` 负责任务提交/查询/持久化。
  - `workflow` 负责运行时路由。
  - `multiagent` 负责多智能体协同。
- 持久化实现具备切换能力：
  - `TaskRepository` 提供抽象。
  - `InMemoryTaskRepository` 与 `JdbcTaskRepository` 基于配置切换。
- 任务链路可观测性有基础：
  - 任务接受、启动、异常、完成事件已串联。
  - 已接入 `MetricsPublisher` 与 `TracingPublisher`。

### 3.2 主要结构问题

- 分层反向依赖：
  - `TaskSubmissionService` 直接依赖 `api.http.dto`（`src/main/java/com/example/agent/orchestration/task/TaskSubmissionService.java:4`）。
  - `TaskQueryService` 直接依赖 `api.http.dto`（`src/main/java/com/example/agent/orchestration/task/TaskQueryService.java:4`）。
  - 这使编排层与传输层绑定，不利于后续接入非 HTTP 通道。
- 任务编排器职责过载：
  - `TaskOrchestrator` 同时处理：幂等、提交、同步等待、事件发布、结果封装、分页、追踪、异常转换。
  - 典型入口与关键逻辑分散在超长文件中（`src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java:198`）。
- 多智能体协调器职责过载：
  - 同时承担输入摘要、提示词模板、模型调用、JSON 修复、追踪回写、事件发布。
  - 建议拆分为 `PromptBuilder`、`RoleParser`、`RoleFallbackPolicy`、`TeamEventPublisher`。

## 4. 坏味道清单（分级）

### 4.1 高风险（High）

#### H1：巨石类（God Class）

- 等级：**High**
- 位置：`src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java:56`
- 现象：单类 `932` 行，覆盖提交、查询、幂等、并发门控、状态机、事件构建、链路追踪、异常转换等多个边界。
- 风险：改动一个需求容易波及多个职责，回归成本高，测试隔离困难。

#### H2：提示词契约与解析契约不一致

- 等级：**High**
- 位置：
  - 提示词要求字段：`team[*].role`、`team[*].responsibility`（`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java:251`）
  - 解析器读取字段：`roleId`、`name`、`modelId`、`description`（`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java:282`）
- 风险：模型即便按提示词正确输出，也可能被解析为默认 `agent` 角色，导致协作质量不稳定。

#### H3：多处异常被吞，失败语义不清

- 等级：**High**
- 位置：
  - `save` 捕获数据库异常后仍返回入参记录（`src/main/java/com/example/agent/orchestration/task/JdbcTaskRepository.java:73`）
  - `parseRoles` 捕获异常直接返回空集合（`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java:289`）
  - `readJson` 解析失败直接返回 `null`（`src/main/java/com/example/agent/orchestration/task/JdbcTaskRepository.java:204`）
- 风险：调用方难以区分“真实无数据”与“系统故障”，容易出现假成功和隐性数据损坏。

#### H4：多智能体关键文案疑似乱码污染

- 等级：**High**
- 位置：`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java:229`
- 现象：核心提示词文本呈现为不可读字符序列（如 `浣犳槸澶氭櫤鑳戒綋...`）。
- 风险：直接影响模型理解与输出稳定性，属于功能正确性风险。

### 4.2 中风险（Medium）

#### M1：内存分页与二次排序

- 等级：**Medium**
- 位置：`src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java:310`
- 现象：先全量加载租户任务，再在内存中过滤、排序、分页。
- 风险：数据量增大时会带来显著内存与响应时延压力。

#### M2：线程池生命周期未显式关闭

- 等级：**Medium**
- 位置：`src/main/java/com/example/agent/orchestration/task/TaskExecutionService.java:57`
- 现象：有 `@PostConstruct` 初始化，但无 `@PreDestroy` 关闭。
- 风险：应用优雅停机、测试上下文重建时存在线程泄漏和资源回收不完整风险。

#### M3：占位服务和未消费对象较多

- 等级：**Medium**
- 位置：
  - `SupervisorCoordinator` 仅日志 + 调用兼容方法（`src/main/java/com/example/agent/orchestration/multiagent/SupervisorCoordinator.java:21`）
  - `MultiAgentCoordinator.coordinate(String taskId)` 仅日志（`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java:214`）
  - `HandoffService` 创建 `HandoffRecord` 但未落库（`src/main/java/com/example/agent/orchestration/multiagent/HandoffService.java:24`）
- 风险：读者难以判断哪些是生产路径，增加维护噪声。

#### M4：状态机使用硬编码字符串

- 等级：**Medium**
- 位置：`src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java:380`
- 现象：`SUBMITTED / RUNNING / COMPLETED / FAILED` 在多个方法中以字符串散落。
- 风险：拼写错误难在编译期发现，状态扩展时改动面大。

### 4.3 低风险（Low）

#### L1：内存仓储状态过滤边界不严谨

- 等级：**Low**
- 位置：`src/main/java/com/example/agent/orchestration/task/InMemoryTaskRepository.java:63`
- 现象：当 `status` 传入非空且记录状态为 `null` 时，该记录不会被过滤。
- 风险：查询结果可能混入状态不符合预期的数据。

## 5. 优先级改造建议

### P0（立即处理）

- 修复 `MultiAgentCoordinator` 的提示词乱码与字段契约错位。
- 为仓储与解析关键路径建立“失败可见”的异常语义：
  - 至少区分“未命中”与“系统故障”。
  - 禁止吞异常后继续返回“成功形态”。

### P1（近期处理）

- 拆分 `TaskOrchestrator`：
  - `TaskLifecycleService`（状态迁移）
  - `IdempotencyService`（本地锁 + Redis）
  - `TaskSyncWaitService`（同步等待策略）
  - `TaskEventPublisher`（事件构建与发布）
- 将编排层接口从 `api.http.dto` 解耦到领域请求/响应对象。

### P2（中期优化）

- 将任务列表分页下推到仓储层 SQL。
- 为 `TaskExecutionService` 增加生命周期关闭逻辑。
- 清理或补全占位对象（`SupervisorCoordinator`、`AgentGraphExecutor`、`Handoff*`）。
- 统一任务状态为枚举或受控常量。

## 6. 结论摘要

- 当前 `orchestration` 包具备基本可运行骨架和可观测性基础。
- 主要问题不在“是否能跑”，而在“长期可维护与故障可诊断”。
- 建议按 `P0 -> P1 -> P2` 逐步收敛结构债务，优先保证多智能体链路和持久化异常语义的确定性。
