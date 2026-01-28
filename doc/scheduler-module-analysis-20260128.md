# 调度模块（`scheduler`）分析

## 分析范围
- 目录：`src/main/java/com/example/agent/scheduler`
- 关联入口：`src/main/java/com/example/agent/gateway/controller/ScheduleController.java`
- 事件类型：`SCHEDULE_TRIGGERED`

## 当前主要功能
1. 调度定义与持久化：`ScheduleSpec` 由 `ScheduleRepository` 保存到 `scheduled_tasks`（或内存）。
2. 调度管理：`ScheduleManager` 负责创建、更新、暂停、恢复、取消、删除与分页。
3. 定时触发：`ScheduleEngine` 使用 `ThreadPoolTaskScheduler` 和 `CronTrigger` 注册定时器。
4. 触发记录与事件：触发时写入 `ScheduleExecutionRecord` 并发布 `SCHEDULE_TRIGGERED` 事件。

## 运行流程（简述）
1. 创建或更新调度：`ScheduleController` -> `ScheduleManager` -> `ScheduleRepository` -> `ScheduleEngine.register`
2. 定时触发：`ScheduleEngine` 定时回调 -> 写 `ScheduleExecutionRecord` -> 发布 `SCHEDULE_TRIGGERED` 事件
3. 暂停、恢复、取消：`ScheduleManager` 调整状态并注销定时器；`ScheduleController` 同步调用 `ExecutionControlService` 以 `schedule-<id>` 维度标记状态

## 对“离线或定时查询任务”的判断
- 当前实现只负责“定时触发事件”和“记录触发历史”，并未直接执行离线或查询任务。
- 证据：`ScheduleSpec` 没有任务负载字段；`ScheduleEngine` 不调用任务编排入口；仓库内未发现对 `SCHEDULE_TRIGGERED` 的消费链路。

## 最初功能意图（规格来源）
- 规格描述为“定时执行任务并记录执行历史与成本”。`specs/001-agent-core-spec/spec.md`
- 数据模型包含 `maxRuns`、`minInterval`、`budgetLimit`、`nextRunAt` 等字段。`specs/001-agent-core-spec/data-model.md`
- 规格接口包含 `ScheduleManager`、`ScheduleRepository`、`ScheduleExecutionRepository` 与调度接口。`specs/001-agent-core-spec/spec.md`

## 当前实现与规格对比
- 未落地字段：`ScheduleSpec` 缺少 `maxRuns`、`minInterval`、`budgetLimit`、`nextRunAt`。`src/main/java/com/example/agent/scheduler/ScheduleSpec.java`
- 返回结构缺口：`ScheduleResponse` 无 `nextRunAt`，`SchedulePage` 无 `total`。`src/main/java/com/example/agent/scheduler/ScheduleResponse.java` `src/main/java/com/example/agent/scheduler/SchedulePage.java`
- 执行记录不完整：仅记录 `TRIGGERED` 与 `startedAt`，`endedAt`、`costUsd`、`tokenUsage` 未赋值。`src/main/java/com/example/agent/scheduler/ScheduleEngine.java`
- 观测指标缺口：仅有 `schedule.run.count`，规格中还包含 `schedule.run.fail.count`。`src/main/java/com/example/agent/scheduler/ScheduleEngine.java` `specs/001-agent-core-spec/spec.md`
- 触发事件无消费：未发现 `SCHEDULE_TRIGGERED` 的业务消费链路，导致“定时执行任务”尚未落地。

## 结论
- 目前模块方向未偏离“定时任务调度”的大方向，但更像“调度框架与触发事件”的最小实现，距离“定时执行任务并记录成本”的规格目标仍有明显缺口。
- 若需求是“离线或定时查询任务”，需要补充“触发事件 -> 任务执行”的链路及任务负载建模。