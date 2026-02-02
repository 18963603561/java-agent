# TaskRequest idempotencyKey 规则分析与改动范围

## 需求要点
- `idempotencyKey` 允许为空。
- 当 `idempotencyKey` 为空时，每次提交都创建新任务。
- 当 `idempotencyKey` 不为空时，需要校验历史任务；若存在历史任务则直接复用历史任务。

## 现状分析
1) 请求模型限制了空值
- 文件：`src/main/java/com/example/agent/common/TaskRequest.java`
- 字段：`idempotencyKey` 标注了 `@NotBlank`，会在 `TaskController` 的 `@Valid` 校验中直接拒绝空值或空白字符串。

2) 提交流程已具备幂等逻辑
- 文件：`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
- 逻辑：`submitTask` 中仅在 `StringUtils.hasText(idempotencyKey)` 为真时进入幂等分支；为空时走“直接创建任务”的路径。
- 结论：服务层已符合“空值不做幂等”的语义，但被请求模型校验拦截。

3) 历史任务查询逻辑已存在
- 文件：`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
- 方法：`findIdempotent` 先查 `Redis`，再回落到 `TaskRepository.findByIdempotencyKey`。
- 结论：当 `idempotencyKey` 不为空时，已满足“存在即复用”的要求。

4) 持久化层对空值处理不一致
- 文件：`src/main/java/com/example/agent/orchestrator/JdbcTaskRepository.java`
  - `findByIdempotencyKey` 对空值直接返回 `null`。
- 文件：`src/main/java/com/example/agent/orchestrator/InMemoryTaskRepository.java`
  - `save` 只判断 `null`，空字符串仍会写入索引；虽然目前不会被调用查询，但可能造成索引污染。

5) 规范文档存在“必填”描述
- 文件：`specs/001-agent-core-spec/plan.md`、`specs/001-agent-core-spec/spec.md`、`specs/001-agent-core-spec/tasks.md`
- 内容：多处描述 `TaskRequest` 必须包含 `idempotencyKey`。
- 结论：若需求改为可选，需要同步更新规范。

## 需要改动的代码位置与建议
1) 请求模型校验
- 文件：`src/main/java/com/example/agent/common/TaskRequest.java`
- 建议：移除 `idempotencyKey` 的 `@NotBlank` 约束；必要时改为可选并保留字段注释说明。

2) 幂等键归一化（可选但建议）
- 文件：`src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java`
- 建议：在 `submitTask` 中对 `idempotencyKey` 做“空白转空值”归一化，确保空字符串不写入记录。

3) 内存仓库索引保护（建议）
- 文件：`src/main/java/com/example/agent/orchestrator/InMemoryTaskRepository.java`
- 建议：`save` 时改为 `StringUtils.hasText` 判定；`findByIdempotencyKey` 增加空值短路返回，避免空键索引。

4) 测试用例补充（建议）
- 文件：`src/test/java/com/example/agent/gateway/controller/TaskControllerTest.java`
- 建议：新增用例验证
  - `idempotencyKey` 为空时，重复提交返回不同 `taskId`。
  - `idempotencyKey` 非空时，重复提交返回相同 `taskId`。

## 可能涉及的文档改动
- `specs/001-agent-core-spec/plan.md`：将“必须包含 `idempotencyKey`”调整为“可选”。
- `specs/001-agent-core-spec/spec.md`、`specs/001-agent-core-spec/tasks.md`：同步更新幂等规则描述。
- 相关示例文档可保留 `idempotencyKey`，但需说明可选。

## 结论
- 当前代码层面的幂等逻辑已支持“空值跳过幂等、非空复用历史任务”的行为。
- 实际阻断点在 `TaskRequest` 的 `@NotBlank` 校验；同时建议对空字符串进行统一处理并完善内存索引与测试。