# 鏅鸿兘浣撳熀纭€鍔熻兘婊¤冻鎬у垎鏋愭姤鍛?
## 鑼冨洿涓庢柟娉?
杈撳叆鏉愭枡
```
vendor/ai-agent-book/zh/Part1-Agent鍩虹/README.md
vendor/ai-agent-book/zh/Part1-Agent鍩虹/绗?1绔狅細Agent鐨勬湰璐?md
vendor/ai-agent-book/zh/Part1-Agent鍩虹/绗?2绔狅細ReAct寰幆.md
```

妫€绱㈣寖鍥?```
src/main/java
src/test/java
```

鏈璇存槑
ReAct
鎺ㄧ悊-琛屽姩-瑙傚療寰幆銆?
妫€绱㈠叧閿瘝
鏅鸿兘浣撲笌杩愯鏃跺叧閿瘝
```
AgentRuntime
PlannerService
TaskOrchestrator
WorkflowRouter
StepRuntimeService
```

澶фā鍨嬩笌鍙嶆€濆叧閿瘝
```
ModelInvocationService
LlmClient
ModelRouter
ReflectionService
ThoughtTreeService
```

宸ュ叿涓庢矙绠卞叧閿瘝
```
ToolExecutor
ToolRegistry
McpToolClient
EnforcementGateway
HookManager
SandboxExecutor
WasiSandboxExecutor
```

璁板繂涓庡悜閲忓叧閿瘝
```
MemoryStore
MemoryRepository
VectorStore
EmbeddingService
CompressionRequest
```

棰勭畻涓庢不鐞嗗叧閿瘝
```
TokenBudgetManager
TokenUsage
PolicyEngine
RateLimitService
CircuitBreakerManager
```

浜嬩欢涓庡璁″叧閿瘝
```
EventStreamService
EventLogService
ReplayService
StreamEvent
EventType
```

鎺ㄧ悊-琛屽姩-瑙傚療寰幆鍏抽敭璇?```
ReAct
React
Reason
Act
Observe
ObservationWindow
MaxIterations
MinIterations
```

瀹℃壒涓庣‘璁ゅ叧閿瘝
```
approve
approval
瀹℃壒
纭
APPROVAL_REQUESTED
APPROVAL_DECISION
```

## 鎬讳綋缁撹

- 鏁翠綋缁撹涓洪儴鍒嗘弧瓒炽€?- 鐩爣椹卞姩鎵ц銆佽鍒掋€佸鏅鸿兘浣撱€佸伐鍏疯皟鐢ㄣ€佽蹇嗗瓨鍙栥€侀绠椾笌娌欑绛夎兘鍔涘叿澶囥€?- 鎺ㄧ悊-琛屽姩-瑙傚療寰幆涓庤凯浠ｇ粓姝㈡潯浠剁己澶憋紝瀹℃壒纭鏈哄埗鏈疄鐜般€?
## 鍔熻兘瀵圭収鏄庣粏

### 鏅鸿兘浣撳畾涔変笌鐩爣椹卞姩鎵ц

缁撹
婊¤冻

璇佹嵁
```
src/main/java/com/example/agent/orchestrator/TaskOrchestrator.java
TaskOrchestrator#submitTask
TaskOrchestrator#handleWorkflowRoute
src/main/java/com/example/agent/orchestrator/WorkflowRouter.java
WorkflowRouter#route
src/main/java/com/example/agent/runtime/AgentRuntime.java
AgentRuntime#run
src/main/java/com/example/agent/planning/PlannerService.java
PlannerService#plan
```

璇存槑
- 浠诲姟鎻愪氦鍚庤嚜鍔ㄧ敓鎴愯鍒掑苟鎵ц姝ラ锛岀鍚堢洰鏍囬┍鍔ㄤ笌鑷富鎺ㄨ繘鐗瑰緛銆?

### 澶фā鍨嬭兘鍔?
缁撹
婊¤冻

璇佹嵁
```
src/main/java/com/example/agent/model/ModelInvocationService.java
ModelInvocationService#invoke
src/main/java/com/example/agent/model/LlmClient.java
LlmClient#generate
src/main/java/com/example/agent/model/ModelRouter.java
ModelRouter#route
```

璇存槑
- 妯″瀷璋冪敤涓庤矾鐢辫兘鍔涘叿澶囷紝瑙勫垝涓庡弽鎬濆潎鍙Е鍙戞ā鍨嬭緭鍑恒€?

