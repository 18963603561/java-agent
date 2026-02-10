# 智能体系统总体架构设计说明

## 1. 模块总览
- 覆盖核心包：`api`、`bootstrap`、`budget`、`capabilities`、`common`、`governance`、`history`、`orchestration`、`planning`、`reasoning`、`reflection`、`runtime`、`scheduler`、`security`、`streaming`。
- 架构目标：多租户前提下实现可规划、可执行、可治理、可观测、可回放的智能体系统。
- 主链路：`api -> orchestration -> runtime -> capabilities -> streaming`。

## 2. 总体分层线框图
```mermaid
graph TB
    U[客户端/外部系统] --> A[api 接入层]
    A --> B[orchestration 编排层]
    B --> C[runtime 执行层]
    C --> D[planning 规划层]
    C --> E[capabilities 能力层]
    C --> F[reasoning 推理层]
    C --> G[reflection 反思层]
    E --> H[budget 预算层]
    C --> I[governance 治理层]
    A --> J[security 安全层]
    C --> K[streaming 事件层]
    K --> L[history 历史层]
    A --> M[scheduler 调度层]
    N[common 公共契约层] --> A
    N --> B
    N --> C
    O[bootstrap 启动层] --> A
    O --> B
    O --> C
```

## 3. 总体流程图
```mermaid
graph TB
    A[请求进入 API] --> B[租户解析 + 鉴权]
    B --> C[TaskOrchestrator 提交任务]
    C --> D[TaskStatus: SUBMITTED -> RUNNING]
    D --> E[AgentRuntime.prepare]
    E --> F[PlannerService.plan]
    F --> G[StepExecutionCoordinator]
    G --> H[Capabilities 执行: 上下文/模型/工具/记忆]
    H --> I[ReflectionService 反思评估]
    I --> J{是否重试/重规划}
    J -->|重试| G
    J -->|重规划| F
    J -->|通过| K[RuntimeFinalizationService]
    K --> L[TaskStatus: COMPLETED/FAILED]
    L --> M[EventStreamService 发布事件]
    M --> N[SSE 客户端实时消费]
    M --> O[History 持久化与时间线]
```

## 4. 总体状态流转图
```mermaid
graph TB
    subgraph TaskState[任务状态]
        T0[SUBMITTED] --> T1[RUNNING]
        T1 --> T2[COMPLETED]
        T1 --> T3[FAILED]
    end

    subgraph WorkflowControl[工作流控制]
        W0[RUNNING] --> W1[PAUSED]
        W1 --> W0
        W0 --> W2[WAIT_APPROVAL]
        W2 -->|approve| W0
        W2 -->|reject| W3[CANCELLED]
        W0 --> W3
    end

    subgraph StepState[步骤状态]
        S0[PENDING] --> S1[STARTED]
        S1 --> S2[COMPLETED]
        S1 --> S3[FAILED]
        S1 --> S4[WAITING]
        S4 --> S1
        S4 --> S3
        S0 --> S5[SKIPPED]
    end

    subgraph ApprovalState[审批状态]
        A0[PENDING] --> A1[APPROVED]
        A0 --> A2[REJECTED]
        A0 --> A3[TIMEOUT]
    end

    subgraph ScheduleState[调度状态]
        C0[ACTIVE] --> C1[PAUSED]
        C1 --> C0
        C0 --> C2[CANCELLED]
        C0 --> C3[TRIGGERED]
        C3 --> C0
    end

    T1 --> W0
    W2 --> A0
    T1 --> S0
    C3 --> T0
```

## 5. 模块直接关系图
```mermaid
graph TB
    A[api] --> B[orchestration]
    B --> C[runtime]
    C --> D[planning]
    C --> E[capabilities]
    C --> F[reasoning]
    C --> G[reflection]
    C --> H[streaming]
    E --> I[budget]
    C --> J[governance]
    A --> K[security]
    C --> K
    H --> L[history]
    A --> M[scheduler]
    M --> H
    N[common] --> A
    N --> B
    N --> C
    O[bootstrap] --> A
```

## 6. 架构重点说明
- 状态驱动：任务、工作流、步骤、审批、调度都有清晰状态机。
- 执行闭环：`planning -> runtime -> reflection -> recovery` 构成自修复闭环。
- 治理内建：审批、限流、熔断、回放是主链路内建能力。
- 可观测优先：关键节点全部事件化，支持实时观察与历史复盘。
- 安全左移：租户解析、鉴权、脱敏前置到入口和能力层。

## 7. 演进建议
- 持续保持 `orchestration` 与 `runtime` 边界稳定。
- 对 `capabilities` 高复杂子域继续解耦。
- 基于历史事件和预算数据做动态阈值调优。
- 保持状态图与事件枚举同步维护。

