# 运行时 Map/JSON Key 访问散乱审计报告

- 生成时间：2026-02-06 16:09:28
- 扫描范围：
  - 优先：`src/main/java/com/example/agent/runtime/**`
  - 附加：`src/main/java/com/example/agent/capabilities/**`、`src/main/java/com/example/agent/reflection/**`（仅用于发现与输出/refs/summary 相关的消费点）
- 判定规则（命中）：
  - `Map/JsonNode` 等对象的 `.get("...")` / `.containsKey("...")` / `.getOrDefault("...")` / `.path("...")` / `.has("...")` / `.hasNonNull("...")` / `.at("...")`
  - 动态 key：`.keySet()` / `.entrySet()`（不含具体 key，但属于“散乱访问”风险点）
- 风险分级（按同一 key 覆盖的类数）：
  - `HIGH`：同一 key 出现在 >= 3 个类
  - `MEDIUM`：同一 key 出现在 2 个类

## A. Map key 硬编码清单（按 key 聚合）

### A1. 总览（读取型 key Top 30）

| key | 命中类数 | 命中次数 | 风险 | 归类 |
|---|---:|---:|---|---|
| `result` | 9 | 11 | HIGH | result/raw |
| `tool` | 8 | 15 | HIGH | tool/toolName |
| `context` | 7 | 15 | HIGH | other |
| `toolName` | 7 | 12 | HIGH | tool/toolName |
| `summary` | 7 | 8 | HIGH | summary/digest/truncated |
| `query` | 6 | 8 | HIGH | other |
| `rawRef` | 5 | 19 | HIGH | rawRef/refs |
| `rawResult` | 5 | 5 | HIGH | result/raw |
| `requiresApproval` | 4 | 15 | HIGH | other |
| `truncated` | 4 | 9 | HIGH | summary/digest/truncated |
| `raw` | 4 | 6 | HIGH | result/raw |
| `citations` | 4 | 4 | HIGH | other |
| `question` | 4 | 4 | HIGH | other |
| `topic` | 4 | 4 | HIGH | other |
| `disableTools` | 3 | 9 | HIGH | other |
| `toolChoice` | 3 | 8 | HIGH | other |
| `type` | 3 | 6 | HIGH | other |
| `message` | 3 | 5 | HIGH | other |
| `refs` | 3 | 5 | HIGH | rawRef/refs |
| `answer` | 3 | 4 | HIGH | other |
| `charCount` | 3 | 4 | HIGH | other |
| `decisionRawRef` | 3 | 4 | HIGH | rawRef/refs |
| `keyCount` | 3 | 4 | HIGH | other |
| `modelRawRef` | 3 | 4 | HIGH | rawRef/refs |
| `summaryRawRef` | 3 | 4 | HIGH | rawRef/refs |
| `toolRawRef` | 3 | 4 | HIGH | rawRef/refs |
| `arguments` | 3 | 3 | HIGH | other |
| `approvalSource` | 2 | 6 | MEDIUM | other |
| `status` | 2 | 5 | MEDIUM | other |
| `mode` | 2 | 4 | MEDIUM | other |

### A2. 重点字段（按指定语义分组，列出全部命中位置）

### rawRef / refs

#### `rawRef`（类数=5，命中=19，风险=HIGH）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:804` `get`：`Object direct = output.get("rawRef");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:809` `get`：`Object nested = rawResultMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:815` `get`：`Object nested = resultMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:821` `get`：`Object nested = rawMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:928` `containsKey`：`if (StringUtils.hasText(decisionRawRef) && !output.containsKey("rawRef")) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:931` `containsKey`：`if (StringUtils.hasText(summaryRawRef) && !output.containsKey("rawRef")) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1370` `get`：`Object direct = result.get("rawRef");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1376` `get`：`Object nested = rawMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1383` `get`：`Object nested = nestedMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:66` `get`：`String direct = readString(rawOutput.get("rawRef"));`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:71` `get`：`String nested = readString(rawResultMap.get("rawRef"));`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:77` `get`：`String nested = readString(resultMap.get("rawRef"));`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:83` `get`：`String nested = readString(rawMap.get("rawRef"));`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:602` `get`：`Object direct = output.get("rawRef");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:607` `get`：`Object nested = rawResultMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:613` `get`：`Object nested = resultMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:619` `get`：`Object nested = rawMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:187` `get`：`Object direct = payload.get("rawRef");`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:208` `get`：`Object nested = map.get("rawRef");`

