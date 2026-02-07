# `MCP` 工具集区分配置与验证说明

## 结论
- 该工程已支持通过不同 `serverId` 绑定不同工具集地址
- 无需新增代码即可同时访问 `http://localhost:8088/mcp` 与 `http://localhost:8088/mcp/test`
- 仅需在 `application.yml` 增加第二个 `server` 配置并补齐 `allowed-hosts`

## 现象与服务端行为确认
- `SSE` 会话通过 `http://localhost:8088/mcp/sse` 返回 `sessionId`
- `/mcp` 与 `/mcp/test` 返回不同工具列表
- 工具集差异由请求路径决定，客户端只需切换 `base-url`

服务端返回示例
```
event:endpoint
data:/mcp?sessionId=757c13ad-4817-4987-991e-97587214506f
```

`/mcp` 工具列表示例
```
current_time
echo
jdbcstructuredHandler
```

`/mcp/test` 工具列表示例
```
current_time
search-user-by-name
search-user-custom
```

## 工程支持点说明
- `agent.mcp.servers` 支持多条配置，每条代表一个外部工具集入口
- `McpToolClient` 会在 `sse-url` 可用时自动获取 `sessionId` 并拼接到 `base-url`
- `McpController` 提供 `tools/list` 与 `tools/call` 转发接口，可指定 `serverId`
- `ToolExecutor` 支持从任务上下文读取 `mcpServerId`，未传时使用 `agent.mcp.default-server-id`

## 推荐配置方案
说明
- `sse-url` 统一指向 `/mcp/sse`
- `base-url` 分别指向 `/mcp` 与 `/mcp/test`
- `protocol` 需为 `jsonrpc`
- `allowed-hosts` 需包含实际服务地址

配置示例
```yaml
agent:
  mcp:
    remote-enabled: true
    default-server-id: mcp-default
    servers:
      - id: mcp-default
        available: true
        protocol: jsonrpc
        base-url: http://localhost:8088/mcp
        sse-url: http://localhost:8088/mcp/sse
        session-param-name: sessionId
        session-refresh-seconds: 300
        allowed-hosts:
          - localhost
          - 127.0.0.1
      - id: mcp-test
        available: true
        protocol: jsonrpc
        base-url: http://localhost:8088/mcp/test
        sse-url: http://localhost:8088/mcp/sse
        session-param-name: sessionId
        session-refresh-seconds: 300
        allowed-hosts:
          - localhost
          - 127.0.0.1
```

如需访问内网主机示例
- 将 `allowed-hosts` 增加 `192.168.50.140`
- `base-url` 与 `sse-url` 改为对应内网地址

## 调用与验证步骤
### 方式一：通过工程接口发现工具
`tools/list` 请求示例
```bash
curl -s http://localhost:8080/api/v1/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{"serverId":"mcp-default"}'
```

`tools/list` 指定工具集示例
```bash
curl -s http://localhost:8080/api/v1/mcp/tools/list \
  -H "Content-Type: application/json" \
  -d '{"serverId":"mcp-test"}'
```

### 方式二：通过任务上下文选择工具集
任务请求示例
```json
{
  "query": "帮我查询一下bob用户信息",
  "context": {
    "mcpServerId": "mcp-test"
  }
}
```

### 方式三：直接调用工具
`tools/call` 请求示例
```bash
curl -s http://localhost:8080/api/v1/mcp/tools/call \
  -H "Content-Type: application/json" \
  -d '{"serverId":"mcp-test","toolName":"search-user-by-name","arguments":{"name":"bob"}}'
```

## 常见问题与排查
- `SSE` 未获取到会话时请检查 `sse-url` 是否可达
- 返回空工具集时请确认 `serverId` 对应的 `base-url` 是否为目标工具集
- 报主机不允许访问时请检查 `allowed-hosts` 是否包含目标主机
- 连接超时可调大 `agent.mcp.http.timeout-seconds`

## 需要修改的文件
- `src/main/resources/application.yml`
  - 增加第二个 `server` 条目
  - 补充 `allowed-hosts`
  - 需要时调整 `default-server-id`