# `history` 包系统设计说明

## 1. 包定位与核心功能
- 包定位：`history` 负责事件持久化、分页检索和时间线聚合输出。
- 核心功能：
  - `EventLogService` 监听 `StreamEvent` 并落盘。
  - 过滤高频 `LLM_PARTIAL` 事件降低噪声。
  - `TimelineService` 输出 full/summary 视图。
  - 支持 InMemory/JDBC 两类仓储实现。

## 2. 分层线框图
```mermaid
graph TB
    A[StreamEvent 总线] --> B[EventLogService]
    B --> C[EventLogRepository]
    C --> D[InMemory/JDBC]
    E[TimelineService] --> C
    E --> F[TimelineResponse]
```

## 3. 关键流程图
```mermaid
graph TB
    A[接收事件] --> B[校验 tenant/workflow]
    B --> C{是否 LLM_PARTIAL}
    C -->|是| D[丢弃]
    C -->|否| E[保存 EventLogRecord]
    E --> F[按 seq/timestamp 排序]
    F --> G[按 cursor/size 分页]
    G --> H[生成时间线视图]
    H --> I[返回]
```

## 4. 状态流转图
```mermaid
graph TB
    S0[EVENT_RECEIVED] --> S1[FILTERING]
    S1 -->|过滤| S2[DROPPED]
    S1 -->|通过| S3[PERSISTED]
    S3 --> S4[QUERYABLE]
    S4 --> S5[TIMELINE_BUILT]
    S5 --> S6[DELIVERED]
```

## 5. 类职责与设计原因
- `EventLogService`：集中事件落盘，避免调用方重复实现。
- `EventLogRepository`：抽象存储，便于实现替换。
- `TimelineService`：聚合展示视图，不耦合写入流程。
- `EventLogRecord`/`TimelineResponse`：区分原始事实和展示模型。

## 6. 关键类直接关系图
```mermaid
graph TB
    A[EventLogService] --> B[EventLogRepository]
    B --> C[InMemoryEventLogRepository]
    B --> D[JdbcEventLogRepository]
    E[TimelineService] --> B
    E --> F[TimelineResponse]
```

## 7. 重点说明
- 读写分离便于历史能力独立演进。
- 序列号优先排序保证回放时序稳定。
- 所有图统一使用 `graph TB`。

