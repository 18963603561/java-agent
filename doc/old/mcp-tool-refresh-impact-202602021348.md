# 远程 MCP 工具清单拉取路径与性能影响分析

## 1. 背景与结论
- 远程工具清单的拉取主要由工具目录查询与外部 MCP 接口触发。
- 默认实现包含缓存与 TTL，缓存命中时不会访问远端，但仍会读取缓存并写入注册表。
- 若 TTL 过短、刷新被强制或外部接口高频调用，可能导致远端频繁被访问并影响启动/请求延迟。

## 2. 远程拉取的核心入口
### 2.1 启动预热（强制刷新）
- 触发点：`McpToolStartupWarmup` 的 `ApplicationReadyEvent` 监听。
- 调用链：
  1) `McpToolStartupWarmup.warmup()`
  2) `McpToolSyncService.refreshAll(true, "startup")`
  3) `McpToolClient.listToolsRemoteOnly(...)`
- 特点：强制刷新，忽略缓存 TTL，启动时必拉一次。

### 2.2 工具目录查询触发（按需）
- 入口：`DefaultToolCatalog`
- 触发方法：
  - `listSummaries(...)`
  - `getDefinition(...)`
  - `getToolSchema(...)`
- 调用链：
  1) `DefaultToolCatalog.refreshRemoteTools(...)`
  2) `McpToolSyncService.refreshIfNeeded(...)`
  3) `McpToolSyncService.refreshAll(false, reason)`
  4) `McpToolClient.listToolsRemoteOnly(...)`（缓存过期或无缓存时）
- 特点：有 TTL 与开关控制，缓存命中则不访问远端。

### 2.3 模型工具注入触发
- 入口：`ModelToolResolver.applyTooling(...)`
- 场景：
  - 注入模式为 `summary` 时调用 `ToolCatalog.listSummaries(...)`，触发 2.2。
  - 指定工具且需要按需 schema 时调用 `ToolCatalogService.getToolSchema(...)`，触发 2.2。

### 2.4 上下文构建触发
- 入口：`DefaultContextBuilder.buildToolState(...)`
- 行为：调用 `ToolCatalog.listSummaries(...)`，触发 2.2。

### 2.5 外部 API 直接拉取
- 入口：`/api/v1/mcp/tools/list`
- 调用链：
  1) `McpController.listTools(...)`
  2) `McpToolClient.listTools(...)`
- 特点：根据策略决定远端/本地/合并，且不写入注册表，外部高频调用会直接放大远端压力。

## 3. 远程拉取的频率控制点
- `agent.mcp.tool-refresh.enabled`：关闭后工具目录查询不触发远端刷新。
- `agent.mcp.tool-refresh.ttl-seconds`：控制缓存有效期，过短会导致频繁拉取。
- `agent.mcp.tool-refresh.max-tools`：限制单次远端工具数，避免超大列表拖慢。
- `agent.mcp.remote-enabled` 与 `agent.mcp.servers[].base-url`：远端不可用时不会真正访问。

## 4. 可能的性能问题与影响
### 4.1 启动时强制拉取
- 影响：启动阶段需要等待远端响应，可能延长启动时间。
- 适用：需要启动即具备完整工具清单的场景。

### 4.2 高频请求触发的按需刷新
- 场景：
  - 上下文构建频繁调用 `listSummaries(...)`。
  - 模型工具注入在每次请求中执行。
- 风险：TTL 设置过短或无缓存时，远端请求放大。

### 4.3 外部接口直连
- 场景：外部系统高频调用 `/api/v1/mcp/tools/list`。
- 风险：远端 MCP 服务压力集中、请求延迟增加。

## 5. 文件与关键方法定位
- `src/main/java/com/example/agent/tools/McpToolStartupWarmup.java`
- `src/main/java/com/example/agent/tools/DefaultToolCatalog.java`
- `src/main/java/com/example/agent/tools/McpToolSyncService.java`
- `src/main/java/com/example/agent/tools/McpToolClient.java`
- `src/main/java/com/example/agent/model/ModelToolResolver.java`
- `src/main/java/com/example/agent/context/DefaultContextBuilder.java`
- `src/main/java/com/example/agent/gateway/controller/McpController.java`