### 宸ュ叿璋冪敤鑳藉姏

缁撹
婊¤冻

璇佹嵁
```
src/main/java/com/example/agent/agentcore/ToolExecutor.java
ToolExecutor#execute
src/main/java/com/example/agent/tools/McpToolClient.java
McpToolClient#callTool
src/main/java/com/example/agent/agentcore/EnforcementGateway.java
EnforcementGateway#execute
src/main/java/com/example/agent/agentcore/ToolRegistry.java
ToolRegistry#listDefinitions
```

璇存槑
- 鎻愪緵宸ュ叿瑙ｆ瀽銆佽皟鐢ㄤ笌缁撴灉杩斿洖閾捐矾锛屾敮鎸佹湰鍦颁笌杩滅▼宸ュ叿鎵ц銆?

### 璁板繂鑳藉姏

缁撹
閮ㄥ垎婊¤冻

璇佹嵁
```
src/main/java/com/example/agent/memory/MemoryStore.java
MemoryStore#save
MemoryStore#search
MemoryStore#compress
src/main/java/com/example/agent/memory/MemoryRepository.java
src/main/java/com/example/agent/memory/VectorStore.java
src/main/java/com/example/agent/memory/QdrantVectorStore.java
src/main/java/com/example/agent/gateway/controller/MemoryController.java
```

说明
- 具备记忆保存、检索、压缩与向量索引接入，支持 recent/semantic/compressed 分层。
- 运行时存在自动召回与自动写入链路（AgentRuntime.run -> MemoryRecallService.recall；AgentRuntime.persistMemorySafely -> MemoryWriteService.saveTaskMemory）。
- 记忆调度以分层检索与压缩阈值为主，未见独立的短期/长期调度器或显式优先级策略。
### 瑙勫垝涓庝换鍔″垎瑙?
缁撹
婊¤冻

璇佹嵁
```
src/main/java/com/example/agent/planning/PlannerService.java
PlannerService#plan
PlannerService#buildHeuristicPlan
src/main/java/com/example/agent/runtime/AgentRuntime.java
AgentRuntime#executeStep
```

璇存槑
- 鍏峰浠诲姟瑙勫垝涓庢楠ゆ墽琛岋紝鏀寔澶辫触鍚庨噸璇曚笌閲嶈鍒掋€?

### 澶氭櫤鑳戒綋鍗忎綔

缁撹
婊¤冻

璇佹嵁
```
src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java
MultiAgentCoordinator#coordinate
src/main/java/com/example/agent/multiagent/SupervisorCoordinator.java
SupervisorCoordinator#supervise
```

璇存槑
- 鏀寔鍥㈤槦瑙掕壊鐢熸垚涓庡崗浣滆皟搴︺€?

### 鎺ㄧ悊-琛屽姩-瑙傚療寰幆

缁撹
涓嶆弧瓒?
璇佹嵁
```
src/main/java/com/example/agent/agentcore/EnforcementGateway.java
EnforcementGateway#execute
src/main/java/com/example/agent/domain/event/EventType.java
EventType.TOOL_INVOKED
EventType.TOOL_OBSERVATION
```

妫€绱㈠叧閿瘝
```
ReAct
React
Reason
Act
Observe
```

妫€绱㈢粨鏋?鏃犲懡涓?
璇存槑
- 瀛樺湪宸ュ叿璋冪敤涓庤瀵熶簨浠讹紝浣嗘湭鍙戠幇鏄惧紡鎺ㄧ悊-琛屽姩-瑙傚療寰幆鐨勫疄鐜颁笌椹卞姩銆?

### 缁堟鏉′欢涓庤凯浠ｆ帶鍒?
缁撹
涓嶆弧瓒?
妫€绱㈠叧閿瘝
```
ObservationWindow
MaxIterations
MinIterations
shouldStop
converge
```

妫€绱㈢粨鏋?鏃犲懡涓?
璇存槑
- 鏈彂鐜板惊鐜粓姝㈡潯浠躲€佹渶灏忔垨鏈€澶ц疆娆＄瓑鎺у埗閫昏緫銆?- 鐜版湁瓒呮椂涓庨噸璇曚富瑕佺敤浜庡閮ㄨ皟鐢ㄤ笌姝ラ澶辫触澶勭悊锛屼笉鏋勬垚寰幆绾х粓姝㈡潯浠躲€?

