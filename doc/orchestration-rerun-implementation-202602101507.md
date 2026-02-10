# orchestration 复评项落地实施记录

## 1. 目标与输入

- 输入文档：`doc/orchestration-package-review-rerun-202602101412.md`
- 执行要求：按复评项分阶段落地，不保留历史兼容分支，完成后执行全量测试。
- 本次范围：覆盖复评中的 `P1`、`P2`、`P3` 对应可落地项。

## 2. 阶段执行结果

### 阶段一：分层纯度与多智能体协调器治理

#### 2.1 `workflow` 去除对 HTTP DTO 的反向依赖

- 新增运行时内部请求模型：`RuntimeTaskRequest`。
- `DefaultWorkflowRouter` 内部统一执行 `TaskSubmitCommand -> RuntimeTaskRequest` 映射。
- `WorkflowRouter` 对外只保留 `route(TaskSubmitCommand, ...)`，避免重载歧义。
- `AgentRuntime` 保留运行时请求入口，不再要求 `workflow` 触达 `api.http` 传输模型。

#### 2.2 `MultiAgentCoordinator` 职责拆分

- 新增并接入四个组件：
  - `MultiAgentInputSummaryBuilder`
  - `MultiAgentPromptBuilder`
  - `MultiAgentRoleResolver`
  - `MultiAgentEventPublisher`
- `MultiAgentCoordinator` 收敛为主流程编排与追踪记录，移除大块模板、解析与事件样板代码。

### 阶段二：编排门面瘦身与参数化治理

#### 2.3 `TaskOrchestrator` 异常翻译职责拆分

- 新增 `TaskRepositoryErrorTranslator`，集中处理：
  - 仓储异常日志与错误码翻译
  - 仓储失败事件发射
  - 执行期仓储异常兜底逻辑
  - 安全读取任务记录
- `TaskOrchestrator` 删除对应私有方法，门面职责更聚焦于“提交/查询编排”。

#### 2.4 同步等待短轮询魔法常量参数化

- `TaskSyncWaitService` 新增配置项：
  - `agent.task.sync.poll-window-ms`
  - `agent.task.sync.poll-interval-ms`
- 删除固定 `120ms`、`10ms` 直接硬编码，改为可配置并保留安全默认值。

### 阶段三：状态语义与结构化收敛

#### 2.5 任务状态强类型化

- 新增 `TaskStatusMapper`，统一状态文本与枚举映射。
- `TaskRecord.status` 由 `String` 改为 `TaskStatus`。
- `TaskStatusView.status` 由 `String` 改为 `TaskStatus`。
- `JdbcTaskRepository`、`InMemoryTaskRepository`、`TaskLifecycleService`、`TaskSyncWaitService`、`TaskEventPublisher`、`TaskHttpMapper` 全链路同步适配。
- 复用方适配：`ReplayTaskResolver` 等读取状态的业务代码改为显式 `.value()` 输出。

## 3. 测试与验证

### 3.1 全量回归执行

- 执行命令：`mvn test -DskipTests=false`
- 最终结果：通过
- 汇总结果：`Tests run: 567, Failures: 0, Errors: 0, Skipped: 0`

### 3.2 过程性失败与修复

- 修复 `WorkflowRouter` 重载导致的测试编译歧义。
- 修复 `toolChoice` 字段类型不一致导致的编译失败。
- 修复多智能体修复失败场景断言与模拟返回不一致问题。
- 修复状态强类型化后波及的回放与编排测试编译错误。

## 4. 结果结论

- 复评核心问题已按新方案直接落地，未引入历史兼容分支。
- 包设计可维护性提升点：
  - `workflow` 分层纯度提升。
  - `multiagent` 职责清晰度提升。
  - `task` 子域中异常翻译与状态语义更一致。
- 可复用性提升点：
  - 状态映射、仓储异常翻译、短轮询参数策略均形成可复用组件。

