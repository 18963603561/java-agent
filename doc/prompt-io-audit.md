# 提示词 JSON 解析入口与容错审计

生成时间：2026-01-31
范围：planner、reflection、final、react、cot、research、debate、multi-agent
说明：本文件仅基于静态代码解析入口与容错逻辑，不包含运行时推测。

一、解析入口与容错逻辑（逐场景）

1. planner
- 解析入口：src/main/java/com/example/agent/planning/PlannerService.java，方法 parsePlan(String content, TaskRequest request, Map<String, Object> context)
- 关键字段：steps、summary、type、input、tool、dependsOn、query、context
- 解析方式：objectMapper.readValue 解析为 Map 后读取 steps 列表
- 容错逻辑：
  - steps 非列表时直接返回 null
  - steps 内元素非 Map 时跳过
  - type 缺省时默认 "TOOL"
  - input 缺省时使用空 Map，并自动补充 query 与 context
  - summary 缺省时默认 "llm-plan"
- 上层回退：tryLlmPlan 解析失败或 steps 为空时返回 null；plan 方法在 LLM 规划失败后回退到规则规划（plannerProperties.isFallbackEnabled=false 时抛出 "planner_fallback_disabled"）

2. reflection
- 解析入口：src/main/java/com/example/agent/reflection/ReflectionService.java，方法 parseReflection(String content)
- 关键字段：score、retry、notes
- 解析方式：objectMapper.readValue 解析为 Map 后读取 score
- 容错逻辑：
  - score 缺失或非数字时返回 null
  - retry 仅在 Boolean 为 true 时生效，缺省为 false
  - notes 缺省时默认 "llm_reflection"
- 上层回退：tryLlmReflection 解析失败返回 null；reflect 方法在 LLM 失败后回退到 heuristicReflection（properties.isFallbackEnabled=false 时抛出 "reflection_fallback_disabled"）

3. final
- 解析入口：src/main/java/com/example/agent/runtime/FinalOutputService.java，方法 parseFinalOutput(String content)
- 关键字段：answer、highlights、confidence
- 解析方式：objectMapper.readValue 解析为 Map
- 容错逻辑：解析异常返回空 Map
- 上层回退：finalizeOutput 解析结果为空时回退为原始文本输出，answer 直接取 response.getContent()
- 结果兜底：AgentRuntime.resolveFinalOutputFromSteps 当 output 仅包含 finalAnswer 时，会补写 answer

4. react
- 解析入口：src/main/java/com/example/agent/runtime/ReactLoopService.java，方法 parseDecision(String content)
- 关键字段：action、tool、arguments、shouldStop、stopReason、finalAnswer
- 解析方式：objectMapper.readValue 解析为 ReactDecision
- 容错逻辑：内容为空或解析异常返回 null
- 上层回退：think 方法在 decision 为 null 时构造新 ReactDecision，并将 action 设置为 "none"

5. cot
- 解析入口：src/main/java/com/example/agent/reasoning/ChainOfThoughtService.java，方法 parseDecision(String content)
- 关键字段：stepSummary、shouldContinue、finalAnswer、confidence、stopReason，兼容备用字段 summary 与 answer
- 解析方式：先 normalizeJsonPayload 清洗代码块与噪声，再 objectMapper.readValue 解析为 Map
- 容错逻辑：
  - 内容为空或清洗后为空时返回无效决策 StepDecision.invalid("empty_response")
  - JSON 解析异常返回无效决策 StepDecision.invalid("invalid_response")
  - shouldContinue 缺省时默认 true
  - stepSummary 缺省时尝试 summary
  - finalAnswer 缺省时尝试 answer
  - confidence 支持数值或数值字符串
  - stopReason 缺省但 shouldContinue=false 时自动填充 "completed"

6. research
- 解析入口：src/main/java/com/example/agent/research/ResearchPipeline.java，方法 parseCitations(String content)
- 关键字段：citations、source、snippet
- 解析方式：objectMapper.readValue 解析为 Map 后读取 citations 列表
- 容错逻辑：
  - content 为空或非 JSON 时返回空列表
  - citations 非列表时返回空列表
  - citations 元素非 Map 时跳过
  - source 缺省时默认 "unknown"
  - snippet 缺省时默认空字符串
