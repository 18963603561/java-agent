# 方法体重复与高相似度扫描结果

## 一、说明
- 扫描范围：src/main/java 下全部 Java 文件。
- 重复判定：归一化后方法体完全一致。
- 相似度判定：基于方法体标识符 token 集合的 Jaccard 相似度。
- 分级规则：
  - P0：完全重复（相似度=1.0）
  - P1：相似度 >= 0.90
  - P2：相似度 >= 0.80
  - P3：相似度 >= 0.70
  - P4：相似度 >= 0.60

## 二、统计
- P0：89 组
- P1：2381 对
- P2：2196 对
- P3：1838 对
- P4：135 对

## 三、P0 完全重复
### P0-1
- `src/main/java/com/example/agent/agentcore/ToolExecutor.java:717` `resolveTraceId()`
- `src/main/java/com/example/agent/runtime/ReactLoopService.java:812` `resolveTraceId()`

### P0-2
- `src/main/java/com/example/agent/approval/ToolApprovalDecisionRequest.java:61` `setApproved()`
- `src/main/java/com/example/agent/approval/ToolApprovalDecisionResponse.java:49` `setApproved()`

### P0-3
- `src/main/java/com/example/agent/approval/ToolApprovalDecisionRequest.java:65` `getReason()`
- `src/main/java/com/example/agent/approval/ToolApprovalDecisionResponse.java:53` `getReason()`
- `src/main/java/com/example/agent/budget/PrunedItem.java:39` `getReason()`
- `src/main/java/com/example/agent/runtime/ApprovalDecisionRequest.java:29` `getReason()`

### P0-4
- `src/main/java/com/example/agent/approval/ToolApprovalDecisionRequest.java:69` `setReason()`
- `src/main/java/com/example/agent/approval/ToolApprovalDecisionResponse.java:57` `setReason()`
- `src/main/java/com/example/agent/budget/PrunedItem.java:43` `setReason()`
- `src/main/java/com/example/agent/runtime/ApprovalDecisionRequest.java:33` `setReason()`

### P0-5
- `src/main/java/com/example/agent/auth/ApiKeyProperties.java:63` `getUserId()`
- `src/main/java/com/example/agent/auth/UserContext.java:41` `getUserId()`

### P0-6
- `src/main/java/com/example/agent/auth/ApiKeyProperties.java:67` `setUserId()`
- `src/main/java/com/example/agent/auth/UserContext.java:45` `setUserId()`

### P0-7
- `src/main/java/com/example/agent/auth/ApiKeyProperties.java:71` `getRoles()`
- `src/main/java/com/example/agent/auth/UserContext.java:49` `getRoles()`

### P0-8
- `src/main/java/com/example/agent/auth/ApiKeyProperties.java:75` `setRoles()`
- `src/main/java/com/example/agent/auth/UserContext.java:53` `setRoles()`

### P0-9
- `src/main/java/com/example/agent/auth/ApiKeyProperties.java:79` `getTenantScope()`
- `src/main/java/com/example/agent/auth/UserContext.java:57` `getTenantScope()`

### P0-10
- `src/main/java/com/example/agent/auth/ApiKeyProperties.java:83` `setTenantScope()`
- `src/main/java/com/example/agent/auth/UserContext.java:61` `setTenantScope()`

### P0-11
- `src/main/java/com/example/agent/budget/ContextPruneRequest.java:57` `setPolicy()`
- `src/main/java/com/example/agent/budget/ContextTrimRequest.java:56` `setPolicy()`

### P0-12
- `src/main/java/com/example/agent/budget/ContextPruneResult.java:42` `getSummary()`
- `src/main/java/com/example/agent/streaming/ContextPruneSummary.java:41` `getSummary()`

### P0-13
- `src/main/java/com/example/agent/budget/ContextPruneResult.java:46` `setSummary()`
- `src/main/java/com/example/agent/streaming/ContextPruneSummary.java:45` `setSummary()`

### P0-14
- `src/main/java/com/example/agent/budget/ContextTrimReport.java:94` `getReasons()`
- `src/main/java/com/example/agent/streaming/ContextTrimSummary.java:69` `getReasons()`

