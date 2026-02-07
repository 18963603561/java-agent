# StepSpec+StepResult 分层提交与验证报告

## 1. 目标

按 `P0 -> P1 -> P2 -> P3 -> P4` 完成运行时步骤模型重构，约束如下：

- 统一主模型为 `StepSpec` + `StepResult`
- `Map<String,Object>` 仅保留在外部动态边界（模型/工具返回、JSON 持久化边界）
- 禁止临时兼容分支与双轨模型

## 2. 分层提交总览

1. `68a0836`：`feat(runtime): migrate core flow to StepSpec and StepResult`（P0）
2. `3e202b0`：`refactor(storage): add StepResult json codec for persistence boundary`（P1）
3. `1c36aa3`：`refactor(runtime): consolidate StepSpec execution input usage`（P2）
4. `fe1c4b8`：`refactor(runtime): remove obsolete StepQuery and StepResponse`（P3）
5. `27675e0`：`test(planning): align assertions with StepSpec arguments model`（P4）

---

## 3. P0 交付

### 3.1 Git Diff

- 查看命令：`git show --stat 68a0836`
- 变更摘要：`22 files changed, 1408 insertions(+), 303 deletions(-)`
- 关键文件：
  - `src/main/java/com/example/agent/runtime/model/plan/StepSpec.java`
  - `src/main/java/com/example/agent/runtime/model/plan/StepPolicy.java`
  - `src/main/java/com/example/agent/runtime/model/result/StepResult.java`
  - `src/main/java/com/example/agent/runtime/model/result/StepResultMeta.java`
  - `src/main/java/com/example/agent/runtime/model/result/StepResultRaw.java`
  - `src/main/java/com/example/agent/runtime/model/result/StepResultSummary.java`
  - `src/main/java/com/example/agent/runtime/model/result/StepResultRefSet.java`
  - `src/main/java/com/example/agent/runtime/model/result/StepResultError.java`
  - `src/main/java/com/example/agent/runtime/model/result/StepResultDigest.java`
  - `src/main/java/com/example/agent/runtime/model/result/StepResultTiming.java`
  - `src/main/java/com/example/agent/runtime/RuntimeResult.java`
  - `src/main/java/com/example/agent/runtime/StepRecord.java`
  - `src/main/java/com/example/agent/runtime/StepRuntimeService.java`
  - `src/main/java/com/example/agent/runtime/AgentRuntime.java`
  - `src/main/java/com/example/agent/runtime/FinalOutputService.java`
  - `src/main/java/com/example/agent/planning/PlannerService.java`
  - `src/main/java/com/example/agent/planning/Plan.java`
  - `src/main/java/com/example/agent/planning/PlanResult.java`
  - `src/main/java/com/example/agent/reflection/ReflectionService.java`
  - `src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java`
  - `src/main/java/com/example/agent/runtime/StepRequest.java`（删除）

### 3.2 受影响类

- 规划输入主链路：`PlannerService`、`Plan`、`PlanResult`
- 执行主链路：`AgentRuntime`、`StepRuntimeService`、`RuntimeResult`、`StepRecord`
- 输出聚合：`FinalOutputService`
- 上下游消费：`ReflectionService`、`MultiAgentCoordinator`
- 新模型：`StepSpec`、`StepPolicy`、`StepResult*`

### 3.3 编译与测试命令

- 编译：`mvn -q -DskipTests compile`
- 测试编译：`mvn -q -DskipTests test-compile`

### 3.4 风险与回滚点

- 风险：主链路类型替换范围大，若遗漏 `List<Map<String,Object>>` 消费点会出现运行期字段读取失败
- 回滚点：`git revert 68a0836`

---

## 4. P1 交付

### 4.1 Git Diff

- 查看命令：`git show --stat 3e202b0`
- 变更摘要：`2 files changed, 90 insertions(+), 31 deletions(-)`
- 关键文件：
  - `src/main/java/com/example/agent/runtime/codec/StepResultJsonCodec.java`（新增）
  - `src/main/java/com/example/agent/runtime/JdbcStepRecordRepository.java`（切换为 codec）

