# `governance` 包系统设计说明

## 1. 包定位与核心功能
- 包定位：`governance` 提供执行治理与风险控制能力。
- 核心功能：
  - `CapabilityBoundaryEvaluator` 风险评估与策略建议。
  - `ApprovalService` 高风险操作审批流程管理。
  - `RateLimitService` 窗口限流保护。
  - `CircuitBreakerManager` 连续失败熔断与恢复。
  - `ReplayService` 回放能力支撑审计复盘。

## 2. 分层线框图
```mermaid
graph TB
    A[Runtime/Planning 请求] --> B[CapabilityBoundaryEvaluator]
    B --> C[PolicyEngine]
    C --> D[ApprovalService]
    C --> E[RateLimitService]
    C --> F[CircuitBreakerManager]
    D --> G[PendingApprovalStore]
    E --> H[RateLimitStore]
    F --> I[CircuitStateStore]
    J[ReplayService] --> K[ReplaySessionStore]
```

## 3. 关键流程图
```mermaid
graph TB
    A[请求进入] --> B[风险评估]
    B --> C[策略判定]
    C -->|拒绝| X[POLICY_DENIED]
    C -->|需审批| D[创建审批请求]
    D --> E[等待审批]
    E -->|通过| F[继续执行]
    E -->|拒绝/超时| Y[终止]
    C -->|无需审批| F
    F --> G[限流检查]
    G -->|超限| Z[REJECTED_THRESHOLD]
    G -->|通过| H[熔断检查]
    H -->|OPEN| W[REJECTED_OPEN_STATE]
    H -->|通过| I[放行]
```

## 4. 状态流转图
```mermaid
graph TB
    A0[EVAL_STARTED] --> A1[RISK_LOW]
    A0 --> A2[RISK_MEDIUM]
    A0 --> A3[RISK_HIGH]
    A3 --> B0[APPROVAL_PENDING]
    B0 --> B1[APPROVED]
    B0 --> B2[REJECTED]
    B0 --> B3[TIMEOUT]
    C0[CIRCUIT_CLOSED] -->|失败超阈值| C1[CIRCUIT_OPEN]
    C1 -->|冷却后| C2[HALF_RECOVERED]
    C2 -->|探测成功| C0
    C2 -->|探测失败| C1
    D0[RATE_WINDOW_ACTIVE] -->|超阈值| D1[RATE_REJECTED]
    D1 -->|窗口滚动| D0
```

## 5. 类职责与设计原因
- `CapabilityBoundaryEvaluator`：先评估后执行，降低高风险误执行。
- `ApprovalService`：审批状态化，支持异步等待和外部决策。
- `RateLimitService`：提供流量护栏，保护下游。
- `CircuitBreakerManager`：阻断错误风暴扩散。
- `ReplayService`：补齐审计与复盘闭环。

## 6. 关键类直接关系图
```mermaid
graph TB
    A[CapabilityBoundaryEvaluator] --> B[CapabilityRuleRegistry]
    C[ApprovalService] --> D[PendingApprovalStore]
    C --> E[ApprovalDecisionAwaiter]
    F[RateLimitService] --> G[RateLimitStore]
    H[CircuitBreakerManager] --> I[CircuitStateStore]
    J[ReplayService] --> K[ReplayTaskResolver]
    J --> L[ReplayItemAssembler]
    J --> M[ReplaySessionStore]
```

## 7. 重点说明
- 治理能力是执行链路内建，不是外挂逻辑。
- 审批、限流、熔断、回放分别覆盖人治、流量、稳定、审计。
- 所有图统一使用 `graph TB`。