### P0-15
- `src/main/java/com/example/agent/budget/ContextTrimReport.java:98` `setReasons()`
- `src/main/java/com/example/agent/streaming/ContextTrimSummary.java:73` `setReasons()`

### P0-16
- `src/main/java/com/example/agent/budget/ContextTrimResult.java:32` `setReport()`
- `src/main/java/com/example/agent/reflection/ReflectionResult.java:38` `setReport()`

### P0-17
- `src/main/java/com/example/agent/budget/TokenUsageInput.java:94` `getTenantId()`
- `src/main/java/com/example/agent/budget/TokenUsageRecord.java:114` `getTenantId()`
- `src/main/java/com/example/agent/governance/ReplaySession.java:60` `getTenantId()`
- `src/main/java/com/example/agent/history/EventLogRecord.java:61` `getTenantId()`
- `src/main/java/com/example/agent/orchestrator/TaskRecord.java:85` `getTenantId()`
- `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java:78` `getTenantId()`
- `src/main/java/com/example/agent/scheduler/ScheduleSpec.java:58` `getTenantId()`

### P0-18
- `src/main/java/com/example/agent/budget/TokenUsageInput.java:98` `setTenantId()`
- `src/main/java/com/example/agent/budget/TokenUsageRecord.java:118` `setTenantId()`
- `src/main/java/com/example/agent/governance/ReplaySession.java:64` `setTenantId()`
- `src/main/java/com/example/agent/history/EventLogRecord.java:65` `setTenantId()`
- `src/main/java/com/example/agent/orchestrator/TaskRecord.java:89` `setTenantId()`
- `src/main/java/com/example/agent/scheduler/ScheduleExecutionRecord.java:82` `setTenantId()`
- `src/main/java/com/example/agent/scheduler/ScheduleSpec.java:62` `setTenantId()`

### P0-19
- `src/main/java/com/example/agent/common/ApiResponse.java:78` `getTraceId()`
- `src/main/java/com/example/agent/common/ErrorResponse.java:66` `getTraceId()`

### P0-20
- `src/main/java/com/example/agent/common/ApiResponse.java:82` `setTraceId()`
- `src/main/java/com/example/agent/common/ErrorResponse.java:70` `setTraceId()`

### P0-21
- `src/main/java/com/example/agent/common/ApiResponse.java:86` `getRequestId()`
- `src/main/java/com/example/agent/common/ErrorResponse.java:74` `getRequestId()`

### P0-22
- `src/main/java/com/example/agent/common/ApiResponse.java:90` `setRequestId()`
- `src/main/java/com/example/agent/common/ErrorResponse.java:78` `setRequestId()`

### P0-23
- `src/main/java/com/example/agent/common/TaskQuery.java:33` `getStatus()`
- `src/main/java/com/example/agent/scheduler/ScheduleQuery.java:15` `getStatus()`

### P0-24
- `src/main/java/com/example/agent/common/TaskQuery.java:37` `setStatus()`
- `src/main/java/com/example/agent/scheduler/ScheduleQuery.java:19` `setStatus()`

### P0-25
- `src/main/java/com/example/agent/common/TaskQuery.java:41` `getCursor()`
- `src/main/java/com/example/agent/history/EventQuery.java:23` `getCursor()`
- `src/main/java/com/example/agent/runtime/StepQuery.java:23` `getCursor()`
- `src/main/java/com/example/agent/scheduler/ScheduleQuery.java:23` `getCursor()`
- `src/main/java/com/example/agent/tools/McpToolListRequest.java:23` `getCursor()`

### P0-26
- `src/main/java/com/example/agent/common/TaskQuery.java:45` `setCursor()`
- `src/main/java/com/example/agent/history/EventQuery.java:27` `setCursor()`
- `src/main/java/com/example/agent/runtime/StepQuery.java:27` `setCursor()`
- `src/main/java/com/example/agent/scheduler/ScheduleQuery.java:27` `setCursor()`
- `src/main/java/com/example/agent/tools/McpToolListRequest.java:27` `setCursor()`