### 4.2 受影响类

- 持久化边界：`JdbcStepRecordRepository`
- 编解码组件：`StepResultJsonCodec`

### 4.3 编译与测试命令

- 编译：`mvn -q -DskipTests compile`
- 测试编译：`mvn -q -DskipTests test-compile`

### 4.4 风险与回滚点

- 风险：JSON 编解码失败时返回 `null`，若下游未做空处理会有空指针风险
- 回滚点：`git revert 3e202b0`

---

## 5. P2 交付

### 5.1 Git Diff

- 查看命令：`git show --stat 1c36aa3`
- 变更摘要：`2 files changed, 57 insertions(+), 91 deletions(-)`
- 关键文件：
  - `src/main/java/com/example/agent/runtime/AgentRuntime.java`
  - `src/main/java/com/example/agent/runtime/model/plan/StepSpec.java`

### 5.2 受影响类

- `AgentRuntime`
- `StepSpec`

### 5.3 本层落地点

1. `AgentRuntime` 全面改为 `resolveStepInput(step)` 读取步骤输入，不再使用 `step.getInput()`
2. 删除 `resolveStepQuestion(...)` 中通过 `new StepSpec(null, stepInput)` 的临时桥接
3. `StepSpec` 删除兼容接口：`getInput`、`setInput`、`fromExecutionInput`
4. 保留 `toExecutionInput()` 作为执行边界映射出口

### 5.4 编译与测试命令

- 已执行：`mvn -q -DskipTests compile`
- 已执行：`mvn -q -DskipTests test-compile`

### 5.5 风险与回滚点

- 风险：审批字段读取逻辑收口后，若 `policy/context` 映射遗漏会改变审批触发时机
- 回滚点：`git revert 1c36aa3`

---

## 6. P3 交付

### 6.1 Git Diff

- 查看命令：`git show --stat fe1c4b8`
- 变更摘要：`2 files changed, 94 deletions(-)`
- 删除文件：
  - `src/main/java/com/example/agent/runtime/StepQuery.java`
  - `src/main/java/com/example/agent/runtime/StepResponse.java`

### 6.2 受影响类

- 已删除无引用冗余类，不再参与主链路

### 6.3 编译与测试命令

- 编译：`mvn -q -DskipTests compile`
- 测试编译：`mvn -q -DskipTests test-compile`

### 6.4 风险与回滚点

- 风险：若存在反射或序列化隐式依赖（当前检索未发现），运行期会出现 `ClassNotFound`
- 回滚点：`git revert fe1c4b8`

---

## 7. P4 交付

### 7.1 Git Diff

- 查看命令：`git show --stat 27675e0`
- 变更摘要：`1 file changed, 4 insertions(+), 3 deletions(-)`
- 关键文件：
  - `src/test/java/com/example/agent/planning/PlannerServiceTest.java`

### 7.2 受影响类

- 测试类：`PlannerServiceTest`

### 7.3 编译与测试命令

- 已执行全流程：`mvn clean test`
- 结果：`Tests run: 184, Failures: 0, Errors: 0, Skipped: 0`

### 7.4 风险与回滚点

- 风险：测试断言从 `getInput()` 切换为 `getArguments()` 后，若后续语义变动需同步更新断言
- 回滚点：`git revert 27675e0`

---

## 8. 当前状态说明

- 本次分层提交已完成，主链路使用 `StepSpec + StepResult`
- 持久化 JSON 边界已通过 `StepResultJsonCodec` 统一收口
- 由于当前工作区还存在你此前并行任务产生的未提交改动（非本次分层提交内容），本报告仅覆盖上述 5 个提交

## 9. 快速核对命令

1. 查看分层提交：`git log --oneline -n 8`
2. 查看 P0 diff：`git show --stat 68a0836`
3. 查看 P1 diff：`git show --stat 3e202b0`
4. 查看 P2 diff：`git show --stat 1c36aa3`
5. 查看 P3 diff：`git show --stat fe1c4b8`
6. 查看 P4 diff：`git show --stat 27675e0`
7. 全量回归：`mvn clean test`
