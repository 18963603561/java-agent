# DefaultPromptAssembler.build 主要路径与 buildLegacy 差异分析

时间：2026-02-02 23:37

## 1. 触发条件与入口判断
- build(...) 先解析 ContextSnapshot 与 PromptAssemblyInput。
- 当无法从 stepInput/taskRequest.context 获取到 promptAssemblyInput 时，进入 buildLegacy(prompt, snapshot)。
- 当 promptAssemblyInput 存在时，进入“主路径”（enrich -> trimIfNeeded -> 组装消息）。

## 2. 主路径流程（promptAssemblyInput 存在时）
1) resolveSnapshot(...)：从 stepInput 优先读取 contextSnapshot，其次 taskRequest.context。
2) resolveAssemblyInput(...)：从 stepInput 优先读取 promptAssemblyInput，其次 taskRequest.context。
3) enrichAssemblyInput(...)：
   - system/developer 缺失时，fillSystemDeveloper(...) 通过 PromptTemplate + PromptRenderContext 补齐。
   - userText 缺失时，用 prompt 参数兜底。
   - 若 truncatedSections 为空则初始化。
4) trimIfNeeded(...)：
   - 按预算进行裁剪，更新 truncatedSections。
   - 记录预算使用、裁剪指标与日志。
5) 组装消息：按 system/developer/user 的顺序输出，user 必定存在。
6) 产出 PromptBundle：带 templateId、estimatedTokens（裁剪后）、truncatedSections。

## 3. buildLegacy 流程（promptAssemblyInput 缺失时）
1) 直接通过 promptTemplate.render(PromptRenderContext.fromSnapshot(snapshot)) 生成 system/developer。
2) 直接拼接 user prompt（prompt 参数为空则用空字符串）。
3) 不进行 trimIfNeeded，不做预算统计与裁剪指标记录。
4) 产出 PromptBundle：templateId 取模板 id，estimatedTokens 仅为消息估算，truncatedSections 为空列表。

## 4. 核心差异对比
### 4.1 数据来源与组装策略
- 主路径依赖 PromptAssemblyInput（规划/装配阶段产物），可携带已整理的 system/developer/user 文本。
- buildLegacy 仅依赖 promptTemplate 与 prompt 参数，不使用 PromptAssemblyInput。

### 4.2 system/developer 文本来源
- 主路径：只有在 input.systemText 或 input.developerText 缺失时才填充，避免覆盖上游内容。
- buildLegacy：始终使用模板渲染结果，不考虑上游已装配内容。

### 4.3 user 文本来源
- 主路径：优先使用 input.userText（可由上游设置，含 snapshot.taskIntent.inputText 的兜底），仅在缺失时用 prompt 参数。
- buildLegacy：只使用 prompt 参数，不读取 input.userText，也不会使用 taskIntent.inputText 的兜底。

### 4.4 裁剪与预算
- 主路径：执行 trimIfNeeded(...)，按预算裁剪并记录 truncatedSections，同时更新预算使用。
- buildLegacy：不做裁剪、无预算统计、无裁剪指标记录。

### 4.5 token 估算与日志
- 主路径：estimatedTokens 使用裁剪后的 tokens，总量受预算控制；日志会输出 trimmed=true/false。
- buildLegacy：estimatedTokens 仅为当前消息估算，trimmed 固定为 false。

### 4.6 行为一致性风险
- buildLegacy 可能忽略规划阶段已有的 prompt 组装策略，导致：
  - system/developer 与上游定义不一致；
  - user 文本来源不一致；
  - 预算裁剪失效，潜在超长 prompt 风险。
- 主路径遵循规划装配结果与预算策略，输出更稳定。

## 5. 结论
- buildLegacy 是“无装配输入”时的兼容路径，逻辑简单、缺乏预算裁剪与装配对齐。
- 主路径是当前推荐流程：充分利用 PromptAssemblyInput，保证 system/developer/user 的来源一致性并受预算控制。
- 若业务已全面产出 promptAssemblyInput，应尽量避免触发 buildLegacy，以减少行为差异与成本风险。