### P0-27
- `src/main/java/com/example/agent/common/TaskQuery.java:49` `getSize()`
- `src/main/java/com/example/agent/history/EventQuery.java:31` `getSize()`
- `src/main/java/com/example/agent/runtime/StepQuery.java:31` `getSize()`
- `src/main/java/com/example/agent/scheduler/ScheduleQuery.java:31` `getSize()`
- `src/main/java/com/example/agent/tools/McpToolListRequest.java:31` `getSize()`

### P0-28
- `src/main/java/com/example/agent/common/TaskQuery.java:53` `setSize()`
- `src/main/java/com/example/agent/history/EventQuery.java:35` `setSize()`
- `src/main/java/com/example/agent/runtime/StepQuery.java:35` `setSize()`
- `src/main/java/com/example/agent/scheduler/ScheduleQuery.java:35` `setSize()`
- `src/main/java/com/example/agent/tools/McpToolListRequest.java:35` `setSize()`

### P0-29
- `src/main/java/com/example/agent/common/TaskQuery.java:30` `TaskQuery()`
- `src/main/java/com/example/agent/scheduler/ScheduleQuery.java:12` `ScheduleQuery()`

### P0-30
- `src/main/java/com/example/agent/common/TaskResponse.java:72` `getResult()`
- `src/main/java/com/example/agent/common/TaskStatusResponse.java:181` `getResult()`

### P0-31
- `src/main/java/com/example/agent/common/TaskResponse.java:76` `setResult()`
- `src/main/java/com/example/agent/common/TaskStatusResponse.java:193` `setResult()`

### P0-32
- `src/main/java/com/example/agent/context/AuditMetadata.java:41` `getCreatedAt()`
- `src/main/java/com/example/agent/multiagent/HandoffRecord.java:42` `getCreatedAt()`

### P0-33
- `src/main/java/com/example/agent/context/AuditMetadata.java:45` `setCreatedAt()`
- `src/main/java/com/example/agent/multiagent/HandoffRecord.java:46` `setCreatedAt()`

### P0-34
- `src/main/java/com/example/agent/context/Citation.java:98` `getSnippet()`
- `src/main/java/com/example/agent/research/ResearchCitation.java:25` `getSnippet()`

### P0-35
- `src/main/java/com/example/agent/context/Citation.java:102` `setSnippet()`
- `src/main/java/com/example/agent/research/ResearchCitation.java:29` `setSnippet()`

### P0-36
- `src/main/java/com/example/agent/context/Citation.java:106` `getFetchedAt()`
- `src/main/java/com/example/agent/research/ResearchCitation.java:33` `getFetchedAt()`

### P0-37
- `src/main/java/com/example/agent/context/Citation.java:110` `setFetchedAt()`
- `src/main/java/com/example/agent/research/ResearchCitation.java:37` `setFetchedAt()`

### P0-38
- `src/main/java/com/example/agent/context/EvidencePack.java:194` `setItems()`
- `src/main/java/com/example/agent/multiagent/AgentProfileProperties.java:21` `setItems()`

### P0-39
- `src/main/java/com/example/agent/context/MemoryEvidence.java:54` `getSummaryVersion()`
- `src/main/java/com/example/agent/streaming/ContextCompressionSummary.java:65` `getSummaryVersion()`

### P0-40
- `src/main/java/com/example/agent/context/MemoryEvidence.java:58` `setSummaryVersion()`
- `src/main/java/com/example/agent/streaming/ContextCompressionSummary.java:69` `setSummaryVersion()`

### P0-41
- `src/main/java/com/example/agent/context/PromptAssemblyInput.java:134` `getWorkflowId()`
- `src/main/java/com/example/agent/memory/CompressionRequest.java:34` `getWorkflowId()`

### P0-42
- `src/main/java/com/example/agent/context/PromptAssemblyInput.java:138` `setWorkflowId()`
- `src/main/java/com/example/agent/memory/CompressionRequest.java:38` `setWorkflowId()`

### P0-43
- `src/main/java/com/example/agent/context/ToolCallState.java:80` `getErrorCode()`
- `src/main/java/com/example/agent/runtime/StepResponse.java:49` `getErrorCode()`

