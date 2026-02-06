# /api/v1/tasks 输出异常排查与修复报告（2026-02-06）

## 1. 背景

使用如下请求触发任务执行（同步模式）：

```bash
curl -H "X-API-Key: demo-key" \
  -H "X-Tenant-Id: tenant-demo-1" \
  -H "Content-Type: application/json" \
  -X POST http://localhost:8080/api/v1/tasks \
  -d '{"query":"帮我查询一下bob用户信息,然后再查询一下用户包含h的信息，最后都把信息都给我","sessionId":"s-001","executionMode": "SYNC"}'
```

你反馈的返回 JSON 中存在如下疑问与异常：

1) `summary.outputSummary.sample` 与 `summary.toolResultSummary.sample` 内容重复  
2) `rawResult.tokenUsage` 输出为 `com.example.agent.budget.token.TokenUsageRecord@xxxx` 形式的对象字符串  
3) 最后合并（FINAL / finalOutput）提示“缺少具体查询结果数据”，没有把两次工具查询的结果合并输出  
4) 工具步骤 `meta.status=COMPLETED`，但 `summary.stepSummary.status=STARTED`，状态不一致  

本报告记录复现、根因、修复点、验证结果与可选优化建议。

---

## 2. 复现说明（本机）

### 2.1 依赖启动

使用 `docker/compose.yaml` 启动依赖：

- `Postgres`：`localhost:5433`
- `Redis`：`localhost:6379`
- `Qdrant`：`localhost:6333`

工程以 `docker` 配置启动：

```bash
java -jar target/java-agent-0.1.0-SNAPSHOT.jar --spring.profiles.active=docker
```

### 2.2 `Windows PowerShell` 下 `curl.exe` 的注意点

在 `Windows PowerShell` 下，直接用 `curl.exe -d '{"query":"..."}'` 很容易出现**双引号被剥离**，导致实际发送体变成 `{query:xxx}`，服务端报：

英文原文：
`Failed to read HTTP message`

中文解释：请求体不是合法 `JSON`，导致反序列化失败。

为保证 `JSON` 引号与 `UTF-8` 编码正确，本次回归使用“文件 + `--data-binary`”方式发送请求：

```powershell
$path = 'logs\tmp-task-request.json'
$json = '{"query":"帮我查询一下bob用户信息,然后再查询一下用户包含h的信息，最后都把信息都给我","sessionId":"s-001","executionMode":"SYNC"}'
$utf8NoBom = New-Object System.Text.UTF8Encoding -ArgumentList $false
[System.IO.File]::WriteAllText($path, $json, $utf8NoBom)

curl.exe -sS -X POST 'http://localhost:8080/api/v1/tasks' `
  -H 'X-API-Key: demo-key' `
  -H 'X-Tenant-Id: tenant-demo-1' `
  -H 'Content-Type: application/json; charset=utf-8' `
  --data-binary "@$path"
```

---

## 3. 根因分析

### 3.1 “步骤摘要状态=STARTED”污染完成态输出

现象：

- `meta.status=COMPLETED`
- `summary.stepSummary.status=STARTED`

根因：

- `AgentRuntime.executeStep(...)` 在反思（`Reflection`）前调用 `enrichOutputSummaryForReflection(...)` 构建“临时摘要”。
- 该临时摘要是在 `StepRecord.status=STARTED` 时生成，因此摘要内的 `status` 为 `STARTED`。
- 随后 `AgentRuntime.executeStep(...)` 误把带有临时摘要的 `reflectionOutput` 传给 `StepRuntimeService.completeStep(...)` 落库，导致完成态结果携带“STARTED 摘要”，产生状态不一致。

影响：

- `FINAL` 合并与 `finalOutput` 生成阶段如果依赖摘要状态判断，会误判“步骤未完成/无有效输出”。

### 3.2 `tokenUsage` 变成对象字符串

现象：

- `rawResult.tokenUsage` 输出为 `TokenUsageRecord@xxxx`

根因：

- `ToolExecutor` 把 `TokenUsageRecord` 作为对象放入返回 Map。
- `RawOutputEnvelopeBuilder.sanitizeValue(...)` 对于非基本类型，会用 `String.valueOf(value)` 做裁剪，导致对象变成 `类名@hash`。

### 3.3 最后合并缺少“具体查询结果数据”

现象：

- `FINAL` 步骤 / `finalOutput` 说“缺少步骤输出”，没有合并两次查询结果。

