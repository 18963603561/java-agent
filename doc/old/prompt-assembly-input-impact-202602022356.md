# 增加 PromptAssemblyInput 对原流程的影响分析

时间：2026-02-02 23:56

## 1. 结论摘要
- 增加 `promptAssemblyInput` 会让 `DefaultPromptAssembler.build(...)` 从兼容路径切换为主路径，触发装配补齐与预算裁剪逻辑。
- 主路径会引入：预算统计、裁剪记录、指标上报与更严格的 userText 来源判断，可能改变提示词内容与长度。
- 若 `promptAssemblyInput.userText` 非空，将覆盖 `prompt` 参数，导致运行阶段的“按步骤生成的 prompt”被忽略，这是最主要的行为差异风险。

## 2. 原流程（未注入 promptAssemblyInput）
- 进入 `buildLegacy(...)`：
  - system/developer：模板渲染结果（最小上下文）。
  - user：直接使用 `prompt` 参数（为空则空串）。
  - 不执行 `trimIfNeeded(...)`，无预算裁剪与指标记录。
  - `truncatedSections` 固定为空列表。

## 3. 新流程（注入 promptAssemblyInput）
- 进入主路径：
  1) `enrichAssemblyInput(...)`：
     - system/developer 缺失时才由模板补齐。
     - userText 缺失时才使用 `prompt` 参数。
  2) `trimIfNeeded(...)`：
     - 若 `budgetAllocation` 有值且预算不足，触发裁剪。
  3) 预算与裁剪统计写回：`budgetUsedTokens/budgetUsedChars/truncatedSections`。

## 4. 影响点清单
### 4.1 userText 来源变化（核心差异）
- 原流程：user 统一使用 `prompt` 参数。
- 新流程：若 `promptAssemblyInput.userText` 非空，则 **不会使用** `prompt` 参数。
- 风险：运行阶段的 `prompt` 往往由步骤逻辑动态拼装（如反思、COT、ReAct），若 `promptAssemblyInput` 在构建时已填入 `snapshot.taskIntent.inputText`，则这些动态 prompt 会被覆盖。

### 4.2 裁剪与预算行为变化
- 新流程会按预算裁剪三段文本：system/developer/user。
- 如果 `contextBudget` 存在，且 `agent.prompt.trim.enabled=true`，可能发生裁剪，导致提示词内容被截断。
- 旧流程无裁剪，提示词长度完全取决于 `prompt` 与模板内容。

### 4.3 指标与日志变化
- 新流程会更新 `budgetUsedTokens/budgetUsedChars`，并记录裁剪指标与日志。
- 旧流程不会生成裁剪记录，`truncatedSections` 固定为空。

### 4.4 对象复用风险
- `PromptAssemblyInput` 在 `build(...)` 内会被修改（更新 `truncatedSections` 与预算统计）。
- 若多个步骤复用同一个 `promptAssemblyInput`，可能导致：
  - 裁剪记录串步；
  - 预算使用统计被后续步骤覆盖；
  - userText 复用导致 prompt 错位。

### 4.5 system/developer 来源差异
- 新流程只有在 `system/developer` 为空时才填充模板内容，避免覆盖。
- 若运行阶段注入的 `PromptAssemblyInput` 已填入 system/developer，则模板不会再生效。

### 4.6 性能影响
- 新流程会执行 token 估算与预算计算，步骤频繁时有一定额外开销。
- 但该开销通常有限，主要与 `tokenEstimator` 实现成本有关。

## 5. 控制风险的建议
1) **保证每次调用时 userText 与 prompt 对齐**：
   - 构建 `PromptAssemblyInput` 时传入当前 `prompt` 作为 userText；或
   - 构建后强制覆盖 `input.setUserText(prompt)`。
2) **避免跨步骤复用同一对象**：
   - 每次 build 前重新构建，或做浅拷贝再写入。
3) **审视预算配置**：
   - 若不希望运行阶段被裁剪，应确保 `contextBudget` 不传入或预算足够。

## 6. 结论
- 增加 `promptAssemblyInput` 会使运行链路行为更接近规划链路，但会带来 **prompt 来源与裁剪行为的变化**。
- 若不处理 `userText` 覆盖风险，运行阶段提示词可能与预期不一致，是最核心的影响点。