### P0-44
- `src/main/java/com/example/agent/context/ToolCallState.java:84` `setErrorCode()`
- `src/main/java/com/example/agent/runtime/StepResponse.java:53` `setErrorCode()`

### P0-45
- `src/main/java/com/example/agent/governance/ReplayRequest.java:40` `getMode()`
- `src/main/java/com/example/agent/history/TimelineRequest.java:22` `getMode()`

### P0-46
- `src/main/java/com/example/agent/governance/ReplayRequest.java:44` `setMode()`
- `src/main/java/com/example/agent/history/TimelineRequest.java:26` `setMode()`

### P0-47
- `src/main/java/com/example/agent/governance/ReplayResponse.java:41` `getStartedAt()`
- `src/main/java/com/example/agent/runtime/StepRecord.java:154` `getStartedAt()`

### P0-48
- `src/main/java/com/example/agent/governance/ReplayResponse.java:45` `setStartedAt()`
- `src/main/java/com/example/agent/runtime/StepRecord.java:158` `setStartedAt()`

### P0-49
- `src/main/java/com/example/agent/governance/ReplayResponse.java:49` `getCompletedAt()`
- `src/main/java/com/example/agent/runtime/StepRecord.java:162` `getCompletedAt()`

### P0-50
- `src/main/java/com/example/agent/governance/ReplayResponse.java:53` `setCompletedAt()`
- `src/main/java/com/example/agent/runtime/StepRecord.java:166` `setCompletedAt()`

### P0-51
- `src/main/java/com/example/agent/governance/ReplayService.java:261` `parseSeq()`
- `src/main/java/com/example/agent/history/EventLogService.java:108` `parseSeq()`
- `src/main/java/com/example/agent/history/TimelineService.java:82` `parseSeq()`

### P0-52
- `src/main/java/com/example/agent/history/EventLogPage.java:31` `getNextCursor()`
- `src/main/java/com/example/agent/runtime/StepTimelineResponse.java:41` `getNextCursor()`
- `src/main/java/com/example/agent/scheduler/SchedulePage.java:31` `getNextCursor()`
- `src/main/java/com/example/agent/tools/McpToolListResponse.java:31` `getNextCursor()`

### P0-53
- `src/main/java/com/example/agent/history/EventLogPage.java:35` `setNextCursor()`
- `src/main/java/com/example/agent/runtime/StepTimelineResponse.java:45` `setNextCursor()`
- `src/main/java/com/example/agent/scheduler/SchedulePage.java:35` `setNextCursor()`
- `src/main/java/com/example/agent/tools/McpToolListResponse.java:35` `setNextCursor()`

### P0-54
- `src/main/java/com/example/agent/history/EventLogPage.java:39` `isHasMore()`
- `src/main/java/com/example/agent/runtime/StepTimelineResponse.java:54` `isHasMore()`
- `src/main/java/com/example/agent/scheduler/SchedulePage.java:39` `isHasMore()`
- `src/main/java/com/example/agent/tools/McpToolListResponse.java:39` `isHasMore()`

### P0-55
- `src/main/java/com/example/agent/history/EventLogPage.java:43` `setHasMore()`
- `src/main/java/com/example/agent/runtime/StepTimelineResponse.java:58` `setHasMore()`
- `src/main/java/com/example/agent/scheduler/SchedulePage.java:43` `setHasMore()`
- `src/main/java/com/example/agent/tools/McpToolListResponse.java:43` `setHasMore()`

### P0-56
- `src/main/java/com/example/agent/history/EventQuery.java:15` `getWorkflowId()`
- `src/main/java/com/example/agent/runtime/StepQuery.java:15` `getWorkflowId()`

### P0-57
- `src/main/java/com/example/agent/history/EventQuery.java:19` `setWorkflowId()`
- `src/main/java/com/example/agent/runtime/StepQuery.java:19` `setWorkflowId()`

### P0-58
- `src/main/java/com/example/agent/history/EventQuery.java:12` `EventQuery()`
- `src/main/java/com/example/agent/runtime/StepQuery.java:12` `StepQuery()`

