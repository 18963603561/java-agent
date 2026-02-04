# 代码瘦身分析报告（2026-02-03 22:05）

## 说明
- 分析范围：`src/main/java/com/example/agent`
- 目标：梳理重复逻辑、命名不一致与可抽取通用点，按优先级 `P0`-`P4` 输出
- 优先级定义：
  - `P0`：影响主流程正确性或造成大量分支
  - `P1`：高收益去重
  - `P2`：中等收益整理
  - `P3`：低风险重构
  - `P4`：可选优化

## agentcore
### 跨模块可抽取
- 优先级 `P0`：统一工具名字段与解析逻辑
  - 现状：`ToolExecutor`、`EnforcementGateway`、`SandboxExecutor` 以 `toolName` 为入参，但事件与上下文载荷大量使用 `tool`
  - 影响：运行时需要多层 `if` 判断，且容易出现字段不一致导致下游解析分支增加
  - 建议：新增 `ToolNameResolver` 与 `ToolContextKeys`（建议放在 `common`），内部统一使用 `toolName`，外部载荷统一由映射方法输出 `tool`
  - 位置：`src/main/java/com/example/agent/agentcore/ToolExecutor.java`，`src/main/java/com/example/agent/agentcore/EnforcementGateway.java`，`src/main/java/com/example/agent/agentcore/SandboxExecutor.java`
- 优先级 `P1`：合并工具参数校验逻辑
  - 现状：`agentcore.ToolArgumentValidator` 与 `runtime.ToolArgumentValidator` 功能重叠
  - 建议：抽取 `ToolArgumentValidationService`，提供“最小校验/严格校验”策略，避免重复与口径漂移
  - 位置：`src/main/java/com/example/agent/agentcore/ToolArgumentValidator.java`，`src/main/java/com/example/agent/runtime/ToolArgumentValidator.java`

### 模块内可抽取
- 优先级 `P2`：明确 `ToolRegistry#resolve` 的归一化职责
  - 现状：当前实现为透传，尚未形成统一入口
  - 建议：如果不需要别名能力可内联；如果需要别名能力，应把标准化逻辑集中在此处
  - 位置：`src/main/java/com/example/agent/agentcore/ToolRegistry.java`

## runtime
### 跨模块可抽取
- 优先级 `P0`：统一 `tool` 与 `toolName` 的读取与输出
  - 现状：`StepOutputSummaryBuilder#resolveToolName` 同时读取 `tool` 与 `toolName`；`StepRuntimeService` 只读取 `tool`；`LlmStepService` 在上下文写入 `tool`
  - 影响：同一字段含义在模块间漂移，导致多分支判断与上下游不一致
  - 建议：复用 `ToolNameResolver` 与 `ToolContextKeys`，将解析逻辑收口，并在边界统一转换
  - 位置：`src/main/java/com/example/agent/runtime/StepOutputSummaryBuilder.java`，`src/main/java/com/example/agent/runtime/StepRuntimeService.java`，`src/main/java/com/example/agent/runtime/LlmStepService.java`
- 优先级 `P2`：抽取 `Map` 读写工具
  - 现状：`LlmStepService` 的 `readString`、`readNumber`、`readMap` 与 `context`、`memory` 模块存在相似实现
  - 建议：在 `common` 抽取 `MapReadUtil`，统一空值与类型转换逻辑
  - 位置：`src/main/java/com/example/agent/runtime/LlmStepService.java`，`src/main/java/com/example/agent/context/DefaultContextBuilder.java`，`src/main/java/com/example/agent/memory/MemoryWriteService.java`，`src/main/java/com/example/agent/memory/MemoryRecallService.java`

### 模块内可抽取
- 优先级 `P3`：拆分 `StepOutputSummaryBuilder` 的内嵌结构
  - 现状：`StepOutputSummaryBuilder` 内部包含 `SummaryLimits`、`SnapshotWriter`、`TruncationState` 等多个内嵌类
  - 建议：拆分为包内类或工具类，降低单类复杂度并便于复用
  - 位置：`src/main/java/com/example/agent/runtime/StepOutputSummaryBuilder.java`

