# multiagent 包设计说明

## 1. 包定位
- `multiagent` 是多智能体编排子域，负责角色规划、执行路由、协作运行与可观测性事件输出。
- 该包只承载多智能体领域能力，不承载 HTTP 协议解析职责。

## 2. 分层边界
- `multiagent.usecase`：应用层用例编排，负责执行路由与结果聚合。
- `multiagent.model`：领域结果模型与状态码，承载跨流程数据契约。
- `multiagent.role`：角色解析子域，拆分解析、修复、回退、诊断职责。
- `multiagent.dag`：DAG 执行子域，遵循 `application/domain/infrastructure` 分层。
- `multiagent.handoff`：Supervisor 协作与交接能力。
- `multiagent.supervisor`：Supervisor 策略与协调能力。
- `multiagent.event`：事件发布支持组件。
- `multiagent.config`：多智能体装配与生产防护配置。

## 3. 依赖方向不变式
- `application` 可以依赖 `domain.port`，禁止依赖 `infrastructure` 具体实现。
- `domain` 只能依赖领域模型与端口接口，禁止反向依赖实现层。
- `infrastructure` 只实现端口与外部适配，禁止承载业务编排。
- 包内组件禁止依赖 `api.http.controller`。

## 4. 新增类放置规则
- 与角色 JSON 解析相关的能力放到 `role`。
- 与 DAG 计划构建相关的能力放到 `dag.application.planner`。
- 与 DAG 持久化接口相关的能力放到 `dag.domain.port`。
- 与 InMemory/Redis/JDBC 等实现相关的能力放到 `dag.infrastructure`。
- 与流程入口编排相关但不含底层实现的能力放到 `usecase`。

## 5. 禁止事项
- 禁止在 `domain` 与 `usecase` 中直接 `new InMemory*` 或 `new Redis*`。
- 禁止在同一类内混合“解析 + 修复 + 回退 + 诊断”多职责实现。
- 禁止新增历史兼容分支掩盖结构问题，优先直接按新分层落位。

## 6. 评审检查清单
- 检查新增类是否放在正确子域与层级（`usecase/role/dag.domain/dag.infrastructure`）。
- 检查 `application/domain` 是否仅依赖端口而非具体实现。
- 检查关键流程是否补齐日志（开始、关键分支、结束、异常）。
- 检查事件与指标是否使用 `observability` 字典常量，禁止硬编码。
- 检查新增方法中的关键循环、分支、异常是否有中文注释说明。
- 检查是否补充对应单测或守护测试，避免结构回退。

## 7. 反例说明
- 反例一：在 `DagNodeExecutionService` 内直接写 `"dag.dispatch.nack"` 指标字符串。
- 反例二：在 `dag.domain` 新增类中引用 `dag.infrastructure.*` 具体实现。
- 反例三：在 `MultiAgentRoleResolver` 中重新混入解析、修复、回退全部细节实现。
