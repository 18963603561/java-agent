# `capabilities` 包系统设计说明

## 1. 包定位与核心功能
- 包定位：`capabilities` 提供上下文、模型、工具、记忆四大执行能力域。
- 核心功能：
  - `DefaultContextBuilder`：组装上下文快照并联动预算/裁剪/压缩。
  - `ModelInvocationService`：统一模型调用、错误映射与结果引用。
  - `ToolExecutor`：统一工具执行、缓存、重试、沙箱和 MCP 路由。
  - `MemoryStore`：统一记忆写入、检索、压缩入口。
  - Hook 与策略组件支撑能力扩展。

## 2. 分层线框图
```mermaid
graph TB
    A[Runtime/Planning 调用] --> B[Context 子域]
    A --> C[LLM 子域]
    A --> D[Tools 子域]
    A --> E[Memory 子域]
    B --> F[ContextSnapshot]
    C --> G[ModelResponse]
    D --> H[ToolExecutionResult]
    E --> I[MemorySearchResult]
    F --> J[能力聚合输出]
    G --> J
    H --> J
    I --> J
```

## 3. 关键流程图
```mermaid
graph TB
    A[步骤输入] --> B[DefaultContextBuilder.build]
    B --> C[ModelToolResolver 注入工具上下文]
    C --> D[ModelInvocationService.invoke]
    D -->|需要工具| E[ToolExecutor.execute]
    E --> F[MemoryStore.save/search]
    F --> G[合并输出与 rawRef]
    D -->|无需工具| G
    G --> H[返回 runtime/planning/reasoning]
```

## 4. 状态流转图
```mermaid
graph TB
    S0[CAPABILITY_REQUESTED] --> S1[CONTEXT_READY]
    S1 --> S2[MODEL_INVOKING]
    S2 -->|SUCCESS| S3[MODEL_OUTPUT_READY]
    S2 -->|RETRIABLE_FAILED| S4[RETRY_OR_FALLBACK]
    S2 -->|FAILED| S8[CAPABILITY_FAILED]
    S3 -->|有工具调用| S5[TOOL_EXECUTING]
    S3 -->|无工具调用| S7[CAPABILITY_COMPLETED]
    S5 -->|SUCCESS| S6[TOOL_OUTPUT_READY]
    S5 -->|TOOL_ERROR| S4
    S4 -->|恢复成功| S2
    S4 -->|恢复失败| S8
    S6 --> S7
```

## 5. 类职责与设计原因
- `DefaultContextBuilder`：集中编排上下文构建，减少流程碎片化。
- `ModelInvocationService`：屏蔽模型供应商差异，统一异常语义。
- `ToolExecutor`：聚合工具执行治理能力，保证路径可控。
- `MemoryStore`：门面化记忆能力，内部职责再拆分。
- `HookManager`：提供扩展点，降低主流程侵入。

## 6. 关键类直接关系图
```mermaid
graph TB
    A[DefaultContextBuilder] --> B[ContextBudgetAllocator]
    A --> C[ContextPruner]
    A --> D[ContextTrimmer]
    A --> E[ContextCompressionService]
    F[ModelInvocationService] --> G[LlmClient]
    F --> H[ProviderErrorMapper]
    I[ToolExecutor] --> J[ToolRegistry]
    I --> K[McpToolClient]
    I --> L[SandboxExecutor]
    M[MemoryStore] --> N[MemorySaveOrchestrator]
    M --> O[MemorySearchOrchestrator]
```

## 7. 重点说明
- `capabilities` 是执行能力中台，决定任务执行上限。
- 与 `budget`、`security`、`streaming` 的协同保障可控可观测。
- 所有图统一使用 `graph TB`。