## planning
### 跨模块可抽取
- 优先级 `P0`：统一规划输入中的工具名字段
  - 现状：`PlannerService` 与 `StepRequest` 同时接受 `tool` 与 `toolName`，并额外支持 `fallbackTool`
  - 影响：规划解析与运行执行出现多分支判断，增加不稳定变量
  - 建议：规划侧统一使用 `toolName`，对外兼容时通过 `ToolNameResolver` 做一次归一化
  - 位置：`src/main/java/com/example/agent/planning/PlannerService.java`，`src/main/java/com/example/agent/runtime/StepRequest.java`

### 模块内可抽取
- 优先级 `P2`：合并 `PlannerService` 内部多处工具字段提取逻辑
  - 现状：多处代码重复从 `context` 或 `input` 读取 `tool`、`toolName`、`fallbackTool`
  - 建议：抽取 `ToolContextExtractor` 或 `PlanToolResolver`，避免同类判断重复
  - 位置：`src/main/java/com/example/agent/planning/PlannerService.java`

## gateway
### 跨模块可抽取
- 优先级 `P1`：统一工具事件载荷的构建
  - 现状：`McpController` 与 `EnforcementGateway` 分别拼接 `tool`、`callId`、`serverId` 等字段
  - 影响：字段命名与内容一致性依赖多处实现
  - 建议：抽取 `ToolEventPayloadBuilder` 或 `ToolEventPublisher`，统一事件字段与日志上下文
  - 位置：`src/main/java/com/example/agent/gateway/controller/McpController.java`，`src/main/java/com/example/agent/agentcore/EnforcementGateway.java`

## budget
### 跨模块可抽取
- 优先级 `P2`：统一 `TokenEstimator` 的包装方法
  - 现状：`ContextCompressionController`、`DefaultContextTrimmer`、`DefaultContextAssembler`、`DefaultPromptAssembler` 都有 `estimateTokens` 与列表聚合逻辑
  - 建议：在 `common` 抽取 `TokenEstimateSupport`，统一空值处理、列表聚合与日志口径
  - 位置：`src/main/java/com/example/agent/budget/ContextCompressionController.java`，`src/main/java/com/example/agent/budget/DefaultContextTrimmer.java`，`src/main/java/com/example/agent/context/DefaultContextAssembler.java`，`src/main/java/com/example/agent/model/DefaultPromptAssembler.java`

### 模块内可抽取
- 优先级 `P1`：合并工具摘要估算方法
  - 现状：`ContextCompressionController#estimateToolSummaryTokens` 与 `DefaultContextTrimmer#estimateToolSummaryTokens` 逻辑一致
  - 建议：在 `budget` 模块内抽取 `ToolSummaryEstimator`，并统一 `estimateToolSummaryChars` 的口径
  - 位置：`src/main/java/com/example/agent/budget/ContextCompressionController.java`，`src/main/java/com/example/agent/budget/DefaultContextTrimmer.java`
- 优先级 `P2`：证据、记忆、引用等估算方法重复
  - 现状：两处类中存在大量 `estimateXxxTokens` 与 `estimateXxxChars` 相同结构
  - 建议：合并为 `EvidenceEstimator`、`MemoryEstimator` 等模块内工具类
  - 位置：`src/main/java/com/example/agent/budget/ContextCompressionController.java`，`src/main/java/com/example/agent/budget/DefaultContextTrimmer.java`