- 上层回退：run 方法在 citations 为空时回退 buildFallbackCitations，source 固定为 "local"

7. debate
- 解析入口：src/main/java/com/example/agent/reasoning/DebateCoordinator.java，方法 parseConclusion(String content)
- 关键字段：conclusion
- 解析方式：objectMapper.readValue 解析为 Map 后读取 conclusion
- 容错逻辑：
  - content 为空时返回 "no_conclusion"
  - JSON 解析异常时直接返回原始 content
  - conclusion 缺失时返回原始 content

8. multi-agent
- 解析入口：src/main/java/com/example/agent/multiagent/MultiAgentCoordinator.java，方法 parseRoles(String content)
- 关键字段：team、roleId、name、modelId、description
- 解析方式：objectMapper.readValue 解析为 Map 后读取 team 列表
- 容错逻辑：
  - content 为空或解析异常时返回空列表
  - team 非列表时返回空列表
  - team 元素非 Map 时跳过
  - roleId 缺省时使用随机 UUID
  - name 缺省时默认 "agent"
  - modelId、description 可为空
- 上层回退：coordinate 方法在 roles 为空时回退 buildFallbackRoles

二、字段口径（以代码为准）

1. planner
- 必须字段：steps（数组，且至少包含一个 Map 元素，否则 parsePlan 返回 null 或上层判空失败）
- 允许缺省字段：summary、type、input、tool、dependsOn、query、context

2. reflection
- 必须字段：score（数字）
- 允许缺省字段：retry、notes

3. final
- 必须字段：无硬性必填字段
- 允许缺省字段：answer、highlights、confidence
- 说明：当 JSON 解析失败时，系统会退回原始文本并写入 answer

4. react
- 必须字段：无硬性必填字段
- 允许缺省字段：action、tool、arguments、shouldStop、stopReason、finalAnswer
- 说明：解析失败时会构造默认 decision，action="none"

5. cot
- 必须字段：无硬性必填字段
- 允许缺省字段：stepSummary、shouldContinue、finalAnswer、confidence、stopReason
- 说明：stepSummary 与 finalAnswer 支持备用字段 summary、answer

6. research
- 必须字段：citations（数组）
- 允许缺省字段：source、snippet（元素级缺省有默认值）

7. debate
- 必须字段：无硬性必填字段
- 允许缺省字段：conclusion（缺省时回落到原始文本或 "no_conclusion"）

8. multi-agent
- 必须字段：team（数组）
- 允许缺省字段：roleId、name、modelId、description（元素级缺省有默认值）

三、最容易导致解析失败的 Top 5 触发点与修复策略

1. 非法 JSON 语法
- 触发点：尾随逗号、未加引号的字段名、单引号字符串、注释、混入非 JSON 文本
- 影响场景：planner、reflection、final、react、research、debate、multi-agent
- 修复策略：严格输出标准 JSON；禁止尾随逗号；字段名与字符串必须使用双引号；禁止输出额外文本

2. 根结构类型错误
- 触发点：输出数组而非对象，或外层包裹额外对象导致根结构与解析逻辑不一致
- 影响场景：planner（必须对象且包含 steps）、reflection、research、multi-agent、debate、final、react
- 修复策略：根对象必须是 JSON 对象；不要输出数组作为根；不要在对象外再包一层非预期结构

3. 关键字段缺失或类型不匹配
- 触发点：planner 缺 steps 或 steps 非数组；reflection 缺 score 或 score 非数字；research 缺 citations 或非数组
- 影响场景：planner、reflection、research
- 修复策略：明确必填字段并保证类型正确；score 必须是数字；steps 与 citations 必须是数组

4. 代码块或冗余文本包裹
- 触发点：输出包含 ``` 包裹、前后解释性文本、日志或提示语
- 影响场景：除 cot 外其余场景都会直接解析失败
- 修复策略：输出仅包含 JSON；不要使用代码块围栏；不要附加解释性文本

5. 字段类型与期望类型不一致
- 触发点：shouldContinue、retry、shouldStop 等布尔字段输出为字符串；confidence 输出为非数字；arguments 输出为非对象
- 影响场景：cot、reflection、react
- 修复策略：布尔字段必须为 true/false；数字字段必须为 number；arguments 必须为对象