### P0-59
- `src/main/java/com/example/agent/history/TimelineRecord.java:39` `getTimestamp()`
- `src/main/java/com/example/agent/runtime/ReactObservation.java:50` `getTimestamp()`

### P0-60
- `src/main/java/com/example/agent/history/TimelineRecord.java:43` `setTimestamp()`
- `src/main/java/com/example/agent/runtime/ReactObservation.java:54` `setTimestamp()`

### P0-61
- `src/main/java/com/example/agent/memory/MemoryRecallService.java:516` `trimText()`
- `src/main/java/com/example/agent/memory/MemoryWriteService.java:253` `trimText()`

### P0-62
- `src/main/java/com/example/agent/model/ModelToolDefinition.java:83` `getTags()`
- `src/main/java/com/example/agent/tools/ToolSummary.java:56` `getTags()`

### P0-63
- `src/main/java/com/example/agent/model/ModelToolDefinition.java:87` `setTags()`
- `src/main/java/com/example/agent/tools/ToolSummary.java:60` `setTags()`

### P0-64
- `src/main/java/com/example/agent/model/ModelToolDefinition.java:91` `getCostLevel()`
- `src/main/java/com/example/agent/tools/ToolSummary.java:64` `getCostLevel()`

### P0-65
- `src/main/java/com/example/agent/model/ModelToolDefinition.java:95` `setCostLevel()`
- `src/main/java/com/example/agent/tools/ToolSummary.java:68` `setCostLevel()`

### P0-66
- `src/main/java/com/example/agent/model/ModelToolDefinition.java:99` `getLatencyLevel()`
- `src/main/java/com/example/agent/tools/ToolSummary.java:72` `getLatencyLevel()`

### P0-67
- `src/main/java/com/example/agent/model/ModelToolDefinition.java:103` `setLatencyLevel()`
- `src/main/java/com/example/agent/tools/ToolSummary.java:76` `setLatencyLevel()`

### P0-68
- `src/main/java/com/example/agent/model/ModelToolDefinition.java:107` `getAuthScope()`
- `src/main/java/com/example/agent/tools/ToolSummary.java:80` `getAuthScope()`

### P0-69
- `src/main/java/com/example/agent/model/ModelToolDefinition.java:111` `setAuthScope()`
- `src/main/java/com/example/agent/tools/ToolSummary.java:84` `setAuthScope()`

### P0-70
- `src/main/java/com/example/agent/multiagent/HandoffRequest.java:33` `getContext()`
- `src/main/java/com/example/agent/multiagent/HandoffResult.java:29` `getContext()`
- `src/main/java/com/example/agent/planning/PlanRequest.java:29` `getContext()`

### P0-71
- `src/main/java/com/example/agent/multiagent/HandoffRequest.java:37` `setContext()`
- `src/main/java/com/example/agent/multiagent/HandoffResult.java:33` `setContext()`
- `src/main/java/com/example/agent/planning/PlanRequest.java:33` `setContext()`

### P0-72
- `src/main/java/com/example/agent/orchestrator/JdbcTaskRepository.java:196` `readJson()`
- `src/main/java/com/example/agent/runtime/JdbcStepRecordRepository.java:182` `readJson()`

### P0-73
- `src/main/java/com/example/agent/orchestrator/TaskExecutionService.java:222` `TaskExecution()`
- `src/main/java/com/example/agent/reflection/ReflectionService.java:511` `ReflectionParsingResult()`

### P0-74
- `src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java:819` `resolveTraceId()`
- `src/main/java/com/example/agent/streaming/SseStreamController.java:176` `resolveTraceId()`
- `src/main/java/com/example/agent/gateway/controller/TaskController.java:252` `resolveTraceId()`
- `src/main/java/com/example/agent/gateway/controller/ToolApprovalController.java:98` `resolveTraceId()`

### P0-75
- `src/main/java/com/example/agent/planning/Plan.java:30` `getSteps()`
- `src/main/java/com/example/agent/planning/PlanResult.java:40` `getSteps()`

### P0-76
- `src/main/java/com/example/agent/planning/Plan.java:34` `setSteps()`
- `src/main/java/com/example/agent/planning/PlanResult.java:44` `setSteps()`

