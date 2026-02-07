# PromptAssemblyInput 生成与为空影响分析

时间：2026-02-02 23:39

## 1. 结论摘要
- PromptAssemblyInput 在当前工程中**主要由规划阶段生成**，具体由 PlannerService.buildPromptAssemblyInput(...) 调用 ContextAssembler 生成并写入 assemblyContext。
- 除规划阶段外，未发现其它代码主动写入 "promptAssemblyInput"；因此运行阶段大多为 null，但并非“流程为空”，会走 buildLegacy(...) 兼容路径。
- 是否为空决定 DefaultPromptAssembler.build(...) 走哪条路径：
  - 非空：走“主路径”（装配补齐 + 预算裁剪）。
  - 为空：走 buildLegacy(...)（模板 + 用户文本直拼，不做裁剪）。

## 2. PromptAssemblyInput 的生成来源
### 2.1 主要来源：规划阶段
- PlannerService.applyPromptBundle(...) 会调用 buildPromptAssemblyInput(...)。
- buildPromptAssemblyInput(...) 通过 ContextAssembler.assemble(...) 生成 PromptAssemblyInput。
- 如果生成成功，会写入 assemblyContext：
  - key: "promptAssemblyInput"
  - value: PromptAssemblyInput
- 随后 promptAssembler.build(prompt, request, assemblyContext) 会在 build(...) 内部读出该对象。

### 2.2 其它来源（理论可能，实际未见）
- DefaultPromptAssembler.resolveAssemblyInput(...) 只从以下位置读取：
  - stepInput.get("promptAssemblyInput")
  - taskRequest.getContext().get("promptAssemblyInput")
- 代码搜索显示，除 PlannerService 外未发现其它写入路径；因此运行链路如 LlmStepService / FinalOutputService / ReflectionService 等通常不会带该对象。

## 3. PromptAssemblyInput 为空的典型场景
1) 运行阶段调用 promptAssembler.build(...)：
   - LlmStepService / ReactLoopService / FinalOutputService / ReflectionService 等仅传入 prompt/stepInput/taskRequest，未注入 promptAssemblyInput。
2) 规划阶段但 ContextAssembler 未注入或为 null：
   - PlannerService.buildPromptAssemblyInput(...) 直接返回 null。
3) 外部调用 build(...) 时仅传 prompt 与上下文快照，未写入 promptAssemblyInput。

## 4. 为空与非空的流程差异（DefaultPromptAssembler.build）
### 4.1 非空（主路径）
- 入口：resolveAssemblyInput(...) 返回 PromptAssemblyInput。
- 处理流程：
  1) enrichAssemblyInput(...)：
     - system/developer 缺失时通过 PromptTemplate + PromptRenderContext 补齐。
     - userText 缺失时用 prompt 参数兜底。
  2) trimIfNeeded(...)：按预算裁剪 system/developer/user，更新 truncatedSections。
  3) 组装消息：system/developer 可为空，user 必有。
  4) 估算 tokens 与记录裁剪统计。
- 影响：
  - 预算裁剪生效，避免超长 prompt。
  - 预算使用统计与裁剪指标会写回。
  - 更符合规划阶段的“装配与预算一致性”。

### 4.2 为空（buildLegacy 路径）
- 入口：resolveAssemblyInput(...) 返回 null。
- 处理流程：
  1) promptTemplate.render(PromptRenderContext.fromSnapshot(snapshot)) 生成 system/developer。
  2) 直接拼接 user prompt（prompt 为空则用空字符串）。
  3) 不执行 trimIfNeeded(...)，不做预算裁剪与指标记录。
- 影响：
  - 不会使用 PromptAssemblyInput 里可能存在的 userText 兜底或预算信息。
  - 预算裁剪失效，可能导致更长的 prompt。
  - truncatedSections 固定为空，裁剪指标不产生。

## 5. 是否“流程为空”的说明
- 即使 PromptAssemblyInput 为空，buildLegacy(...) 仍会返回完整的 PromptBundle：
  - system/developer 来自模板渲染（最小上下文）。
  - user 来自 prompt 参数。
- 因此并非“流程为空”，而是走“简化兼容路径”。

## 6. 结论与建议
- 从工程现状看，PromptAssemblyInput 基本只在规划阶段出现。
- 若希望运行阶段也走主路径，需要在运行链路显式注入 promptAssemblyInput（或统一装配入口）。
- 若保持现状，应接受运行阶段使用 buildLegacy 的差异：无裁剪、无预算统计、userText 来源更简单。