# 应用配置说明与场景示例

## 目标与范围
本文说明 `src/main/resources/application.yml` 的主要配置项含义、使用场景与示例配置。
重点覆盖内网试用关闭审批、统一接口契约、向量存储服务地址、关系型数据库与缓存服务配置。

## 配置结构概览
| 配置域 | 说明 |
| --- | --- |
| `server` | 服务端口与基础运行参数 |
| `spring` | 应用基础、自动配置、数据源与缓存 |
| `management` | 监控端点与健康探针 |
| `logging` | 日志级别控制 |
| `auth` | 接口访问鉴权与可信上游 |
| `tenant` | 白名单路径 |
| `agent` | 代理核心能力、模型、工具、记忆与安全 |

## 关键配置项说明

### 服务端口
配置片段：
```yaml
server:
  port: 8080
```
说明：对外监听端口。
场景：多实例部署时按实例区分端口。

### 应用基础与自动配置
配置片段：
```yaml
spring:
  application:
    name: java-agent
  autoconfigure:
    exclude: ""
```
说明：应用名称用于日志与监控标识。自动配置排除用于关闭不需要的组件。
场景：接入自定义实现时可排除冲突组件。

### 关系型数据库配置
配置片段：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/agent
    username: agent
    password: agent
    driver-class-name: org.postgresql.Driver
```
说明：持久化存储连接。
场景：内网部署时替换为内网数据库地址，并通过环境注入敏感信息。

### 缓存服务配置
配置片段：
```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```
说明：用于幂等缓存与工具缓存。
场景：高并发或需要幂等控制时必须指向可用缓存服务。

### 管理端点与健康探针
配置片段：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      probes:
        enabled: true
  tracing:
    sampling:
      probability: 1.0
```
说明：控制监控端点暴露范围与采样比例。
场景：对外环境应缩小暴露范围并调整采样。

### 日志级别
配置片段：
```yaml
logging:
  level:
    root: debug
    com.example.agent: debug
```
说明：控制全局与模块日志级别。
场景：内网试用可使用较高日志级别，生产建议调整为 `info`。

### 接口访问鉴权
配置片段：
```yaml
auth:
  api-keys:
    demo-key:
      user-id: demo-user
      roles:
        - ROLE_USER
  trusted-upstream:
    enabled: false
    token: change-me
    token-header: X-Trusted-Token
```
说明：可通过静态接口密钥或可信上游鉴权。
场景：内网统一网关可启用可信上游并设置固定令牌。

### 白名单路径
配置片段：
```yaml
tenant:
  whitelist-paths:
    - /actuator/health
    - /actuator/info
```
说明：白名单路径用于绕过租户或鉴权限制。
场景：健康检查与基础信息接口建议加入白名单。

### 接口契约
配置片段：
```yaml
agent:
  openapi:
    contract-path: specs/001-agent-core-spec/contracts/openapi.yaml
```
说明：接口契约文件路径用于统一接口定义。
场景：内网多团队共用统一契约时需指向共享路径。

### 运行参数与推理控制
配置片段：
```yaml
agent:
  runtime:
    max-retries: 1
    maxIterations: 10
    minIterations: 1
    observationWindow: 3
  cot:
    max-steps: 4
    temperature-override: 0.2
    model-hint: reflect
    emit-step-events: false
    max-final-answer-chars: 400
```
说明：控制重试与推理步骤等运行行为。
场景：试用环境可保持默认，生产环境按预算与时延调整。

### 能力评估与审批
配置片段：
```yaml
agent:
  capability-evaluation:
    enabled: true
    risk-threshold: 0.7
    complexity-threshold: 0.7
    force-approval-above-risk: true
    budget-threshold-tokens: 10000
    default-strategy: tool
  tool:
    approval:
      enabled: false
      high-risk-tools: []
      timeout-seconds: 300
```
说明：能力评估用于风险判断，审批用于高风险工具流程。
场景：内网试用关闭审批时需关闭审批开关并避免强制审批。

### 向量存储与记忆
配置片段：
```yaml
agent:
  memory:
    vector:
      enabled: true
      provider: qdrant
      base-url: http://localhost:6333
      collection: memory_records
      dimension: 256
      timeout-seconds: 5
    recall:
      enabled: true
      limit: 10
      min-query-length: 4
      include-compressed: true
      max-summary-chars: 800
      max-record-chars: 500
    write:
      enabled: true
      save-user-query: true
      save-final-output: true
      max-record-chars: 2000
      max-summary-chars: 500
    expire:
      enabled: true
      ttl-seconds: 2592000
      cleanup-on-read: true
      cleanup-interval-seconds: 300
```
说明：向量存储用于记忆召回与写入，过期用于存量治理。
场景：无向量库环境可整体关闭记忆相关功能。