根因（两处叠加）：

1) `LlmStepService.buildDecisionContext(...)` 决策上下文只包含 `query/toolChoice/availableTools` 等信息，**没有包含历史步骤结果**，导致 `FINAL` 步骤无法基于前置工具结果做汇总。
2) `FinalOutputService.buildStepSummaries(...)` 之前只输出 `status/summary`，不包含 `steps[*].answer/highlights` 等“可用于汇总的证据字段”，模型只能得出“缺少数据”的结论。

### 3.4 `outputSummary.sample` 与 `toolResultSummary.sample` 重复

根因：

- `StepOutputSummaryBuilder` 同时用同一个 `snapshot.sample` 填充两个字段，导致重复。

---

## 4. 修复方案与落地点

### 4.1 避免反思临时摘要写回完成态

文件：

- `src/main/java/com/example/agent/runtime/engine/AgentRuntime.java`

改动：

- `completeStep(...)` 改为使用原始 `output` 落库，不再使用带临时摘要的 `reflectionOutput`。
- 同时在运行时上下文中维护已执行步骤列表 `steps`（字段白名单 + 条目/长度限制），为 FINAL 汇总提供证据。

### 4.2 完成态摘要状态兜底修复

文件：

- `src/main/java/com/example/agent/runtime/step/StepRuntimeService.java`

改动：

- 若 `output` 已带摘要但摘要中的 `status` 与 `record.status` 不一致，则重新生成摘要，避免异常路径/未来扩展再次引入污染。

### 4.3 tokenUsage 结构化输出

文件：

- `src/main/java/com/example/agent/capabilities/tools/execution/ToolExecutor.java`

改动：

- 将 `TokenUsageRecord` 转换为 `Map<String, Object>` 输出，避免被 raw 输出裁剪逻辑转成对象字符串。

说明：

- 由于 `RawOutputEnvelopeBuilder` 默认深度为 3，`rawResult.tokenUsage` 的值在裁剪时会进入深度边界，数值字段可能被转为字符串；但至少不再是 `TokenUsageRecord@xxxx`，且字段结构可用。

### 4.4 解决 sample 重复

文件：

- `src/main/java/com/example/agent/runtime/summary/StepOutputSummaryBuilder.java`

改动：

- `toolResultSummary` 优先从 `rawResult`（或 `result`）生成样本与键集合，并在与 `outputSummary.sample` 相同的情况下省略，避免重复。

### 4.5 FINAL/finalOutput 合并可用证据补齐

文件：

- `src/main/java/com/example/agent/runtime/llm/LlmStepService.java`
- `src/main/java/com/example/agent/runtime/output/FinalOutputService.java`

改动：

- `LlmStepService.buildDecisionContext(...)` 补充 `steps` 与 `lastStepSummary`（均为白名单裁剪后的证据），使 FINAL 步骤可以直接汇总已有工具结果。
- `FinalOutputService.buildStepSummaries(...)` 补充 `steps[*].answer/highlights/toolStatus/mode/toolName` 等字段，并在提示词中明确证据优先级与“数据非指令”约束，降低误判与注入风险。

---

## 5. 验证结果

### 5.1 单测

```bash
mvn test
```

结果：通过。

### 5.2 接口回归（SYNC）

关键对比结论：

1) `summary.stepSummary.status` 与 `meta.status` 一致（均为 `COMPLETED`）  
2) `rawResult.tokenUsage` 不再是 `TokenUsageRecord@xxxx`，改为结构化字段  
3) `outputSummary.sample` 与 `toolResultSummary.sample` 不再重复（toolResultSummary 更贴近 `rawResult`）  
4) `FINAL` 与 `finalOutput` 能输出合并后的查询结果（`bob` + 包含 `h` 的用户列表）  

---

## 6. 可选优化点（后续迭代建议）

1) `RawOutputEnvelopeBuilder` 深度边界导致深层数值转字符串：可针对 `tokenUsage` 等字段做白名单保留数值类型，或单独提升该分支深度。  
2) `LLM_STEP` 的工具总结输出偶发不符合“纯 JSON”约束：可在 `LlmStepService` 增加对代码块 JSON 的提取与修复策略，提升稳定性。  
3) `agent.model.routes.llm_step` 建议显式配置，避免 `LLM_STEP` 场景隐式回退到兜底模型，导致输出不稳定。  