### P0-77
- `src/main/java/com/example/agent/reasoning/DebateCoordinator.java:269` `applyPromptBundle()`
- `src/main/java/com/example/agent/research/ResearchPipeline.java:287` `applyPromptBundle()`

### P0-78
- `src/main/java/com/example/agent/research/DeepResearchWorkflowProperties.java:41` `getModelId()`
- `src/main/java/com/example/agent/tools/skill/SkillRoute.java:27` `getModelId()`

### P0-79
- `src/main/java/com/example/agent/research/DeepResearchWorkflowProperties.java:45` `setModelId()`
- `src/main/java/com/example/agent/tools/skill/SkillRoute.java:31` `setModelId()`

### P0-80
- `src/main/java/com/example/agent/sandbox/SandboxResult.java:39` `getError()`
- `src/main/java/com/example/agent/tools/McpToolCallResponse.java:49` `getError()`

### P0-81
- `src/main/java/com/example/agent/sandbox/SandboxResult.java:43` `setError()`
- `src/main/java/com/example/agent/tools/McpToolCallResponse.java:53` `setError()`

### P0-82
- `src/main/java/com/example/agent/tools/McpToolCallRequest.java:55` `setTimeoutMs()`
- `src/main/java/com/example/agent/tools/hook/HookProperties.java:95` `setTimeoutMs()`

### P0-83
- `src/main/java/com/example/agent/tools/hook/HookContext.java:52` `getTenantId()`
- `src/main/java/com/example/agent/domain/event/StreamEvent.java:134` `getTenantId()`

### P0-84
- `src/main/java/com/example/agent/tools/hook/HookContext.java:56` `setTenantId()`
- `src/main/java/com/example/agent/domain/event/StreamEvent.java:138` `setTenantId()`

### P0-85
- `src/main/java/com/example/agent/tools/hook/HookContext.java:60` `getPayload()`
- `src/main/java/com/example/agent/domain/event/StreamEvent.java:142` `getPayload()`

### P0-86
- `src/main/java/com/example/agent/tools/hook/HookContext.java:64` `setPayload()`
- `src/main/java/com/example/agent/domain/event/StreamEvent.java:146` `setPayload()`

### P0-87
- `src/main/java/com/example/agent/gateway/controller/ApprovalController.java:126` `authenticate()`
- `src/main/java/com/example/agent/gateway/controller/BudgetController.java:87` `authenticate()`
- `src/main/java/com/example/agent/gateway/controller/PolicyController.java:69` `authenticate()`
- `src/main/java/com/example/agent/gateway/controller/ReplayController.java:57` `authenticate()`

### P0-88
- `src/main/java/com/example/agent/gateway/controller/ApprovalController.java:133` `getTenantContext()`
- `src/main/java/com/example/agent/gateway/controller/BudgetController.java:94` `getTenantContext()`
- `src/main/java/com/example/agent/gateway/controller/MemoryController.java:103` `getTenantContext()`
- `src/main/java/com/example/agent/gateway/controller/PolicyController.java:76` `getTenantContext()`
- `src/main/java/com/example/agent/gateway/controller/ReplayController.java:64` `getTenantContext()`
- `src/main/java/com/example/agent/gateway/controller/TimelineController.java:129` `getTenantContext()`

### P0-89
- `src/main/java/com/example/agent/gateway/controller/TaskController.java:229` `getTenantContext()`
- `src/main/java/com/example/agent/gateway/controller/ToolApprovalController.java:90` `getTenantContext()`


## 八、归纳建议（是否可抽取到 util 包）
- 若 P0/P1 组集中在同一模块（例如 runtime/reflection/summary），可优先抽取为模块内 utils，避免跨模块耦合。
- 对“摘要/截断/格式化/Map 安全读取”类方法，建议抽取 `runtime.util` 或 `common.text` 子包。
- 对“事件发布/日志构造”类方法，建议抽取 `observability.util` 子包，并保持事件字段一致性。
- 对“对象到 Map/JSON 的安全转换”类方法，建议抽取 `common.convert` 子包。
- P3/P4 相似度较低时不建议抽取，优先保持局部可读性。

