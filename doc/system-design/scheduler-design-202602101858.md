# `scheduler` 包系统设计说明

## 1. 包定位与核心功能
- 包定位：`scheduler` 负责计划任务管理与 Cron 触发，并把触发行为接入统一事件流。
- 核心功能：
  - `ScheduleManager` 负责创建、更新、暂停、恢复、取消、删除、分页查询。
  - `ScheduleEngine` 负责调度注册、反注册、Cron 到点触发。
  - `ScheduleRepository` 与 `ScheduleExecutionRepository` 管理配置与执行记录。
  - 触发时发布 `SCHEDULE_TRIGGERED` 事件供下游消费。

## 2. 分层线框图
```mermaid
graph TB
    A[ScheduleController] --> B[ScheduleManager]
    B --> C[ScheduleRepository]
    B --> D[ScheduleExecutionRepository]
    B --> E[ScheduleEngine]
    E --> F[ThreadPoolTaskScheduler]
    E --> G[EventStreamService]
    E --> H[ApplicationEventPublisher]
```

## 3. 关键流程图
```mermaid
graph TB
    A[创建/更新调度] --> B[ScheduleManager.save]
    B --> C[ScheduleEngine.register]
    C --> D[CronTrigger 注册]
    D --> E[到点 triggerExecution]
    E --> F[保存 ScheduleExecutionRecord]
    F --> G[发布 SCHEDULE_TRIGGERED]
    A --> H[pause/resume/cancel]
    H --> I[register/unregister]
```

## 4. 状态流转图
```mermaid
graph TB
    S0[NEW] --> S1[ACTIVE]
    S1 -->|pause| S2[PAUSED]
    S2 -->|resume| S1
    S1 -->|cancel| S3[CANCELLED]
    S2 -->|cancel| S3
    S1 -->|trigger| S4[TRIGGERED]
    S4 --> S1
    S3 --> S5[TERMINAL]
```

## 5. 类职责与设计原因
- `ScheduleManager`：统一管理入口与租户边界校验，避免配置操作分散。
- `ScheduleEngine`：专注执行引擎职责，隔离调度框架细节。
- `ScheduleRepository`：抽象调度配置存储，支持内存/数据库切换。
- `ScheduleExecutionRepository`：记录执行事实，支撑审计与统计。

## 6. 关键类直接关系图
```mermaid
graph TB
    A[ScheduleManager] --> B[ScheduleRepository]
    A --> C[ScheduleExecutionRepository]
    A --> D[ScheduleEngine]
    D --> E[ThreadPoolTaskScheduler]
    D --> F[EventStreamService]
    D --> G[ApplicationEventPublisher]
```

## 7. 重点说明
- 调度配置状态与执行记录分离，便于管理与审计。
- 调度触发通过事件总线进入系统观测链路。
- 所有图统一使用 `graph TB`。

