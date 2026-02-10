# `orchestration` 包 `P1` 级改造任务清单

## 1. 任务来源与目标

- 来源文档：`doc/orchestration-package-review-202602101158.md`
- 对应章节：`5. 优先级改造建议 -> P1（近期处理）`
- 本清单目标：将 `P1` 结论拆解为可直接落地的架构改造任务，重点解决“巨石类拆分”和“编排层与传输层解耦”。

## 2. `P1` 改造原则

- 原则一：按新方案直落，不保留旧接口兼容分支。
- 原则二：优先抽象可复用能力，再让 `TaskOrchestrator` 仅保留编排职责。
- 原则三：强约束依赖方向，禁止 `orchestration` 直接依赖 `api.http.dto`。

## 3. `P1` 任务清单

### 任务一：建立编排层领域契约并移除 `api.http.dto` 依赖

- 任务编号：`ORCH-P1-ARC-01`
- 优先级：`P1`
- 问题指向：`TaskSubmissionService`、`TaskQueryService` 当前直接使用 `api.http.dto`，造成层间耦合。
- 改造动作：
  - 在 `orchestration.task.contract` 新建领域契约对象：
    - `TaskSubmitCommand`
    - `TaskQueryCommand`
    - `TaskSubmissionResult`
    - `TaskStatusView`
    - `TaskListView`
  - 重写 `TaskSubmissionService`、`TaskQueryService` 方法签名，仅使用领域契约。
  - 删除接口层对 `api.http.dto` 的 import。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskSubmissionService.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskQueryService.java`
  - `src/main/java/com/example/agent/orchestration/task/contract/*`
- 验收标准：
  - `orchestration.task` 不再直接引用 `api.http.dto`。
  - 编排层可被非 HTTP 场景复用。

### 任务二：拆分任务状态迁移能力为 `TaskLifecycleService`

- 任务编号：`ORCH-P1-SPLIT-01`
- 优先级：`P1`
- 问题指向：状态迁移、结果写入、最终状态判定散落在 `TaskOrchestrator`。
- 改造动作：
  - 新建 `TaskLifecycleService`，承接：
    - `createTask`
    - `updateTaskStatus`
    - `isTerminalStatus`
    - `buildResultPayload`
  - 统一状态管理入口，避免多处写状态导致漂移。
  - 将状态字符串收敛到 `TaskStatus` 枚举。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskLifecycleService.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskStatus.java`
- 验收标准：
  - 状态迁移逻辑只保留一个服务入口。
  - 不再出现散落的硬编码状态字符串。

### 任务三：拆分幂等能力为 `TaskIdempotencyService`

- 任务编号：`ORCH-P1-SPLIT-02`
- 优先级：`P1`
- 问题指向：本地锁、`Redis` 读写、幂等键规范化都在 `TaskOrchestrator` 内部。
- 改造动作：
  - 新建 `TaskIdempotencyService`，承接：
    - 幂等键归一化
    - 本地并发锁管理
    - `Redis` / 仓储幂等查询
    - 幂等映射写入
  - 输出统一幂等查询结果模型（命中/未命中/异常）。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskIdempotencyService.java`
- 验收标准：
  - `TaskOrchestrator` 不再直接管理 `idempotencyLocks`。
  - 幂等流程可独立单测覆盖并复用。

### 任务四：拆分同步等待能力为 `TaskSyncWaitService`

- 任务编号：`ORCH-P1-SPLIT-03`
- 优先级：`P1`
- 问题指向：同步等待策略（信号量、超时、轮询收敛）耦合在主编排器。
- 改造动作：
  - 新建 `TaskSyncWaitService`，承接：
    - 同步并发控制
    - 超时策略
    - `waitForSyncResult` 轮询收敛
  - 输出统一等待结果模型，编排层只做分支决策。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskSyncWaitService.java`
- 验收标准：
  - `TaskOrchestrator` 中同步等待逻辑显著收敛。
  - 等待超时与并发上限行为可独立测试。

### 任务五：拆分事件发布能力为 `TaskEventPublisher`

- 任务编号：`ORCH-P1-SPLIT-04`
- 优先级：`P1`
- 问题指向：事件构建、追踪信息注入、事件发布分散在 `TaskOrchestrator`。
- 改造动作：
  - 新建 `TaskEventPublisher`，承接：
    - `TASK_ACCEPTED`
    - `WORKFLOW_STARTED`
    - `ERROR_OCCURRED`
    - `WORKFLOW_COMPLETED`
  - 封装统一事件载荷结构与追踪上下文注入。
  - 保留事件序号生成能力在单一组件中。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskEventPublisher.java`
- 验收标准：
  - `TaskOrchestrator` 不再直接构建 `StreamEvent`。
  - 事件结构一致、日志与指标语义一致。

### 任务六：重构 `TaskOrchestrator` 为轻量编排门面

- 任务编号：`ORCH-P1-SPLIT-05`
- 优先级：`P1`
- 问题指向：当前类承担过多细节，修改风险高。
- 改造动作：
  - `TaskOrchestrator` 仅保留流程编排与错误边界转换。
  - 通过构造注入调用 `TaskLifecycleService`、`TaskIdempotencyService`、`TaskSyncWaitService`、`TaskEventPublisher`。
  - 删除已迁移的私有工具方法，保持单一职责。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java`
- 验收标准：
  - `TaskOrchestrator` 行数显著下降（建议控制在 `300` 行以内）。
  - 主流程可读性明显提升，方法语义清晰。

### 任务七：控制层改为 DTO 适配，不侵入编排层

- 任务编号：`ORCH-P1-ADAPTER-01`
- 优先级：`P1`
- 问题指向：当前控制层与编排层边界不清晰，DTO 语义向下泄漏。
- 改造动作：
  - 在 `api.http` 层新增映射器：
    - `TaskHttpMapper`（DTO <-> 领域契约）
  - `TaskController` 仅做鉴权、映射、响应包装，不承载业务规则。
  - 删除控制层对编排内部细节依赖。
- 涉及文件：
  - `src/main/java/com/example/agent/api/http/controller/TaskController.java`
  - `src/main/java/com/example/agent/api/http/mapper/TaskHttpMapper.java`
- 验收标准：
  - 编排层不再感知 HTTP DTO。
  - 控制层改动不影响编排核心实现。

### 任务八：补齐架构守护与回归测试

- 任务编号：`ORCH-P1-TEST-01`
- 优先级：`P1`
- 改造动作：
  - 新增架构守护测试，禁止 `orchestration` import `api.http.dto`。
  - 按新拆分服务补齐单测：
    - `TaskLifecycleServiceTest`
    - `TaskIdempotencyServiceTest`
    - `TaskSyncWaitServiceTest`
    - `TaskEventPublisherTest`
  - 更新 `TaskOrchestratorTest` 为门面流程测试。
- 涉及文件：
  - `src/test/java/com/example/agent/orchestrator/*`
  - `src/test/java/com/example/agent/orchestration/*`
- 验收标准：
  - 新增测试覆盖拆分后的关键边界分支。
  - 全量测试通过且架构守护可稳定防回退。

## 4. 实施顺序建议

- 第一步：先做 `ORCH-P1-ARC-01`，锁定领域契约边界。
- 第二步：按 `Lifecycle -> Idempotency -> SyncWait -> EventPublisher` 顺序拆分能力。
- 第三步：完成 `TaskOrchestrator` 门面化重构。
- 第四步：完成控制层映射重构与架构守护测试。
- 第五步：执行全量回归并清理废弃代码。

## 5. `P1` 完成定义（DoD）

- `orchestration.task` 对 `api.http.dto` 依赖为零。
- `TaskOrchestrator` 从巨石类收敛为轻量门面。
- 任务生命周期、幂等、同步等待、事件发布四类能力均可独立复用。
- 架构守护测试和全量测试通过，且无遗留占位逻辑。
