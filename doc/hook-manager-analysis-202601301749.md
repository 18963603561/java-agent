# HookManager 分析报告

## 1. 类职责定位
`HookManager` 是 Hook 机制的统一调度器，负责在“步骤”与“工具调用”的关键节点触发 Hook，并完成：
- Hook 执行顺序控制与超时控制。
- Hook 决策合并与拦截逻辑。
- 执行记录留存与审计。
- 事件与指标的对外发布。

核心目标是把“可插拔的执行前后检查/记录能力”集中收敛到一个组件，避免业务流程中散落的前置检查逻辑。

## 2. 触发入口与调用位置
`HookManager` 的调用入口集中在运行时与网关层：
- `AgentRuntime`：在每个步骤执行前后触发 `preStep/postStep`，在工具执行前后触发 `preTool/postTool`。
- `ReactLoopService`：在 ReAct 的工具调用前后触发 `preTool/postTool`。
- `McpController`：直接调用 MCP 工具时触发 `preTool/postTool`。

对应调用位置：
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
- `src/main/java/com/example/agent/runtime/ReactLoopService.java`
- `src/main/java/com/example/agent/gateway/controller/McpController.java`

## 3. 核心流程（以 preTool 为例）
1) 入口方法 `preTool(...)` 被调用。
2) 构造 Hook 上下文（`HookContext`），包含：
   - `hookType`（PRE_TOOL）
   - `toolName`
   - `stepId`（来自 `StepRecord`）
   - `tenantId`
   - `payload`（步骤与结果摘要）
3) 读取 Hook 配置（顺序、超时）并排序执行。
4) 依次执行 HookHandler：
   - 若超时：根据 `HookTimeoutPolicy` 生成放行/阻断决策。
   - 若异常：同样根据策略生成放行/阻断决策。
5) 记录执行结果（HookRecord，保留最近 1000 条）。
6) 如果是 `preTool` 且返回 `allowed=false`：
   - 记录指标 `hook.block.count`。
   - 抛出 `ErrorCodeException`，中断工具调用。
7) 发布 `HOOK_PRE_TOOL` 事件，供事件流消费。

## 4. 各 Hook 类型的行为差异
- `preTool`：**强制阻断**。若 Hook 决策拒绝，直接抛异常，工具调用不会继续。
- `postTool`：仅记录与发布事件，不阻断流程。
- `preStep`：仅记录与发布事件，不阻断步骤执行。
- `postStep`：仅记录与发布事件，不阻断流程。

结论：当前只有 `preTool` 具备硬阻断能力，其他 Hook 仅用于观测或审计。

## 5. Hook 执行顺序与超时控制
### 5.1 顺序
`HookHandler` 的执行顺序由 `HookProperties.HookConfig.order` 决定：
- 数值越小越先执行。
- 若 order 相同，按 `hookId` 字典序稳定排序。

### 5.2 超时
- 配置项 `timeoutMs` > 0 时，使用线程池执行并超时控制。
- 超时后根据 `HookTimeoutPolicy` 决定放行或阻断。
- 默认策略为 `FAIL_OPEN`（超时放行）。

## 6. 决策与记录
### 6.1 决策对象
`HookDecision` 包含：
- `allowed`：是否放行
- `reason`：拒绝或放行原因
- `metadata`：扩展信息

### 6.2 执行记录
`HookRecord` 保存以下关键信息：
- hookId / hookType / toolName / stepId / tenantId
- allowed / reason / timeout / durationMs
- result（如 `ALLOW` / `BLOCK` / `TIMEOUT_ALLOW`）

记录保存在内存队列中，容量上限为 1000，超过则丢弃最早记录。

## 7. 事件与指标
### 7.1 事件
Hook 触发后会发布事件：
- `HOOK_PRE_TOOL`
- `HOOK_POST_TOOL`
- `HOOK_PRE_STEP`
- `HOOK_POST_STEP`

事件由 `ApplicationEventPublisher` 发送，序列号通过 `EventStreamService` 生成。

### 7.2 指标
当 `preTool` 决策拒绝时，指标 `hook.block.count` 自增，用于监控阻断频次。

## 8. 主要依赖关系
- `HookProperties`：开关、超时策略、Hook 配置。
- `HookHandler`：具体 Hook 实现，业务自定义。
- `EventStreamService` / `ApplicationEventPublisher`：事件发布。
- `MetricsPublisher`：指标记录。

## 9. 配置与扩展建议
### 9.1 配置入口
配置前缀为 `agent.hook`，关键项：
- `enabled`：全局开关
- `timeoutPolicy`：超时策略（`FAIL_OPEN` / `FAIL_CLOSED`）
- `hooks`：每个 Hook 的 `hookId/order/timeoutMs`

### 9.2 扩展方式
- 新增 Hook：实现 `HookHandler` 并注册为 Spring Bean。
- 通过 `hookId` 与配置进行绑定。
- 若需要“步骤级阻断”，建议在 `preStep` 中显式加阻断逻辑或扩展 HookManager 行为。

## 10. 注意事项
- `preTool` 的阻断会直接影响业务执行，应确保 HookHandler 的稳定性。
- 超时策略默认放行，若场景需要强约束，应切换为 `FAIL_CLOSED`。
- Hook 执行使用线程池，异常被捕获并转换为决策，不会向上抛出。

