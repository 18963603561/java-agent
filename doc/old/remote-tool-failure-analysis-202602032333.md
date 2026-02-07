# 远程工具调用失败分析报告（2026-02-03 23:33）

## 结论摘要
- 失败不是由 MCP 服务端连接问题导致，而是客户端在本地校验阶段直接拒绝了调用。
- 具体异常为 `McpToolClient#validateServer` 抛出 `MCP_UNAVAILABLE`，说明服务配置未命中或被标记为不可用。
- 由于校验发生在调用远端之前，所以 MCP 服务端没有连接失败日志是正常现象。

## 关键日志定位
```
com.example.agent.common.ErrorCodeException: 503 SERVICE_UNAVAILABLE "MCP 服务不可用"
	at com.example.agent.tools.McpToolClient.validateServer(McpToolClient.java:1181)
	at com.example.agent.tools.McpToolClient.callTool(McpToolClient.java:230)
```
中文解释：调用在本地校验阶段即失败，未发起网络请求。

## 代码流程与原因
1. `ToolExecutor` 进入工具调用链路，最终调用 `McpToolClient#callTool`。
2. `callTool` 首行执行 `validateServer(server)`：
   - `server == null` 或 `server.available == false` 时抛出 `MCP_UNAVAILABLE`。
3. `validateServer` 未判断 `remoteEnabled`，也未等待进入远端调用分支；因此即使本地工具存在，也会先失败。

从日志与代码可推断原因属于以下几类：
- **服务端配置未命中**：`serverId` 解析后找不到对应的 `agent.mcp.servers[*].id`。
- **服务端被标记不可用**：配置中 `available=false`，或未加载正确的配置文件导致默认值被覆盖。
- **默认服务标识不一致**：`normalizeServerId` 默认返回 `mcp-default`，若配置中没有该 id，会直接失败。

## 为什么 MCP 服务端没有错误日志
- 本次异常在 `validateServer` 中抛出，未触发任何远端 HTTP 请求。
- MCP 服务端因此不会产生“连接失败”的日志。

## 优化与修复建议
### 配置层
1. 确认当前运行环境加载的是含 `agent.mcp.servers` 的配置文件。
2. 确保存在与请求匹配的 `serverId`：
   - 默认值为 `mcp-default`。
   - 若客户端请求传入了其它 `serverId`，需在配置中添加对应项。
3. 确认目标服务可用标记：
```yaml
agent:
  mcp:
    servers:
      - id: mcp-default
        available: true
        base-url: http://localhost:8088/mcp/test
```

### 代码层
1. **推迟校验位置**
   - 将 `validateServer` 放到确定需要远端调用的分支里，避免本地可用时仍提前失败。
2. **使用配置的默认服务标识**
   - `normalizeServerId` 目前硬编码 `mcp-default`，建议改为 `agent.mcp.default-server-id`。
3. **提升可观测性**
   - 在 `validateServer` 抛错前记录 `serverId`、`available` 和服务器列表长度，便于快速定位配置错误。

## 关联问题提示（模型调用异常）
日志中还出现 `executor not accepting a task`，表明 Reactor/Netty 线程池拒绝任务，多见于进程退出或线程池已关闭。
该问题独立于 MCP 调用失败，但会导致后续 LLM 回退步骤也失败。
建议排查应用是否被停止、线程池资源是否不足或阻塞。