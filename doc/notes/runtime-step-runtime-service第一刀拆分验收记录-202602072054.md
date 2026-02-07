# runtime StepRuntimeService 第一刀拆分验收记录

## 1. 目标与原则

- 目标：按复核文档推进超大类拆分，优先落地 `StepRuntimeService`。
- 原则：新项目按新方案直接落地，不保留历史兼容分支，不做遗留条件分叉。
- 关注点：通用性、可复用性、职责边界清晰、测试可回归。

## 2. 分阶段实施

### 阶段一：提取步骤结果装配职责

- 新增组件：`StepResultAssembler`
  - 路径：`src/main/java/com/example/agent/runtime/step/StepResultAssembler.java`
  - 职责：集中装配 `StepResult`（meta/raw/rawRef/refs/structured/summary/errors）。
- 调整点：
  - `StepRuntimeService` 删除结果装配相关私有方法。
  - `StepRuntimeService.completeStep(...)` 改为委托 `stepResultAssembler.assemble(...)`。

### 阶段二：提取事件与序列号职责

- 新增组件：`StepRuntimeEventService`
  - 路径：`src/main/java/com/example/agent/runtime/step/StepRuntimeEventService.java`
  - 职责：统一 `nextSequence`、`publish`、`resolveTraceId`。
- 调整点：
  - `StepRuntimeService` 不再直接依赖 `ApplicationEventPublisher/EventStreamService/TracingPublisher`。
  - 序列号分配与事件发布全部下沉到 `StepRuntimeEventService`。

### 阶段三：提取摘要重建与事件载荷工厂

- 新增组件：`StepSummaryRegenerationService`
  - 路径：`src/main/java/com/example/agent/runtime/step/StepSummaryRegenerationService.java`
  - 职责：摘要是否重建判断、摘要重建、摘要指标日志。
- 新增组件：`StepEventPayloadFactory`
  - 路径：`src/main/java/com/example/agent/runtime/step/StepEventPayloadFactory.java`
  - 职责：统一构建 `STEP_STARTED/COMPLETED/FAILED` 事件载荷。
- 调整点：
  - `StepRuntimeService` 删除摘要状态比对与摘要日志组装细节。
  - `StepRuntimeService` 删除内联事件载荷 Map 构建细节。

## 3. 结果量化

- `StepRuntimeService` 行数变化：
  - 拆分前：`604` 行（历史版本）
  - 本轮阶段一后：`367` 行
  - 本轮阶段二后：`323` 行
  - 本轮阶段三后：`232` 行

结论：中枢服务从“重实现类”收敛为“编排入口类”。

## 4. 测试执行与结果

### 4.1 阶段一验证

执行：

`mvn -q "-Dtest=StepRuntimeServiceTest,StepExecutionCoordinatorTest,AgentRuntimeApprovalIntegrationTest" test`

结果：通过。

### 4.2 阶段二验证

执行：

`mvn -q "-Dtest=StepRuntimeServiceTest,StepExecutionCoordinatorTest,AgentRuntimeApprovalIntegrationTest" test`

结果：通过。

### 4.3 阶段三验证

执行：

`mvn -q "-Dtest=StepRuntimeServiceTest,StepExecutionCoordinatorTest,AgentRuntimeApprovalIntegrationTest,StepOutputSummaryBuilderTest" test`

结果：通过。

### 4.4 全流程回归（阶段内全链路）

执行：

`mvn -q "-Dtest=StepRuntimeServiceTest,StepExecutionCoordinatorTest,AgentRuntimeApprovalIntegrationTest,StepOutputSummaryBuilderTest,SummaryDependencyGuardTest,MapKeyAccessGuardTest,RuntimeSourceEncodingGuardTest,RawResultStoreDefaultMethodTest" test`

结果：通过。

### 4.5 全量回归

执行：

`mvn -q test`

结果：通过。

## 5. 验收结论

- `StepRuntimeService` 已完成第一刀拆分，职责边界如下：
  - 编排入口：`StepRuntimeService`
  - 结果装配：`StepResultAssembler`
  - 事件与序列号：`StepRuntimeEventService`
  - 摘要重建：`StepSummaryRegenerationService`
  - 事件载荷构造：`StepEventPayloadFactory`
- 改造满足“可复用 + 可维护 + 可测试”目标，且全流程测试通过。

