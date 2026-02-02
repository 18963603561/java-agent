# ReflectionService 反思提示词中 output 全量注入分析

## 1. 背景与现状
当前 `ReflectionService.tryLlmReflection(...)` 在构建提示词时，通过 `buildReflectionPrompt(...)` 将 `output` 整体序列化到 `REFLECTION_CONTEXT_JSON` 中。该 `output` 来自步骤执行结果，包含工具返回、上下文快照、预算、证据包、调用记录、计量等大量信息。

用户示例中的 `output` 包含但不限于：
- `contextSnapshot` / `contextBudget` / `contextPrune` 等上下文结构
- `evidencePack` 与工具调用统计
- `tokenUsage` 计量信息
- 运行链路标识与工具名称

这意味着当前反思模型几乎拿到“完整运行快照”，而不仅是“步骤输出本身”。

## 2. 反思执行流程（现有）
1) `AgentRuntime` 在步骤执行后调用 `ReflectionService.reflect(...)`。
2) 若启用 LLM 反思，进入 `tryLlmReflection(...)`。
3) 调用 `buildReflectionPrompt(...)`：
   - 组装 `stepType`、`attempt`、`output`。
   - `output` 为完整 Map，原样放入 JSON。
4) 生成提示词并调用模型。
5) 解析 `score` / `retry` / `notes` 作为反思结果。

## 3. 是否必要
### 3.1 需要保留的最小信息
反思目的主要是判断：
- 结果是否有效
- 是否需要重试
- 失败原因是否明确

通常只需要：
- 工具执行结果主体（`result`）
- 是否成功、错误码、错误信息
- 关键输出字段是否存在
- 业务相关的返回摘要

### 3.2 不必要或风险较高的信息
以下信息对“质量评估”帮助有限，但会显著拉高成本与噪声：
- `contextSnapshot`、`contextBudget`、`contextPrune`
- 证据包 `evidencePack` 的详细结构
- 计量 `tokenUsage` 的完整字段
- 运行时工作记忆、审计、工具清单等

## 4. 影响分析
### 4.1 对反思结果的影响
- **噪声过多**：模型注意力被大量上下文淹没，可能忽略真实输出质量。
- **偏差风险**：模型可能基于预算或工具耗时做主观判断，而不是输出质量。
- **稳定性下降**：输出结构变化会导致反思输出波动。

### 4.2 成本与性能
- 提示词体积巨大，导致反思模型的 token 成本大幅增加。
- 反思请求变慢，影响整体步骤时延。
- 若输出包含循环结构或超大字段，序列化与传输风险更高。

### 4.3 安全与合规
- 可能包含敏感数据、用户信息、租户链路信息。
- 反思模型等同获得完整运行快照，扩大数据暴露范围。

## 5. 优化思路（建议方案）
核心原则：**反思只看“质量相关的最小输出”，不看“运行快照”。**

### 5.1 输出裁剪与脱敏
- 建议实现 `buildReflectionInput(...)`，仅保留：
  - `stepType` / `attempt`
  - `result`（工具主输出）
  - `errorCode` / `error` / `status`
  - `critical`、`tool`、`fallbackFrom` 等与质量相关字段
- 对敏感字段（用户、租户、链路）进行脱敏或移除。

### 5.2 字段白名单策略
- 只保留白名单字段，明确排除：
  - `contextSnapshot` / `contextBudget` / `contextPrune`
  - `evidencePack` / `tokenUsage`
  - `availableTools` / `auditMetadata` 等

### 5.3 长度与层级限制
- 设定最大字符数或 token 上限，例如 2KB 或 4KB。
- 超出部分自动截断并标记 `truncated=true`。

### 5.4 摘要化输出
- 对大型 `result` 结构先生成摘要字段（如：关键字段 + 前若干项），再用于反思。
- 例如：
  - 列表保留前 N 条
  - Map 保留前 N 个关键字段

### 5.5 配置化开关
在 `ReflectionProperties` 中加入配置项：
- `maxOutputChars`
- `whitelistKeys`
- `blacklistKeys`
- `enableOutputSummary`

## 6. 推荐落地方案（具体步骤）
1) 新增 `buildReflectionInput(step, output, attempt)`：
   - 抽取必要字段并做裁剪。
2) 在 `buildReflectionPrompt(...)` 中改为使用精简后的输入。
3) 引入长度限制与白名单策略。
4) 增加日志记录：
   - 记录裁剪前后大小
   - 标记是否触发截断
5) 添加单元测试：
   - 验证输出字段过滤与截断逻辑

## 7. 结论
当前将 `output` 全量注入反思提示词**并非必要**，且会带来明显的成本、噪声与数据暴露风险。建议改为“质量相关字段 + 有限摘要”的结构化输入，既能保障反思质量，又能显著降低 token 与安全风险。

