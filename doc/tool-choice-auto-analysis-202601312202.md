# toolChoice=AUTO 未调用工具问题分析报告

## 结论概述
- 默认加载的工具仅包含 `demo_tool`（来自 ToolRegistry 默认注册），插件工具未加载、MCP 远程工具默认关闭。
- 本次请求虽为 toolChoice=AUTO，但规划阶段走了“规则规划”且未指定 toolName，运行时检测到工具名为空，直接转为大模型步骤执行。
- 日志与响应均显示本次流程未产生工具调用（toolCallsCount=0，toolName=null）。

## 复现信息
- 请求文件：`logs/request-20260131-auto.json`
- 关键响应字段（节选）：
  - steps[0].type = "TOOL"
  - steps[0].output.outputSummary.sample 中包含 “source: llm” 与 “steps为空”
  - finalOutput 明确说明“未执行/无数据/缺少步骤输出”
- 工作流：workflowId = d4e3a38d-f00e-4760-b771-f4beeac38d27

## 默认工具加载确认
### 代码层确认
- 默认工具注册：`src/main/java/com/example/agent/agentcore/ToolRegistry.java`
  - registerDefaults() 仅注册 `demo_tool`。
- 插件加载器：`src/main/java/com/example/agent/tools/plugin/PluginLoader.java`
  - 需要 `plugins/` 目录与 `plugin.json` 描述文件；当前项目根目录不存在 `plugins/`，因此无额外工具加载。
- MCP 远程工具默认关闭：`src/main/resources/application.yml`
  - `agent.mcp.enabled: false`

### 结论
默认可用工具仅为 `demo_tool`，且与“查询包含 h 的用户信息”不匹配。

## 是否发生工具调用
### 日志证据
- EvidencePack 统计显示 toolCallsCount=0：`logs/info.log`
  - 2026-01-31 22:00:54.369 记录 evidence finalize，toolCallsCount=0。
- 运行时明确降级为大模型步骤：`logs/info.log`
  - 2026-01-31 22:00:54.667 记录“未指定工具, 转为大模型步骤”。
- 步骤摘要记录 toolName=null：`logs/info.log`
  - 2026-01-31 22:01:02.608 记录 stepType=TOOL, toolName=null。

### 响应证据
- steps[0] 的输出 sample 显示 `source: llm`，说明该步输出来自大模型而非工具执行。

## 未调用工具的原因分析
1) 规划阶段未启用 LLM 规划，走规则规划
- 配置：`src/main/resources/application.yml`
  - `agent.planner.llm-enabled: false`
- 规则规划逻辑：`src/main/java/com/example/agent/planning/PlannerService.java`
  - buildHeuristicPlan() 在未禁用工具时创建 TOOL 步骤，但不会自动选择工具名。

2) 请求未指定 tool/toolName
- 本次请求仅设置 toolChoice.mode=AUTO，未指定 `tool` / `toolName`。
- 规则规划生成的 TOOL 步骤中缺少 toolName。

3) 运行时工具名为空，直接降级为 LLM
- 执行逻辑：`src/main/java/com/example/agent/runtime/AgentRuntime.java`
  - resolveToolName(...) 返回空 → executeStep(...) 记录“未指定工具, 转为大模型步骤”。

## 结论（问题确认）
- 本次请求未触发任何实际工具调用，原因是：
  - 仅默认 `demo_tool` 可用，且未与业务查询绑定；
  - 规划阶段未启用 LLM 规划，规则规划不会自动挑选工具；
  - 请求未指定 toolName，导致运行时降级为大模型步骤。

## 如需触发工具调用的可选方向
1) 在请求中显式指定工具（如 toolChoice=SPECIFIED + toolName）。
2) 启用 LLM 规划并提供可用工具清单，让模型选择工具。
3) 引入与“用户查询”相关的真实工具定义（通过插件或 MCP）。