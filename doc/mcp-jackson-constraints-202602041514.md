# `MCP` 响应字符串截断原因复核与处置建议

## 结论摘要
- 现有截图所示结构属于“`JSON` 内嵌巨大 `JSON` 字符串”的典型场景，确实容易触发解析层或展示层问题。
- `Jackson` 的 `StreamReadConstraints` 在超限时会抛出异常，并非“静默截断”；若现场没有异常日志，该解释成立的前提不足。
- 结合本项目实现，默认 `maxResponseBytes` 为 2MB，未放大配置时很难触发 2,000 万字符级别的字符串限制。
- 更高概率原因是摘要层或展示层截断，需按下文步骤验证。

## 对 `ChatGPT` 说法的合理性评估
### 合理部分
- 业务返回“`JSON` 里包 `JSON` 字符串”，且内层字符串极大，这是常见风险点。
- `Jackson 2.15+` 引入了 `StreamReadConstraints`，其中包含 `maxStringLength` 限制，默认值较大但并非无限。

### 不合理或存疑部分
- “超过 `maxStringLength` 会静默截断且不报错”的说法与官方说明不一致。`StreamReadConstraints` 设计为超限触发异常；若真实触发，`ObjectMapper#readValue` 应抛异常并在本项目被转为 `MCP_UNAVAILABLE`。
- 本项目 `McpToolClient#post` 在反序列化之前就有 `maxResponseBytes` 限制，默认 2MB。除非已显式放大配置，否则无法到达 2,000 万字符级别的约束阈值。

## 结合本项目的关键约束
- `McpToolClient#post` 会在读取响应流时按 `agent.mcp.servers[].maxResponseBytes` 限制大小，默认 2MB，超限直接抛 `MCP_RESPONSE_TOO_LARGE`。
- `ObjectMapper#readValue` 反序列化失败会被捕获并转换为 `MCP_UNAVAILABLE`，正常情况下不会悄悄返回“截断字符串”。

## 更可能的截断来源
1. 摘要层截断
   - `StepOutputSummaryBuilder` 会按 `agent.summary.max-field-chars` 与 `agent.summary.max-chars` 对文本进行裁剪，并标记 `truncated=true`。
   - 如果前端或接口展示的是 `outputSummary.sample`、`stepSummary.summary` 等摘要字段，会看到明显截断。
2. 最终摘要二次截断
   - `FinalOutputService` 会对最终摘要按 `agent.final-output.prompt-summary-max-chars` 再次裁剪，默认 800 字符并追加 `...(truncated)`。
3. 展示或网关层截断
   - `Postman`、日志平台、前端渲染、反向代理都可能对大文本做展示或传输裁剪。

## 现场验证步骤
1. 从同一请求中导出原始响应文件（保存为本地文件），对比文件长度与页面展示是否一致。
2. 检查返回体中是否出现 `truncated=true` 或 `...(truncated)`。
3. 检查日志中是否出现 `MCP_RESPONSE_TOO_LARGE` 或 `MCP_UNAVAILABLE`，确认是否存在解析异常。
4. 核对配置 `agent.mcp.servers[].maxResponseBytes` 是否被提高到远超 2MB。

## 解决方案建议
### 方案一：结构层改造（推荐）
- 避免在 `JSON` 字段中再塞巨大 `JSON` 字符串，改为结构化字段返回，或将大文本落库后返回引用标识。
- 对超长字段采用分页、分段或压缩策略，降低单次响应体积。

### 方案二：放开解析约束（有风险）
- 若确需解析超长字符串，可在应用启动时统一调整 `StreamReadConstraints`。
- 同时确保 `agent.mcp.servers[].maxResponseBytes` 与网关限制同步放大，否则依然会在读取阶段被拦截。

示例配置（`Spring Boot` 方式）：
```java
@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer streamReadConstraintsCustomizer() {
        return builder -> builder.postConfigurer(mapper -> mapper.getFactory()
                .setStreamReadConstraints(StreamReadConstraints.builder()
                        .maxStringLength(100_000_000)
                        .build()));
    }
}
```

### 风险提示
- 放大 `maxStringLength` 会显著增加内存占用与拒绝服务风险，应结合请求来源可信度与压测结果谨慎设置。
