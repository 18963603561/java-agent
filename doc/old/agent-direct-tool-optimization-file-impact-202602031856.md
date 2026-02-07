# 落地建议（不改业务语义）涉及修改文件分析

## 1. 目标范围
依据 `agent-direct-tool-optimization-202602031845.md` 的落地建议，分析需要修改的文件与原因，保证：
- 规划阶段可提供完整工具参数（在条件满足时）。
- 执行阶段新增“直达工具”分支，并保留原有回退路径。
- 输出结构与上下文回写保持一致，不改变业务语义。

## 2. 建议拆解与落点
### 2.1 规划阶段强化参数生成
涉及变更：
- 在规划模型请求中注入完整工具 schema。
- 强化规划输出约束：`stepType=TOOL` 时必须包含 `toolName + arguments`。
- 在解析阶段做参数完整性校验，缺失则视为不可用并回退。

落点说明：
- `PlannerService` 负责规划提示词与解析逻辑，是约束与校验的核心落点。
- `ModelToolResolver` 当前按全局 `toolInjectMode` 注入工具，若仅在规划场景需要完整 schema，应提供按场景注入能力或增加显式参数。

### 2.2 执行阶段新增“直达工具”分支
涉及变更：
- `AgentRuntime.executeStep` 增加分支：当 `stepType=TOOL` 且参数完整时直接调用 `executeToolStep`。
- 直达分支失败时回退到 `llmStepService.run`，保持可用性与语义一致。

落点说明：
- 直达分支应复用现有工具执行、钩子、执行控制与异常处理逻辑，避免绕过统一策略。

### 2.3 输出结构与上下文回写统一
涉及变更：
- 直达分支需补齐 `mode`、`tool`、`toolStatus` 等字段，确保与 LLM 路径一致。
- 需复用或抽取 `LlmStepService` 的总结逻辑与上下文回写逻辑。

落点说明：
- `LlmStepService` 内部的总结与上下文回写逻辑当前为私有方法，需抽取为可复用能力或新增公共服务。

## 3. 涉及修改的文件清单（按职责）
### 3.1 规划层
- `src/main/java/com/example/agent/planning/PlannerService.java`
  - 强化规划提示词：明确 `stepType=TOOL` 必须输出 `toolName + arguments`。
  - 解析与校验：在 `parsePlan` 中校验参数完整性，失败则触发回退。

- `src/main/java/com/example/agent/model/ModelToolResolver.java`
  - 增加按场景注入完整 schema 的能力（规划场景使用完整 schema，其他保持 summary）。
  - 或提供可选参数控制注入模式，避免影响其他调用方。

### 3.2 执行层
- `src/main/java/com/example/agent/runtime/AgentRuntime.java`
  - 在 `stepType=TOOL` 场景增加“直达工具”分支与回退路径。
  - 增加参数完整性检查与日志记录，确保可观测性。

- `src/main/java/com/example/agent/runtime/LlmStepService.java`
  - 抽取工具总结与上下文回写逻辑，供直达分支复用。
  - 保持输出结构与原路径一致。

### 3.3 可选新增文件
- `src/main/java/com/example/agent/runtime/ToolArgumentValidator.java`
  - 负责参数完整性与类型校验，避免在运行时重复拼接规则。
  - 可结合工具 schema 或白名单进行校验。

### 3.4 配置层（如需）
- `src/main/resources/application.yml` 或 `src/main/resources/application.properties`
  - 增加可控开关，例如：是否启用直达分支、规划阶段是否使用完整 schema。

## 4. 变更边界与注意事项
- 直达分支仅在“工具名与参数完整且明确必须调用”时触发，否则保持原决策路径。
- 需要保持输出结构一致，避免影响反思与最终输出链路。
- 日志需覆盖：直达触发、参数校验失败、回退路径与工具执行结果。

## 5. 建议执行顺序
1) 先增强规划提示词与解析校验，确保参数输出质量。
2) 增加直达分支与回退路径。
3) 抽取总结与上下文回写逻辑，统一输出结构。
4) 最后引入配置开关，便于灰度与回滚。