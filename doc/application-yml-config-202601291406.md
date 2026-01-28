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

补充说明：内网 `MCP` `HTTP` 工具服务需要开启远程调用开关，并配置允许访问的目标主机列表，否则会拒绝远程请求。
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
        available: true
        base-url: http://mcp.internal:9999
        allowed-hosts:
          - mcp.internal
        max-response-bytes: 2097152
```
接口约定：远程服务需提供 `POST /tools/list` 获取工具列表，`POST /tools/call` 执行工具调用。

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
说明：模型列表、路由与代理配置用于控制不同能力的调用路径。`provider` 包含 `openai` 或 `deepseek` 时走 `OpenAI` 兼容接口，`endpoint` 为模型服务基础地址。
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
说明：适用于内网通过 `vLLM` 暴露 `OpenAI` 兼容接口的模型服务。`endpoint` 需包含 `/v1` 前缀，`model-id` 与 `vLLM` 启动时的模型名称一致。调用需要设置环境变量 `DEEPSEEK_API_KEY`，未启用鉴权时可设置为任意非空值。
配置示例：
```yaml
agent:
  model:
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

环境变量示例：
```bash
DEEPSEEK_API_KEY=inner-token
```

### 场景六：内网 `MCP` `HTTP` 工具服务
说明：适用于内网已有 `MCP` 服务，需要通过 `HTTP` 拉取工具列表并调用工具，`base-url` 指向服务根路径。
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
        base-url: http://mcp.internal:9999
        allowed-hosts:
          - mcp.internal
```
校验要点：可通过 `POST /api/v1/mcp/tools/list` 获取工具列表，并使用 `POST /api/v1/mcp/tools/call` 验证工具调用。

## 变更与校验建议
- 通过环境覆盖或配置中心分环境注入敏感信息。
- 修改数据库与缓存地址后应进行连通性校验。
- 对外环境应确认管理端点与白名单路径的暴露范围。
- 关闭审批时需确保所有审批相关开关一致关闭。
