# `runtime` 包系统设计说明

## 1. 包定位与核心功能
- 包定位：`runtime` 是任务执行主引擎，负责编排准备、计划执行、步骤状态迁移、失败恢复与最终收敛。
- 核心功能：
  - `AgentRuntime` 组织主循环：`prepare -> plan -> step loop -> finalize`。
  - `StepExecutionCoordinator` 负责单步执行编排（控制门禁、执行、反思、恢复、上下文更新）。
  - `StepRuntimeService` + `StepStateMachine` 管理步骤记录、状态迁移与事件发布。
  - `RuntimeExecutionGate` + `RuntimeApprovalGate` 处理暂停/恢复/取消/审批阻塞。
  - `StepFailureRecoveryService` 提供 `RETRY/REPLAN/FALLBACK/STOP` 恢复策略。
  - `RuntimeFinalizationService` 汇总执行输出并生成 `RuntimeResult`。

## 2. 分层线框图
```mermaid
graph TB
    A[Orchestration 请求] --> B[AgentRuntime]
    B --> C[RuntimePreparationService]
    B --> D[PlannerService]
    B --> E[StepExecutionCoordinator]
    E --> F[StepRuntimeService]
    E --> G[RuntimeExecutionGate]
    E --> H[RuntimeApprovalGate]
    E --> I[StepExecutionDelegate]
    E --> J[ReflectionService]
    E --> K[StepFailureRecoveryService]
    E --> L[RuntimeContextUpdateService]
    B --> M[RuntimeFinalizationService]
    M --> N[RuntimeResult]
```

## 3. 关键流程图
```mermaid
graph TB
    A[AgentRuntime.run] --> B[prepare 构建 RuntimeContext]
    B --> C[PlannerService.plan]
    C --> D{计划为空?}
    D -->|是| E[finalizeWhenPlanEmpty]
    D -->|否| F[遍历 StepSpec]
    F --> G[StepExecutionCoordinator.executeStep]
    G --> H{执行结果}
    H -->|SUCCESS| I[继续下一步]
    H -->|REPLAN| J[重建请求并重新规划]
    J --> C
    I --> K{是否还有步骤}
    K -->|是| F
    K -->|否| L[finalizeRun]
```

## 4. 状态流转图
```mermaid
graph TB
    subgraph WorkflowControl[工作流控制状态]
        C0[RUNNING] --> C1[PAUSED]
        C1 --> C0
        C0 --> C2[WAIT_APPROVAL]
        C2 -->|approve| C0
        C2 -->|reject| C3[CANCELLED]
        C0 --> C3
    end

    subgraph StepLifecycle[步骤状态机]
        S0[PENDING] --> S1[STARTED]
        S0 --> S5[SKIPPED]
        S0 --> S4[WAITING]
        S1 --> S2[COMPLETED]
        S1 --> S3[FAILED]
        S1 --> S4
        S4 --> S1
        S4 --> S3
        S4 --> S5
    end

    subgraph RecoveryAction[失败恢复动作]
        R0[FAILED] --> R1[RETRY]
        R0 --> R2[REPLAN]
        R0 --> R3[FALLBACK_SUCCESS]
        R0 --> R4[STOP]
        R1 --> S1
        R2 --> S0
        R3 --> S2
        R4 --> S3
    end
```

## 5. 类职责与设计原因
- `AgentRuntime`：保持主循环边界清晰，避免步级细节分散到多个入口。
- `StepExecutionCoordinator`：聚合步级核心逻辑，是稳定性和恢复能力中枢。
- `StepRuntimeService`：统一步骤记录、事件发布和状态落盘，保证时序一致性。
- `StepStateMachine`：集中状态迁移规则，防止非法迁移。
- `RuntimeExecutionGate`/`RuntimeApprovalGate`：将控制平面从执行平面解耦。
- `StepFailureRecoveryService`：独立错误分类与恢复策略，便于后续演进。

## 6. 关键类直接关系图
```mermaid
graph TB
    A[AgentRuntime] --> B[RuntimePreparationService]
    A --> C[PlannerService]
    A --> D[StepExecutionCoordinator]
    A --> E[RuntimeFinalizationService]
    D --> F[StepRuntimeService]
    D --> G[RuntimeExecutionGate]
    D --> H[RuntimeApprovalGate]
    D --> I[StepExecutionDelegate]
    D --> J[ReflectionService]
    D --> K[StepFailureRecoveryService]
    D --> L[RuntimeContextUpdateService]
```

## 7. 重点说明
- `runtime` 是系统执行稳定性的核心，强调状态正确性、恢复性与可观测性。
- 控制状态机、步骤状态机、恢复动作三层共同确保执行收敛。
- 所有图统一使用 `graph TB`，与其它包文档保持一致。

