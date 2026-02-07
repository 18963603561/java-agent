# 方法重复与通用化分级分析

## 一、分析范围与方法
- 扫描范围：`src/main/java` 与 `src/test/java` 全量 Java 文件。
- 扫描结果：共识别方法 `3452` 个，其中主代码 `3148` 个，测试代码 `304` 个。
- 统计产物：
  - `target/method-dup-analysis.json`：完全重复方法体与方法名重复计数。
  - `target/method-high-similarity.json`：高相似方法体（非完全一致）配对。
- 判定口径：
  - 完全重复：去注释、去空白后方法体一致。
  - 高相似：结构与控制流高度一致，阈值约 `0.85+`。
- 本文中的“重复数量”以“方法实例数”为主，少量为“方法簇近似统计”（已在条目中标注）。

## 二、优先级定义（P0-P4）
- `P0`：跨模块高频重复，且涉及安全、租户隔离、预算控制、主链路稳定性，优先统一。
- `P1`：中高频重复，且对行为一致性影响明显，建议本轮统一。
- `P2`：中频重复，统一后收益明确，但可排在 P0/P1 之后。
- `P3`：低频重复或测试侧重复，建议按重构窗口处理。
- `P4`：相似但不应强行抽取（领域语义差异大），仅做约束与模板化。

## 三、按重复数量降序的分级清单

| 排名 | 方法簇 | 重复数量 | 典型位置 | 是否建议进通用 util | 优先级 |
|---|---|---:|---|---|---|
| 1 | 租户/鉴权/链路上下文簇（`getTenantContext`、`authenticate`、`resolveTraceId`、`attachTraceContext`） | 约 `34`（`13+6+10+5`） | `gateway/controller/*`、`runtime/*`、`orchestrator/*`、`streaming/*` | 建议，拆成 `auth` 与 `trace` 两层 | `P0` |
| 2 | 预算令牌估算簇（`estimateSectionTokens` 等 16 个同名方法） | 约 `32`（两类各一套） | `budget/ContextCompressionController`、`budget/DefaultContextTrimmer` | 强烈建议，统一为 `budget` 工具类/组件 | `P0` |
| 3 | 上下文策略 Map 解析簇（`resolvePolicyFromContext`、`buildPolicyFromMap`、`hasPolicyContent`、`readString*`、`readInteger`、`readBoolean`、`trimText`） | 约 `18`（9 对） | `context/DefaultContextBuilder`、`memory/MemoryRecallService` | 建议，统一为 `context/policy` + `value` | `P1` |
| 4 | 提示词参数注入簇（`applyPromptBundle`） | `7` | `multiagent`、`reasoning`、`research`、`repair`、`runtime` | 建议，统一为 `prompt` 组件 | `P1` |
| 5 | 真值判定簇（`isTruthy`） | `6` | `model`、`planning`、`runtime` | 建议，统一为 `value/BooleanValueUtils` | `P1` |
| 6 | 文本兜底簇（`firstNonBlank`） | `6` | `memory/*`、`budget/*`、`context/*`、`tools/hook/*` | 建议，统一为 `value/StringValueUtils` | `P2` |
| 7 | 序列号解析簇（`parseSeq`） | `5` | `history/*`、`governance/ReplayService`、`streaming/EventStreamService` | 建议，统一为 `sequence/SeqUtils` | `P1` |
| 8 | 字符串裁剪簇（`trimText`/`truncate`） | `7`（同名+同体） | `context`、`memory`、`reasoning` | 建议，统一为 `value/StringCutUtils` | `P2` |
| 9 | 摘要摘要簇（`buildDigest`、`buildMapDigest`、`buildDigestSummary`） | `6`（`2+2+2`） | `agentcore/ToolExecutor`、`approval/ApprovalService`、`runtime/*` | 建议，统一为 `digest/DigestUtils` | `P2` |
|10| 模型工具选择解析簇（`parseToolChoice`） | `3` | `model/DefaultModelProvider`、`model/ModelToolResolver` | 建议，统一为 `model/ToolChoiceParser` | `P2` |
|11| JSON 映射抽取簇（`extractMap`） | `2` | `reflection/ReflectionService`、`runtime/ReactLoopService` | 可统一到 `json/MapExtractUtils` | `P3` |
|12| 测试辅助簇（`buildRedactionService`、`findFirst`、`buildStore`） | `8+`（测试侧） | `src/test/java/*` | 建议仅在测试基类统一，不进主 util | `P3` |

## 四、高相似但不建议直接放 util（P4）
以下方法体相似度高，但领域语义不同，强行抽到 util 会增加认知成本或导致分层污染：
- `model/DefaultModelProvider#invokeOpenAiCompatible` 与 `model/DefaultModelProvider#invokeOllama`
- `runtime/ExecutionControlService#pause|resume|requestApproval|decideApproval`（流程状态机语义强）
- `scheduler/ScheduleManager#pause|resume|cancel`（调度域语义强）
- `reasoning/DebateCoordinator#buildPrompt` 与 `research/ResearchPipeline#buildPrompt`（提示词模板语义不同）

建议：这类方法保留在领域服务中，仅通过“模板方法/抽象基类/策略接口”做轻量收敛，不进入通用 util。

## 五、建议的通用 util 分层包方案

### 1）`com.example.agent.common.util.auth`
- `TenantAuthUtils`
  - `authenticate(...)`
  - `getTenantContext(...)`
- `TraceContextUtils`
  - `resolveTraceId(...)`
  - `attachTraceContext(...)`

### 2）`com.example.agent.common.util.budget`
- `ContextTokenEstimateUtils`
  - 收敛 `estimateSectionTokens` 及其 16 个子估算方法。
- `ContextTokenMathUtils`
  - `sumTokens(...)`

### 3）`com.example.agent.common.util.context`
- `ContextPolicyMapper`
  - `resolvePolicyFromContext(...)`
  - `buildPolicyFromMap(...)`
  - `hasPolicyContent(...)`

### 4）`com.example.agent.common.util.prompt`
- `PromptBundleUtils`
  - `applyPromptBundle(...)`

### 5）`com.example.agent.common.util.value`
- `BooleanValueUtils`
  - `isTruthy(...)`
  - `readBoolean(...)`
- `StringValueUtils`
  - `firstNonBlank(...)`
  - `readString(...)`
  - `readStringList(...)`
  - `trimText(...)`
- `NumberValueUtils`
  - `readInteger(...)`
  - `parseSeq(...)`
  - `nextSeq(...)`

### 6）`com.example.agent.common.util.digest`
- `DigestUtils`
  - `buildDigest(...)`
  - `buildMapDigest(...)`
  - `buildDigestSummary(...)`

## 六、落地顺序建议
1. `P0`：先统一 `auth/trace` 与 `budget` 估算簇，优先消除跨入口行为漂移。
2. `P1`：统一 `context policy` 与 `prompt bundle`，减少上下文构建/推理链路分叉。
3. `P2`：统一 `value` 与 `digest` 工具，降低维护成本。
4. `P3`：测试侧提取测试基类与测试工具。
5. `P4`：仅做约束，不进入 util；通过抽象接口控制复用边界。

## 七、结论
- 可以统一到通用 util 包，但必须按“功能分层 + 领域边界”拆类，避免形成 `CommonUtil` 大杂烩。
- 当前最值得立刻处理的是：
  - `P0`：`auth/trace` 上下文链路
  - `P0`：`budget` 令牌估算簇
- 这两项完成后，可显著降低主链路行为不一致风险，并为后续模块化重构打底。