### 工具服务与安全
配置片段：
```yaml
agent:
  mcp:
    servers:
      -
        id: mcp-default
        available: true
        protocol: rest
        base-url: http://localhost:9999
  hook:
    enabled: true
    blocked-tools: []
  sandbox:
    wasi:
      enabled: true
      blocked-tools: []
  security:
    redaction:
      enabled: true
      reject-on-secrets: true
      redact-on-pii: true
      ignore-keys: []
      max-scan-chars: 8000
```
说明：外部工具服务与沙箱用于控制工具调用，脱敏用于输出治理。
场景：对外环境建议保留沙箱与脱敏功能。

补充说明：内网 MCP 工具服务需要开启远程调用开关，并配置允许访问的目标主机列表，否则会拒绝远程请求。
协议类型通过 `protocol` 指定，支持 `rest` 与 `jsonrpc`。
示例配置：
```yaml
agent:
  mcp:
    remote-enabled: true
    default-server-id: mcp-internal
    http:
      timeout-seconds: 10
    retry:
      max-attempts: 2
      base-delay-ms: 100
      max-delay-ms: 1000
      jitter-ratio: 0.2
    servers:
      -
        id: mcp-internal
        protocol: rest
        available: true
        base-url: http://mcp.internal:9999
        allowed-hosts:
          - mcp.internal
        max-response-bytes: 2097152
```
接口约定：`rest` 模式提供 `POST /tools/list` 与 `POST /tools/call`，`jsonrpc` 模式提供 `initialize`、`tools/list`、`tools/call`。

### MCP 调用策略解析与回退场景
配置片段：
```yaml
agent:
  mcp:
    remote-enabled: true
    fallback-to-local: true
    merge-local-tools: true
    call-strategy: remote-first
```
说明：`call-strategy` 决定远端/本地优先级，`fallback-to-local` 控制远端失败是否回退本地，`merge-local-tools` 控制工具清单是否合并。  
策略解析规则：`remote-only`、`local-only`、`remote-first`、`local-first`，解析时忽略大小写并允许使用下划线或短横线。

回退逻辑说明：
1. `remote-first`：优先远端，出现远端不可用或工具不存在时，且开启 `fallback-to-local` 且本地存在同名工具，回退本地执行。
2. `local-first`：优先本地，本地执行失败或不存在时再尝试远端。
3. `remote-only`：只走远端，不回退本地。
4. `local-only`：只走本地，不触发远端调用。

回退触发条件建议：
- 远端返回 `MCP_UNAVAILABLE` 或 `CIRCUIT_OPEN`。
- 远端返回“工具不存在”类错误信息。

列表合并说明：
- 当 `merge-local-tools=true` 时，`tools/list` 会合并远端与本地工具清单，并优先保留远端版本。

使用场景示例：
1. 远端优先，本地兜底（推荐默认）
```yaml
agent:
  mcp:
    remote-enabled: true
    fallback-to-local: true
    merge-local-tools: true
    call-strategy: remote-first
```
适用：远端为主，本地作为应急兜底。

2. 本地优先，远端补充
```yaml
agent:
  mcp:
    remote-enabled: true
    fallback-to-local: false
    merge-local-tools: true
    call-strategy: local-first
```
适用：本地低延迟优先，远端仅在本地失败时调用。

3. 仅远端，禁止本地
```yaml
agent:
  mcp:
    remote-enabled: true
    fallback-to-local: false
    merge-local-tools: false
    call-strategy: remote-only
```
适用：远端为唯一可信来源，严禁本地结果介入。

4. 仅本地，离线运行
```yaml
agent:
  mcp:
    remote-enabled: false
    fallback-to-local: true
    merge-local-tools: false
    call-strategy: local-only
```
适用：离线或内网环境，无远端依赖。

