# MCP 工具未加载问题分析报告

## 配置确认
- 配置文件：`src/main/resources/application.yml`
- 已配置 MCP 服务器地址：
  - `agent.mcp.servers[0].sse-url = http://localhost:8088/mcp/sse`
  - `agent.mcp.servers[0].base-url = http://localhost:8088/mcp`
  - `agent.mcp.servers[1].sse-url = http://localhost:8088/mcp/sse`
  - `agent.mcp.servers[1].base-url = http://localhost:8088/mcp/test`
- MCP 远程调用开关已开启：`agent.mcp.remote-enabled = true`

## 连接可用性线索
- 日志存在 MCP SSE 连接成功记录（历史时刻）：`logs/info.log` 中出现 “MCP SSE connect start / session updated”
- 说明 sse-url 在历史运行中可达，但本次并未看到与工具列表加载相关的日志。

## 工具加载流程梳理
1) 本地工具来源
- `ToolRegistry` 在构造时只注册默认工具 `demo_tool`：
  - `src/main/java/com/example/agent/agentcore/ToolRegistry.java`
- 插件工具：
  - `PluginLoader` 只会扫描 `plugins/` 目录并注册工具定义：
  - `src/main/java/com/example/agent/tools/plugin/PluginLoader.java`
  - 当前项目根目录不存在 `plugins/`，因此没有插件工具被加载。

2) 运行时工具注入路径
- `ModelToolResolver` 负责把工具注入模型请求：
  - `src/main/java/com/example/agent/model/ModelToolResolver.java`
- 其默认工具来源为 `ToolCatalog` / `ToolCatalogService`，最终走 `DefaultToolCatalog`：
  - `src/main/java/com/example/agent/tools/DefaultToolCatalog.java`
- `DefaultToolCatalog` 只从 `ToolRegistry` 读取工具定义，不会触发 MCP 远程拉取。

3) MCP 远程工具获取路径
- MCP 列表与调用仅在 `McpToolClient` 中实现：
  - `src/main/java/com/example/agent/tools/McpToolClient.java`
- 远程工具列表只在 `McpController.listTools` 调用时触发：
  - `src/main/java/com/example/agent/gateway/controller/McpController.java`
- `McpToolClient.listTools` 仅返回列表，不会把远程工具注册到 `ToolRegistry`，也不会更新 `ToolCatalog`。

## 关键问题定位
- **问题点**：系统没有在启动或规划阶段调用 `McpToolClient.listTools`，也没有把远程工具写回 `ToolRegistry` 或 `ToolCatalog`。
- **结果**：尽管 `sse-url/base-url` 已配置且 MCP 可达，模型注入与规划阶段仍只能看到本地 `demo_tool`，导致“工具未加载”。

## 代码级问题归因
1) 工具注入层只依赖本地注册表
- `ModelToolResolver.resolveTools(...)` → `DefaultToolCatalog.listSummaries(...)` → `ToolRegistry.listDefinitions()`
- 远程 MCP 工具未进入该链路。

2) MCP 列表未做同步注册
- `McpToolClient.listTools(...)` 仅返回结果，没有调用 `toolRegistry.registerDefinitions(...)`。

3) 规划阶段未触发远程工具装配
- 即使 `toolChoice=AUTO`，规划阶段仍需有“已加载工具列表”，否则无法选择具体工具。

## 结论
- 已配置 `sse-url` 与 `base-url` 并不等于“工具已加载”。
- 当前实现缺少“远程 MCP 工具 → 本地注册表/目录”的同步步骤，这是导致工具未加载的直接原因。

## 建议方向（供参考）
1) 在启动时或定时任务中调用 `McpToolClient.listTools(...)`，并将结果写入 `ToolRegistry.registerDefinitions(...)`。
2) 或在 `ToolCatalogService.listToolSummaries(...)` 内部补充远程拉取并合并。
3) 为 MCP 工具引入缓存刷新机制，避免每次注入都远程请求。