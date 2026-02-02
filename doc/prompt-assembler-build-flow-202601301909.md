# PromptAssembler.build(prompt, taskRequest, null) 功能与流程说明

## 1. 调用语句的含义
`PromptBundle bundle = promptAssembler.build(prompt, taskRequest, null);` 表示：
- 使用提示词装配器将原始 `prompt` 与 `taskRequest` 组合为结构化消息（system/developer/user）。
- 第三个参数 `stepInput` 传入 `null`，意味着**不会使用步骤级输入**，只会从 `taskRequest` 中取上下文信息。

最终返回 `PromptBundle`，包含：
- `messages`：消息列表
- `templateId`：模板标识
- `estimatedTokens`：预估 token
- `truncatedSections`：被裁剪段落标识

## 2. 组件职责与核心作用
`PromptAssembler` 是提示词组装器接口，默认实现为 `DefaultPromptAssembler`，主要负责：
- 从上下文中提取系统/开发者提示词模板。
- 将原始用户提示 `prompt` 填充为用户消息。
- 在预算限制下执行裁剪（system/developer/user）。
- 生成可直接送入模型的消息结构 `PromptBundle`。

## 3. 关键数据来源（stepInput = null 时）
当 `stepInput` 为 `null` 时，`DefaultPromptAssembler` 的取值逻辑如下：
1) **ContextSnapshot**：
   - 优先从 `taskRequest.context.contextSnapshot` 获取。
2) **PromptAssemblyInput**：
   - 优先从 `taskRequest.context.promptAssemblyInput` 获取。
3) **用户提示词**：
   - 直接使用传入的 `prompt` 作为用户消息内容。

因此：
- 如果 `taskRequest.context` 内存在 `promptAssemblyInput`，会启用“预算裁剪逻辑”。
- 如果不存在，则走“legacy 模式”。

## 4. 详细执行流程
### 4.1 主入口流程（DefaultPromptAssembler.build）
1) 解析 `contextSnapshot`：
   - `resolveSnapshot(taskRequest, null)`
2) 解析 `promptAssemblyInput`：
   - `resolveAssemblyInput(null, taskRequest)`
3) 若 `promptAssemblyInput` 为空：
   - 进入 `buildLegacy(...)`
4) 若 `promptAssemblyInput` 存在：
   - 补齐 system/developer/user
   - 进行预算裁剪
   - 生成 `PromptBundle`

### 4.2 Legacy 模式（无 PromptAssemblyInput）
- 从 `PromptTemplate.render(snapshot)` 获取 system/developer 消息（可能为空）。
- 追加 user 消息（即 `prompt`）。
- 估算 token 并返回 `PromptBundle`。

### 4.3 裁剪模式（有 PromptAssemblyInput）
- 填充缺失的 system/developer（使用 `PromptTemplate` 渲染结果）。
- 若 user 文本为空，设置为 `prompt`。
- 根据预算执行裁剪：
  - 优先裁剪 developer
  - 再裁剪 user
  - 最后裁剪 system
- 生成 `PromptBundle` 并记录：
  - 估算 token
  - 被裁剪段落（`truncatedSections`）
  - 日志与指标

## 5. 预算裁剪逻辑说明
裁剪规则来自 `PromptAssemblyInput.budgetAllocation`，具体：
- 使用 `ContextBudgetAllocation.sectionTokens` 的 SYSTEM/DEVELOPER/USER 配额。
- 若未配置 sectionTokens，则回退总预算 `totalTokens`。
- 每段提示词有最小保留长度：
  - system >= 30 chars
  - developer >= 10 chars
  - user >= 40 chars

裁剪顺序固定：developer -> user -> system。

## 6. 返回结果 PromptBundle 的作用
返回的 `PromptBundle` 会被上游使用：
- 将 `messages` 写入 `ModelRequest`，作为模型输入。
- `estimatedTokens` 用于预算与观测。
- `truncatedSections` 用于调试与审计。

## 7. 典型调用场景
当前项目中 `promptAssembler.build(prompt, taskRequest, null)` 常见于：
- `FinalOutputService`：生成最终答复提示词。
- `ReflectionService`：生成反思提示词。
- `PlannerService` / `ResearchPipeline` / `DebateCoordinator` 等模块中也会调用，传入 stepInput 时走步骤级上下文。

## 8. 注意事项
- 若 `PromptTemplate` 为空，则 system/developer 消息可能为空，仅保留用户消息。
- 若 `taskRequest.context` 未包含 `promptAssemblyInput`，则不会进行裁剪。
- `stepInput=null` 时无法从步骤级上下文获取 `promptAssemblyInput` 或 `contextSnapshot`，完全依赖 `taskRequest.context`。