### 瑙傚療绐楀彛涓庡帇缂╂満鍒?
缁撹
閮ㄥ垎婊¤冻

璇佹嵁
```
src/main/java/com/example/agent/memory/MemoryStore.java
MemoryStore#compress
```

妫€绱㈠叧閿瘝
```
ObservationWindow
```

妫€绱㈢粨鏋?鏃犲懡涓?
璇存槑
- 浠呮彁渚涜蹇嗗帇缂╂帴鍙ｏ紝缂哄皯瑙傚療绐楀彛涓庤嚜鍔ㄥ帇缂╄Е鍙戞満鍒躲€?

### 鎶ゆ爮涓庢不鐞?
缁撹
閮ㄥ垎婊¤冻

璇佹嵁
```
src/main/java/com/example/agent/budget/TokenBudgetManager.java
src/main/java/com/example/agent/auth/ApiKeyAuthenticator.java
src/main/java/com/example/agent/policy/PolicyEngine.java
src/main/java/com/example/agent/agentcore/SandboxExecutor.java
src/main/java/com/example/agent/sandbox/WasiSandboxExecutor.java
src/main/java/com/example/agent/history/EventLogService.java
src/main/java/com/example/agent/governance/ReplayService.java
src/main/java/com/example/agent/tools/hook/HookManager.java
src/main/java/com/example/agent/domain/event/EventType.java
EventType.APPROVAL_REQUESTED
EventType.APPROVAL_DECISION
```

妫€绱㈠叧閿瘝
```
approve
approval
瀹℃壒
纭
APPROVAL_REQUESTED
APPROVAL_DECISION
```

妫€绱㈢粨鏋?鏃犲懡涓?
璇存槑
- 棰勭畻銆佹潈闄愩€佹矙绠便€佷簨浠跺璁′笌鍥炴斁鑳藉姏鍏峰銆?- 瀹℃壒纭鏈哄埗浠呭瓨鍦ㄤ簨浠舵灇涓惧畾涔夛紝缂哄皯娴佺▼瀹炵幇涓庡澶栨帴鍙ｃ€?

### 鍙娴嬫€т笌杩借釜

缁撹
婊¤冻

璇佹嵁
```
src/main/java/com/example/agent/observability/MetricsPublisher.java
src/main/java/com/example/agent/observability/TracingPublisher.java
src/main/java/com/example/agent/streaming/EventStreamService.java
src/main/java/com/example/agent/history/EventLogService.java
src/main/java/com/example/agent/common/ApiResponse.java
src/main/java/com/example/agent/gateway/controller/GlobalExceptionHandler.java
```

璇存槑
- 鎸囨爣涓庤拷韪兘鍔涘瓨鍦紝鍝嶅簲涓繑鍥為摼璺爣璇嗭紝浜嬩欢娴佷笌鏃ュ織鍙敮鎾戣拷韪垎鏋愩€?
## 鍏抽敭缂哄彛涓庡奖鍝?
- 鎺ㄧ悊-琛屽姩-瑙傚療寰幆缂哄け锛屾棤娉曟弧瓒充功涓鏅鸿兘浣撴牳蹇冩墽琛屾ā寮忕殑瑕佹眰銆?- 缁堟鏉′欢涓庤凯浠ｆ帶鍒剁己澶憋紝缂哄皯瀵规棤闄愬惊鐜笌鎴愭湰澶辨帶鐨勭洿鎺ラ槻鎶ゃ€?- 瀹℃壒纭鏈哄埗缂哄け锛屼笉婊¤冻楂橀闄╁姩浣滅殑浜哄伐纭瑕佹眰銆?
## 缁撹

- 褰撳墠瀹炵幇瑕嗙洊浜嗙洰鏍囬┍鍔ㄦ墽琛屻€佽鍒掋€佸鏅鸿兘浣撱€佸伐鍏疯皟鐢ㄣ€佽蹇嗗瓨鍙栥€侀绠椾笌娌欑绛夊熀纭€鑳藉姏銆?- 闇€琛ュ厖鎺ㄧ悊-琛屽姩-瑙傚療寰幆銆佽凯浠ｇ粓姝㈡潯浠朵笌瀹℃壒纭閾捐矾锛屾柟鑳藉畬鏁存弧瓒崇涓€閮ㄥ垎鐨勬牳蹇冨姛鑳借姹傘€