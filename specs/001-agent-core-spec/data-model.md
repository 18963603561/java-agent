# Data Model: 001-agent-core-spec

**Date**: 2026-01-25
**Spec**: `F:\ai-code\java-agent\specs\001-agent-core-spec\spec.md`

## Task
- Fields: `taskId`、`workflowId`、`status`、`request`、`result`、`createdAt`、`updatedAt`、`tenantId`
- Relationships: 1 个 `Task` 对应多条 `WorkflowEvent` 与 1 个 `TaskTimeline`
- Validation: `taskId` 唯一；`status` 必须在允许枚举内

## WorkflowEvent
- Fields: `eventId`、`workflowId`、`type`、`seq`、`timestamp`、`payload`、`agentId`、`streamId`、`tenantId`
- Relationships: 多条 `WorkflowEvent` 归属 1 个 `Task`
- Validation: `seq` 单调递增；`type` 必须为 `EventType` 枚举值

## TaskTimeline
- Fields: `timelineId`、`workflowId`、`mode`、`events`、`stats`、`generatedAt`、`tenantId`
- Relationships: 1 个 `TaskTimeline` 归属 1 个 `Task`
- Validation: `mode` 仅允许 `summary` 与 `full`

## EventLogRecord
- Fields: `eventId`、`workflowId`、`type`、`timestamp`、`payload`、`tenantId`
- Relationships: 归属 1 个 `Task`
- Validation: `eventId` 唯一；关键事件必须落库

## MemoryRecord
- Fields: `memoryId`、`sessionId`、`taskId`、`content`、`summary`、`embeddingRef`、`createdAt`、`tenantId`、`layer`
- Relationships: 归属 1 个 `Task` 或 `sessionId`
- Validation: `layer` 仅允许 `recent`、`semantic`、`compressed`

## MemoryChunk
- Fields: `chunkId`、`memoryId`、`content`、`embeddingRef`、`position`
- Relationships: 多个 `MemoryChunk` 归属 1 个 `MemoryRecord`
- Validation: `position` 连续递增

## Schedule
- Fields: `scheduleId`、`cron`、`timezone`、`status`、`maxRuns`、`minInterval`、`budgetLimit`、`nextRunAt`、`tenantId`
- Relationships: 1 个 `Schedule` 对应多条 `ScheduleExecutionRecord`
- Validation: `cron` 合法；`timezone` 必填

## ScheduleExecutionRecord
- Fields: `executionId`、`scheduleId`、`status`、`startedAt`、`endedAt`、`costUsd`、`tokenUsage`、`tenantId`
- Relationships: 归属 1 个 `Schedule`
- Validation: `status` 必须在允许枚举内

## TokenUsageRecord
- Fields: `recordId`、`taskId`、`agentId`、`model`、`provider`、`inputTokens`、`outputTokens`、`totalTokens`、`costUsd`、`createdAt`、`tenantId`
- Relationships: 归属 1 个 `Task`
- Validation: `totalTokens = inputTokens + outputTokens`

## TokenUsageSummary
- Fields: `taskId`、`totalTokens`、`totalCostUsd`、`byModel`、`byProvider`
- Relationships: 归属 1 个 `Task`
- Validation: 汇总字段不可为空

## TenantContext
- Fields: `tenantId`、`userId`、`roles`、`apiKeyId`、`requestId`、`traceId`
- Relationships: 请求级上下文，与所有数据访问强关联
- Validation: `tenantId` 必填
