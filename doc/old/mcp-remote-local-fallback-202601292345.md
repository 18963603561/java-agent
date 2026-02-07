# 远端与本地 `MCP` 同时保留的优化方案

## 现状说明
- `ToolExecutor` 统一通过 `McpToolClient` 调用工具。
- `McpToolClient` 使用 `agent.mcp.remote-enabled` 与 `server.base-url` 决定是否走远端。
- 当 `agent.mcp.remote-enabled=true` 且 `base-url` 为 `http` 时，仅走远端。
- 本地工具仅在 `agent.mcp.remote-enabled=false` 或 `base-url` 为空时触发。

结论：当前实现为“远端或本地二选一”，无法同时保留并实现“远端优先、本地兜底”。

## 优化目标
- 保留远端 `MCP` 工具集。
- 远端不可用或工具不存在时，自动回退本地工具。
- `tools/list` 可选合并远端与本地工具清单。

## 推荐方案：远端优先 + 本地回退
### 关键行为
1. `tools/call` 先调用远端。
2. 远端返回不可用或工具不存在时，若本地存在同名工具，则回退本地执行。
3. `tools/list` 可配置合并本地与远端工具清单，便于前端展示。

### 新增配置建议
在 `application.yml` 增加可配置项：
```yaml
agent:
  mcp:
    remote-enabled: true
    fallback-to-local: true
    merge-local-tools: true
    call-strategy: remote-first
```

说明：
- `fallback-to-local` 控制是否启用回退。
- `merge-local-tools` 控制列表是否合并。
- `call-strategy` 预留扩展：`remote-only`、`local-only`、`remote-first`、`local-first`。

#### `call-strategy` 详细含义
该字段用于声明调用优先级与是否允许回退，属于策略约定，便于未来扩展。

- `remote-only`：只调用远端 `MCP`，无论是否存在本地工具都不回退。
  - 适用场景：远端为唯一可信来源，且不允许本地结果参与。
- `local-only`：只调用本地工具，不触发远端调用。
  - 适用场景：离线、内网、或本地工具语义更可信的环境。
- `remote-first`：优先远端，远端不可用或工具不存在时，按 `fallback-to-local` 决定是否回退本地。
  - 适用场景：远端为主、本地兜底。
- `local-first`：优先本地，若本地不存在或执行失败，再尝试远端。
  - 适用场景：本地低延迟优先，远端作为补充。

注意：
- `call-strategy` 仅定义“优先级顺序”，是否回退仍需结合 `fallback-to-local` 或扩展的 `fallback-to-remote` 配置决定。
- 当前代码未实现该策略，需要在 `McpToolClient` 中落地后才会生效。

### 需要修改的代码点
1. `src/main/java/com/example/agent/tools/McpToolClient.java`
   - 增加配置字段：`fallback-to-local`、`merge-local-tools`、`call-strategy`。
   - 在 `callTool` 中捕获远端异常并回退到 `callToolLocal`。
   - 在 `listTools` 中按配置合并远端与本地结果。
   - 记录回退日志，便于排查。

2. `src/main/java/com/example/agent/agentcore/ToolRegistry.java`
   - 增加 `hasTool` 或 `containsDefinition` 方法，便于判断本地是否可回退。

3. `src/main/resources/application.yml`
   - 增加上述配置项并补充说明注释。

### 异常回退触发条件建议
建议将以下错误视为回退条件：
- `MCP_UNAVAILABLE`
- `CIRCUIT_OPEN`
- 远端返回“工具不存在”类错误信息

### 风险与注意事项
- 回退可能掩盖远端故障，需要日志与指标区分远端失败与本地命中。
- 远端与本地工具语义可能不一致，需统一输入输出结构。
- 合并列表时需去重，优先保留远端版本。

## 备选方案：显式路由
由调用方在任务 `context` 内指定：
```json
{
  "mcpServerId": "mcp-default"
}
```

说明：
- 该方案不需要回退逻辑。
- 调用方需要明确选择远端或本地。

## 结论
可以同时保留远端与本地 `MCP`，并通过“远端优先、本地回退”的方式提升稳定性。
推荐在 `McpToolClient` 增加策略与回退逻辑，同时引入配置控制，以避免生产环境无感掩盖远端异常。
