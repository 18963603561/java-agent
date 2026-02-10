# `reasoning` 包系统设计说明

## 1. 包定位与核心功能
- 包定位：`reasoning` 提供多推理策略编排与选优能力。
- 核心功能：
  - `ReasoningOrchestrator` 支持串行降级与并行候选执行。
  - `ReasoningStrategyRegistry` 管理 COT、ThoughtTree、Debate 等策略。
  - `ReasoningArbitrator` 对并行候选进行仲裁选优。
  - `ReasoningDegradePolicy` 维护降级顺序。
  - `ReasoningMetricsPublisher` 输出推理指标。

## 2. 分层线框图
```mermaid
graph TB
    A[Runtime 输入] --> B[ReasoningOrchestrator]
    B --> C[ReasoningExecutionPlan]
    B --> D[ReasoningStrategyRegistry]
    D --> E[ChainOfThoughtService]
    D --> F[ThoughtTreeService]
    D --> G[DebateCoordinator]
    B --> H[ReasoningArbitrator]
    H --> I[ReasoningResult]
```

## 3. 关键流程图
```mermaid
graph TB
    A[推理请求] --> B[解析候选策略]
    B --> C{并行模式?}
    C -->|否| D[按降级顺序串行执行]
    D -->|成功| H[返回结果]
    D -->|全部失败| X[抛出 all_strategies_failed]
    C -->|是| E[并发执行]
    E --> F[收集候选结果]
    F --> G[Arbitrator 选优]
    G --> H
```

## 4. 状态流转图
```mermaid
graph TB
    S0[REASONING_INIT] --> S1[STRATEGY_SELECTED]
    S1 --> S2[EXECUTING]
    S2 -->|成功| S6[COMPLETED]
    S2 -->|失败可降级| S3[DEGRADE_TO_NEXT]
    S3 --> S2
    S2 -->|并行| S4[ARBITRATING]
    S4 -->|选优成功| S6
    S4 -->|失败| S5[FAILED]
    S3 -->|无可降级| S5
```

## 5. 类职责与设计原因
- `ReasoningOrchestrator`：统一执行模式决策，降低调用复杂度。
- `ReasoningStrategyRegistry`：解耦策略实现与编排。
- `ReasoningArbitrator`：统一候选质量判断标准。
- `ReasoningDegradePolicy`：降级链路配置化。
- 各策略服务：按推理范式分治实现。

## 6. 关键类直接关系图
```mermaid
graph TB
    A[ReasoningOrchestrator] --> B[ReasoningStrategyRegistry]
    A --> C[ReasoningArbitrator]
    A --> D[ReasoningDegradePolicy]
    A --> E[ReasoningMetricsPublisher]
    B --> F[ChainOfThoughtService]
    B --> G[ThoughtTreeService]
    B --> H[DebateCoordinator]
```

## 7. 重点说明
- 通过策略组合平衡稳定性和效果上限。
- 串行降级机制可降低推理链路整体失败率。
- 所有图统一使用 `graph TB`。