### 模型与路由
配置片段：
```yaml
agent:
  model:
      models:
        planner:
          model-id: planner
          provider: local
          endpoint: local
          api-key: ""
          input-cost-usd: 0
          output-cost-usd: 0
          max-tokens: 4096
    routes:
      planner: planner
      reflect: reflect
      research: research
      cheap: cheap
    fallback-enabled: true
    fallback-model-id: cheap
  profiles:
    items:
      -
        agent-id: planner
        model-id: planner
        prompt: planner
        allow-tools:
          - demo_tool
        budget-tokens: 2000
```
说明：模型列表、路由与代理配置用于控制不同能力的调用路径。`provider` 包含 `openai` 或 `deepseek` 时走 `OpenAI` 兼容接口，`endpoint` 为模型服务基础地址。模型级密钥优先使用 `agent.model.models.<id>.api-key`，未配置时使用 `agent.model.http.api-key`。
场景：内网统一模型服务或 `vLLM` 部署时修改 `provider` 与 `endpoint`，并确保 `model-id` 与部署名称一致。

### 可靠性与资源控制
配置片段：
```yaml
agent:
  idempotency:
    redis-enabled: true
    ttl-seconds: 86400
  budget:
    enabled: true
    threshold-tokens: 10000
  rate-limit:
    max-per-minute: 100
  circuit:
    failure-threshold: 3
    open-seconds: 30
  tool:
    inject-mode: summary
    max-summaries: 200
    cache:
      redis-enabled: true
    definition-cache-ttl-seconds: 300
    schema-cache-ttl-seconds: 300
```
说明：幂等、预算、限流与熔断用于稳定性控制。
场景：高并发环境应配置合理阈值并确保缓存可用。

## 内网试用关闭审批的配置要点
1. 关闭审批开关。
2. 避免能力评估触发强制审批。
3. 保留风险评估的情况下仅关闭审批流。

示例配置：
```yaml
agent:
  capability-evaluation:
    enabled: false
    force-approval-above-risk: false
  tool:
    approval:
      enabled: false
```

## 场景配置示例

### 场景一：内网试用，关闭审批，统一接口契约与内网存储
说明：适用于内网试用，关闭审批流程，使用内网统一接口契约与存储服务。
配置示例：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://pg-internal:5432/agent
    username: agent
    password: change-me
  data:
    redis:
      host: redis-internal
      port: 6379

auth:
  trusted-upstream:
    enabled: true
    token: inner-token
    token-header: X-Trusted-Token

agent:
  openapi:
    contract-path: /data/contracts/openapi.yaml
  capability-evaluation:
    enabled: false
    force-approval-above-risk: false
  tool:
    approval:
      enabled: false
    cache:
      redis-enabled: true
  memory:
    vector:
      enabled: true
      base-url: http://vector-store:6333
      collection: memory_records
  storage:
    mode: postgres
  idempotency:
    redis-enabled: true
```

### 场景二：对外测试或生产，启用审批与风险控制
说明：适用于对外环境，启用审批与风控，收敛管理端点。
配置示例：
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info

agent:
  capability-evaluation:
    enabled: true
    force-approval-above-risk: true
  tool:
    approval:
      enabled: true
      high-risk-tools:
        - demo_tool
      timeout-seconds: 300
  rate-limit:
    max-per-minute: 60
```

### 场景三：无向量存储环境，关闭记忆功能
说明：适用于不具备向量存储服务的环境。
配置示例：
```yaml
agent:
  memory:
    vector:
      enabled: false
    recall:
      enabled: false
    write:
      enabled: false
    expire:
      enabled: false
```

### 场景四：资源受限环境，降低推理成本
说明：适用于预算受限或低成本运行场景。
配置示例：
```yaml
agent:
  cot:
    max-steps: 2
    temperature-override: 0.1
    max-final-answer-chars: 200
  model:
    fallback-enabled: true
    fallback-model-id: cheap
  budget:
    threshold-tokens: 3000
```

### 场景五：内网 `vLLM` 部署 `Qwen3` 模型
说明：适用于内网通过 `vLLM` 暴露 `OpenAI` 兼容接口的模型服务。`endpoint` 需包含 `/v1` 前缀，`model-id` 与 `vLLM` 启动时的模型名称一致。是否鉴权由 `agent.model.http.api-key` 控制，留空表示不需要鉴权。
配置示例：
```yaml
agent:
  model:
    http:
      api-key: ""
    models:
      qwen3:
        model-id: Qwen3-8B-Instruct
        provider: openai
        endpoint: http://vllm.internal:8000/v1
        input-cost-usd: 0
        output-cost-usd: 0
        max-tokens: 8192
    routes:
      planner: qwen3
      reflect: qwen3
      research: qwen3
      cheap: qwen3
```
鉴权示例：
```yaml
agent:
  model:
    http:
      api-key: inner-token
```

