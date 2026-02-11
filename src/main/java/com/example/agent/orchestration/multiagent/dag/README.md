# dag 子域设计说明

## 1. 子域职责
- `dag` 子域负责多智能体 DAG 计划构建、Actor 执行、审计、诊断与回放。
- 子域通过端口隔离存储实现，支持内存与持久化实现替换。

## 2. 分层结构
- `dag.application`：应用编排层（当前核心为 `planner`）。
- `dag.domain`：领域层，包含模型与端口：
  - `dag.domain.model`：`DagPlan`、`DagNode` 等核心模型。
  - `dag.domain.port`：`DagAuditRepository`、`DagRuntimeStateRepository` 等端口。
- `dag.infrastructure`：基础设施实现层：
  - `dag.infrastructure.audit`：审计仓储实现。
  - `dag.infrastructure.state`：运行态与去重仓储实现。
  - `dag.infrastructure.recovery`：死信仓储实现。
- `dag.actor`：运行时引擎与控制面，依赖领域端口，不依赖具体实现。
- `dag.diagnosis`：诊断报告生成。
- `dag.replay`：审计回放能力。

## 3. 关键不变式
- `dag.actor` 与 `dag.application` 只依赖 `dag.domain.port`，不依赖 `dag.infrastructure` 具体类。
- `dag.domain` 禁止依赖 `dag.infrastructure`。
- `dag.infrastructure` 禁止依赖 `multiagent.usecase`。
- 所有外部调用失败必须记录包含 `workflowId`、`dagRunId`、`nodeId` 的日志上下文。

## 4. 开发约定
- 新增仓储接口优先定义在 `dag.domain.port`。
- 新增仓储实现统一落在 `dag.infrastructure.*`。
- 新增 DAG 编排逻辑优先落在 `dag.application` 或 `dag.actor.runtime`，避免 `DagActorRuntime` 再次膨胀。
- 回归测试必须包含：计划构建、运行执行、控制命令、诊断与回放链路。

## 5. 评审检查清单
- 检查 `dag.domain` 是否只保留模型与端口，不依赖基础设施实现。
- 检查 `dag.infrastructure` 是否只做实现，不引入业务编排决策。
- 检查 `dag.actor` 关键路径是否完整记录 `workflowId/dagRunId/nodeId`。
- 检查审计、诊断、回放字段是否与事件载荷键保持一致。
- 检查新增指标名与标签键是否统一来自 `observability` 字典。

## 6. 反例说明
- 反例一：在 `dag.actor.distributed` 直接硬编码新的指标名字符串。
- 反例二：在 `dag.infrastructure.state` 中引入运行编排逻辑。
- 反例三：在 `dag.actor` 中新增跨层依赖绕过 `dag.domain.port`。
