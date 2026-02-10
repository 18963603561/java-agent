# `orchestration` 包 `P0` 级改造任务清单

## 1. 任务来源与目标

- 来源文档：`doc/orchestration-package-review-202602101158.md`
- 对应章节：`5. 优先级改造建议 -> P0（立即处理）`
- 本清单目标：将 `P0` 结论转为“可开发、可测试、可验收”的具体任务项。

## 2. `P0` 总体范围

- 范围一：修复 `MultiAgentCoordinator` 提示词乱码与输出契约错位问题。
- 范围二：将仓储与解析关键路径从“静默失败”改为“失败可见、语义明确”。
- 范围三：补齐 `P0` 回归测试，确保修复不回退。

## 3. `P0` 任务清单

### 任务一：修复多智能体提示词乱码

- 任务编号：`ORCH-P0-MA-01`
- 优先级：`P0`
- 问题指向：`MultiAgentCoordinator` 核心提示词存在乱码，影响模型理解。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java`
  - `src/test/java/com/example/agent/multiagent/MultiAgentCoordinatorTest.java`
- 改造动作：
  - 将 `buildPrompt` 中不可读文本替换为可读且语义完整的中文指令。
  - 保留并明确 `MULTI_AGENT_CONTEXT_JSON` 输入约定。
  - 修正注释乱码，确保文件统一为 `UTF-8`。
- 验收标准：
  - `buildPrompt` 生成内容可读、语义完整，无乱码片段。
  - 现有 `MultiAgentCoordinatorTest` 通过，并新增断言校验关键提示词语句存在。

### 任务二：统一输出契约并补充向后兼容解析

- 任务编号：`ORCH-P0-MA-02`
- 优先级：`P0`
- 问题指向：提示词约束字段与 `parseRoles` 读取字段不一致。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java`
  - `src/test/java/com/example/agent/multiagent/MultiAgentCoordinatorTest.java`
- 改造动作：
  - 定义单一主契约：`team[*].roleId/name/modelId/description`。
  - 在提示词中使用主契约字段，并更新最小 `JSON` 示例。
  - `parseRoles` 增加兼容：若仅返回 `role/responsibility`，映射到 `name/description`。
  - 对无法映射为有效角色的数据记录 `warn` 日志并跳过。
- 验收标准：
  - 主契约字段与解析字段一致。
  - 对旧格式输出（`role/responsibility`）仍可解析出有效角色。
  - 新增契约对齐测试通过。

### 任务三：解析失败路径可观测化

- 任务编号：`ORCH-P0-MA-03`
- 优先级：`P0`
- 问题指向：`parseRoles` 与修复分支存在吞异常，排障信息不足。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/multiagent/MultiAgentCoordinator.java`
  - `src/test/java/com/example/agent/multiagent/MultiAgentCoordinatorTest.java`
- 改造动作：
  - 在 `parseRoles` 异常捕获处补充 `warn` 日志（含 `workflowId/stepType/parseErrorType`）。
  - 在 `tryRepairRoles` 失败场景补充 `warn` 日志，明确“原始解析失败”与“修复失败”。
  - 保证 `recordPromptTrace` 能区分：空输出、解析失败、修复失败三类原因。
- 验收标准：
  - 解析失败时日志中可定位失败阶段与原因类型。
  - 对非法 `JSON` 输入，仍能回退默认角色并留下完整失败轨迹。

### 任务四：仓储失败语义显式化（禁止假成功）

- 任务编号：`ORCH-P0-REPO-01`
- 优先级：`P0`
- 问题指向：`JdbcTaskRepository` 数据库/序列化异常后返回“看似成功”的对象或 `null`。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/JdbcTaskRepository.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskRepository.java`
  - `src/main/java/com/example/agent/orchestration/task/TaskRecord.java`
  - `src/test/java/com/example/agent/orchestrator/TaskOrchestratorTest.java`
- 改造动作：
  - 新增仓储层异常类型（建议：`TaskRepositoryException`），用于封装 `DataAccessException` 与序列化异常。
  - `save/find/list` 出错时抛出仓储异常，不再吞掉后返回正常对象。
  - `readJson` 反序列化失败时记录 `error` 并抛出仓储异常，避免静默置空。
- 验收标准：
  - 仓储失败可被上层明确感知，不再出现“写失败但调用方无感”的语义。
  - 异常日志包含关键上下文：`tenantId/taskId/operation`。

### 任务五：编排层失败闭环与错误语义统一

- 任务编号：`ORCH-P0-REPO-02`
- 优先级：`P0`
- 问题指向：仓储失败后，`TaskOrchestrator` 缺少统一失败闭环处理。
- 涉及文件：
  - `src/main/java/com/example/agent/orchestration/task/TaskOrchestrator.java`
  - `src/test/java/com/example/agent/orchestrator/TaskOrchestratorTest.java`
- 改造动作：
  - 在任务提交、状态更新、同步等待读取路径捕获仓储异常并转为统一业务错误码。
  - 发布 `ERROR_OCCURRED` 事件并附带标准错误码（例如：`task_repository_failure`）。
  - 统一对外行为：不可恢复仓储错误返回 `503`，禁止返回“已接受/已完成”假状态。
- 验收标准：
  - 仓储异常时 HTTP 语义一致（`503`），事件与日志一致可追踪。
  - 无“失败后仍返回成功状态”的路径。

### 任务六：`P0` 回归测试补齐

- 任务编号：`ORCH-P0-TEST-01`
- 优先级：`P0`
- 涉及文件：
  - `src/test/java/com/example/agent/multiagent/MultiAgentCoordinatorTest.java`
  - `src/test/java/com/example/agent/orchestrator/TaskOrchestratorTest.java`
  - （按需新增）`src/test/java/com/example/agent/orchestrator/TaskOrchestratorFailurePathTest.java`
- 必测场景：
  - 多智能体输出使用主契约字段时可解析。
  - 多智能体输出使用旧契约字段时兼容解析。
  - 多智能体输出非法 `JSON` 时可观测日志 + fallback。
  - 仓储抛异常时，`TaskOrchestrator` 返回 `503` 且状态不假成功。
- 验收标准：
  - `P0` 相关测试全部通过。
  - 新增测试可稳定复现并守护已修复问题。

## 4. 实施顺序与依赖

- 顺序一：先做 `ORCH-P0-MA-01` 与 `ORCH-P0-MA-02`，先修“提示词可用 + 契约一致”。
- 顺序二：执行 `ORCH-P0-MA-03`，补齐解析失败可观测性。
- 顺序三：执行 `ORCH-P0-REPO-01`，先把仓储层失败语义拉直。
- 顺序四：执行 `ORCH-P0-REPO-02`，再把编排层错误闭环打通。
- 顺序五：执行 `ORCH-P0-TEST-01`，统一回归验证并冻结 `P0` 结果。

## 5. `P0` 完成定义（DoD）

- 提示词不再乱码，且字段契约与解析契约一致。
- 关键失败路径（模型解析失败、仓储失败）均可在日志/事件中定位。
- 不存在吞异常后继续返回成功形态的实现。
- `P0` 回归测试通过并可在后续改动中持续守护。