### 场景六：内网 MCP REST 工具服务
说明：适用于内网已有 MCP 服务，通过 REST 接口拉取工具列表并调用工具，`base-url` 指向服务根路径。
配置示例：
```yaml
agent:
  mcp:
    remote-enabled: true
    default-server-id: mcp-internal
    servers:
      -
        id: mcp-internal
        available: true
        protocol: rest
        base-url: http://mcp.internal:9999
        allowed-hosts:
          - mcp.internal
```
校验要点：可通过 `POST /api/v1/mcp/tools/list` 获取工具列表，并使用 `POST /api/v1/mcp/tools/call` 验证工具调用。

### 场景七：内网 MCP JSON-RPC 工具服务
说明：适用于内网 MCP 服务提供 JSON-RPC 协议的工具调用，`base-url` 指向 JSON-RPC 入口路径。若服务通过 SSE 下发会话标识，需要配置 `sse-url` 与 `session-param-name`，`base-url` 无需拼接 `sessionId`。
配置示例：
```yaml
agent:
  mcp:
    remote-enabled: true
    default-server-id: mcp-internal
    servers:
      -
        id: mcp-internal
        available: true
        protocol: jsonrpc
        base-url: http://localhost:8088/mcp
        sse-url: http://localhost:8088/mcp/sse
        session-param-name: sessionId
        session-refresh-seconds: 300
        allowed-hosts:
          - localhost
```
校验要点：依次调用 `initialize`、`tools/list`、`tools/call` 验证工具发现与调用链路。
示例命令：
```bash
curl -s http://192.168.50.140:8088/mcp/test \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}'

curl -s http://192.168.50.140:8088/mcp/test \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'

curl -s http://192.168.50.140:8088/mcp/test \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"fmdb_query","arguments":{"sql":"select id,name from users limit 5","params":[]}}}'
```

### 场景八：内网 `Ollama` 原生接口
说明：适用于内网 `Ollama` 服务仅开放原生接口 `/api/chat` 的场景，`provider` 需设置为 `ollama`，`endpoint` 指向服务根地址。需要鉴权时通过 `agent.model.http.api-key` 传递令牌，不需要鉴权时保持为空。工具调用需模型本身支持。
配置示例：
```yaml
agent:
  model:
    http:
      api-key: ""
    models:
      ollama:
        model-id: llama3
        provider: ollama
        endpoint: http://ollama.internal:11434
        input-cost-usd: 0
        output-cost-usd: 0
        max-tokens: 8192
    routes:
      planner: ollama
      reflect: ollama
      research: ollama
      cheap: ollama
```
鉴权示例：
```yaml
agent:
  model:
    http:
      api-key: inner-token
```

### 场景九：内网 `Ollama` `OpenAI` 兼容接口
说明：适用于内网 `Ollama` 开启 `OpenAI` 兼容接口的场景，`endpoint` 需包含 `/v1` 前缀，调用路径为 `/chat/completions`。
配置示例：
```yaml
agent:
  model:
    http:
      api-key: ""
    models:
      ollama:
        model-id: llama3
        provider: openai
        endpoint: http://ollama.internal:11434/v1
        input-cost-usd: 0
        output-cost-usd: 0
        max-tokens: 8192
    routes:
      planner: ollama
      reflect: ollama
      research: ollama
      cheap: ollama
```

### 场景十：多模型不同密钥
说明：适用于多个模型服务使用不同密钥的场景，每个模型可配置独立 `api-key` 覆盖全局密钥。
配置示例：
```yaml
agent:
  model:
    http:
      api-key: ""
    models:
      qwen3:
        model-id: Qwen3-8B-Instruct
        provider: openai
        endpoint: http://vllm.internal:8000/v1
        api-key: qwen3-token
        input-cost-usd: 0
        output-cost-usd: 0
        max-tokens: 8192
      distill:
        model-id: distill-1b
        provider: openai
        endpoint: http://distill.internal:8001/v1
        api-key: distill-token
        input-cost-usd: 0
        output-cost-usd: 0
        max-tokens: 4096
    routes:
      planner: qwen3
      reflect: qwen3
      research: qwen3
      cheap: distill
```

## 变更与校验建议
- 通过环境覆盖或配置中心分环境注入敏感信息。
- 修改数据库与缓存地址后应进行连通性校验。
- 对外环境应确认管理端点与白名单路径的暴露范围。
- 关闭审批时需确保所有审批相关开关一致关闭。
