# `orchestration` 包 `P2` 级改造任务清单

## 1. 任务来源与目标

- 来源文档：`doc/orchestration-package-review-202602101158.md`
- 对应章节：`5. 优先级改造建议 -> P2（中期优化）`
- 目标：将 P2 结论拆分为可直接落地、可验收、可回归的改造任务，重点解决列表分页扩展性、执行器生命周期、占位能力收敛与状态语义统一。

## 2. P2 改造原则

- 原则一：优先解决规模化风险，避免在编排层进行全量扫描与内存分页。
- 原则二：所有长生命周期组件必须具备显式关闭能力，避免线程与资源泄漏。
- 原则三：新项目不保留占位代码，未形成价值闭环的类应直接删除或一次性补齐。
- 原则四：任务状态在跨层传递时保持受控语义，禁止任意字符串扩散。

## 3. P2 具体改造任务

### 任务一：任务列表分页下推仓储层

- 任务编号：`ORCH-P2-PAGING-01`
- 优先级：`P2`
- 问题指向：当前 `TaskOrchestrator#listTasks` 对租户任务做全量拉取后再内存分页，数据规模增大时会放大延迟与内存占用。
- 改造动作：
  - 新增仓储分页查询契约对象，明确 `status`、`cursor`、`size`、`sort` 语义。
  - `TaskRepository` 增加分页查询方法，替代全量 `listByTenant` 查询路径。
  - `JdbcTaskRepository` 落地 SQL 分页（按 `updated_at DESC, task_id DESC` 稳定排序），并输出下一页游标。
  - `InMemoryTaskRepository` 保留等价分页语义，保障测试与本地模式一致性。
  - `TaskOrchestrator` 改为直接消费仓储分页结果，不再二次全量排序切片。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskRepository.java`
  - `src/main/java/com/example/agent/orchestration/task/JdbcTaskRepository.java`
  - `src/main/java/com/example/agent/orchestration/task/InMemoryTaskRepository.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java`
  - `src/main/java/com/example/agent/orchestration/task/contract/TaskListView.java`
- 验收标准：
  - 列表查询链路不再出现“全量查 + 内存分页”逻辑。
  - 分页游标可稳定翻页且不重复、不漏数。
  - 全量测试通过。

### 任务二：统一任务列表游标协议

- 任务编号：`ORCH-P2-PAGING-02`
- 优先级：`P2`
- 问题指向：当前 `cursor` 仅使用 `taskId`，排序与游标键不完全对齐，存在翻页边界不稳定风险。
- 改造动作：
  - 定义统一游标编码协议（建议：`updatedAtEpochMillis + taskId` 组合）。
  - 在 `TaskHttpMapper` 与编排契约层保持游标透明透传，不引入 HTTP 语义泄漏。
  - 在仓储层实现游标解析与 SQL 条件拼装，异常游标返回明确错误码。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java`
  - `src/main/java/com/example/agent/orchestration/task/JdbcTaskRepository.java`
  - `src/main/java/com/example/agent/api/http/mapper/TaskHttpMapper.java`
- 验收标准：
  - 游标与排序键完全一致。
  - 非法游标可被识别并返回可诊断错误。
  - 相关分页测试覆盖边界场景。

### 任务三：为 `TaskExecutionService` 增加生命周期关闭逻辑

- 任务编号：`ORCH-P2-LIFECYCLE-01`
- 优先级：`P2`
- 问题指向：执行器只初始化不关闭，进程退出或容器重启时存在线程泄漏与任务悬挂风险。
- 改造动作：
  - 为 `TaskExecutionService` 增加 `@PreDestroy` 或 `DisposableBean` 关闭实现。
  - 关闭流程包含：停止接收新任务、等待在途任务、超时后强制中断。
  - 在关闭路径增加 `info/warn/error` 日志，记录活跃线程与队列残留。
  - 增加单测覆盖正常关闭与超时强制关闭分支。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskExecutionService.java`
  - `src/test/java/com/example/agent/orchestrator/TaskExecutionServiceTest.java`（若不存在则新增）
- 验收标准：
  - 服务销毁时线程池可被正确回收。
  - 关闭分支具备完整日志可观测性。
  - 全量测试通过。

### 任务四：清理或补全占位对象（`AgentGraphExecutor`、`Handoff*`）

- 任务编号：`ORCH-P2-CLEANUP-01`
- 优先级：`P2`
- 问题指向：多智能体包内仍存在功能闭环不足或仅日志化执行的对象，增加维护成本与误用风险。
- 改造动作：
  - 逐一评估 `AgentGraphExecutor` 与 `HandoffService/HandoffRequest/HandoffResult/HandoffRecord` 的调用链。
  - 无真实调用价值的占位对象直接删除，并同步清理 Bean 注入与测试依赖。
  - 需要保留的对象一次性补齐最小可用实现与异常语义，不保留“空执行”路径。
  - 增加架构守护测试，防止后续引入新的占位空实现类。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/multiagent/*`
  - `src/test/java/com/example/agent/multiagent/*`
  - `src/test/java/com/example/agent/orchestration/*ArchitectureGuardTest.java`
