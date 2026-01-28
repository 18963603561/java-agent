# `Java` `Bean` æ¥å£ä¸éæåéæ³¨éç¼ºå¤±æ«æ

## æ«æèå´
- `src/main/java`

## è¯å«è§å
- `Bean` ç±»è¯å«: å«å¸¸è§ `Bean`/æ¡æ¶æ³¨è§£ (å¦ `Component`/`Service`/`ConfigurationProperties`, `Lombok` `Data` ç­), æç±»åæ»¡è¶³å¸¸è§æ°æ®åç¼ (å¦ `Dto`/`Request`/`Response`/`Result`/`Properties` ç­), æå
è·¯å¾å
å« `domain`/`model`/`dto`/`entity`/`request`/`response`/`event`/`contracts`.
- æ³¨éè¯å«: å­æ®µææ¥å£å£°æè¡å­å¨è¡å
æ³¨é, æä¸æ¹æè¿çéç©ºéæ³¨è§£è¡å­å¨æ³¨é.
- éæåé: å
å« `static` ä¿®é¥°ç¬¦çå­æ®µå£°æ.
- æ¥å£: `interface` å£°æ.

## æªå æ³¨éçæ¥å£
| æä»¶ | è¡å· | æ¥å£å |
| --- | --- | --- |
| `src/main/java/com/example/agent/agentcore/ToolRegistry.java` | 113 | `ToolHandler` |

## æªå æ³¨éçéæåé
| æä»¶ | è¡å· | æå±ç±»å | åéå |
| --- | --- | --- | --- |
| `src/main/java/com/example/agent/AgentApplication.java` | 14 | `AgentApplication` | `log` |
| `src/main/java/com/example/agent/agentcore/ToolCache.java` | 22 | `ToolCache` | `log` |
| `src/main/java/com/example/agent/agentcore/ToolRegistry.java` | 20 | `ToolRegistry` | `log` |
| `src/main/java/com/example/agent/auth/TenantContext.java` | 11 | `TenantContext` | `CONTEXT_KEY` |
| `src/main/java/com/example/agent/auth/TenantContextFilter.java` | 20 | `TenantContextFilter` | `log` |
| `src/main/java/com/example/agent/auth/TenantResolver.java` | 15 | `TenantResolver` | `HEADER_TENANT_ID` |
| `src/main/java/com/example/agent/auth/TenantResolver.java` | 16 | `TenantResolver` | `HEADER_REQUEST_ID` |
| `src/main/java/com/example/agent/auth/TenantResolver.java` | 17 | `TenantResolver` | `HEADER_TRACE_ID` |
| `src/main/java/com/example/agent/auth/UserContext.java` | 10 | `UserContext` | `CONTEXT_KEY` |
| `src/main/java/com/example/agent/budget/ContextCompressionController.java` | 44 | `ContextCompressionController` | `log` |
| `src/main/java/com/example/agent/budget/DefaultContextBudgetAllocator.java` | 17 | `DefaultContextBudgetAllocator` | `log` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 29 | `TokenBudgetManager` | `log` |
| `src/main/java/com/example/agent/contracts/OpenApiConfig.java` | 21 | `OpenApiConfig` | `log` |
| `src/main/java/com/example/agent/evaluation/CapabilityBoundaryEvaluator.java` | 25 | `CapabilityBoundaryEvaluator` | `log` |
| `src/main/java/com/example/agent/evaluation/CapabilityBoundaryEvaluator.java` | 26 | `CapabilityBoundaryEvaluator` | `MAX_PREVIEW_CHARS` |
| `src/main/java/com/example/agent/gateway/controller/ApprovalController.java` | 39 | `ApprovalController` | `log` |
| `src/main/java/com/example/agent/gateway/controller/BudgetController.java` | 32 | `BudgetController` | `log` |
| `src/main/java/com/example/agent/gateway/controller/MemoryController.java` | 30 | `MemoryController` | `log` |
| `src/main/java/com/example/agent/gateway/controller/PolicyController.java` | 28 | `PolicyController` | `log` |
| `src/main/java/com/example/agent/gateway/controller/ReplayController.java` | 28 | `ReplayController` | `log` |
| `src/main/java/com/example/agent/gateway/controller/ScheduleController.java` | 36 | `ScheduleController` | `log` |
| `src/main/java/com/example/agent/gateway/controller/TaskController.java` | 37 | `TaskController` | `log` |
| `src/main/java/com/example/agent/gateway/controller/TimelineController.java` | 32 | `TimelineController` | `log` |
| `src/main/java/com/example/agent/gateway/controller/ToolApprovalController.java` | 30 | `ToolApprovalController` | `log` |
| `src/main/java/com/example/agent/governance/ReplayService.java` | 37 | `ReplayService` | `log` |
| `src/main/java/com/example/agent/history/EventLogService.java` | 20 | `EventLogService` | `log` |
| `src/main/java/com/example/agent/memory/CompressedMemoryStore.java` | 21 | `CompressedMemoryStore` | `log` |
| `src/main/java/com/example/agent/memory/CompressedMemoryStore.java` | 22 | `CompressedMemoryStore` | `SUMMARY_VERSION` |
| `src/main/java/com/example/agent/memory/CompressedMemoryStore.java` | 23 | `CompressedMemoryStore` | `MAX_BULLET_COUNT` |
| `src/main/java/com/example/agent/memory/CompressedMemoryStore.java` | 24 | `CompressedMemoryStore` | `MAX_ITEM_COUNT` |
| `src/main/java/com/example/agent/memory/CompressedMemoryStore.java` | 25 | `CompressedMemoryStore` | `MAX_ITEM_CHARS` |
| `src/main/java/com/example/agent/memory/CompressedMemoryStore.java` | 26 | `CompressedMemoryStore` | `MAX_SUMMARY_CHARS` |
| `src/main/java/com/example/agent/memory/SemanticMemoryStore.java` | 18 | `SemanticMemoryStore` | `log` |
| `src/main/java/com/example/agent/model/DefaultLlmClient.java` | 13 | `DefaultLlmClient` | `log` |
| `src/main/java/com/example/agent/model/ModelInvocationService.java` | 24 | `ModelInvocationService` | `log` |
| `src/main/java/com/example/agent/model/ModelRouter.java` | 15 | `ModelRouter` | `log` |
| `src/main/java/com/example/agent/multiagent/AgentGraphExecutor.java` | 13 | `AgentGraphExecutor` | `log` |
| `src/main/java/com/example/agent/multiagent/HandoffService.java` | 16 | `HandoffService` | `log` |
| `src/main/java/com/example/agent/multiagent/SupervisorCoordinator.java` | 13 | `SupervisorCoordinator` | `log` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 41 | `TaskOrchestrator` | `log` |
| `src/main/java/com/example/agent/policy/PolicyEngine.java` | 18 | `PolicyEngine` | `log` |
| `src/main/java/com/example/agent/reasoning/ThoughtTreeService.java` | 20 | `ThoughtTreeService` | `log` |
| `src/main/java/com/example/agent/runtime/ExecutionControlService.java` | 19 | `ExecutionControlService` | `log` |
| `src/main/java/com/example/agent/runtime/StepRuntimeService.java` | 31 | `StepRuntimeService` | `log` |
| `src/main/java/com/example/agent/sandbox/WasiSandboxExecutor.java` | 18 | `WasiSandboxExecutor` | `log` |
| `src/main/java/com/example/agent/scheduler/ScheduleEngine.java` | 30 | `ScheduleEngine` | `log` |
| `src/main/java/com/example/agent/scheduler/ScheduleManager.java` | 23 | `ScheduleManager` | `log` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 16 | `RedactionService` | `SECRET_PATTERN` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 18 | `RedactionService` | `EMAIL_PATTERN` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 20 | `RedactionService` | `PHONE_PATTERN` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 21 | `RedactionService` | `ID_PATTERN` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 23 | `RedactionService` | `SECRET_MASK` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 24 | `RedactionService` | `EMAIL_MASK` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 25 | `RedactionService` | `PHONE_MASK` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 26 | `RedactionService` | `ID_MASK` |
| `src/main/java/com/example/agent/streaming/SseStreamController.java` | 41 | `SseStreamController` | `log` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 36 | `McpToolClient` | `log` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 40 | `HookManager` | `log` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 49 | `HookManager` | `MAX_RECORDS` |
| `src/main/java/com/example/agent/tools/plugin/PluginLoader.java` | 25 | `PluginLoader` | `log` |
| `src/main/java/com/example/agent/tools/plugin/PluginLoader.java` | 26 | `PluginLoader` | `DEFAULT_DESCRIPTOR` |
| `src/main/java/com/example/agent/tools/skill/SkillRegistry.java` | 15 | `SkillRegistry` | `log` |

