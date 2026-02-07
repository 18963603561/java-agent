# `McpToolClient` 鐨?`post` 鏂规硶鍝嶅簲鎴柇鍒嗘瀽鎶ュ憡

## 缁撹
- `post` 鏂规硶鍐呴儴涓嶅瓨鍦ㄥ鍝嶅簲鍐呭鍋氬瓧绗︿覆鎴柇鐨勯€昏緫銆?- 鍝嶅簲瓒呰繃涓婇檺鏃朵細鐩存帴鎶涘嚭 `MCP_RESPONSE_TOO_LARGE`锛屼笉浼氳繑鍥為儴鍒嗗唴瀹广€?- 鍝嶅簲涓婇檺鐢?`maxResponseBytes` 鎺у埗锛岄粯璁ゅ€间负 `2 * 1024 * 1024` 瀛楄妭锛屽彲鍦?`agent.mcp.servers[].maxResponseBytes` 閰嶇疆銆?
## 浠ｇ爜璇佹嵁涓庡叧閿矾寰?- 鍝嶅簲澶у皬涓婇檺鏉ユ簮浜?`resolveMaxResponseBytes`锛屼紭鍏堣鍙?`McpServerProperties.McpServer#maxResponseBytes`锛屾湭閰嶇疆鏃朵娇鐢ㄩ粯璁ゅ€笺€?- `WebClient` 璺緞涓娇鐢?`bodyToFlux(DataBuffer)` 姹囪仛鍒?`ByteArrayOutputStream`锛屾瘡娆¤拷鍔犲墠妫€鏌?`stream.size() + readable`锛岃秴杩囦笂闄愮珛鍗虫姏鍑?`ErrorCodeException`锛岄敊璇爜涓?`MCP_RESPONSE_TOO_LARGE`銆?- `JDK` 瀹㈡埛绔矾寰勪腑閫氳繃 `readResponseBytes` 璇诲彇娴佸苟鍦ㄥ惊鐜唴鍋氱浉鍚岀殑澶у皬妫€鏌ワ紝瓒呰繃涓婇檺鍚屾牱鐩存帴鎶涘嚭 `MCP_RESPONSE_TOO_LARGE`銆?- 瑙ｆ瀽闃舵 `readResponseAsMap` 浠呮墽琛屽弽搴忓垪鍖栵紝涓嶅寘鍚换浣曟埅鏂垨瑁佸壀閫昏緫銆?- 鍗曞厓娴嬭瘯 `McpToolClientTest#rejectsResponseWhenTooLarge` 楠岃瘉浜嗚秴闄愭椂鎶涘嚭 `MCP_RESPONSE_TOO_LARGE`锛屾病鏈夎繑鍥為儴鍒嗗搷搴斻€?
## 瀵光€滄埅鏂幇璞♀€濈殑鍙兘瑙ｉ噴锛堜笉鍦?`post` 鍐呴儴锛?- 瀹為檯瓒呴檺鏃惰鏂规硶鍙細鎶涢敊锛屽鏋滆皟鐢ㄦ柟鎹曡幏寮傚父鍚庤繑鍥炩€滈儴鍒嗙粨鏋溾€濓紝闇€瑕佹鏌ヤ笂灞傝皟鐢ㄩ摼鏄惁瀛樺湪闄嶇骇鎴栨嫾鎺ラ€昏緫銆?- 鍙嶅悜浠ｇ悊鎴栫綉鍏冲彲鑳藉鍝嶅簲浣撳ぇ灏忔湁闄愬埗锛屽鑷村悗绔疄闄呰繑鍥炲凡琚埅鏂殑鍐呭銆?- 璁板綍鏃ュ織銆佸墠绔睍绀烘垨鎸佷箙鍖栧瓧娈甸暱搴﹀彈闄愪篃鍙兘閫犳垚鈥滅湅璧锋潵琚埅鏂€濈殑鐜拌薄锛屼絾杩欎笌 `post` 鏂规硶鍐呴儴鏃犲叧銆?
## 寤鸿鎺掓煡椤?- 鏍稿閰嶇疆 `agent.mcp.servers[].maxResponseBytes` 鏄惁杩囧皬锛岀‘璁ゆ槸鍚﹁Е鍙戜簡 `MCP_RESPONSE_TOO_LARGE`銆?- 鍦ㄥ彂鐢熲€滄埅鏂€濇椂纭璋冪敤閾句腑鏄惁鍑虹幇 `MCP_RESPONSE_TOO_LARGE` 鎴?`MCP_UNAVAILABLE`锛屾帓闄よ涓婂眰鍚炴帀寮傚父鐨勬儏鍐点€?- 妫€鏌ョ綉鍏虫垨浠ｇ悊鐨勫搷搴斾綋澶у皬闄愬埗锛屼互鍙婅皟鐢ㄦ柟鐨勫搷搴斿簭鍒楀寲鎴栧瓨鍌ㄥ瓧娈甸暱搴﹂檺鍒躲€?
## 鐩稿叧浠ｇ爜浣嶇疆
- `src/main/java/com/example/agent/tools/McpToolClient.java`锛歚post`銆乣postWithJdkHttpClient`銆乣readResponseBytes`銆乣readResponseAsMap`銆?- `src/main/java/com/example/agent/tools/McpServerProperties.java`锛歚maxResponseBytes` 榛樿鍊间笌閰嶇疆鍏ュ彛銆?- `src/test/java/com/example/agent/tools/McpToolClientTest.java`锛氳秴闄愬搷搴旀祴璇曠敤渚嬨€
## 补充分析：现场字符串截断的可能来源（非 `post`）

### 1. 摘要模块主动截断（高概率）
- `StepOutputSummaryBuilder` 生成 `outputSummary`、`toolResultSummary`、`stepSummary`、`outputDigest`，并按 `maxFieldChars`（默认 1000）与 `maxChars`（默认 4000）限制文本长度，同时将 `truncated` 标记为 `true`。
- 摘要在 `StepRuntimeService#completeStep` 中合并进输出；如果前端或接口只展示摘要字段（例如 `outputSummary.sample`、`stepSummary.summary`），会看到被截断的字符串。
- 对应配置：`agent.summary.max-field-chars`、`agent.summary.max-chars`、`agent.summary.max-list-items`。

### 2. 最终摘要二次截断
- `FinalOutputService` 会对最终 `summary` 做长度限制（默认 800），超限后追加 `...(truncated)`。
- 对应配置：`agent.final-output.prompt-summary-max-chars`。

### 3. 记录与证据摘要截断
- `ToolExecutor`、`ApprovalService` 在构建摘要或审计信息时使用 `truncate`，不影响原始输出，但若截图来自日志或审计视图，仍会呈现截断结果。

### 快速判定方式
- 如果输出中出现 `truncated=true` 或 `...(truncated)`，基本可判定为摘要层截断。
- 若接口返回 `200` 且响应体大小明显小于 `maxResponseBytes`，可排除 `McpToolClient#post` 的响应大小限制。
- 若直接调用远端 `MCP` 服务仍出现截断，则应排查远端工具或上游网关的响应限制。