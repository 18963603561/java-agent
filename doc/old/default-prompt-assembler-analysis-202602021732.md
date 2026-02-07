# DefaultPromptAssembler 作用与调用路径分析

## 1. 功能定位
- `DefaultPromptAssembler` 是提示词组装器的默认实现，负责将 system/developer/user 三段消息组装为可发送给模型的 `PromptBundle`。
- 其核心目标是：
  - 统一生成消息序列（system/developer/user）；
  - 基于预算执行裁剪；
  - 记录裁剪与体量指标；
  - 兼容 legacy 路径（仅模板 + 用户输入）。

## 2. 核心流程概览
### 2.1 主入口
- 方法：`build(String prompt, TaskRequest taskRequest, Map<String, Object> stepInput)`
- 处理步骤：
  1) 解析 `ContextSnapshot` 与 `PromptAssemblyInput`；
  2) 若缺少 `PromptAssemblyInput` 则走 legacy 路径；
  3) 补齐 system/developer/user；
  4) 按预算裁剪（`trimIfNeeded`）；
  5) 组装 `PromptBundle`，并写入估算 tokens、裁剪记录。

### 2.2 Legacy 路径
- 方法：`buildLegacy(String prompt, ContextSnapshot snapshot)`
- 逻辑：
  - 使用模板渲染 system/developer（最小渲染上下文）；
  - 拼接 user 文本；
  - 估算 tokens 并返回。
- 作用：兼容旧链路或缺失装配输入的场景。

### 2.3 裁剪与预算
- 方法：`trimIfNeeded(PromptAssemblyInput input)`
- 逻辑：
  - 依据 `ContextBudgetAllocation` 计算 prompt 预算；
  - 先裁剪 developer，再裁剪 user，最后裁剪 system；
  - 保证每段最小保留长度（`MIN_SYSTEM_CHARS`/`MIN_USER_CHARS`/`MIN_DEVELOPER_CHARS`）；
  - 写回裁剪结果与预算使用统计；
  - 记录指标与日志。

## 3. 关键依赖
- `PromptTemplate`：用于渲染 system/developer。
- `PromptAssemblyInput`：承载上下文装配后的 system/developer/user 文本与预算分配。
- `TokenEstimator`：用于估算 token 数量。
- `MetricsPublisher`：记录裁剪相关指标。

## 4. 主要调用路径
### 4.1 规划与决策路径
- `PlannerService`/`LlmStepService`/`FinalOutputService` 等调用 `PromptAssembler.build(...)` 组装模型消息。
- 若 `PromptAssemblyInput` 已在上游构建，则走主路径并可触发裁剪逻辑。

### 4.2 运行时兜底路径
- 当 `stepInput` 或 `taskRequest` 中不存在 `promptAssemblyInput` 时，走 legacy 路径。
- legacy 路径仍会生成 system/developer/user，但不进行裁剪。

## 5. 作用与影响
- 保证提示词输出结构一致性（system/developer/user 三段）。
- 控制提示词预算与裁剪，避免超预算导致模型失败。
- 通过日志与指标提供可观测性，便于监控裁剪行为与 prompt 体量。
- 兼容旧链路，降低上游改造成本。

## 6. 关联文件
- `src/main/java/com/example/agent/model/DefaultPromptAssembler.java`
- `src/main/java/com/example/agent/model/PromptAssembler.java`
- `src/main/java/com/example/agent/context/PromptAssemblyInput.java`
- `src/main/java/com/example/agent/model/PromptTemplate.java`
- `src/main/java/com/example/agent/model/PromptBundle.java`
- `src/main/java/com/example/agent/budget/ContextBudgetAllocation.java`