## `Bean` å±æ§æªå æ³¨é
| æä»¶ | è¡å· | `Bean` ç±» | å±æ§å |
| --- | --- | --- | --- |
| `src/main/java/com/example/agent/agentcore/SandboxExecutor.java` | 17 | `SandboxExecutor` | `wasiSandboxExecutor` |
| `src/main/java/com/example/agent/agentcore/ToolCache.java` | 24 | `ToolCache` | `cache` |
| `src/main/java/com/example/agent/agentcore/ToolCache.java` | 25 | `ToolCache` | `redisTemplateProvider` |
| `src/main/java/com/example/agent/agentcore/ToolCache.java` | 26 | `ToolCache` | `objectMapper` |
| `src/main/java/com/example/agent/agentcore/ToolCache.java` | 29 | `ToolCache` | `redisEnabled` |
| `src/main/java/com/example/agent/agentcore/ToolCache.java` | 32 | `ToolCache` | `redisKeyPrefix` |
| `src/main/java/com/example/agent/agentcore/ToolCache.java` | 35 | `ToolCache` | `defaultTtlSeconds` |
| `src/main/java/com/example/agent/agentcore/ToolRegistry.java` | 22 | `ToolRegistry` | `handlers` |
| `src/main/java/com/example/agent/agentcore/ToolRegistry.java` | 23 | `ToolRegistry` | `definitions` |
| `src/main/java/com/example/agent/auth/TenantContextFilter.java` | 22 | `TenantContextFilter` | `tenantResolver` |
| `src/main/java/com/example/agent/auth/TenantContextFilter.java` | 23 | `TenantContextFilter` | `tenantProperties` |
| `src/main/java/com/example/agent/budget/ContextCompressionController.java` | 46 | `ContextCompressionController` | `memoryStore` |
| `src/main/java/com/example/agent/budget/ContextCompressionController.java` | 47 | `ContextCompressionController` | `tokenEstimator` |
| `src/main/java/com/example/agent/budget/ContextCompressionController.java` | 48 | `ContextCompressionController` | `metricsPublisher` |
| `src/main/java/com/example/agent/budget/ContextCompressionController.java` | 49 | `ContextCompressionController` | `properties` |
| `src/main/java/com/example/agent/budget/ContextCompressionController.java` | 50 | `ContextCompressionController` | `lastCompressedAt` |
| `src/main/java/com/example/agent/budget/DefaultContextBudgetAllocator.java` | 19 | `DefaultContextBudgetAllocator` | `properties` |
| `src/main/java/com/example/agent/budget/DefaultContextBudgetAllocator.java` | 20 | `DefaultContextBudgetAllocator` | `metricsPublisher` |
| `src/main/java/com/example/agent/budget/DefaultContextBudgetAllocator.java` | 21 | `DefaultContextBudgetAllocator` | `tokenBudgetManager` |
| `src/main/java/com/example/agent/budget/InMemoryTokenUsageRepository.java` | 16 | `InMemoryTokenUsageRepository` | `usageIndex` |
| `src/main/java/com/example/agent/budget/InMemoryTokenUsageRepository.java` | 17 | `InMemoryTokenUsageRepository` | `taskIndex` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 31 | `TokenBudgetManager` | `repository` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 32 | `TokenBudgetManager` | `costCalculator` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 33 | `TokenBudgetManager` | `modelRegistry` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 34 | `TokenBudgetManager` | `fallbackPolicy` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 35 | `TokenBudgetManager` | `eventPublisher` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 36 | `TokenBudgetManager` | `eventStreamService` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 37 | `TokenBudgetManager` | `metricsPublisher` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 40 | `TokenBudgetManager` | `enabled` |
| `src/main/java/com/example/agent/budget/TokenBudgetManager.java` | 43 | `TokenBudgetManager` | `thresholdTokens` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 10 | `TokenUsageRecord` | `recordId` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 11 | `TokenUsageRecord` | `usageId` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 12 | `TokenUsageRecord` | `taskId` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 13 | `TokenUsageRecord` | `agentId` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 14 | `TokenUsageRecord` | `model` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 15 | `TokenUsageRecord` | `provider` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 16 | `TokenUsageRecord` | `inputTokens` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 17 | `TokenUsageRecord` | `outputTokens` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 18 | `TokenUsageRecord` | `totalTokens` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 19 | `TokenUsageRecord` | `costUsd` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 20 | `TokenUsageRecord` | `createdAt` |
| `src/main/java/com/example/agent/budget/TokenUsageRecord.java` | 21 | `TokenUsageRecord` | `tenantId` |
| `src/main/java/com/example/agent/budget/TokenUsageSummary.java` | 10 | `TokenUsageSummary` | `taskId` |
| `src/main/java/com/example/agent/budget/TokenUsageSummary.java` | 11 | `TokenUsageSummary` | `totalTokens` |
| `src/main/java/com/example/agent/budget/TokenUsageSummary.java` | 12 | `TokenUsageSummary` | `totalCostUsd` |
| `src/main/java/com/example/agent/budget/TokenUsageSummary.java` | 13 | `TokenUsageSummary` | `byModel` |
| `src/main/java/com/example/agent/budget/TokenUsageSummary.java` | 14 | `TokenUsageSummary` | `byProvider` |
| `src/main/java/com/example/agent/common/ApiResponse.java` | 10 | `ApiResponse` | `code` |
| `src/main/java/com/example/agent/common/ApiResponse.java` | 11 | `ApiResponse` | `message` |
| `src/main/java/com/example/agent/common/ApiResponse.java` | 12 | `ApiResponse` | `data` |
| `src/main/java/com/example/agent/common/ApiResponse.java` | 13 | `ApiResponse` | `traceId` |
| `src/main/java/com/example/agent/common/ApiResponse.java` | 14 | `ApiResponse` | `requestId` |
| `src/main/java/com/example/agent/common/ErrorResponse.java` | 10 | `ErrorResponse` | `code` |
| `src/main/java/com/example/agent/common/ErrorResponse.java` | 11 | `ErrorResponse` | `message` |
| `src/main/java/com/example/agent/common/ErrorResponse.java` | 12 | `ErrorResponse` | `details` |
| `src/main/java/com/example/agent/common/ErrorResponse.java` | 13 | `ErrorResponse` | `traceId` |
| `src/main/java/com/example/agent/common/ErrorResponse.java` | 14 | `ErrorResponse` | `requestId` |
| `src/main/java/com/example/agent/common/TaskListResponse.java` | 10 | `TaskListResponse` | `tasks` |
| `src/main/java/com/example/agent/common/TaskListResponse.java` | 11 | `TaskListResponse` | `nextCursor` |
| `src/main/java/com/example/agent/common/TaskListResponse.java` | 12 | `TaskListResponse` | `hasMore` |
| `src/main/java/com/example/agent/common/TaskListResponse.java` | 13 | `TaskListResponse` | `total` |
| `src/main/java/com/example/agent/common/TaskResponse.java` | 8 | `TaskResponse` | `taskId` |
| `src/main/java/com/example/agent/common/TaskResponse.java` | 9 | `TaskResponse` | `workflowId` |
| `src/main/java/com/example/agent/common/TaskResponse.java` | 10 | `TaskResponse` | `status` |
| `src/main/java/com/example/agent/common/TaskStatusResponse.java` | 11 | `TaskStatusResponse` | `taskId` |
| `src/main/java/com/example/agent/common/TaskStatusResponse.java` | 12 | `TaskStatusResponse` | `workflowId` |
| `src/main/java/com/example/agent/common/TaskStatusResponse.java` | 13 | `TaskStatusResponse` | `status` |
| `src/main/java/com/example/agent/common/TaskStatusResponse.java` | 14 | `TaskStatusResponse` | `updatedAt` |
| `src/main/java/com/example/agent/common/TaskStatusResponse.java` | 15 | `TaskStatusResponse` | `result` |
| `src/main/java/com/example/agent/context/DefaultContextAssembler.java` | 24 | `DefaultContextAssembler` | `promptTemplate` |
| `src/main/java/com/example/agent/context/DefaultContextAssembler.java` | 25 | `DefaultContextAssembler` | `tokenEstimator` |
| `src/main/java/com/example/agent/contracts/OpenApiConfig.java` | 23 | `OpenApiConfig` | `objectMapper` |
| `src/main/java/com/example/agent/contracts/OpenApiConfig.java` | 26 | `OpenApiConfig` | `contractPath` |
| `src/main/java/com/example/agent/contracts/OpenApiController.java` | 14 | `OpenApiController` | `documentStore` |
| `src/main/java/com/example/agent/contracts/OpenApiDocumentStore.java` | 8 | `OpenApiDocumentStore` | `json` |
| `src/main/java/com/example/agent/domain/event/StreamCursor.java` | 8 | `StreamCursor` | `streamId` |
| `src/main/java/com/example/agent/domain/event/StreamCursor.java` | 9 | `StreamCursor` | `seq` |
| `src/main/java/com/example/agent/domain/event/StreamCursor.java` | 10 | `StreamCursor` | `eventId` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 11 | `StreamEvent` | `eventId` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 12 | `StreamEvent` | `schemaVersion` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 13 | `StreamEvent` | `workflowId` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 14 | `StreamEvent` | `type` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 15 | `StreamEvent` | `agentId` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 16 | `StreamEvent` | `message` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 17 | `StreamEvent` | `timestamp` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 18 | `StreamEvent` | `seq` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 19 | `StreamEvent` | `streamId` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 20 | `StreamEvent` | `tenantId` |
| `src/main/java/com/example/agent/domain/event/StreamEvent.java` | 21 | `StreamEvent` | `payload` |
| `src/main/java/com/example/agent/evaluation/CapabilityBoundaryEvaluator.java` | 28 | `CapabilityBoundaryEvaluator` | `properties` |
| `src/main/java/com/example/agent/evaluation/CapabilityBoundaryEvaluator.java` | 29 | `CapabilityBoundaryEvaluator` | `eventPublisher` |
| `src/main/java/com/example/agent/evaluation/CapabilityBoundaryEvaluator.java` | 30 | `CapabilityBoundaryEvaluator` | `eventStreamService` |
| `src/main/java/com/example/agent/gateway/controller/ApprovalController.java` | 41 | `ApprovalController` | `executionControlService` |
| `src/main/java/com/example/agent/gateway/controller/ApprovalController.java` | 42 | `ApprovalController` | `authService` |
| `src/main/java/com/example/agent/gateway/controller/ApprovalController.java` | 43 | `ApprovalController` | `eventPublisher` |
| `src/main/java/com/example/agent/gateway/controller/ApprovalController.java` | 44 | `ApprovalController` | `eventStreamService` |
| `src/main/java/com/example/agent/gateway/controller/ApprovalController.java` | 45 | `ApprovalController` | `tracingPublisher` |
| `src/main/java/com/example/agent/gateway/controller/BudgetController.java` | 34 | `BudgetController` | `tokenBudgetManager` |
| `src/main/java/com/example/agent/gateway/controller/BudgetController.java` | 35 | `BudgetController` | `authService` |
| `src/main/java/com/example/agent/gateway/controller/MemoryController.java` | 32 | `MemoryController` | `memoryStore` |
| `src/main/java/com/example/agent/gateway/controller/MemoryController.java` | 33 | `MemoryController` | `authService` |
| `src/main/java/com/example/agent/gateway/controller/PolicyController.java` | 30 | `PolicyController` | `policyEngine` |
| `src/main/java/com/example/agent/gateway/controller/PolicyController.java` | 31 | `PolicyController` | `authService` |
| `src/main/java/com/example/agent/gateway/controller/ReplayController.java` | 30 | `ReplayController` | `replayService` |
| `src/main/java/com/example/agent/gateway/controller/ReplayController.java` | 31 | `ReplayController` | `authService` |
| `src/main/java/com/example/agent/gateway/controller/ScheduleController.java` | 38 | `ScheduleController` | `scheduleManager` |
| `src/main/java/com/example/agent/gateway/controller/ScheduleController.java` | 39 | `ScheduleController` | `authService` |
| `src/main/java/com/example/agent/gateway/controller/TaskController.java` | 39 | `TaskController` | `taskSubmissionService` |
| `src/main/java/com/example/agent/gateway/controller/TaskController.java` | 40 | `TaskController` | `taskQueryService` |
| `src/main/java/com/example/agent/gateway/controller/TaskController.java` | 41 | `TaskController` | `authService` |
| `src/main/java/com/example/agent/gateway/controller/TaskController.java` | 42 | `TaskController` | `tracingPublisher` |
| `src/main/java/com/example/agent/gateway/controller/TimelineController.java` | 34 | `TimelineController` | `eventLogService` |
| `src/main/java/com/example/agent/gateway/controller/TimelineController.java` | 35 | `TimelineController` | `timelineService` |
| `src/main/java/com/example/agent/gateway/controller/TimelineController.java` | 36 | `TimelineController` | `stepRuntimeService` |
| `src/main/java/com/example/agent/gateway/controller/TimelineController.java` | 37 | `TimelineController` | `authService` |
| `src/main/java/com/example/agent/gateway/controller/ToolApprovalController.java` | 32 | `ToolApprovalController` | `approvalService` |
| `src/main/java/com/example/agent/gateway/controller/ToolApprovalController.java` | 33 | `ToolApprovalController` | `authService` |
| `src/main/java/com/example/agent/gateway/controller/ToolApprovalController.java` | 34 | `ToolApprovalController` | `tracingPublisher` |
| `src/main/java/com/example/agent/governance/CircuitBreakerManager.java` | 15 | `CircuitBreakerManager` | `states` |
| `src/main/java/com/example/agent/governance/CircuitBreakerManager.java` | 18 | `CircuitBreakerManager` | `failureThreshold` |
| `src/main/java/com/example/agent/governance/CircuitBreakerManager.java` | 21 | `CircuitBreakerManager` | `openSeconds` |
| `src/main/java/com/example/agent/governance/RateLimitService.java` | 15 | `RateLimitService` | `counters` |
| `src/main/java/com/example/agent/governance/RateLimitService.java` | 18 | `RateLimitService` | `maxPerMinute` |
| `src/main/java/com/example/agent/governance/ReplayRequest.java` | 8 | `ReplayRequest` | `taskId` |
| `src/main/java/com/example/agent/governance/ReplayRequest.java` | 9 | `ReplayRequest` | `fromStepId` |
| `src/main/java/com/example/agent/governance/ReplayRequest.java` | 10 | `ReplayRequest` | `toStepId` |
| `src/main/java/com/example/agent/governance/ReplayRequest.java` | 11 | `ReplayRequest` | `mode` |
| `src/main/java/com/example/agent/governance/ReplayResponse.java` | 10 | `ReplayResponse` | `replayId` |
| `src/main/java/com/example/agent/governance/ReplayResponse.java` | 11 | `ReplayResponse` | `status` |
| `src/main/java/com/example/agent/governance/ReplayResponse.java` | 12 | `ReplayResponse` | `startedAt` |
| `src/main/java/com/example/agent/governance/ReplayResponse.java` | 13 | `ReplayResponse` | `completedAt` |
| `src/main/java/com/example/agent/governance/ReplayService.java` | 39 | `ReplayService` | `taskQueryService` |
| `src/main/java/com/example/agent/governance/ReplayService.java` | 40 | `ReplayService` | `eventLogRepository` |
| `src/main/java/com/example/agent/governance/ReplayService.java` | 41 | `ReplayService` | `stepRuntimeService` |
| `src/main/java/com/example/agent/governance/ReplayService.java` | 42 | `ReplayService` | `eventPublisher` |
| `src/main/java/com/example/agent/governance/ReplayService.java` | 43 | `ReplayService` | `eventStreamService` |
| `src/main/java/com/example/agent/governance/ReplayService.java` | 44 | `ReplayService` | `metricsPublisher` |
| `src/main/java/com/example/agent/governance/ReplayService.java` | 46 | `ReplayService` | `sessions` |
| `src/main/java/com/example/agent/history/EventLogRecord.java` | 11 | `EventLogRecord` | `eventId` |
| `src/main/java/com/example/agent/history/EventLogRecord.java` | 12 | `EventLogRecord` | `workflowId` |
| `src/main/java/com/example/agent/history/EventLogRecord.java` | 13 | `EventLogRecord` | `type` |
| `src/main/java/com/example/agent/history/EventLogRecord.java` | 14 | `EventLogRecord` | `timestamp` |
| `src/main/java/com/example/agent/history/EventLogRecord.java` | 15 | `EventLogRecord` | `payload` |
| `src/main/java/com/example/agent/history/EventLogRecord.java` | 16 | `EventLogRecord` | `tenantId` |
| `src/main/java/com/example/agent/history/EventLogService.java` | 22 | `EventLogService` | `repository` |
| `src/main/java/com/example/agent/history/EventLogService.java` | 23 | `EventLogService` | `metricsPublisher` |
| `src/main/java/com/example/agent/history/InMemoryEventLogRepository.java` | 18 | `InMemoryEventLogRepository` | `eventIndex` |
| `src/main/java/com/example/agent/history/InMemoryEventLogRepository.java` | 19 | `InMemoryEventLogRepository` | `workflowIndex` |
| `src/main/java/com/example/agent/history/TimelineRecord.java` | 10 | `TimelineRecord` | `eventId` |
| `src/main/java/com/example/agent/history/TimelineRecord.java` | 11 | `TimelineRecord` | `type` |
| `src/main/java/com/example/agent/history/TimelineRecord.java` | 12 | `TimelineRecord` | `timestamp` |
| `src/main/java/com/example/agent/history/TimelineRequest.java` | 8 | `TimelineRequest` | `workflowId` |
| `src/main/java/com/example/agent/history/TimelineRequest.java` | 9 | `TimelineRequest` | `mode` |
| `src/main/java/com/example/agent/history/TimelineResponse.java` | 11 | `TimelineResponse` | `workflowId` |
| `src/main/java/com/example/agent/history/TimelineResponse.java` | 12 | `TimelineResponse` | `mode` |
| `src/main/java/com/example/agent/history/TimelineResponse.java` | 13 | `TimelineResponse` | `events` |
| `src/main/java/com/example/agent/history/TimelineResponse.java` | 14 | `TimelineResponse` | `stats` |
| `src/main/java/com/example/agent/history/TimelineService.java` | 19 | `TimelineService` | `repository` |
| `src/main/java/com/example/agent/memory/CompressedMemoryStore.java` | 28 | `CompressedMemoryStore` | `memoryRepository` |
| `src/main/java/com/example/agent/memory/CompressedMemoryStore.java` | 29 | `CompressedMemoryStore` | `expirationService` |
| `src/main/java/com/example/agent/memory/CompressionRequest.java` | 8 | `CompressionRequest` | `sessionId` |
| `src/main/java/com/example/agent/memory/CompressionRequest.java` | 9 | `CompressionRequest` | `strategy` |
| `src/main/java/com/example/agent/memory/HashEmbeddingService.java` | 15 | `HashEmbeddingService` | `properties` |
| `src/main/java/com/example/agent/memory/InMemoryMemoryRepository.java` | 21 | `InMemoryMemoryRepository` | `records` |
| `src/main/java/com/example/agent/memory/InMemoryMemoryRepository.java` | 22 | `InMemoryMemoryRepository` | `sessionIndex` |
| `src/main/java/com/example/agent/memory/MemoryExpirationService.java` | 14 | `MemoryExpirationService` | `properties` |
| `src/main/java/com/example/agent/memory/MemoryPolicy.java` | 17 | `MemoryPolicy` | `properties` |
| `src/main/java/com/example/agent/memory/MemoryPolicy.java` | 18 | `MemoryPolicy` | `tokenEstimator` |
| `src/main/java/com/example/agent/memory/MemoryPolicyProperties.java` | 13 | `MemoryPolicyProperties` | `enabled` |
| `src/main/java/com/example/agent/memory/MemoryPolicyProperties.java` | 14 | `MemoryPolicyProperties` | `sizeThreshold` |
| `src/main/java/com/example/agent/memory/MemoryPolicyProperties.java` | 15 | `MemoryPolicyProperties` | `tokenThreshold` |
| `src/main/java/com/example/agent/memory/MemoryPolicyProperties.java` | 16 | `MemoryPolicyProperties` | `maxAgeSeconds` |
| `src/main/java/com/example/agent/memory/MemoryPolicyProperties.java` | 17 | `MemoryPolicyProperties` | `minCompressIntervalSeconds` |
| `src/main/java/com/example/agent/memory/MemoryRecord.java` | 10 | `MemoryRecord` | `memoryId` |
| `src/main/java/com/example/agent/memory/MemoryRecord.java` | 11 | `MemoryRecord` | `sessionId` |
| `src/main/java/com/example/agent/memory/MemoryRecord.java` | 12 | `MemoryRecord` | `taskId` |
| `src/main/java/com/example/agent/memory/MemoryRecord.java` | 13 | `MemoryRecord` | `content` |
| `src/main/java/com/example/agent/memory/MemoryRecord.java` | 14 | `MemoryRecord` | `summary` |
| `src/main/java/com/example/agent/memory/MemoryRecord.java` | 15 | `MemoryRecord` | `embeddingRef` |
| `src/main/java/com/example/agent/memory/MemoryRecord.java` | 16 | `MemoryRecord` | `tenantId` |
| `src/main/java/com/example/agent/memory/MemoryRecord.java` | 17 | `MemoryRecord` | `layer` |
| `src/main/java/com/example/agent/memory/MemoryRecord.java` | 18 | `MemoryRecord` | `createdAt` |
| `src/main/java/com/example/agent/memory/MemorySearchResult.java` | 10 | `MemorySearchResult` | `records` |
| `src/main/java/com/example/agent/memory/MemoryVectorProperties.java` | 13 | `MemoryVectorProperties` | `enabled` |
| `src/main/java/com/example/agent/memory/MemoryVectorProperties.java` | 14 | `MemoryVectorProperties` | `provider` |
| `src/main/java/com/example/agent/memory/MemoryVectorProperties.java` | 16 | `MemoryVectorProperties` | `collection` |
| `src/main/java/com/example/agent/memory/MemoryVectorProperties.java` | 17 | `MemoryVectorProperties` | `dimension` |
| `src/main/java/com/example/agent/memory/MemoryVectorProperties.java` | 18 | `MemoryVectorProperties` | `timeoutSeconds` |
| `src/main/java/com/example/agent/memory/MemoryVectorProperties.java` | 19 | `MemoryVectorProperties` | `topK` |
| `src/main/java/com/example/agent/memory/MemoryVectorProperties.java` | 20 | `MemoryVectorProperties` | `scoreThreshold` |
| `src/main/java/com/example/agent/memory/RecentMemoryStore.java` | 14 | `RecentMemoryStore` | `memoryRepository` |
| `src/main/java/com/example/agent/memory/SemanticMemoryStore.java` | 20 | `SemanticMemoryStore` | `vectorStoreProvider` |
| `src/main/java/com/example/agent/memory/SemanticMemoryStore.java` | 21 | `SemanticMemoryStore` | `embeddingServiceProvider` |
| `src/main/java/com/example/agent/model/DefaultLlmClient.java` | 15 | `DefaultLlmClient` | `modelRouter` |
| `src/main/java/com/example/agent/model/DefaultLlmClient.java` | 16 | `DefaultLlmClient` | `modelProvider` |
| `src/main/java/com/example/agent/model/DefaultPromptTemplate.java` | 17 | `DefaultPromptTemplate` | `systemMessage` |
| `src/main/java/com/example/agent/model/DefaultPromptTemplate.java` | 20 | `DefaultPromptTemplate` | `developerMessage` |
| `src/main/java/com/example/agent/model/ModelConfigProperties.java` | 15 | `ModelConfigProperties` | `models` |
| `src/main/java/com/example/agent/model/ModelConfigProperties.java` | 16 | `ModelConfigProperties` | `routes` |
| `src/main/java/com/example/agent/model/ModelConfigProperties.java` | 17 | `ModelConfigProperties` | `fallbackEnabled` |
| `src/main/java/com/example/agent/model/ModelConfigProperties.java` | 18 | `ModelConfigProperties` | `fallbackModelId` |
| `src/main/java/com/example/agent/model/ModelDefinition.java` | 8 | `ModelDefinition` | `modelId` |
| `src/main/java/com/example/agent/model/ModelDefinition.java` | 9 | `ModelDefinition` | `provider` |
| `src/main/java/com/example/agent/model/ModelDefinition.java` | 10 | `ModelDefinition` | `endpoint` |
| `src/main/java/com/example/agent/model/ModelDefinition.java` | 11 | `ModelDefinition` | `inputCostUsd` |
| `src/main/java/com/example/agent/model/ModelDefinition.java` | 12 | `ModelDefinition` | `outputCostUsd` |
| `src/main/java/com/example/agent/model/ModelDefinition.java` | 13 | `ModelDefinition` | `maxTokens` |
| `src/main/java/com/example/agent/model/ModelFallbackDecision.java` | 10 | `ModelFallbackDecision` | `decisionId` |
| `src/main/java/com/example/agent/model/ModelFallbackDecision.java` | 11 | `ModelFallbackDecision` | `tenantId` |
| `src/main/java/com/example/agent/model/ModelFallbackDecision.java` | 12 | `ModelFallbackDecision` | `taskId` |
| `src/main/java/com/example/agent/model/ModelFallbackDecision.java` | 13 | `ModelFallbackDecision` | `fromModel` |
| `src/main/java/com/example/agent/model/ModelFallbackDecision.java` | 14 | `ModelFallbackDecision` | `toModel` |
| `src/main/java/com/example/agent/model/ModelFallbackDecision.java` | 15 | `ModelFallbackDecision` | `reason` |
| `src/main/java/com/example/agent/model/ModelFallbackDecision.java` | 16 | `ModelFallbackDecision` | `decidedAt` |
| `src/main/java/com/example/agent/model/ModelFallbackPolicy.java` | 14 | `ModelFallbackPolicy` | `modelConfigProperties` |
| `src/main/java/com/example/agent/model/ModelInvocationService.java` | 26 | `ModelInvocationService` | `llmClient` |
| `src/main/java/com/example/agent/model/ModelInvocationService.java` | 27 | `ModelInvocationService` | `modelRouter` |
| `src/main/java/com/example/agent/model/ModelInvocationService.java` | 28 | `ModelInvocationService` | `eventPublisher` |
| `src/main/java/com/example/agent/model/ModelInvocationService.java` | 29 | `ModelInvocationService` | `eventStreamService` |
| `src/main/java/com/example/agent/model/ModelRegistry.java` | 13 | `ModelRegistry` | `modelConfigProperties` |
| `src/main/java/com/example/agent/model/ModelResponse.java` | 8 | `ModelResponse` | `modelId` |
| `src/main/java/com/example/agent/model/ModelResponse.java` | 9 | `ModelResponse` | `content` |
| `src/main/java/com/example/agent/model/ModelResponse.java` | 10 | `ModelResponse` | `inputTokens` |
| `src/main/java/com/example/agent/model/ModelResponse.java` | 11 | `ModelResponse` | `outputTokens` |
| `src/main/java/com/example/agent/model/ModelRouter.java` | 17 | `ModelRouter` | `modelConfigProperties` |
| `src/main/java/com/example/agent/model/ModelRouter.java` | 18 | `ModelRouter` | `modelRegistry` |
| `src/main/java/com/example/agent/multiagent/AgentConfig.java` | 8 | `AgentConfig` | `agentId` |
| `src/main/java/com/example/agent/multiagent/AgentConfig.java` | 9 | `AgentConfig` | `modelId` |
| `src/main/java/com/example/agent/multiagent/AgentConfig.java` | 10 | `AgentConfig` | `prompt` |
| `src/main/java/com/example/agent/multiagent/AgentProfileProperties.java` | 15 | `AgentProfileProperties` | `items` |
| `src/main/java/com/example/agent/multiagent/HandoffRecord.java` | 10 | `HandoffRecord` | `handoffId` |
| `src/main/java/com/example/agent/multiagent/HandoffRecord.java` | 11 | `HandoffRecord` | `fromAgent` |
| `src/main/java/com/example/agent/multiagent/HandoffRecord.java` | 12 | `HandoffRecord` | `toAgent` |
| `src/main/java/com/example/agent/multiagent/HandoffRecord.java` | 13 | `HandoffRecord` | `createdAt` |
| `src/main/java/com/example/agent/multiagent/HandoffRequest.java` | 10 | `HandoffRequest` | `fromAgent` |
| `src/main/java/com/example/agent/multiagent/HandoffRequest.java` | 11 | `HandoffRequest` | `toAgent` |
| `src/main/java/com/example/agent/multiagent/HandoffRequest.java` | 12 | `HandoffRequest` | `context` |
| `src/main/java/com/example/agent/multiagent/HandoffResult.java` | 10 | `HandoffResult` | `status` |
| `src/main/java/com/example/agent/multiagent/HandoffResult.java` | 11 | `HandoffResult` | `context` |
| `src/main/java/com/example/agent/multiagent/SupervisorCoordinator.java` | 15 | `SupervisorCoordinator` | `multiAgentCoordinator` |
| `src/main/java/com/example/agent/observability/MetricsPublisher.java` | 12 | `MetricsPublisher` | `meterRegistry` |
| `src/main/java/com/example/agent/observability/TracingPublisher.java` | 13 | `TracingPublisher` | `tracer` |
| `src/main/java/com/example/agent/orchestrator/InMemoryTaskRepository.java` | 18 | `InMemoryTaskRepository` | `tasks` |
| `src/main/java/com/example/agent/orchestrator/InMemoryTaskRepository.java` | 19 | `InMemoryTaskRepository` | `idempotencyIndex` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 43 | `TaskOrchestrator` | `eventPublisher` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 44 | `TaskOrchestrator` | `workflowRouter` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 45 | `TaskOrchestrator` | `metricsPublisher` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 46 | `TaskOrchestrator` | `tracingPublisher` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 47 | `TaskOrchestrator` | `eventStreamService` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 48 | `TaskOrchestrator` | `taskRepository` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 49 | `TaskOrchestrator` | `redisTemplateProvider` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 50 | `TaskOrchestrator` | `idempotencyLocks` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 53 | `TaskOrchestrator` | `redisIdempotencyEnabled` |
| `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java` | 56 | `TaskOrchestrator` | `idempotencyTtlSeconds` |
| `src/main/java/com/example/agent/orchestrator/TaskRecord.java` | 11 | `TaskRecord` | `taskId` |
| `src/main/java/com/example/agent/orchestrator/TaskRecord.java` | 12 | `TaskRecord` | `workflowId` |
| `src/main/java/com/example/agent/orchestrator/TaskRecord.java` | 13 | `TaskRecord` | `status` |
| `src/main/java/com/example/agent/orchestrator/TaskRecord.java` | 14 | `TaskRecord` | `request` |
| `src/main/java/com/example/agent/orchestrator/TaskRecord.java` | 15 | `TaskRecord` | `result` |
| `src/main/java/com/example/agent/orchestrator/TaskRecord.java` | 16 | `TaskRecord` | `idempotencyKey` |
| `src/main/java/com/example/agent/orchestrator/TaskRecord.java` | 17 | `TaskRecord` | `createdAt` |
| `src/main/java/com/example/agent/orchestrator/TaskRecord.java` | 18 | `TaskRecord` | `updatedAt` |
| `src/main/java/com/example/agent/orchestrator/TaskRecord.java` | 19 | `TaskRecord` | `tenantId` |
| `src/main/java/com/example/agent/orchestrator/WorkflowRouter.java` | 16 | `WorkflowRouter` | `agentRuntime` |
| `src/main/java/com/example/agent/planning/PlanRequest.java` | 10 | `PlanRequest` | `query` |
| `src/main/java/com/example/agent/planning/PlanRequest.java` | 11 | `PlanRequest` | `context` |
| `src/main/java/com/example/agent/planning/PlanResult.java` | 11 | `PlanResult` | `planId` |
| `src/main/java/com/example/agent/planning/PlanResult.java` | 12 | `PlanResult` | `summary` |
| `src/main/java/com/example/agent/planning/PlanResult.java` | 13 | `PlanResult` | `steps` |
| `src/main/java/com/example/agent/policy/PolicyEngine.java` | 20 | `PolicyEngine` | `metricsPublisher` |
| `src/main/java/com/example/agent/policy/PolicyRequest.java` | 10 | `PolicyRequest` | `policyId` |
| `src/main/java/com/example/agent/policy/PolicyRequest.java` | 11 | `PolicyRequest` | `action` |
| `src/main/java/com/example/agent/policy/PolicyRequest.java` | 12 | `PolicyRequest` | `resource` |
| `src/main/java/com/example/agent/policy/PolicyRequest.java` | 13 | `PolicyRequest` | `input` |
| `src/main/java/com/example/agent/reflection/ReflectionRequest.java` | 10 | `ReflectionRequest` | `stepId` |
| `src/main/java/com/example/agent/reflection/ReflectionRequest.java` | 11 | `ReflectionRequest` | `output` |
| `src/main/java/com/example/agent/research/DeepResearchWorkflowProperties.java` | 15 | `DeepResearchWorkflowProperties` | `steps` |
| `src/main/java/com/example/agent/runtime/InMemoryStepRecordRepository.java` | 17 | `InMemoryStepRecordRepository` | `stepStore` |
| `src/main/java/com/example/agent/runtime/RuntimeResult.java` | 11 | `RuntimeResult` | `planId` |
| `src/main/java/com/example/agent/runtime/RuntimeResult.java` | 12 | `RuntimeResult` | `planSummary` |
| `src/main/java/com/example/agent/runtime/RuntimeResult.java` | 13 | `RuntimeResult` | `steps` |
| `src/main/java/com/example/agent/runtime/RuntimeResult.java` | 14 | `RuntimeResult` | `finalOutput` |
| `src/main/java/com/example/agent/runtime/StepRequest.java` | 10 | `StepRequest` | `stepType` |
| `src/main/java/com/example/agent/runtime/StepRequest.java` | 11 | `StepRequest` | `input` |
| `src/main/java/com/example/agent/runtime/StepResponse.java` | 10 | `StepResponse` | `stepId` |
| `src/main/java/com/example/agent/runtime/StepResponse.java` | 11 | `StepResponse` | `status` |
| `src/main/java/com/example/agent/runtime/StepResponse.java` | 12 | `StepResponse` | `output` |
| `src/main/java/com/example/agent/runtime/StepResponse.java` | 13 | `StepResponse` | `errorCode` |
| `src/main/java/com/example/agent/runtime/StepRuntimeService.java` | 33 | `StepRuntimeService` | `stateMachine` |
| `src/main/java/com/example/agent/runtime/StepRuntimeService.java` | 34 | `StepRuntimeService` | `eventPublisher` |
| `src/main/java/com/example/agent/runtime/StepRuntimeService.java` | 35 | `StepRuntimeService` | `eventStreamService` |
| `src/main/java/com/example/agent/runtime/StepRuntimeService.java` | 36 | `StepRuntimeService` | `metricsPublisher` |
| `src/main/java/com/example/agent/runtime/StepRuntimeService.java` | 37 | `StepRuntimeService` | `tracingPublisher` |
| `src/main/java/com/example/agent/runtime/StepRuntimeService.java` | 38 | `StepRuntimeService` | `stepRecordRepository` |
| `src/main/java/com/example/agent/runtime/StepTimelineResponse.java` | 10 | `StepTimelineResponse` | `workflowId` |
| `src/main/java/com/example/agent/runtime/StepTimelineResponse.java` | 11 | `StepTimelineResponse` | `steps` |
| `src/main/java/com/example/agent/runtime/StepTimelineResponse.java` | 12 | `StepTimelineResponse` | `nextCursor` |
| `src/main/java/com/example/agent/runtime/StepTimelineResponse.java` | 13 | `StepTimelineResponse` | `hasMore` |
| `src/main/java/com/example/agent/sandbox/SandboxProperties.java` | 15 | `SandboxProperties` | `enabled` |
| `src/main/java/com/example/agent/sandbox/SandboxProperties.java` | 16 | `SandboxProperties` | `blockedTools` |
| `src/main/java/com/example/agent/sandbox/SandboxRequest.java` | 10 | `SandboxRequest` | `toolName` |
| `src/main/java/com/example/agent/sandbox/SandboxRequest.java` | 11 | `SandboxRequest` | `input` |
| `src/main/java/com/example/agent/sandbox/SandboxRequest.java` | 12 | `SandboxRequest` | `limits` |
| `src/main/java/com/example/agent/sandbox/SandboxResult.java` | 10 | `SandboxResult` | `status` |
| `src/main/java/com/example/agent/sandbox/SandboxResult.java` | 11 | `SandboxResult` | `output` |
| `src/main/java/com/example/agent/sandbox/SandboxResult.java` | 12 | `SandboxResult` | `error` |
| `src/main/java/com/example/agent/sandbox/WasiSandboxExecutor.java` | 20 | `WasiSandboxExecutor` | `sandboxProperties` |
| `src/main/java/com/example/agent/sandbox/WasiSandboxExecutor.java` | 21 | `WasiSandboxExecutor` | `metricsPublisher` |
| `src/main/java/com/example/agent/scheduler/InMemoryScheduleExecutionRepository.java` | 16 | `InMemoryScheduleExecutionRepository` | `executions` |
| `src/main/java/com/example/agent/scheduler/InMemoryScheduleRepository.java` | 17 | `InMemoryScheduleRepository` | `schedules` |
| `src/main/java/com/example/agent/scheduler/InMemoryScheduleRepository.java` | 18 | `InMemoryScheduleRepository` | `idempotencyIndex` |
| `src/main/java/com/example/agent/scheduler/ScheduleEngine.java` | 32 | `ScheduleEngine` | `executionRepository` |
| `src/main/java/com/example/agent/scheduler/ScheduleEngine.java` | 33 | `ScheduleEngine` | `metricsPublisher` |
| `src/main/java/com/example/agent/scheduler/ScheduleEngine.java` | 34 | `ScheduleEngine` | `eventPublisher` |
| `src/main/java/com/example/agent/scheduler/ScheduleEngine.java` | 35 | `ScheduleEngine` | `eventStreamService` |
| `src/main/java/com/example/agent/scheduler/ScheduleEngine.java` | 36 | `ScheduleEngine` | `scheduler` |
| `src/main/java/com/example/agent/scheduler/ScheduleEngine.java` | 37 | `ScheduleEngine` | `futures` |
| `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java` | 10 | `ScheduleExecutionRecord` | `executionId` |
| `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java` | 11 | `ScheduleExecutionRecord` | `scheduleId` |
| `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java` | 12 | `ScheduleExecutionRecord` | `status` |
| `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java` | 13 | `ScheduleExecutionRecord` | `startedAt` |
| `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java` | 14 | `ScheduleExecutionRecord` | `endedAt` |
| `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java` | 15 | `ScheduleExecutionRecord` | `costUsd` |
| `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java` | 16 | `ScheduleExecutionRecord` | `tokenUsage` |
| `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java` | 17 | `ScheduleExecutionRecord` | `tenantId` |
| `src/main/java/com/example/agent/scheduler/ScheduleManager.java` | 25 | `ScheduleManager` | `scheduleRepository` |
| `src/main/java/com/example/agent/scheduler/ScheduleManager.java` | 26 | `ScheduleManager` | `executionRepository` |
| `src/main/java/com/example/agent/scheduler/ScheduleManager.java` | 27 | `ScheduleManager` | `scheduleEngine` |
| `src/main/java/com/example/agent/scheduler/ScheduleManager.java` | 28 | `ScheduleManager` | `metricsPublisher` |
| `src/main/java/com/example/agent/scheduler/ScheduleResponse.java` | 8 | `ScheduleResponse` | `scheduleId` |
| `src/main/java/com/example/agent/scheduler/ScheduleResponse.java` | 9 | `ScheduleResponse` | `status` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 28 | `RedactionService` | `properties` |
| `src/main/java/com/example/agent/security/RedactionService.java` | 29 | `RedactionService` | `metricsPublisher` |
| `src/main/java/com/example/agent/streaming/SseStreamController.java` | 43 | `SseStreamController` | `eventStreamService` |
| `src/main/java/com/example/agent/streaming/SseStreamController.java` | 44 | `SseStreamController` | `authService` |
| `src/main/java/com/example/agent/streaming/SseStreamController.java` | 45 | `SseStreamController` | `tracingPublisher` |
| `src/main/java/com/example/agent/streaming/SseStreamController.java` | 47 | `SseStreamController` | `firstEventTimeout` |
| `src/main/java/com/example/agent/streaming/SseStreamController.java` | 49 | `SseStreamController` | `timeoutScheduler` |
| `src/main/java/com/example/agent/streaming/TaskStreamRequest.java` | 10 | `TaskStreamRequest` | `workflowId` |
| `src/main/java/com/example/agent/streaming/TaskStreamRequest.java` | 11 | `TaskStreamRequest` | `types` |
| `src/main/java/com/example/agent/streaming/TaskStreamRequest.java` | 12 | `TaskStreamRequest` | `lastEventId` |
| `src/main/java/com/example/agent/streaming/TaskStreamRequest.java` | 13 | `TaskStreamRequest` | `cursor` |
| `src/main/java/com/example/agent/tools/McpServerProperties.java` | 15 | `McpServerProperties` | `servers` |
| `src/main/java/com/example/agent/tools/McpToolCallRequest.java` | 10 | `McpToolCallRequest` | `callId` |
| `src/main/java/com/example/agent/tools/McpToolCallRequest.java` | 11 | `McpToolCallRequest` | `serverId` |
| `src/main/java/com/example/agent/tools/McpToolCallRequest.java` | 12 | `McpToolCallRequest` | `toolName` |
| `src/main/java/com/example/agent/tools/McpToolCallRequest.java` | 13 | `McpToolCallRequest` | `arguments` |
| `src/main/java/com/example/agent/tools/McpToolCallRequest.java` | 14 | `McpToolCallRequest` | `timeoutMs` |
| `src/main/java/com/example/agent/tools/McpToolCallResponse.java` | 10 | `McpToolCallResponse` | `callId` |
| `src/main/java/com/example/agent/tools/McpToolCallResponse.java` | 11 | `McpToolCallResponse` | `status` |
| `src/main/java/com/example/agent/tools/McpToolCallResponse.java` | 12 | `McpToolCallResponse` | `result` |
| `src/main/java/com/example/agent/tools/McpToolCallResponse.java` | 13 | `McpToolCallResponse` | `error` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 38 | `McpToolClient` | `serverProperties` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 39 | `McpToolClient` | `toolRegistry` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 40 | `McpToolClient` | `rateLimitService` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 41 | `McpToolClient` | `circuitBreakerManager` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 42 | `McpToolClient` | `webClientBuilder` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 43 | `McpToolClient` | `objectMapper` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 46 | `McpToolClient` | `maxAttempts` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 49 | `McpToolClient` | `baseDelayMs` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 52 | `McpToolClient` | `maxDelayMs` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 55 | `McpToolClient` | `jitterRatio` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 58 | `McpToolClient` | `timeoutSeconds` |
| `src/main/java/com/example/agent/tools/McpToolClient.java` | 61 | `McpToolClient` | `remoteEnabled` |
| `src/main/java/com/example/agent/tools/McpToolListRequest.java` | 8 | `McpToolListRequest` | `serverId` |
| `src/main/java/com/example/agent/tools/McpToolListRequest.java` | 9 | `McpToolListRequest` | `cursor` |
| `src/main/java/com/example/agent/tools/McpToolListRequest.java` | 10 | `McpToolListRequest` | `size` |
| `src/main/java/com/example/agent/tools/McpToolListResponse.java` | 10 | `McpToolListResponse` | `tools` |
| `src/main/java/com/example/agent/tools/McpToolListResponse.java` | 11 | `McpToolListResponse` | `nextCursor` |
| `src/main/java/com/example/agent/tools/McpToolListResponse.java` | 12 | `McpToolListResponse` | `hasMore` |
| `src/main/java/com/example/agent/tools/hook/BlockedToolHookHandler.java` | 19 | `BlockedToolHookHandler` | `hookProperties` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 42 | `HookManager` | `hookProperties` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 43 | `HookManager` | `eventPublisher` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 44 | `HookManager` | `eventStreamService` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 45 | `HookManager` | `metricsPublisher` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 46 | `HookManager` | `hookHandlers` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 47 | `HookManager` | `hookExecutor` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 51 | `HookManager` | `records` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 52 | `HookManager` | `recordLock` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 322 | `HookExecutionResult` | `decision` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 323 | `HookExecutionResult` | `timeout` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 324 | `HookExecutionResult` | `startedAt` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 325 | `HookExecutionResult` | `endedAt` |
| `src/main/java/com/example/agent/tools/hook/HookManager.java` | 326 | `HookExecutionResult` | `durationMs` |
| `src/main/java/com/example/agent/tools/hook/HookProperties.java` | 15 | `HookProperties` | `enabled` |
| `src/main/java/com/example/agent/tools/hook/HookProperties.java` | 16 | `HookProperties` | `blockedTools` |
| `src/main/java/com/example/agent/tools/hook/HookRecord.java` | 10 | `HookRecord` | `hookId` |
| `src/main/java/com/example/agent/tools/hook/HookRecord.java` | 11 | `HookRecord` | `hookType` |
| `src/main/java/com/example/agent/tools/hook/HookRecord.java` | 12 | `HookRecord` | `toolName` |
| `src/main/java/com/example/agent/tools/hook/HookRecord.java` | 13 | `HookRecord` | `stepId` |
| `src/main/java/com/example/agent/tools/hook/HookRecord.java` | 14 | `HookRecord` | `tenantId` |
| `src/main/java/com/example/agent/tools/hook/HookRecord.java` | 15 | `HookRecord` | `allowed` |
| `src/main/java/com/example/agent/tools/hook/HookRecord.java` | 16 | `HookRecord` | `reason` |
| `src/main/java/com/example/agent/tools/hook/HookRecord.java` | 37 | `HookRecord` | `executedAt` |
| `src/main/java/com/example/agent/tools/plugin/PluginLoader.java` | 28 | `PluginLoader` | `pluginProperties` |
| `src/main/java/com/example/agent/tools/plugin/PluginLoader.java` | 29 | `PluginLoader` | `toolRegistry` |
| `src/main/java/com/example/agent/tools/plugin/PluginLoader.java` | 30 | `PluginLoader` | `objectMapper` |
| `src/main/java/com/example/agent/tools/skill/SkillProperties.java` | 15 | `SkillProperties` | `definitions` |
| `src/main/java/com/example/agent/tools/skill/SkillRegistry.java` | 17 | `SkillRegistry` | `skillProperties` |

