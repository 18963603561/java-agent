# `streaming` 包系统设计说明

## 1. 包定位与核心功能
- 包定位：`streaming` 是实时事件总线，提供事件入流、订阅推送、历史游标和可观测能力。
- 核心功能：
  - `EventStreamService` 负责索引、序列号、Redis 持久化与订阅过滤。
  - `SseStreamController` 提供 SSE 接口与首事件超时控制。
  - `ContextEventPublisher` 发布上下文阶段事件。
  - `EventType` 统一全系统事件语义。
  - `MetricsPublisher`/`TracingPublisher` 支撑链路观测。

## 2. 分层线框图
```mermaid
graph TB
    A[模块发布 StreamEvent] --> B[EventStreamService]
    B --> C[内存 sink 广播]
    B --> D[streamIndex/sequenceCounters]
    B --> E[Redis Stream]
    F[SseStreamController] --> B
    F --> G[SSE 输出]
    H[ContextEventPublisher] --> B
```

## 3. 关键流程图
```mermaid
graph TB
    A[内部发布事件] --> B[onStreamEvent]
    B --> C[同步序列号]
    C --> D[更新索引]
    D --> E[异步写 Redis]
    E --> F[sink 广播]
    G[SSE 订阅请求] --> H[校验租户与游标]
    H --> I[加载历史事件]
    I --> J[拼接实时流]
    J --> K[按类型过滤]
    K --> L[输出 SSE]
```

## 4. 状态流转图
```mermaid
graph TB
    S0[EVENT_CREATED] --> S1[INDEX_UPDATED]
    S1 --> S2[REDIS_PERSISTED]
    S2 --> S3[LIVE_BROADCASTED]
    S3 --> S4[SUBSCRIBER_FILTERED]
    S4 --> S5[SSE_DELIVERED]
    S6[SUBSCRIBE_INIT] --> S7[CURSOR_VALIDATED]
    S7 -->|有效| S8[HISTORY_REPLAY]
    S7 -->|无效| S9[INVALID_CURSOR_ERROR]
    S8 --> S3
```

## 5. 类职责与设计原因
- `EventStreamService`：统一流式能力，防止多模块重复建设通道。
- `SseStreamController`：专注协议层、认证、超时与游标处理。
- `EventType`：统一事件语义，降低协作歧义。
- `ContextEventPublisher`：使上下文构建过程可追踪。
- `MetricsPublisher`/`TracingPublisher`：支持性能和故障诊断。

## 6. 关键类直接关系图
```mermaid
graph TB
    A[SseStreamController] --> B[AuthService]
    A --> C[EventStreamService]
    C --> D[StreamEvent]
    C --> E[StringRedisTemplate]
    C --> F[Sinks.Many]
    G[ContextEventPublisher] --> C
    H[MetricsPublisher] --> C
    I[TracingPublisher] --> A
```

## 7. 重点说明
- 历史回放与实时订阅统一于同一服务，语义一致。
- 序列号机制是断线续传和时序稳定的关键。
- 所有图统一使用 `graph TB`。