## context
### 跨模块可抽取
- 优先级 `P2`：统一 `Map` 字段读取工具
  - 现状：`DefaultContextBuilder` 内部 `readString` 与 `memory`、`runtime` 的工具方法重复
  - 建议：复用 `MapReadUtil`，减少多处 `if` 分支与空值判断
  - 位置：`src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- 优先级 `P2`：统一上下文的 `TokenEstimator` 包装
  - 现状：`DefaultContextAssembler` 与 `model.DefaultPromptAssembler` 使用相似估算逻辑
  - 建议：复用 `TokenEstimateSupport`，减少重复方法
  - 位置：`src/main/java/com/example/agent/context/DefaultContextAssembler.java`，`src/main/java/com/example/agent/model/DefaultPromptAssembler.java`

## memory
### 跨模块可抽取
- 优先级 `P2`：统一 `Map` 字段读取工具
  - 现状：`MemoryWriteService` 与 `MemoryRecallService` 各自实现 `readString`
  - 建议：复用 `MapReadUtil`，统一空值与类型处理
  - 位置：`src/main/java/com/example/agent/memory/MemoryWriteService.java`，`src/main/java/com/example/agent/memory/MemoryRecallService.java`

### 模块内可抽取
- 优先级 `P2`：合并上下文字段解析
  - 现状：`MemoryWriteService` 与 `MemoryRecallService` 均需要解析 `workflowId`、`snapshotId` 等字段
  - 建议：抽取 `MemoryContextResolver`，集中管理字段名与默认策略
  - 位置：`src/main/java/com/example/agent/memory/MemoryWriteService.java`，`src/main/java/com/example/agent/memory/MemoryRecallService.java`

## model
### 跨模块可抽取
- 优先级 `P2`：统一文本估算与裁剪逻辑
  - 现状：`DefaultPromptAssembler` 内部多处 `estimateTokens` 与裁剪逻辑，与 `context`、`budget` 重复
  - 建议：复用 `TokenEstimateSupport` 与公共裁剪工具，减少重复实现
  - 位置：`src/main/java/com/example/agent/model/DefaultPromptAssembler.java`

## history
### 跨模块可抽取
- 优先级 `P3`：抽取 JDBC 访问基础能力
  - 现状：`JdbcEventLogRepository` 与 `JdbcTaskRepository` 存在相似的 JSON 序列化与异常处理
  - 建议：抽取 `JdbcJsonSupport` 或 `JsonbConverter` 到 `common`，统一 JSON 读写与日志
  - 位置：`src/main/java/com/example/agent/history/JdbcEventLogRepository.java`，`src/main/java/com/example/agent/orchestrator/JdbcTaskRepository.java`

### 模块内可抽取
- 优先级 `P3`：抽取内存索引通用结构
  - 现状：`InMemoryEventLogRepository` 使用双索引结构，与 `InMemoryTaskRepository` 等模式相似
  - 建议：抽取 `InMemoryIndexSupport`，统一索引与去重逻辑
  - 位置：`src/main/java/com/example/agent/history/InMemoryEventLogRepository.java`

## orchestrator
### 跨模块可抽取
- 优先级 `P3`：统一 JDBC 仓储模板
  - 现状：`JdbcTaskRepository` 与其他 JDBC 仓储重复查询与异常处理结构
  - 建议：抽取 `JdbcRepositorySupport`，统一模板方法与日志
  - 位置：`src/main/java/com/example/agent/orchestrator/JdbcTaskRepository.java`

### 模块内可抽取
- 优先级 `P3`：抽取内存仓储模板
  - 现状：`InMemoryTaskRepository` 与 `history`、`scheduler` 的内存实现结构接近
  - 建议：抽取 `InMemoryRepositorySupport`，统一排序与索引
  - 位置：`src/main/java/com/example/agent/orchestrator/InMemoryTaskRepository.java`

## scheduler
### 跨模块可抽取
- 优先级 `P3`：统一 JDBC 仓储模板
  - 现状：`JdbcScheduleRepository` 与 `JdbcScheduleExecutionRepository` 与其他 JDBC 仓储模式相似
  - 建议：抽取 `JdbcRepositorySupport`，统一增删改查与日志
  - 位置：`src/main/java/com/example/agent/scheduler/JdbcScheduleRepository.java`，`src/main/java/com/example/agent/scheduler/JdbcScheduleExecutionRepository.java`

### 模块内可抽取
- 优先级 `P4`：统一调度相关对象构建
  - 现状：`ScheduleSpec` 与 `ScheduleExecutionRecord` 在多处被重复构建与赋值
  - 建议：在 `scheduler` 模块内引入简单构建器或工厂
  - 位置：`src/main/java/com/example/agent/scheduler/ScheduleManager.java`，`src/main/java/com/example/agent/scheduler/ScheduleEngine.java`

## 其他模块
- 暂未发现明显重复逻辑或命名不一致问题，可待后续改动时再做聚焦抽取