#### `refs`（类数=3，命中=5，风险=HIGH）

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1403` `get`：`Object refsObj = output.get("refs");`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:102` `get`：`mergeRefs(refs, rawOutput.get("refs"));`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:108` `get`：`mergeRefs(refs, rawMap.get("refs"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:217` `get`：`mergeRefs(refs, payload.get("refs"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:223` `get`：`mergeRefs(refs, rawMap.get("refs"));`

#### `decisionRawRef`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:103` `get`：`mergeRefValue(refs, "decisionRawRef", rawOutput.get("decisionRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:218` `get`：`mergeRefValue(refs, "decisionRawRef", payload.get("decisionRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:416` `get`：`refs.setDecisionRawRef(extracted.get("decisionRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:462` `get`：`refs.setDecisionRawRef(extracted.get("decisionRawRef"));`

#### `summaryRawRef`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:104` `get`：`mergeRefValue(refs, "summaryRawRef", rawOutput.get("summaryRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:219` `get`：`mergeRefValue(refs, "summaryRawRef", payload.get("summaryRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:417` `get`：`refs.setSummaryRawRef(extracted.get("summaryRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:465` `get`：`refs.setSummaryRawRef(extracted.get("summaryRawRef"));`

#### `toolRawRef`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:105` `get`：`mergeRefValue(refs, "toolRawRef", rawOutput.get("toolRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:220` `get`：`mergeRefValue(refs, "toolRawRef", payload.get("toolRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:418` `get`：`refs.setToolRawRef(extracted.get("toolRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:468` `get`：`refs.setToolRawRef(extracted.get("toolRawRef"));`

#### `modelRawRef`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:106` `get`：`mergeRefValue(refs, "modelRawRef", rawOutput.get("modelRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:221` `get`：`mergeRefValue(refs, "modelRawRef", payload.get("modelRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:419` `get`：`refs.setModelRawRef(extracted.get("modelRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:471` `get`：`refs.setModelRawRef(extracted.get("modelRawRef"));`

### tool / toolName

#### `tool`（类数=8，命中=15，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1003` `get`：`Object tool = context.get("tool");`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1012` `get`：`Object innerTool = inner.get("tool");`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:224` `get`：`Object toolName = stepInput.get("tool");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:250` `get`：`Map<String, Object> toolMap = readMap(decision.get("tool"));`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:893` `containsKey`：`if (!output.containsKey("tool")) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:469` `get`：`String tool = toText(output.get("tool"));`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:670` `get`：`Object tool = request.getContext().get("tool");`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:173` `get`：`Object tool = payload.get("tool");`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:158` `get`：`Object tool = stepSummary.get("tool");`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:444` `get`：`String toolName = rawOutput != null && rawOutput.get("tool") != null ? String.valueOf(rawOutput.get("tool")) : null;`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:444` `get`：`String toolName = rawOutput != null && rawOutput.get("tool") != null ? String.valueOf(rawOutput.get("tool")) : null;`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:121` `get`：`Object tool = stepInput.get("tool");`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:131` `get`：`Object tool = request.getContext().get("tool");`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:264` `get`：`Object tool = stepInput.get("tool");`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:246` `get`：`Object value = map.get("tool");`

#### `toolName`（类数=7，命中=12，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1007` `get`：`Object toolName = context.get("toolName");`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1016` `get`：`Object innerToolName = inner.get("toolName");`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1053` `get`：`String name = map.get("toolName") != null ? map.get("toolName").toString() : null;`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1053` `get`：`String name = map.get("toolName") != null ? map.get("toolName").toString() : null;`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:554` `get`：`String name = map.get("toolName") != null ? map.get("toolName").toString() : null;`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:554` `get`：`String name = map.get("toolName") != null ? map.get("toolName").toString() : null;`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:226` `get`：`toolName = stepInput.get("toolName");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:471` `get`：`tool = toText(output.get("toolName"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:175` `get`：`tool = payload.get("toolName");`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:125` `get`：`Object toolName = stepInput.get("toolName");`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:268` `get`：`Object toolNameObj = stepInput.get("toolName");`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:248` `get`：`value = map.get("toolName");`

### result / raw / rawResult

#### `result`（类数=9，命中=11，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:167` `get`：`Object resultObj = response.get("result");`
- `src/main/java/com/example/agent/capabilities/tools/hook/EvidencePackHookHandler.java:150` `get`：`Object result = payload.get("result");`
- `src/main/java/com/example/agent/capabilities/tools/hook/EvidencePackHookHandler.java:165` `get`：`Object result = payload.get("result");`
- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:642` `containsKey`：`if (!response.containsKey("result")) {`
- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:645` `get`：`return response.get("result");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:814` `get`：`if (output.get("result") instanceof Map<?, ?> resultMap) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1381` `get`：`Object nestedResult = result.get("result");`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:76` `get`：`if (rawOutput.get("result") instanceof Map<?, ?> resultMap) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:612` `get`：`if (output.get("result") instanceof Map<?, ?> resultMap) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:196` `get`：`ref = readNestedRawRef(payload.get("result"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:437` `get`：`Object result = rawOutput.get("result");`

#### `raw`（类数=4，命中=6，风险=HIGH）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:820` `get`：`if (output.get("raw") instanceof Map<?, ?> rawMap) {`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:82` `get`：`if (rawOutput.get("raw") instanceof Map<?, ?> rawMap) {`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:107` `get`：`if (rawOutput.get("raw") instanceof Map<?, ?> rawMap) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:618` `get`：`if (output.get("raw") instanceof Map<?, ?> rawMap) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:200` `get`：`ref = readNestedRawRef(payload.get("raw"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:222` `get`：`if (payload.get("raw") instanceof Map<?, ?> rawMap) {`

#### `rawResult`（类数=5，命中=5，风险=HIGH）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:808` `get`：`if (output.get("rawResult") instanceof Map<?, ?> rawResultMap) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1374` `get`：`Object rawResult = result.get("rawResult");`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:70` `get`：`if (rawOutput.get("rawResult") instanceof Map<?, ?> rawResultMap) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:606` `get`：`if (output.get("rawResult") instanceof Map<?, ?> rawResultMap) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:192` `get`：`ref = readNestedRawRef(payload.get("rawResult"));`

#### `data`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:1102` `get`：`Object data = response.get("data");`

#### `output`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1093` `get`：`Object output = context.get("output");`

### stepSummary / outputSummary / outputDigest / truncated

#### `stepSummary`（类数=2，命中=3，风险=MEDIUM）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:838` `get`：`Map<String, Object> stepSummary = normalizeStepSummary(summary.get("stepSummary"), record, null);`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:157` `get`：`if (summary.get("stepSummary") instanceof Map<?, ?> stepSummary) {`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:483` `get`：`if (summaryMap.get("stepSummary") instanceof Map<?, ?> stepSummaryMap) {`

#### `outputSummary`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:490` `get`：`if (summaryMap.get("outputSummary") instanceof Map<?, ?> outputSummaryMap) {`

#### `toolResultSummary`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:493` `get`：`if (summaryMap.get("toolResultSummary") instanceof Map<?, ?> toolSummaryMap) {`

#### `outputDigest`（类数=2，命中=4，风险=MEDIUM）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:840` `containsKey`：`if (!summary.containsKey("outputDigest")) {`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:147` `get`：`Map<String, Object> digest = summary.get("outputDigest") instanceof Map<?, ?> map`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:150` `get`：`if (summary.get("outputDigest") instanceof Map<?, ?> map) {`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:502` `get`：`if (summaryMap.get("outputDigest") instanceof Map<?, ?> outputDigestMap) {`

#### `truncated`（类数=4，命中=9，风险=HIGH）

- `src/main/java/com/example/agent/reflection/ReflectionService.java:382` `get`：`boolean truncated = Boolean.TRUE.equals(digest.get("truncated"));`
- `src/main/java/com/example/agent/reflection/ReflectionService.java:386` `get`：`appendDigestField(builder, "truncated", digest.get("truncated"));`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:847` `containsKey`：`if (!summary.containsKey("truncated")) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:540` `get`：`Object truncated = output != null ? output.get("truncated") : null;`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:544` `get`：`Object digestValue = outputDigest != null ? outputDigest.get("truncated") : null;`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:555` `get`：`appendDigestField(builder, "truncated", digest.get("truncated"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:155` `get`：`Boolean truncated = summary.get("truncated") instanceof Boolean value ? value : null;`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:505` `get`：`boolean truncated = summaryMap.get("truncated") instanceof Boolean value && value;`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:528` `get`：`if (map.get("truncated") instanceof Boolean value) {`

#### `summary`（类数=7，命中=8，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:185` `get`：`record.setSummary(valueAsString(payload.get("summary")));`
- `src/main/java/com/example/agent/reflection/ReflectionService.java:342` `get`：`Object summaryValue = outputSummary.get("summary");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:864` `get`：`Object summaryValue = stepSummary.get("summary");`
- `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:181` `get`：`if (stepSummary != null && stepSummary.get("summary") != null) {`
- `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:182` `get`：`return Map.of("answer", String.valueOf(stepSummary.get("summary")));`
- `src/main/java/com/example/agent/runtime/output/FinalOutputService.java:322` `get`：`Object summaryValue = stepSummary.get("summary");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:507` `get`：`Object summary = summaryMap.get("summary");`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:484` `get`：`Object text = stepSummaryMap.get("summary");`

### A3. 动态 key 遍历（`.keySet()` / `.entrySet()`）

- `src/main/java/com/example/agent/capabilities/memory/InMemoryMemoryRepository.java:71` `entrySet`：`Iterator<Map.Entry<String, MemoryRecord>> iterator = records.entrySet().iterator();`
- `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java:501` `keySet`：`for (Object key : map.keySet()) {`
- `src/main/java/com/example/agent/capabilities/tools/hook/EvidencePackHookHandler.java:167` `keySet`：`return "resultKeys=" + resultMap.keySet();`
- `src/main/java/com/example/agent/capabilities/tools/registry/ToolRegistry.java:143` `entrySet`：`for (Map.Entry<String, String> entry : definitionSources.entrySet()) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:77` `keySet`：`Set<String> allowed = properties != null ? properties.keySet() : Set.of();`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:78` `keySet`：`for (String key : target.keySet()) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:88` `entrySet`：`for (Map.Entry<String, Object> entry : properties.entrySet()) {`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:231` `keySet`：`payload.put("inputKeys", stepInput.keySet());`
- `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:94` `keySet`：`finalOutput != null ? finalOutput.keySet() : List.of());`
- `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:160` `keySet`：`result.keySet());`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:256` `keySet`：`log.info("LLM 步骤完成, workflowId={}, outputKeys={}", workflowId, answerOutput.keySet());`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:285` `keySet`：`workflowId, resolvedSummaryMode, toolResult.status, output.keySet());`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:294` `keySet`：`workflowId, toolResult.status, summaryOutput.output.keySet());`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:320` `keySet`：`workflowId, toolName, summaryOutput.output.keySet());`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:968` `keySet`：`List<String> keys = new ArrayList<>(toolResult.keySet());`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:994` `keySet`：`List<String> keys = new ArrayList<>(toolResult.keySet());`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1256` `entrySet`：`for (Map.Entry<?, ?> entry : source.entrySet()) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1350` `entrySet`：`for (Map.Entry<?, ?> entry : map.entrySet()) {`
- `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:120` `keySet`：`runtimeContext.asMap().keySet());`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:166` `entrySet`：`for (Map.Entry<?, ?> entry : map.entrySet()) {`
- `src/main/java/com/example/agent/runtime/step/executor/LlmStepExecutor.java:63` `keySet`：`log.info("LLM 步骤完成, workflowId={}, outputKeys={}", workflowId, output.keySet());`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:177` `keySet`：`toolArguments.keySet());`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:187` `keySet`：`output.keySet());`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:203` `keySet`：`summaryOutput.keySet());`
- `src/main/java/com/example/agent/runtime/structured/GenericStructuredExtractor.java:28` `keySet`：`data.put("keys", rawResult.keySet().stream().map(String::valueOf).toList());`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:217` `keySet`：`for (Object key : map.keySet()) {`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:499` `entrySet`：`for (Map.Entry<?, ?> entry : map.entrySet()) {`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:686` `entrySet`：`for (Map.Entry<?, ?> entry : map.entrySet()) {`

### A4. 全量 key 清单（读取型，按类数/次数降序）

说明：本节给出全量 key 的统计概览；逐 key 的“全部命中位置”见附录 A。

| key | 命中类数 | 命中次数 | 风险 |
|---|---:|---:|---|
| `result` | 9 | 11 | HIGH |
| `tool` | 8 | 15 | HIGH |
| `context` | 7 | 15 | HIGH |
| `toolName` | 7 | 12 | HIGH |
| `summary` | 7 | 8 | HIGH |
| `query` | 6 | 8 | HIGH |
| `rawRef` | 5 | 19 | HIGH |
| `rawResult` | 5 | 5 | HIGH |
| `requiresApproval` | 4 | 15 | HIGH |
| `truncated` | 4 | 9 | HIGH |
| `raw` | 4 | 6 | HIGH |
| `citations` | 4 | 4 | HIGH |
| `question` | 4 | 4 | HIGH |
| `topic` | 4 | 4 | HIGH |
| `disableTools` | 3 | 9 | HIGH |
| `toolChoice` | 3 | 8 | HIGH |
| `type` | 3 | 6 | HIGH |
| `message` | 3 | 5 | HIGH |
| `refs` | 3 | 5 | HIGH |
| `answer` | 3 | 4 | HIGH |
| `charCount` | 3 | 4 | HIGH |
| `decisionRawRef` | 3 | 4 | HIGH |
| `keyCount` | 3 | 4 | HIGH |
| `modelRawRef` | 3 | 4 | HIGH |
| `summaryRawRef` | 3 | 4 | HIGH |
| `toolRawRef` | 3 | 4 | HIGH |
| `arguments` | 3 | 3 | HIGH |
| `approvalSource` | 2 | 6 | MEDIUM |
| `status` | 2 | 5 | MEDIUM |
| `mode` | 2 | 4 | MEDIUM |
| `name` | 2 | 4 | MEDIUM |
| `outputDigest` | 2 | 4 | MEDIUM |
| `content` | 2 | 3 | MEDIUM |
| `finalAnswer` | 2 | 3 | MEDIUM |
| `stepSummary` | 2 | 3 | MEDIUM |
| `enableSensitiveMask` | 2 | 2 | MEDIUM |
| `keys` | 2 | 2 | MEDIUM |
| `pruneOrder` | 2 | 2 | MEDIUM |
| `records` | 2 | 2 | MEDIUM |
| `required` | 2 | 2 | MEDIUM |
| `retrievalPriority` | 2 | 2 | MEDIUM |
| `snapshotId` | 2 | 2 | MEDIUM |
| `tools` | 2 | 2 | MEDIUM |
| `contextSnapshot` | 1 | 2 | LOW |
| `dateRange` | 1 | 2 | LOW |
| `errorCode` | 1 | 2 | LOW |
| `fallbackTool` | 1 | 2 | LOW |
| `filter` | 1 | 2 | LOW |
| `filters` | 1 | 2 | LOW |
| `id` | 1 | 2 | LOW |
| `model` | 1 | 2 | LOW |
| `promptAssemblyInput` | 1 | 2 | LOW |
| `rawOnly` | 1 | 2 | LOW |
| `tenantId` | 1 | 2 | LOW |
| `timeRange` | 1 | 2 | LOW |
| `additionalProperties` | 1 | 1 | LOW |
| `allowTools` | 1 | 1 | LOW |
| `allowedTools` | 1 | 1 | LOW |
| `choices` | 1 | 1 | LOW |
| `code` | 1 | 1 | LOW |
| `conditions` | 1 | 1 | LOW |
| `confidence` | 1 | 1 | LOW |
| `constraints` | 1 | 1 | LOW |
| `contextPolicy` | 1 | 1 | LOW |
| `count` | 1 | 1 | LOW |
| `created_at` | 1 | 1 | LOW |
| `critical` | 1 | 1 | LOW |
| `data` | 1 | 1 | LOW |
| `dataScopes` | 1 | 1 | LOW |
| `decision` | 1 | 1 | LOW |
| `endTime` | 1 | 1 | LOW |
| `error` | 1 | 1 | LOW |
| `evidencePack` | 1 | 1 | LOW |
| `expires_at` | 1 | 1 | LOW |
| `forbiddenActions` | 1 | 1 | LOW |
| `from` | 1 | 1 | LOW |
| `highlights` | 1 | 1 | LOW |
| `inputDigest` | 1 | 1 | LOW |
| `inputSummary` | 1 | 1 | LOW |
| `items` | 1 | 1 | LOW |
| `layer` | 1 | 1 | LOW |
| `longTermMemoryRefs` | 1 | 1 | LOW |
| `mcpServerId` | 1 | 1 | LOW |
| `memory_id` | 1 | 1 | LOW |
| `nextAction` | 1 | 1 | LOW |
| `notes` | 1 | 1 | LOW |
| `output` | 1 | 1 | LOW |
| `outputSummary` | 1 | 1 | LOW |
| `payload` | 1 | 1 | LOW |
| `planSteps` | 1 | 1 | LOW |
| `policy` | 1 | 1 | LOW |
| `prompt` | 1 | 1 | LOW |
| `promptScene` | 1 | 1 | LOW |
| `promptTrace` | 1 | 1 | LOW |
| `properties` | 1 | 1 | LOW |
| `range` | 1 | 1 | LOW |
| `researchCitations` | 1 | 1 | LOW |
| `response` | 1 | 1 | LOW |
| `retry` | 1 | 1 | LOW |
| `rows` | 1 | 1 | LOW |
| `sample` | 1 | 1 | LOW |
| `score` | 1 | 1 | LOW |
| `selectedTools` | 1 | 1 | LOW |
| `session_id` | 1 | 1 | LOW |
| `skill` | 1 | 1 | LOW |
| `skillName` | 1 | 1 | LOW |
| `snippet` | 1 | 1 | LOW |
| `source` | 1 | 1 | LOW |
| `sql` | 1 | 1 | LOW |
| `startTime` | 1 | 1 | LOW |
| `summaryMode` | 1 | 1 | LOW |
| `task_id` | 1 | 1 | LOW |
| `tenant_id` | 1 | 1 | LOW |
| `text` | 1 | 1 | LOW |
| `to` | 1 | 1 | LOW |
| `tokenUsage` | 1 | 1 | LOW |
| `toolResultSummary` | 1 | 1 | LOW |
| `toolSummaryMode` | 1 | 1 | LOW |
| `usage` | 1 | 1 | LOW |

## B. 重复解析函数清单（按“解析目标”聚合）

### B1. rawRef 解析相关方法

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:800` `com.example.agent.runtime.engine.AgentRuntime#resolveRawRef`：`private String resolveRawRef(Map<String, Object> output) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1366` `com.example.agent.runtime.llm.LlmStepService#resolveRawRefFromToolResult`：`private String resolveRawRefFromToolResult(Map<String, Object> result) {`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:62` `com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder#resolveRawRef`：`public String resolveRawRef(Map<String, Object> rawOutput) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:598` `com.example.agent.runtime.react.ReactLoopService#resolveRawRef`：`private String resolveRawRef(Map<String, Object> output) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:183` `com.example.agent.runtime.step.StepExecutionOutput#resolveRawRef`：`private static String resolveRawRef(Map<String, Object> payload) {`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:374` `com.example.agent.runtime.step.StepRuntimeService#resolveRawRef`：`private RawRef resolveRawRef(StepExecutionOutput output) {`

### B2. refs 解析相关方法

- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:97` `com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder#resolveRefs`：`public Map<String, String> resolveRefs(Map<String, Object> rawOutput) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:212` `com.example.agent.runtime.step.StepExecutionOutput#resolveRefs`：`private static Map<String, String> resolveRefs(Map<String, Object> payload) {`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:409` `com.example.agent.runtime.step.StepRuntimeService#resolveRefs`：`private StepResultRefSet resolveRefs(StepExecutionOutput output, RawRef rawRef) {`

### B3. toolName 解析相关方法

- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:665` `com.example.agent.runtime.react.ReactLoopService#resolveToolName`：`private String resolveToolName(TaskRequest request, ReactDecision decision) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionDelegate.java:62` `com.example.agent.runtime.step.StepExecutionDelegate#resolveToolName`：`public String resolveToolName(TaskRequest request, StepSpec step) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:169` `com.example.agent.runtime.step.StepExecutionOutput#resolveToolName`：`private static String resolveToolName(Map<String, Object> payload) {`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:118` `com.example.agent.runtime.step.executor.ToolStepExecutor#resolveToolName`：`public String resolveToolName(TaskRequest request, com.example.agent.runtime.model.StepSpec step) {`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:254` `com.example.agent.runtime.step.executor.ToolStepExecutor#resolveToolNameForToolStep`：`private String resolveToolNameForToolStep(TaskRequest request,`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:239` `com.example.agent.runtime.summary.StepOutputSummaryBuilder#resolveToolName`：`private String resolveToolName(String toolName, Object output) {`

### B4. summary/stepSummary 构建相关方法

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:829` `com.example.agent.runtime.engine.AgentRuntime#buildStepOutputSummary`：`private Map<String, Object> buildStepOutputSummary(StepRecord record, Map<String, Object> output) {`

### B5. normalize 相关方法

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:771` `com.example.agent.capabilities.llm.DefaultModelProvider#normalizeMessages`：`private List<PromptMessage> normalizeMessages(ModelDefinition definition, List<PromptMessage> messages) {`
- `src/main/java/com/example/agent/capabilities/memory/HashEmbeddingService.java:43` `com.example.agent.capabilities.memory.HashEmbeddingService#normalize`：`private void normalize(float[] vector) {`
- `src/main/java/com/example/agent/capabilities/memory/MemoryRecallService.java:263` `com.example.agent.capabilities.memory.MemoryRecallService#normalizePriorityValue`：`private String normalizePriorityValue(String value) {`
- `src/main/java/com/example/agent/capabilities/memory/MemoryStore.java:159` `com.example.agent.capabilities.memory.MemoryStore#normalizeRetrievalPriority`：`private List<String> normalizeRetrievalPriority(List<String> retrievalPriority) {`
- `src/main/java/com/example/agent/capabilities/memory/MemoryStore.java:177` `com.example.agent.capabilities.memory.MemoryStore#normalizePriorityValue`：`private String normalizePriorityValue(String value) {`
- `src/main/java/com/example/agent/capabilities/tools/hook/HookManager.java:301` `com.example.agent.capabilities.tools.hook.HookManager#normalizeHookId`：`private String normalizeHookId(String hookId) {`
- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:664` `com.example.agent.capabilities.tools.mcp.McpToolClient#normalizeResultMap`：`private Map<String, Object> normalizeResultMap(Object result) {`
- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:1181` `com.example.agent.capabilities.tools.mcp.McpToolClient#normalizeServerId`：`private String normalizeServerId(String serverId) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:105` `com.example.agent.capabilities.tools.validation.ToolArgumentValidator#normalizeValue`：`private Object normalizeValue(Map<String, Object> schema, Object value, String toolName, String path) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:124` `com.example.agent.capabilities.tools.validation.ToolArgumentValidator#normalizeString`：`private Object normalizeString(Object value, String toolName, String path) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:134` `com.example.agent.capabilities.tools.validation.ToolArgumentValidator#normalizeNumber`：`private Object normalizeNumber(Object value, String toolName, String path) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:157` `com.example.agent.capabilities.tools.validation.ToolArgumentValidator#normalizeInteger`：`private Object normalizeInteger(Object value, String toolName, String path) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:195` `com.example.agent.capabilities.tools.validation.ToolArgumentValidator#normalizeBoolean`：`private Object normalizeBoolean(Object value, String toolName, String path) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:220` `com.example.agent.capabilities.tools.validation.ToolArgumentValidator#normalizeObject`：`private Object normalizeObject(Map<String, Object> schema, Object value, String toolName, String path) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:230` `com.example.agent.capabilities.tools.validation.ToolArgumentValidator#normalizeArray`：`private Object normalizeArray(Map<String, Object> schema, Object value, String toolName, String path) {`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:310` `com.example.agent.capabilities.tools.validation.ToolArgumentValidator#normalizeType`：`private String normalizeType(Object value) {`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:195` `com.example.agent.runtime.control.RuntimeApprovalGate#normalizeApprovalSource`：`private String normalizeApprovalSource(Object source, String fallback) {`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:853` `com.example.agent.runtime.engine.AgentRuntime#normalizeStepSummary`：`private Map<String, Object> normalizeStepSummary(Object stepSummaryObj, StepRecord record, String fallbackSummary) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1073` `com.example.agent.runtime.llm.LlmStepService#normalizeMode`：`private String normalizeMode(String mode) {`

### B6. Envelope 封装相关方法（方法名包含 Envelope）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:789` `com.example.agent.runtime.engine.AgentRuntime#buildStepRawEnvelope`：`private RawOutputEnvelope buildStepRawEnvelope(Map<String, Object> output) {`

## C. 不一致风险点（同语义不同取法）

### C1. 同语义字段出现多套 key（HIGH 风险）

- `toolName` 语义同时出现 key：`tool`, `toolName`
- `rawRef/refs` 语义同时出现 key：`decisionRawRef`, `modelRawRef`, `rawRef`, `summaryRawRef`, `toolRawRef`
- `rawResult`/输出载体语义同时出现 key：`data`, `output`, `raw`, `rawResult`, `result`
- `summary` 语义同时出现 key：`outputDigest`, `outputSummary`, `stepSummary`, `summary`, `toolResultSummary`, `truncated`

### C2. 同字段提取路径不一致（rawRef/toolName/summary）

以下为“可定位的典型差异点”，用于说明风险：

- `rawRef` 提取路径差异（同为 `resolveRawRef*`，但 nested key 覆盖不一致）：
  - `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:800` `com.example.agent.runtime.engine.AgentRuntime#resolveRawRef`
  - `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1366` `com.example.agent.runtime.llm.LlmStepService#resolveRawRefFromToolResult`
  - `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:62` `com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder#resolveRawRef`
  - `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:598` `com.example.agent.runtime.react.ReactLoopService#resolveRawRef`
  - `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:183` `com.example.agent.runtime.step.StepExecutionOutput#resolveRawRef`
  - `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:374` `com.example.agent.runtime.step.StepRuntimeService#resolveRawRef`

- `toolName` 提取路径差异（`tool` vs `toolName` 的优先级/兜底点可能不同）：
  - `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:665` `com.example.agent.runtime.react.ReactLoopService#resolveToolName`
  - `src/main/java/com/example/agent/runtime/step/StepExecutionDelegate.java:62` `com.example.agent.runtime.step.StepExecutionDelegate#resolveToolName`
  - `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:169` `com.example.agent.runtime.step.StepExecutionOutput#resolveToolName`
  - `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:118` `com.example.agent.runtime.step.executor.ToolStepExecutor#resolveToolName`
  - `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:254` `com.example.agent.runtime.step.executor.ToolStepExecutor#resolveToolNameForToolStep`
  - `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:239` `com.example.agent.runtime.summary.StepOutputSummaryBuilder#resolveToolName`

- `summary` 构建差异（`buildStepOutputSummary` 与 `StepOutputSummaryBuilder`/`ReflectionService` 的字段定义可能不同步）：
  - `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:829` `com.example.agent.runtime.engine.AgentRuntime#buildStepOutputSummary`

- `tool` 与 `toolName` 同时被读取的典型文件（同一语义多 key，HIGH）：
  - `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java`
  - `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java`
  - `src/main/java/com/example/agent/runtime/react/ReactLoopService.java`
  - `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java`
  - `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java`
  - `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java`

## D. 影响面评估（权威收口点 vs 消费方）

### D1. 建议的“权威收口点”（应尽量成为唯一解析入口）

- `com.example.agent.runtime.step.StepExecutionOutput`：步骤执行器统一返回类型，建议承载 `toolName/rawRef/refs/summary` 的显式字段。
- `com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder`：负责从“任意输出 Map”中抽取 `rawRef/refs/data/truncated` 的权威实现。
- `com.example.agent.runtime.summary.StepOutputSummaryBuilder`：负责生成稳定的 `outputSummary/outputDigest/stepSummary` 结构，避免各处复制粘贴。
- `OutputKeys`（建议新增）：集中管理 key 常量，作为静态分析白名单/治理入口。

### D2. 典型消费方（当前存在散乱读取/重复解析的位置）

- `com.example.agent.capabilities.llm.DefaultModelProvider`
- `com.example.agent.capabilities.llm.ModelToolResolver`
- `com.example.agent.capabilities.memory.QdrantVectorStore`
- `com.example.agent.capabilities.tools.hook.EvidencePackHookHandler`
- `com.example.agent.capabilities.tools.mcp.McpToolClient`
- `com.example.agent.reflection.ReflectionService`
- `com.example.agent.runtime.control.RuntimeApprovalGate`
- `com.example.agent.runtime.engine.AgentRuntime`
- `com.example.agent.runtime.llm.LlmStepService`
- `com.example.agent.runtime.raw.output.RawOutputEnvelopeBuilder`
- `com.example.agent.runtime.react.ReactLoopService`
- `com.example.agent.runtime.step.StepExecutionOutput`
- `com.example.agent.runtime.step.StepRuntimeService`
- `com.example.agent.runtime.step.executor.ToolStepExecutor`
- `com.example.agent.runtime.summary.StepOutputSummaryBuilder`

### D3. 写入侧（谁在产出这些 key）

说明：写入侧不在“命中规则”里，但用于判断影响面与收口点是否合理。

| key | 写入类数 | 写入次数 | 主要写入位置（前 5） |
|---|---:|---:|---|
| `query` | 9 | 15 | `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:104`, `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:148`, `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:220`, `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:983`, `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:119` |
| `summary` | 8 | 15 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:990`, `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1157`, `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1204`, `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:136`, `src/main/java/com/example/agent/reflection/ReflectionService.java:351` |
| `tool` | 7 | 17 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:979`, `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:118`, `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java:229`, `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java:269`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:410` |
| `rawRef` | 7 | 15 | `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java:234`, `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java:274`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:709`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:740`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:867` |
| `promptScene` | 7 | 8 | `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:106`, `src/main/java/com/example/agent/capabilities/llm/PromptTrace.java:88`, `src/main/java/com/example/agent/capabilities/llm/repair/JsonOutputRepairService.java:64`, `src/main/java/com/example/agent/reflection/ReflectionService.java:142`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:231` |
| `reason` | 6 | 8 | `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:326`, `src/main/java/com/example/agent/capabilities/tools/hook/HookManager.java:420`, `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:381`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:705`, `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:149` |
| `status` | 6 | 6 | `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:242`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:412`, `src/main/java/com/example/agent/runtime/output/FinalOutputService.java:306`, `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:442`, `src/main/java/com/example/agent/runtime/step/executor/ChainOfThoughtStepExecutor.java:54` |
| `answer` | 5 | 11 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1130`, `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:152`, `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:155`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:445`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:704` |
| `toolName` | 5 | 6 | `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:295`, `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:320`, `src/main/java/com/example/agent/capabilities/tools/hook/HookManager.java:417`, `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:229`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:503` |
| `traceId` | 5 | 5 | `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:217`, `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:608`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:480`, `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:858`, `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:305` |
| `confidence` | 4 | 9 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1131`, `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1232`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:448`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:706`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:733` |
| `source` | 4 | 7 | `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:294`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:707`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:738`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:864`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:916` |
| `stepType` | 4 | 7 | `src/main/java/com/example/agent/capabilities/tools/hook/HookManager.java:314`, `src/main/java/com/example/agent/reflection/ReflectionService.java:139`, `src/main/java/com/example/agent/reflection/ReflectionService.java:235`, `src/main/java/com/example/agent/reflection/ReflectionService.java:309`, `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:221` |
| `workflowId` | 4 | 7 | `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:291`, `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:316`, `src/main/java/com/example/agent/capabilities/tools/hook/HookManager.java:312`, `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:133`, `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:158` |
| `requestId` | 4 | 6 | `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:218`, `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:297`, `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:323`, `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:609`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:481` |
| `modelRawRef` | 4 | 4 | `src/main/java/com/example/agent/runtime/step/executor/ChainOfThoughtStepExecutor.java:57`, `src/main/java/com/example/agent/runtime/step/executor/DebateStepExecutor.java:44`, `src/main/java/com/example/agent/runtime/step/executor/ReactStepExecutor.java:56`, `src/main/java/com/example/agent/runtime/step/executor/ResearchStepExecutor.java:67` |
| `toolChoice` | 4 | 4 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:358`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:471`, `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:130`, `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:235` |
| `name` | 3 | 8 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:424`, `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:598`, `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:634`, `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1201`, `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:485` |
| `attempt` | 3 | 7 | `src/main/java/com/example/agent/reflection/ReflectionService.java:141`, `src/main/java/com/example/agent/reflection/ReflectionService.java:236`, `src/main/java/com/example/agent/reflection/ReflectionService.java:310`, `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:517`, `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:524` |
| `mode` | 3 | 7 | `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:501`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:703`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:730`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:848`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:891` |
| `truncated` | 3 | 5 | `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:848`, `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:440`, `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:153`, `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:160`, `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:174` |
| `result` | 3 | 4 | `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java:230`, `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java:270`, `src/main/java/com/example/agent/capabilities/tools/hook/HookManager.java:319`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:413` |
| `stepId` | 3 | 4 | `src/main/java/com/example/agent/capabilities/tools/hook/HookManager.java:311`, `src/main/java/com/example/agent/capabilities/tools/hook/HookManager.java:414`, `src/main/java/com/example/agent/runtime/output/FinalOutputService.java:302`, `src/main/java/com/example/agent/runtime/step/executor/ResearchStepExecutor.java:70` |
| `steps` | 3 | 4 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:991`, `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:557`, `src/main/java/com/example/agent/runtime/output/FinalOutputService.java:236`, `src/main/java/com/example/agent/runtime/output/FinalOutputService.java:273` |
| `approvalSource` | 3 | 3 | `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:241`, `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:722`, `src/main/java/com/example/agent/runtime/model/StepSpec.java:140` |
| `context` | 3 | 3 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:986`, `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1171`, `src/main/java/com/example/agent/runtime/model/StepSpec.java:130` |
| `evidencePack` | 3 | 3 | `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:214`, `src/main/java/com/example/agent/runtime/step/executor/ResearchStepExecutor.java:115`, `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:358` |
| `finalAnswer` | 3 | 3 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1231`, `src/main/java/com/example/agent/runtime/step/executor/ChainOfThoughtStepExecutor.java:50`, `src/main/java/com/example/agent/runtime/step/executor/ReactStepExecutor.java:51` |
| `outputDigest` | 3 | 3 | `src/main/java/com/example/agent/reflection/ReflectionService.java:355`, `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:845`, `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:173` |
| `score` | 3 | 3 | `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1102`, `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:525`, `src/main/java/com/example/agent/runtime/step/executor/ThoughtTreeStepExecutor.java:62` |

## E. 重构路线图（P0~P3）

### P0（立即收益，低风险）：建立 key 权威与契约测试

- 目标：把 `rawRef/refs/toolName/summary` 的 key 定义与解析入口收口到少数权威类，减少散乱读取。
- 建议改动（不在本次执行范围）：
  - 新增 `com.example.agent.runtime.output.OutputKeys`（或放在 `runtime.step` 下）统一常量：`rawRef`、`refs`、`toolName`、`tool`、`outputSummary`、`outputDigest`、`stepSummary`、`truncated`、`rawResult`、`result`、`raw` 等。
  - 在 `RawOutputEnvelopeBuilder` / `StepOutputSummaryBuilder` / `StepExecutionOutput` 内部统一引用常量，形成“唯一实现”。
- 建议补测：
  - `RawOutputEnvelopeBuilder`：rawRef 的优先级与 nested 路径（顶层/rawResult/result/raw）契约测试。
  - `ToolStepExecutor`：toolName 解析优先级（`tool` vs `toolName` vs `toolChoice.toolName`）契约测试。

### P1（中等收益，中风险）：消除重复解析实现，统一调用权威收口点

- 目标：删除/替换 `AgentRuntime`、`ReactLoopService`、`LlmStepService` 内部的 `resolveRawRef*`/refs 合并等重复逻辑，让它们调用 `RawOutputEnvelopeBuilder`/`StepExecutionOutput`。
- 建议改动文件（不执行，仅列影响面）：
  - `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java`：移除/弱化 `resolveRawRef`、`buildStepOutputSummary` 的自实现分支，统一走 builder。
  - `src/main/java/com/example/agent/runtime/react/ReactLoopService.java`：rawRef/refs/summary 统一走收口点。
  - `src/main/java/com/example/agent/runtime/llm/LlmStepService.java`：`resolveRawRefFromToolResult` 与 `mergeRef` 逻辑与 `RawOutputEnvelopeBuilder`/`StepExecutionOutput` 对齐。
- 建议补测：
  - 回归用例覆盖：同一份工具输出，三处链路得到一致的 `rawRef/refs`。

### P2（结构性收益，偏高风险）：输出从 Map 逐步升级为 DTO，限制 Map 逃逸

- 目标：关键阶段输出使用 DTO（如 `StepExecutionOutput`、`RawOutputEnvelope`、`StepOutputSummary`），Map 只作为 `payload` 原始承载，不跨层透传。
- 建议策略：
  - 执行器输出统一返回 `StepExecutionOutput`（或至少由 `StepExecutionDelegate` 在边界处组装）。
  - 将 `toolName/rawRef/refs/summary/truncated` 等字段从 Map 变为显式字段；消费方只读 DTO 字段。
- 建议补测：
  - 契约测试：序列化结构、时间线/回放/反思 view 的字段一致性。

### P3（治理收益，持续约束）：加入静态规则与白名单

- 目标：防止新增“散乱 Map.get("xxx")”。
- 建议：
  - 增加简单脚本或 Checkstyle 自定义规则：除 `RawOutputEnvelopeBuilder`/`StepOutputSummaryBuilder`/`StepExecutionOutput`/`OutputKeys` 外，禁止直接访问关键 key。
  - 在 CI 中跑审计脚本，保证 key 访问集中化。

## 附录 A：逐 key 全部命中位置（读取型）

说明：此处为“全量展开”，便于后续做批量替换/收口。

### `result`（类数=9，命中=11，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:167` `get`：`Object resultObj = response.get("result");`
- `src/main/java/com/example/agent/capabilities/tools/hook/EvidencePackHookHandler.java:150` `get`：`Object result = payload.get("result");`
- `src/main/java/com/example/agent/capabilities/tools/hook/EvidencePackHookHandler.java:165` `get`：`Object result = payload.get("result");`
- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:642` `containsKey`：`if (!response.containsKey("result")) {`
- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:645` `get`：`return response.get("result");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:814` `get`：`if (output.get("result") instanceof Map<?, ?> resultMap) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1381` `get`：`Object nestedResult = result.get("result");`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:76` `get`：`if (rawOutput.get("result") instanceof Map<?, ?> resultMap) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:612` `get`：`if (output.get("result") instanceof Map<?, ?> resultMap) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:196` `get`：`ref = readNestedRawRef(payload.get("result"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:437` `get`：`Object result = rawOutput.get("result");`

### `tool`（类数=8，命中=15，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1003` `get`：`Object tool = context.get("tool");`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1012` `get`：`Object innerTool = inner.get("tool");`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:224` `get`：`Object toolName = stepInput.get("tool");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:250` `get`：`Map<String, Object> toolMap = readMap(decision.get("tool"));`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:893` `containsKey`：`if (!output.containsKey("tool")) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:469` `get`：`String tool = toText(output.get("tool"));`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:670` `get`：`Object tool = request.getContext().get("tool");`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:173` `get`：`Object tool = payload.get("tool");`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:158` `get`：`Object tool = stepSummary.get("tool");`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:444` `get`：`String toolName = rawOutput != null && rawOutput.get("tool") != null ? String.valueOf(rawOutput.get("tool")) : null;`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:444` `get`：`String toolName = rawOutput != null && rawOutput.get("tool") != null ? String.valueOf(rawOutput.get("tool")) : null;`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:121` `get`：`Object tool = stepInput.get("tool");`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:131` `get`：`Object tool = request.getContext().get("tool");`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:264` `get`：`Object tool = stepInput.get("tool");`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:246` `get`：`Object value = map.get("tool");`

### `context`（类数=7，命中=15，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:985` `containsKey`：`if (context.containsKey("context")) {`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:986` `get`：`input.put("context", context.get("context"));`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1011` `get`：`if (context.get("context") instanceof Map<?, ?> inner) {`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1031` `get`：`if (context.get("context") instanceof Map<?, ?> inner && isTruthy(inner.get("disableTools"))) {`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1035` `get`：`if (choice == null && context.get("context") instanceof Map<?, ?> inner) {`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:195` `get`：`Object context = stepInput.get("context");`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:515` `get`：`if (stepInput != null && stepInput.get("context") instanceof Map<?, ?> contextMap) {`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:171` `get`：`if (source == null && stepInput != null && stepInput.get("context") instanceof Map<?, ?> contextMap`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:714` `get`：`Object context = merged.get("context");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:140` `get`：`mode = resolveToolSummaryModeFromContext(stepInput != null ? stepInput.get("context") : null);`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:211` `get`：`boolean hasContext = stepInput != null && stepInput.get("context") != null;`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1040` `get`：`Object context = stepInput.get("context");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1167` `get`：`Object context = stepInput.get("context");`
- `src/main/java/com/example/agent/runtime/step/executor/LlmStepExecutor.java:49` `get`：`boolean hasContext = stepInput != null && stepInput.get("context") != null;`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:247` `get`：`Object context = stepInput.get("context");`

### `toolName`（类数=7，命中=12，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1007` `get`：`Object toolName = context.get("toolName");`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1016` `get`：`Object innerToolName = inner.get("toolName");`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1053` `get`：`String name = map.get("toolName") != null ? map.get("toolName").toString() : null;`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1053` `get`：`String name = map.get("toolName") != null ? map.get("toolName").toString() : null;`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:554` `get`：`String name = map.get("toolName") != null ? map.get("toolName").toString() : null;`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:554` `get`：`String name = map.get("toolName") != null ? map.get("toolName").toString() : null;`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:226` `get`：`toolName = stepInput.get("toolName");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:471` `get`：`tool = toText(output.get("toolName"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:175` `get`：`tool = payload.get("toolName");`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:125` `get`：`Object toolName = stepInput.get("toolName");`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:268` `get`：`Object toolNameObj = stepInput.get("toolName");`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:248` `get`：`value = map.get("toolName");`

### `summary`（类数=7，命中=8，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:185` `get`：`record.setSummary(valueAsString(payload.get("summary")));`
- `src/main/java/com/example/agent/reflection/ReflectionService.java:342` `get`：`Object summaryValue = outputSummary.get("summary");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:864` `get`：`Object summaryValue = stepSummary.get("summary");`
- `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:181` `get`：`if (stepSummary != null && stepSummary.get("summary") != null) {`
- `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:182` `get`：`return Map.of("answer", String.valueOf(stepSummary.get("summary")));`
- `src/main/java/com/example/agent/runtime/output/FinalOutputService.java:322` `get`：`Object summaryValue = stepSummary.get("summary");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:507` `get`：`Object summary = summaryMap.get("summary");`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:484` `get`：`Object text = stepSummaryMap.get("summary");`

### `query`（类数=6，命中=8，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:982` `containsKey`：`if (context.containsKey("query")) {`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:983` `get`：`input.put("query", context.get("query"));`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1125` `get`：`Object query = context.get("query");`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:233` `get`：`Object query = stepInput.get("query");`
- `src/main/java/com/example/agent/runtime/step/executor/ChainOfThoughtStepExecutor.java:78` `get`：`Object query = stepInput.get("query");`
- `src/main/java/com/example/agent/runtime/step/executor/DebateStepExecutor.java:62` `get`：`Object query = stepInput.get("query");`
- `src/main/java/com/example/agent/runtime/step/executor/ResearchStepExecutor.java:84` `get`：`Object query = stepInput.get("query");`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:355` `get`：`putTextSummary(summary, "query", input.get("query"), limits, truncation);`

### `rawRef`（类数=5，命中=19，风险=HIGH）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:804` `get`：`Object direct = output.get("rawRef");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:809` `get`：`Object nested = rawResultMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:815` `get`：`Object nested = resultMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:821` `get`：`Object nested = rawMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:928` `containsKey`：`if (StringUtils.hasText(decisionRawRef) && !output.containsKey("rawRef")) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:931` `containsKey`：`if (StringUtils.hasText(summaryRawRef) && !output.containsKey("rawRef")) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1370` `get`：`Object direct = result.get("rawRef");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1376` `get`：`Object nested = rawMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1383` `get`：`Object nested = nestedMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:66` `get`：`String direct = readString(rawOutput.get("rawRef"));`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:71` `get`：`String nested = readString(rawResultMap.get("rawRef"));`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:77` `get`：`String nested = readString(resultMap.get("rawRef"));`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:83` `get`：`String nested = readString(rawMap.get("rawRef"));`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:602` `get`：`Object direct = output.get("rawRef");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:607` `get`：`Object nested = rawResultMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:613` `get`：`Object nested = resultMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:619` `get`：`Object nested = rawMap.get("rawRef");`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:187` `get`：`Object direct = payload.get("rawRef");`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:208` `get`：`Object nested = map.get("rawRef");`

### `rawResult`（类数=5，命中=5，风险=HIGH）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:808` `get`：`if (output.get("rawResult") instanceof Map<?, ?> rawResultMap) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1374` `get`：`Object rawResult = result.get("rawResult");`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:70` `get`：`if (rawOutput.get("rawResult") instanceof Map<?, ?> rawResultMap) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:606` `get`：`if (output.get("rawResult") instanceof Map<?, ?> rawResultMap) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:192` `get`：`ref = readNestedRawRef(payload.get("rawResult"));`

### `requiresApproval`（类数=4，命中=15，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:357` `get`：`boundary.setApprovalRequired(readBoolean(context.get("requiresApproval")));`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:119` `containsKey`：`if (!context.containsKey("requiresApproval")) {`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:122` `get`：`boolean required = isTruthy(context.get("requiresApproval"));`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:138` `containsKey`：`if (input != null && input.containsKey("requiresApproval")) {`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:143` `get`：`boolean required = isTruthy(input.get("requiresApproval"));`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:160` `containsKey`：`if (source == null && input != null && input.containsKey("requiresApproval")) {`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:163` `get`：`value = input.get("requiresApproval");`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:167` `containsKey`：`if (source == null && stepInput != null && stepInput.containsKey("requiresApproval")) {`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:168` `get`：`value = stepInput.get("requiresApproval");`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:172` `containsKey`：`&& contextMap.containsKey("requiresApproval")) {`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:173` `get`：`value = contextMap.get("requiresApproval");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:711` `containsKey`：`if (merged == null \|\| merged.containsKey("requiresApproval")) {`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:718` `containsKey`：`if (contextMap.containsKey("requiresApproval")) {`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:719` `get`：`merged.put("requiresApproval", contextMap.get("requiresApproval"));`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:770` `get`：`Object requiresApproval = request.getContext().get("requiresApproval");`

### `truncated`（类数=4，命中=9，风险=HIGH）

- `src/main/java/com/example/agent/reflection/ReflectionService.java:382` `get`：`boolean truncated = Boolean.TRUE.equals(digest.get("truncated"));`
- `src/main/java/com/example/agent/reflection/ReflectionService.java:386` `get`：`appendDigestField(builder, "truncated", digest.get("truncated"));`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:847` `containsKey`：`if (!summary.containsKey("truncated")) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:540` `get`：`Object truncated = output != null ? output.get("truncated") : null;`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:544` `get`：`Object digestValue = outputDigest != null ? outputDigest.get("truncated") : null;`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:555` `get`：`appendDigestField(builder, "truncated", digest.get("truncated"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:155` `get`：`Boolean truncated = summary.get("truncated") instanceof Boolean value ? value : null;`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:505` `get`：`boolean truncated = summaryMap.get("truncated") instanceof Boolean value && value;`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:528` `get`：`if (map.get("truncated") instanceof Boolean value) {`

### `raw`（类数=4，命中=6，风险=HIGH）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:820` `get`：`if (output.get("raw") instanceof Map<?, ?> rawMap) {`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:82` `get`：`if (rawOutput.get("raw") instanceof Map<?, ?> rawMap) {`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:107` `get`：`if (rawOutput.get("raw") instanceof Map<?, ?> rawMap) {`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:618` `get`：`if (output.get("raw") instanceof Map<?, ?> rawMap) {`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:200` `get`：`ref = readNestedRawRef(payload.get("raw"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:222` `get`：`if (payload.get("raw") instanceof Map<?, ?> rawMap) {`

### `citations`（类数=4，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:559` `get`：`Object citationsObj = context.get("citations");`
- `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:192` `get`：`Object citationsObj = root.get("citations");`
- `src/main/java/com/example/agent/capabilities/tools/hook/EvidencePackHookHandler.java:115` `get`：`Object citationsObj = payload.get("citations");`
- `src/main/java/com/example/agent/runtime/structured/GenericStructuredExtractor.java:47` `containsKey`：`\|\| rawResult.containsKey("citations")) {`

### `question`（类数=4，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1227` `get`：`String question = context.get("question") instanceof String value ? value : "";`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1015` `get`：`Object question = stepInput.get("question");`
- `src/main/java/com/example/agent/runtime/step/executor/ChainOfThoughtStepExecutor.java:64` `get`：`Object question = stepInput.get("question");`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:356` `get`：`putTextSummary(summary, "question", input.get("question"), limits, truncation);`

### `topic`（类数=4，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1019` `get`：`Object topic = stepInput.get("topic");`
- `src/main/java/com/example/agent/runtime/step/executor/ChainOfThoughtStepExecutor.java:68` `get`：`Object topic = stepInput.get("topic");`
- `src/main/java/com/example/agent/runtime/step/executor/DebateStepExecutor.java:52` `get`：`Object topic = stepInput.get("topic");`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:357` `get`：`putTextSummary(summary, "topic", input.get("topic"), limits, truncation);`

### `disableTools`（类数=3，命中=9，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1028` `get`：`if (isTruthy(context.get("disableTools"))) {`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1031` `get`：`if (context.get("context") instanceof Map<?, ?> inner && isTruthy(inner.get("disableTools"))) {`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:183` `get`：`return isTruthy(taskRequest.getContext().get("disableTools"));`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:192` `containsKey`：`if (stepInput.containsKey("disableTools")) {`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:193` `get`：`return stepInput.get("disableTools");`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:197` `get`：`return contextMap.get("disableTools");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1036` `get`：`Object disable = stepInput.get("disableTools");`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1041` `get`：`if (context instanceof Map<?, ?> contextMap && isTruthy(contextMap.get("disableTools"))) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1046` `get`：`return isTruthy(request.getContext().get("disableTools"));`

### `toolChoice`（类数=3，命中=8，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1034` `get`：`ModelToolChoice choice = parseToolChoice(context.get("toolChoice"));`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1036` `get`：`choice = parseToolChoice(inner.get("toolChoice"));`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:293` `get`：`Object raw = skillDefinition.getConstraints().get("toolChoice");`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:511` `get`：`ModelToolChoice fromStep = parseToolChoice(stepInput != null ? stepInput.get("toolChoice") : null);`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:516` `get`：`ModelToolChoice fromContext = parseToolChoice(contextMap.get("toolChoice"));`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:526` `get`：`return parseToolChoice(taskRequest.getContext().get("toolChoice"));`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:244` `get`：`if (stepInput.get("toolChoice") != null) {`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:249` `get`：`return contextMap.get("toolChoice") != null;`

### `type`（类数=3，命中=6，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1050` `get`：`if (!StringUtils.hasText(mode) && map.get("type") != null) {`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1051` `get`：`mode = map.get("type").toString();`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:551` `get`：`if (!StringUtils.hasText(mode) && map.get("type") != null) {`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:552` `get`：`mode = map.get("type").toString();`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:63` `get`：`String type = normalizeType(schema.get("type"));`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:106` `get`：`String type = normalizeType(schema.get("type"));`

### `message`（类数=3，命中=5，风险=HIGH）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1257` `get`：`Object message = choice.get("message");`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1301` `get`：`Object message = response.get("message");`
- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:886` `get`：`if (error != null && error.get("message") != null) {`
- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:887` `get`：`return error.get("message").toString();`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:951` `get`：`Object message = toolResult.get("message");`

### `refs`（类数=3，命中=5，风险=HIGH）

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:1403` `get`：`Object refsObj = output.get("refs");`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:102` `get`：`mergeRefs(refs, rawOutput.get("refs"));`
- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:108` `get`：`mergeRefs(refs, rawMap.get("refs"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:217` `get`：`mergeRefs(refs, payload.get("refs"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:223` `get`：`mergeRefs(refs, rawMap.get("refs"));`

### `answer`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:154` `containsKey`：`if (!result.containsKey("answer") && result.get("finalAnswer") != null) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:900` `containsKey`：`if (!output.containsKey("answer")) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:943` `get`：`Object answer = toolResult.get("answer");`
- `src/main/java/com/example/agent/runtime/output/FinalOutputService.java:181` `containsKey`：`if (!parsed.containsKey("answer")) {`

### `charCount`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/reflection/ReflectionService.java:380` `get`：`Object charCount = digest.get("charCount");`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:554` `get`：`appendDigestField(builder, "charCount", digest.get("charCount"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:153` `get`：`Integer charCount = digest != null ? resolveInt(digest.get("charCount")) : null;`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:525` `get`：`if (map.get("charCount") instanceof Number number) {`

### `decisionRawRef`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:103` `get`：`mergeRefValue(refs, "decisionRawRef", rawOutput.get("decisionRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:218` `get`：`mergeRefValue(refs, "decisionRawRef", payload.get("decisionRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:416` `get`：`refs.setDecisionRawRef(extracted.get("decisionRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:462` `get`：`refs.setDecisionRawRef(extracted.get("decisionRawRef"));`

### `keyCount`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/reflection/ReflectionService.java:378` `get`：`appendDigestField(builder, "keyCount", digest.get("keyCount"));`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:553` `get`：`appendDigestField(builder, "keyCount", digest.get("keyCount"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:154` `get`：`Integer keyCount = digest != null ? resolveInt(digest.get("keyCount")) : null;`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:513` `get`：`if (map.get("keyCount") instanceof Number number) {`

### `modelRawRef`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:106` `get`：`mergeRefValue(refs, "modelRawRef", rawOutput.get("modelRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:221` `get`：`mergeRefValue(refs, "modelRawRef", payload.get("modelRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:419` `get`：`refs.setModelRawRef(extracted.get("modelRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:471` `get`：`refs.setModelRawRef(extracted.get("modelRawRef"));`

### `summaryRawRef`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:104` `get`：`mergeRefValue(refs, "summaryRawRef", rawOutput.get("summaryRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:219` `get`：`mergeRefValue(refs, "summaryRawRef", payload.get("summaryRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:417` `get`：`refs.setSummaryRawRef(extracted.get("summaryRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:465` `get`：`refs.setSummaryRawRef(extracted.get("summaryRawRef"));`

### `toolRawRef`（类数=3，命中=4，风险=HIGH）

- `src/main/java/com/example/agent/runtime/raw/RawOutputEnvelopeBuilder.java:105` `get`：`mergeRefValue(refs, "toolRawRef", rawOutput.get("toolRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepExecutionOutput.java:220` `get`：`mergeRefValue(refs, "toolRawRef", payload.get("toolRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:418` `get`：`refs.setToolRawRef(extracted.get("toolRawRef"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:468` `get`：`refs.setToolRawRef(extracted.get("toolRawRef"));`

### `arguments`（类数=3，命中=3，风险=HIGH）

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:261` `get`：`Map<String, Object> toolArguments = readMap(toolMap.get("arguments"));`
- `src/main/java/com/example/agent/runtime/step/executor/ToolStepExecutor.java:279` `get`：`Object raw = stepInput.get("arguments");`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:359` `get`：`Object arguments = input.get("arguments");`

### `approvalSource`（类数=2，命中=6，风险=MEDIUM）

- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:139` `get`：`String source = normalizeApprovalSource(input.get("approvalSource"), "step");`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:161` `get`：`String inputSource = normalizeApprovalSource(input.get("approvalSource"), "evaluation");`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:169` `get`：`source = normalizeApprovalSource(stepInput.get("approvalSource"), "evaluation");`
- `src/main/java/com/example/agent/runtime/control/RuntimeApprovalGate.java:174` `get`：`source = normalizeApprovalSource(contextMap.get("approvalSource"), "evaluation");`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:721` `containsKey`：`if (contextMap.containsKey("approvalSource")) {`
- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:722` `get`：`merged.putIfAbsent("approvalSource", contextMap.get("approvalSource"));`

### `status`（类数=2，命中=5，风险=MEDIUM）

- `src/main/java/com/example/agent/runtime/output/FinalOutputService.java:321` `get`：`data.status = toText(stepSummary.get("status"));`
- `src/main/java/com/example/agent/runtime/output/FinalOutputService.java:330` `get`：`data.status = toText(summaryModel.getOutputSummary().get("status"));`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:521` `get`：`String status = toText(outputSummary != null ? outputSummary.get("status") : null);`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:523` `get`：`status = toText(stepSummary != null ? stepSummary.get("status") : null);`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:526` `get`：`status = toText(output != null ? output.get("status") : null);`

### `mode`（类数=2，命中=4，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1049` `get`：`String mode = map.get("mode") != null ? map.get("mode").toString() : null;`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1049` `get`：`String mode = map.get("mode") != null ? map.get("mode").toString() : null;`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:550` `get`：`String mode = map.get("mode") != null ? map.get("mode").toString() : null;`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:550` `get`：`String mode = map.get("mode") != null ? map.get("mode").toString() : null;`

### `name`（类数=2，命中=4，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1054` `get`：`if (!StringUtils.hasText(name) && map.get("name") != null) {`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1055` `get`：`name = map.get("name").toString();`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:555` `get`：`if (!StringUtils.hasText(name) && map.get("name") != null) {`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:556` `get`：`name = map.get("name").toString();`

### `outputDigest`（类数=2，命中=4，风险=MEDIUM）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:840` `containsKey`：`if (!summary.containsKey("outputDigest")) {`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:147` `get`：`Map<String, Object> digest = summary.get("outputDigest") instanceof Map<?, ?> map`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:150` `get`：`if (summary.get("outputDigest") instanceof Map<?, ?> map) {`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:502` `get`：`if (summaryMap.get("outputDigest") instanceof Map<?, ?> outputDigestMap) {`

### `content`（类数=2，命中=3，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1258` `get`：`if (message instanceof Map<?, ?> msg && msg.get("content") instanceof String content) {`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1302` `get`：`if (message instanceof Map<?, ?> msg && msg.get("content") instanceof String content) {`
- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:184` `get`：`record.setContent(valueAsString(payload.get("content")));`

### `finalAnswer`（类数=2，命中=3，风险=MEDIUM）

- `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:154` `get`：`if (!result.containsKey("answer") && result.get("finalAnswer") != null) {`
- `src/main/java/com/example/agent/runtime/finalize/RuntimeFinalizationService.java:155` `get`：`result.put("answer", result.get("finalAnswer"));`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:947` `get`：`Object finalAnswer = toolResult.get("finalAnswer");`

### `stepSummary`（类数=2，命中=3，风险=MEDIUM）

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java:838` `get`：`Map<String, Object> stepSummary = normalizeStepSummary(summary.get("stepSummary"), record, null);`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:157` `get`：`if (summary.get("stepSummary") instanceof Map<?, ?> stepSummary) {`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:483` `get`：`if (summaryMap.get("stepSummary") instanceof Map<?, ?> stepSummaryMap) {`

### `enableSensitiveMask`（类数=2，命中=2，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:788` `get`：`policy.setEnableSensitiveMask(readBoolean(map.get("enableSensitiveMask")));`
- `src/main/java/com/example/agent/capabilities/memory/MemoryRecallService.java:225` `get`：`policy.setEnableSensitiveMask(readBoolean(map.get("enableSensitiveMask")));`

### `keys`（类数=2，命中=2，风险=MEDIUM）

- `src/main/java/com/example/agent/reflection/ReflectionService.java:379` `get`：`appendDigestField(builder, "keys", digest.get("keys"));`
- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:516` `get`：`if (map.get("keys") instanceof List<?> list) {`

### `pruneOrder`（类数=2，命中=2，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:785` `get`：`policy.setPruneOrder(readStringList(map.get("pruneOrder")));`
- `src/main/java/com/example/agent/capabilities/memory/MemoryRecallService.java:222` `get`：`policy.setPruneOrder(readStringList(map.get("pruneOrder")));`

### `records`（类数=2，命中=2，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/tools/hook/EvidencePackHookHandler.java:81` `get`：`Object recordsObj = payload.get("records");`
- `src/main/java/com/example/agent/runtime/structured/GenericStructuredExtractor.java:53` `containsKey`：`if (rawResult.containsKey("rows") \|\| rawResult.containsKey("records")) {`

### `required`（类数=2，命中=2，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:67` `get`：`List<String> requiredFields = toStringList(schema.get("required"));`
- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidatorRuntime.java:56` `get`：`Object required = definition.getInputSchema().get("required");`

### `retrievalPriority`（类数=2，命中=2，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:784` `get`：`policy.setRetrievalPriority(readStringList(map.get("retrievalPriority")));`
- `src/main/java/com/example/agent/capabilities/memory/MemoryRecallService.java:221` `get`：`policy.setRetrievalPriority(readStringList(map.get("retrievalPriority")));`

### `snapshotId`（类数=2，命中=2，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:344` `get`：`Object value = request.getContext().get("snapshotId");`
- `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:181` `get`：`Object snapshotId = runtimeContext.get("snapshotId");`

### `tools`（类数=2，命中=2，风险=MEDIUM）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:909` `get`：`Object tools = body.get("tools");`
- `src/main/java/com/example/agent/capabilities/tools/plugin/PluginLoader.java:126` `get`：`toolsNode = node.get("tools");`

### `contextSnapshot`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/DefaultPromptAssembler.java:182` `get`：`Object snapshot = stepInput.get("contextSnapshot");`
- `src/main/java/com/example/agent/capabilities/llm/DefaultPromptAssembler.java:189` `get`：`Object snapshot = taskRequest.getContext().get("contextSnapshot");`

### `dateRange`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:368` `get`：`: input.containsKey("dateRange") ? input.get("dateRange")`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:368` `containsKey`：`: input.containsKey("dateRange") ? input.get("dateRange")`

### `errorCode`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:532` `get`：`String errorCode = toText(outputSummary != null ? outputSummary.get("errorCode") : null);`
- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:534` `get`：`errorCode = toText(output != null ? output.get("errorCode") : null);`

### `fallbackTool`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/runtime/recovery/StepFailureRecoveryService.java:126` `get`：`Object tool = stepInput.get("fallbackTool");`
- `src/main/java/com/example/agent/runtime/recovery/StepFailureRecoveryService.java:132` `get`：`Object tool = request.getContext().get("fallbackTool");`

### `filter`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:363` `get`：`: input.containsKey("filter") ? input.get("filter")`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:363` `containsKey`：`: input.containsKey("filter") ? input.get("filter")`

### `filters`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:362` `get`：`Object filters = input.containsKey("filters") ? input.get("filters")`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:362` `containsKey`：`Object filters = input.containsKey("filters") ? input.get("filters")`

### `id`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:682` `get`：`if (response != null && response.get("id") != null) {`
- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:683` `get`：`return response.get("id").toString();`

### `model`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:249` `get`：`String responseModel = response.get("model") instanceof String value ? value : modelId;`
- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:322` `get`：`String responseModel = response.get("model") instanceof String value ? value : modelId;`

### `promptAssemblyInput`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/DefaultPromptAssembler.java:207` `get`：`Object value = stepInput.get("promptAssemblyInput");`
- `src/main/java/com/example/agent/capabilities/llm/DefaultPromptAssembler.java:214` `get`：`Object value = taskRequest.getContext().get("promptAssemblyInput");`

### `rawOnly`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:761` `get`：`if (isTruthy(stepInput.get("rawOnly"))) {`
- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:778` `get`：`if (isTruthy(contextMap.get("rawOnly"))) {`

### `tenantId`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:496` `get`：`Object value = stepInput.get("tenantId");`
- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:502` `get`：`Object value = taskRequest.getContext().get("tenantId");`

### `timeRange`（类数=1，命中=2，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:367` `get`：`Object timeRange = input.containsKey("timeRange") ? input.get("timeRange")`
- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:367` `containsKey`：`Object timeRange = input.containsKey("timeRange") ? input.get("timeRange")`

### `additionalProperties`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:75` `get`：`Boolean additionalProperties = toBoolean(schema.get("additionalProperties"));`

### `allowTools`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:265` `get`：`Object raw = constraints.get("allowTools");`

### `allowedTools`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:331` `get`：`meta.setAllowedTools(readStringList(context.get("allowedTools")));`

### `choices`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1253` `get`：`Object choices = response.get("choices");`

### `code`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:871` `get`：`Object code = error.get("code");`

### `conditions`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:364` `get`：`: input.get("conditions");`

### `confidence`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:907` `containsKey`：`if (!output.containsKey("confidence")) {`

### `constraints`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:384` `get`：`intent.setConstraints(readStringList(context.get("constraints")));`

### `contextPolicy`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:756` `get`：`Object raw = context.get("contextPolicy");`

### `count`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/hook/EvidencePackHookHandler.java:98` `get`：`Integer count = resolveInt(payload.get("count"));`

### `created_at`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:188` `get`：`String createdAt = valueAsString(payload.get("created_at"));`

### `critical`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/reflection/ReflectionService.java:492` `get`：`Object critical = step.getArguments().get("critical");`

### `data`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:1102` `get`：`Object data = response.get("data");`

### `dataScopes`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:353` `get`：`boundary.setDataScopes(readStringList(context.get("dataScopes")));`

### `decision`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/structured/GenericStructuredExtractor.java:56` `containsKey`：`if (rawResult.containsKey("decision") \|\| rawResult.containsKey("nextAction")) {`

### `endTime`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:375` `get`：`putTextSummary(summary, "endTime", input.get("endTime"), limits, truncation);`

### `error`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/mcp/McpToolClient.java:652` `get`：`Object error = response.get("error");`

### `evidencePack`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/prepare/RuntimePreparationService.java:274` `get`：`Object evidenceObj = runtimeContext.get("evidencePack");`

### `expires_at`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:196` `get`：`String expiresAt = valueAsString(payload.get("expires_at"));`

### `forbiddenActions`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:352` `get`：`boundary.setForbiddenActions(readStringList(context.get("forbiddenActions")));`

### `from`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:372` `get`：`putTextSummary(summary, "from", input.get("from"), limits, truncation);`

### `highlights`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:903` `containsKey`：`if (!output.containsKey("highlights")) {`

### `inputDigest`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:499` `get`：`if (summaryMap.get("inputDigest") instanceof Map<?, ?> inputDigestMap) {`

### `inputSummary`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:496` `get`：`if (summaryMap.get("inputSummary") instanceof Map<?, ?> inputSummaryMap) {`

### `items`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:235` `get`：`Map<String, Object> itemSchema = toMap(schema.get("items"));`

### `layer`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:186` `get`：`record.setLayer(valueAsString(payload.get("layer")));`

### `longTermMemoryRefs`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:604` `get`：`if (context != null && context.get("longTermMemoryRefs") instanceof List<?> list) {`

### `mcpServerId`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java:388` `get`：`Object serverId = request.getContext().get("mcpServerId");`

### `memory_id`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:181` `get`：`record.setMemoryId(valueAsString(payload.get("memory_id")));`

### `nextAction`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/structured/GenericStructuredExtractor.java:56` `containsKey`：`if (rawResult.containsKey("decision") \|\| rawResult.containsKey("nextAction")) {`

### `notes`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/reflection/ReflectionService.java:297` `get`：`String notes = root.get("notes") instanceof String value ? value : "llm_reflection";`

### `output`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1093` `get`：`Object output = context.get("output");`

### `outputSummary`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:490` `get`：`if (summaryMap.get("outputSummary") instanceof Map<?, ?> outputSummaryMap) {`

### `payload`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:176` `get`：`Object payloadObj = itemMap.get("payload");`

### `planSteps`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:424` `get`：`memory.setPlanSteps(readStringList(context.get("planSteps")));`

### `policy`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:758` `get`：`raw = context.get("policy");`

### `prompt`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/step/executor/ThoughtTreeStepExecutor.java:40` `get`：`String prompt = input != null && input.get("prompt") instanceof String value ? value : "";`

### `promptScene`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/ModelInvocationService.java:359` `get`：`Object value = metadata.get("promptScene");`

### `promptTrace`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/PromptTrace.java:121` `get`：`Object value = metadata.get("promptTrace");`

### `properties`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/validation/ToolArgumentValidator.java:74` `get`：`Map<String, Object> properties = toMap(schema.get("properties"));`

### `range`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:369` `get`：`: input.get("range");`

### `researchCitations`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:561` `get`：`citationsObj = context.get("researchCitations");`

### `response`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1305` `get`：`Object responseText = response.get("response");`

### `retry`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/reflection/ReflectionService.java:296` `get`：`boolean retry = root.get("retry") instanceof Boolean value && value;`

### `rows`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/structured/GenericStructuredExtractor.java:53` `containsKey`：`if (rawResult.containsKey("rows") \|\| rawResult.containsKey("records")) {`

### `sample`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/react/ReactLoopService.java:511` `get`：`Object sample = summaryMap.get("sample");`

### `score`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/reflection/ReflectionService.java:290` `get`：`if (root.get("score") instanceof Number number) {`

### `selectedTools`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/DefaultContextBuilder.java:649` `get`：`state.setSelectedTools(readStringList(context.get("selectedTools")));`

### `session_id`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:182` `get`：`record.setSessionId(valueAsString(payload.get("session_id")));`

### `skill`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:205` `get`：`Object fromStep = stepInput.get("skill");`

### `skillName`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/ModelToolResolver.java:207` `get`：`fromStep = stepInput.get("skillName");`

### `snippet`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:203` `get`：`citation.setSnippet(map.get("snippet") instanceof String value ? value : "");`

### `source`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/context/research/ResearchPipeline.java:202` `get`：`citation.setSource(map.get("source") instanceof String value ? value : "unknown");`

### `sql`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/structured/GenericStructuredExtractor.java:50` `containsKey`：`if (rawResult.containsKey("sql")) {`

### `startTime`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:374` `get`：`putTextSummary(summary, "startTime", input.get("startTime"), limits, truncation);`

### `summaryMode`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:764` `get`：`ToolSummaryMode mode = parseToolSummaryMode(stepInput.get("summaryMode"));`

### `task_id`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:183` `get`：`record.setTaskId(valueAsString(payload.get("task_id")));`

### `tenant_id`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/memory/QdrantVectorStore.java:187` `get`：`record.setTenantId(valueAsString(payload.get("tenant_id")));`

### `text`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1261` `get`：`Object content = choice.get("text");`

### `to`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java:373` `get`：`putTextSummary(summary, "to", input.get("to"), limits, truncation);`

### `tokenUsage`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/tools/enforcement/EnforcementGateway.java:177` `get`：`Object tokenUsage = result.get("tokenUsage");`

### `toolResultSummary`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java:493` `get`：`if (summaryMap.get("toolResultSummary") instanceof Map<?, ?> toolSummaryMap) {`

### `toolSummaryMode`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java:781` `get`：`return parseToolSummaryMode(contextMap.get("toolSummaryMode"));`

### `usage`（类数=1，命中=1，风险=LOW）

- `src/main/java/com/example/agent/capabilities/llm/DefaultModelProvider.java:1282` `get`：`Object usage = response.get("usage");`

## 附录 B：建议的下一步重构指令草案（不执行）

1. 新增 `OutputKeys` 常量类并补充单测：
   - 新增：`src/main/java/com/example/agent/runtime/output/OutputKeys.java`
   - 新增：`src/test/java/com/example/agent/runtime/output/OutputKeysTest.java`（校验关键 key 不变）
2. 收口 rawRef/refs：
   - 让 `AgentRuntime`/`ReactLoopService`/`LlmStepService` 统一调用 `RawOutputEnvelopeBuilder`（或由 `StepExecutionOutput` 暴露 `getRawEnvelope()`）。
3. 收口 summary：
   - 删除各处 `buildStepOutputSummary/normalize*` 的自实现，统一走 `StepOutputSummaryBuilder`。
4. 加入治理脚本：
   - 新增 `scripts/runtime-map-key-audit.ps1`：扫描并在 CI 中失败（白名单：权威收口点）。

