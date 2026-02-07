# 大模型规划工具参数丢失分析与方案（2026-02-01 17:31）

## 一、现象与触发条件
- 触发条件：开启大模型自主规划，规划步骤为工具步骤，且工具必填参数未出现在任务请求上下文中。
- 现象：运行时能解析出工具名称，但执行工具时参数校验报必填字段缺失。

日志片段（英文原文保持）：
```text
com.example.agent.common.ErrorCodeException: 400 BAD_REQUEST "工具参数校验失败: tool=search-user-by-name, field=arguments.name, reason=必填字段缺失"
	at com.example.agent.agentcore.ToolArgumentValidator.invalid(ToolArgumentValidator.java:334)
	at com.example.agent.agentcore.ToolArgumentValidator.validateObjectSchema(ToolArgumentValidator.java:70)
```
说明：工具入参缺少 `arguments.name`，触发参数校验失败。

## 二、关键流程
1. 规划阶段
   - `PlannerService.tryLlmPlan` 调用模型生成规划结果。
   - `PlannerService.parsePlan` 解析 `steps`，只读取 `steps[*].input` 与 `steps[*].tool`，将结果写入 `StepRequest`。
2. 运行阶段
   - `AgentRuntime.executeStep` 调用 `resolveToolName` 获取工具名称。
   - `AgentRuntime.executeToolStep` 仅传入工具名称，调用 `EnforcementGateway.execute`。
3. 工具执行阶段
   - `EnforcementGateway.execute` 内部调用 `ToolExecutor.executeInternal`。
   - `ToolExecutor.executeInternal` 在未传入工具参数时调用 `buildArguments`，仅使用 `TaskRequest.query` 与 `TaskRequest.context`。
   - `ToolArgumentValidator.validateObjectSchema` 校验必填字段，缺失即抛出异常。

## 三、根因分析
- 规划解析仅保证工具名称可用，未确保工具参数链路被完整保留与传递。
- 工具参数即便被写入 `StepRequest.input`，运行时也未取用并传递给工具执行层。
- 规划输出若将参数放在 `steps[*].arguments`，`parsePlan` 不会读取，参数在解析阶段即丢失。

## 四、影响范围
- 依赖必填字段的工具在大模型规划模式下容易失败。
- 即使规划结果包含参数，也可能在运行阶段被忽略，导致重复失败与重试。
- 工具兜底与重规划无法解决参数链路丢失问题，只能绕过或继续报错。

## 五、解决方案

### 方案一（推荐）：运行时接入工具参数
- 在 `AgentRuntime.executeStep` 或 `AgentRuntime.executeToolStep` 中读取 `StepRequest.input` 的 `arguments`。
- 使用 `EnforcementGateway.executeWithArguments` 将参数传入执行层。
- 参数合并优先级建议：`StepRequest.input.arguments` 覆盖 `TaskRequest.context`；保留 `query` 作为默认输入。
- 建议过滤保留字段（如 `tool`、`toolName`、`context`、`query`、`dependsOn`）与内部字段，避免污染工具入参。
- 补充日志：
  - `info` 记录是否启用步骤参数与工具名。
  - `debug` 记录参数键集合与裁剪结果。
  - `warn` 记录缺参但仍执行的情况。

### 方案二：规划解析阶段标准化参数
- 在 `PlannerService.parsePlan` 同时支持 `steps[*].arguments` 与 `steps[*].input.arguments`，统一落入 `input.arguments`。
- 同步更新 `JsonOutputSchema.PLANNER`，明确规划输出包含 `arguments`。
- 需要同步更新规划提示词或规划输出约定，避免模型产出不一致。

### 方案三：执行层统一合并策略
- 扩展 `ToolExecutor` 或 `EnforcementGateway` 接口，允许在更底层合并步骤参数。
- 影响范围更大，需梳理调用方与测试面，实施成本较高。

## 六、推荐落地路径
- 优先落地方案一，确保运行时参数可达。
- 并行推进方案二，保证规划输出结构稳定、可预测。
- 方案三仅在后续需要统一入口或重构执行链路时考虑。

## 七、涉及代码与配置
- `src/main/java/com/example/agent/planning/PlannerService.java`：解析规划输出与参数结构。
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`：执行工具步骤时传入参数。
- `src/main/java/com/example/agent/agentcore/EnforcementGateway.java`：已支持 `executeWithArguments`，需被调用。
- `src/main/java/com/example/agent/agentcore/ToolExecutor.java`：参数合并与校验逻辑。
- `src/main/java/com/example/agent/repair/JsonOutputSchema.java`：规划输出结构约束。
- 规划提示词与输出约定位置（如 `PlannerService.buildPlanPrompt` 或提示词模板文件）。

## 八、文档改动
- 新增：`doc/llm-plan-tool-arguments-analysis-202602011731.md`，记录问题分析与方案。
- 建议更新：`doc/prompt-contracts.md`，补充规划输出包含 `arguments` 的字段约定与示例。

## 九、验证建议
- 构造包含必填字段的规划步骤，验证 `arguments` 在运行时可被传递与校验通过。
- 覆盖两类规划输出：
  - `steps[*].arguments` 在顶层。
  - `steps[*].input.arguments` 在输入内部。
- 校验日志中能看到参数链路与裁剪信息，且不泄露敏感字段。