## è§£æå¤±è´¥æä»¶
| æä»¶ | åå  |
| --- | --- |
| `src/main/java/com/example/agent/agentcore/EnforcementGateway.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/agentcore/ToolArgumentValidator.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/agentcore/ToolExecutor.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/approval/ApprovalService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/budget/DefaultContextPruner.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/budget/DefaultContextTrimmer.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/budget/JdbcTokenUsageRepository.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/context/DefaultContextBuilder.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/context/EvidencePackService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/gateway/controller/GlobalExceptionHandler.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/gateway/controller/McpController.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/history/JdbcEventLogRepository.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/memory/JdbcMemoryRepository.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/memory/MemoryRecallService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/memory/MemoryStore.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/memory/MemoryWriteService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/memory/QdrantVectorStore.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/model/DefaultModelProvider.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/model/DefaultPromptAssembler.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/model/ModelToolResolver.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/orchestrator/JdbcTaskRepository.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/planning/PlannerService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/reasoning/ChainOfThoughtService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/reasoning/DebateCoordinator.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/reflection/ReflectionService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/research/ResearchPipeline.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/runtime/AgentRuntime.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/runtime/FailureClassifier.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/runtime/FinalOutputService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/runtime/JdbcStepRecordRepository.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/runtime/ReactLoopService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/runtime/StepStateMachine.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/scheduler/JdbcScheduleExecutionRepository.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/scheduler/JdbcScheduleRepository.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/streaming/ContextEventPublisher.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/streaming/EventStreamService.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |
| `src/main/java/com/example/agent/tools/DefaultToolCatalog.java` | è§£æå¤±è´¥, åå æªç¥ (å¯è½ä¸ºçæ¬è¯­æ³ä¸å
¼å®¹)? |

