# orchestration 包设计复评报告（复盘版）

## 1. 评审范围

- 评审目录：`src/main/java/com/example/agent/orchestration`
- 评审目标：判断当前包设计合理性、识别坏味道、输出等级与治理优先级。
- 评审基线：在 `P2` 改造完成后重新评估，不考虑历史兼容负担。

## 2. 总体结论

### 2.1 综合等级

- **综合设计等级：`B`（结构可用，仍有关键治理项）**
- **运行风险等级：`中`**
- **演进健康度：`中上`**

### 2.2 分包等级

| 子包 | 等级 | 结论摘要 |
|---|---|---|
| `orchestration.task` | `B+` | 领域边界较清晰，分页与状态语义已明显提升，但仍有“门面偏大”和“结构化结果不足”问题。 |
| `orchestration.workflow` | `C+` | 路由层职责简单，但出现跨层 DTO 反向依赖，破坏编排域纯度。 |
| `orchestration.multiagent` | `C` | 能力可运行，但 `MultiAgentCoordinator` 过大且职责混合，属于明显可维护性风险点。 |

## 3. 已确认的正向改进

1. 任务列表分页已下推仓储层，编排层不再全量扫描切页。
   - 证据：`src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java:143`
   - 证据：`src/main/java/com/example/agent/orchestration/task/TaskRepository.java:18`

2. 游标协议已统一为 `updatedAtEpochMillis|taskId`，与排序键一致。
   - 证据：`src/main/java/com/example/agent/orchestration/task/TaskListCursorCodec.java:11`
   - 证据：`src/main/java/com/example/agent/orchestration/task/JdbcTaskRepository.java:150`

3. 执行器生命周期已补齐关闭逻辑，线程池可回收。
   - 证据：`src/main/java/com/example/agent/orchestration/task/TaskExecutionService.java:83`

4. 多智能体占位对象已清理，并存在守护测试防回流。
   - 证据：`src/test/java/com/example/agent/orchestration/OrchestrationArchitectureGuardTest.java:49`

## 4. 坏味道与分级清单

## 4.1 `P1`（应优先治理）

### 问题一：`workflow` 层反向依赖 `api.http` DTO（分层泄漏）

- 现象：`DefaultWorkflowRouter` 直接依赖 `TaskRequest`，把接口传输模型带入编排内部。
- 证据：`src/main/java/com/example/agent/orchestration/workflow/DefaultWorkflowRouter.java:3`
- 影响：
  - 编排域被 Web 层模型耦合，后续替换接口协议或新增调用入口时改动面扩大。
  - 破坏“编排契约独立于传输协议”的既有方向。
- 评级：`P1 / 高`

### 问题二：`MultiAgentCoordinator` 过大且职责混合（God Class）

- 现象：同一类同时承担输入摘要、提示词组装、LLM 调用、输出修复、JSON 解析、事件发布、追踪记录。
- 证据：`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java:37`
- 规模：约 `474` 行。
- 影响：
  - 改动冲突高，单测编排复杂，回归成本高。
  - 任何一个分支逻辑变化都可能影响协调主路径稳定性。
- 评级：`P1 / 高`

## 4.2 `P2`（中期优化）

### 问题三：`TaskOrchestrator` 仍偏大，提交/查询/执行异常收敛耦合在同类

- 证据：`src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java:34`
- 规模：约 `379` 行。
- 影响：可读性和变更隔离能力一般，新增提交策略时易增加圈复杂度。
- 评级：`P2 / 中`

### 问题四：同步等待策略仍为短轮询 + 魔法常量

- 现象：轮询窗口 `120ms`、间隔 `10ms` 为硬编码。
- 证据：`src/main/java/com/example/agent/orchestration/task/TaskSyncWaitService.java:139`
- 影响：
  - 参数不可配置，性能调优和场景适配能力不足。
  - 高并发时存在不必要数据库读放大。
- 评级：`P2 / 中`

### 问题五：任务状态在持久化模型层仍为字符串字段

- 现象：`TaskRecord.status` 与 `TaskStatusView.status` 仍为 `String`，通过外围约束保持合法。
- 证据：`src/main/java/com/example/agent/orchestration/task/TaskRecord.java:12`
- 证据：`src/main/java/com/example/agent/orchestration/task/contract/TaskStatusView.java:28`
- 影响：类型系统无法在编译期兜底，后续扩展状态机时容易出现漏改点。
- 评级：`P2 / 中`

### 问题六：结果载荷广泛使用 `Map<String, Object>`，语义边界弱

- 证据：`src/main/java/com/example/agent/orchestration/task/TaskLifecycleService.java:89`
- 证据：`src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java:77`
- 影响：字段契约依赖约定，重构安全性偏弱，序列化边界容易回归。
- 评级：`P2 / 中`

## 4.3 `P3`（持续治理）

### 问题七：部分异常语义仍偏通用，领域错误码颗粒度可继续提升

- 现象：例如状态非法校验目前通过 `IllegalArgumentException("invalid_task_status")` 抛出。
- 证据：`src/main/java/com/example/agent/orchestration/task/TaskStatus.java:67`
- 影响：可诊断性可用，但对业务定位与告警聚合仍不够细。
- 评级：`P3 / 低`

## 5. 设计合理性判断

从“是否可支撑新项目持续演进”角度看，当前 `orchestration` 包已经具备可用骨架：

1. `task` 子域已形成“编排门面 + 生命周期 + 幂等 + 等待 + 事件 + 仓储”的基本分工。
2. 分页与游标协议已走向正确方向，线上扩展性风险显著下降。
3. 但 `workflow` 与 `multiagent` 仍有明显结构债务，主要集中在分层纯度与类职责过载。

综合判断：**结构总体合理，但尚未达到“高内聚、低耦合、可长期稳定演进”的优秀态。**

## 6. 建议的下一轮治理顺序

1. 先做 `P1`：
   - 清理 `DefaultWorkflowRouter` 对 `api.http.dto.TaskRequest` 的依赖，改为内部运行时命令对象。
   - 拆分 `MultiAgentCoordinator` 至最少 4 个协作组件：输入摘要、提示词构建、响应解析修复、事件发射。

2. 再做 `P2`：
   - 拆薄 `TaskOrchestrator` 的执行分支与异常翻译分支。
   - 将同步等待常量参数化，并评估事件驱动替代短轮询。
   - 逐步引入结构化结果对象，减少动态 `Map` 扩散。

3. 最后做 `P3`：
   - 精细化任务域错误码，统一错误码字典与告警标签。

## 7. 复评结论摘要

- 本轮复评结论与等级：`B`。
- 当前不存在 `P0` 阻断问题。
- 存在 `P1` 两项（分层泄漏、多智能体协调器过载），建议作为下一阶段首批改造目标。