- 验收标准：
  - 多智能体包无“仅记录日志不执行业务”的占位实现。
  - 清理后所有相关测试通过。

### 任务五：任务状态语义全链路收敛

- 任务编号：`ORCH-P2-STATUS-01`
- 优先级：`P2`
- 问题指向：虽然已引入 `TaskStatus`，但状态在查询参数、仓储过滤、事件载荷中仍可能以自由字符串扩散。
- 改造动作：
  - 将列表查询 `status` 从自由字符串收敛为 `TaskStatus` 可解析集合。
  - 仓储层过滤条件统一使用受控状态值，非法状态快速失败。
  - 事件与响应转换统一走 `TaskStatus`，避免魔法字符串散落。
  - 数据库层补充状态约束（如 `CHECK`）或等价校验逻辑。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskStatus.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java`
  - `src/main/java/com/example/agent/orchestration/task/JdbcTaskRepository.java`
  - `src/main/java/com/example/agent/api/http/mapper/TaskHttpMapper.java`
  - `src/main/resources/db/migration/*`（如存在）
- 验收标准：
  - 任务状态写入、查询、过滤、事件输出全部走受控状态语义。
  - 非法状态输入可被统一拦截并返回明确错误。

### 任务六：补齐 P2 范围回归与守护测试

- 任务编号：`ORCH-P2-TEST-01`
- 优先级：`P2`
- 改造动作：
  - 增加仓储分页与游标集成测试。
  - 增加执行器关闭行为测试。
  - 增加占位对象清理后的架构守护测试。
  - 更新现有 `TaskOrchestratorTest` 与 `TaskControllerTest` 的分页断言。
- 涉及文件：
  - `src/test/java/com/example/agent/orchestrator/*`
  - `src/test/java/com/example/agent/gateway/controller/*`
  - `src/test/java/com/example/agent/orchestration/*`
- 验收标准：
  - P2 相关新增分支均有测试覆盖。
  - 全量 `mvn test` 通过且无新增不稳定用例。

## 4. 建议实施顺序

- 第一步：`ORCH-P2-PAGING-01` + `ORCH-P2-PAGING-02`，先解决规模化风险。
- 第二步：`ORCH-P2-LIFECYCLE-01`，补齐执行器退出闭环。
- 第三步：`ORCH-P2-CLEANUP-01`，清理占位实现并收敛多智能体结构。
- 第四步：`ORCH-P2-STATUS-01`，完成状态语义全链路统一。
- 第五步：`ORCH-P2-TEST-01`，回归与守护收口。

## 5. `P2` 完成定义（DoD）

- 任务列表分页完全下推仓储层，编排层不再全量扫描。
- `TaskExecutionService` 具备可验证的关闭生命周期。
- 多智能体包内占位对象完成“删除或补齐”，不存在无业务价值空实现。
- 任务状态在接口、编排、仓储、事件链路实现受控一致。
- 全量测试通过，新增架构守护可防止回退。

## 6. 落地进度（2026-02-10）

- `ORCH-P2-PAGING-01`：已完成。
- `ORCH-P2-PAGING-02`：已完成。
- `ORCH-P2-LIFECYCLE-01`：已完成。
- `ORCH-P2-CLEANUP-01`：已完成。
- `ORCH-P2-STATUS-01`：已完成。
- `ORCH-P2-TEST-01`：已完成。

- 全量测试结果：`mvn test -DskipTests=false` 通过（`Tests run: 567, Failures: 0, Errors: 0, Skipped: 0`）。
