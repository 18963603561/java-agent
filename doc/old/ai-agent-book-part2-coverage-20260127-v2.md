# 工具与扩展功能满足性分析报告（证据补齐复核版）- 20260127

## 说明与范围
- 约束：仅分析与产出文档，不修改代码，不执行测试。
- 复核对象：`doc/ai-agent-book-part2-coverage-20260127.md`
- 规格权威：`specs/001-agent-core-spec/spec.md`、`specs/001-agent-core-spec/contracts/openapi.yaml`、`specs/001-agent-core-spec/quickstart.md`、`specs/001-agent-core-spec/checklists/checklist.md`
- 参考材料：`doc/shannon-flow-alignment-20260126.md`、`doc/shannon-alignment-verification-20260127.md`
- 对照来源：`vendor/Shannon/**`
- 检索范围：`src/main/java`、`src/test/java`（全量关键词/类名/包名检索）

## 全量检索关键词
### 工具调用
```
ToolExecutor
ToolRegistry
McpToolDefinition
McpToolClient
EnforcementGateway
SandboxExecutor
WasiSandboxExecutor
RateLimitService
CircuitBreakerManager
TokenBudgetManager
```

### 模型上下文协议要素（`MCP`）
```
McpController
McpToolListRequest
McpToolCallRequest
McpServerProperties
tools/list
tools/call
jsonrpc
initialize
resources
stdio
```

### `Skill` 系统
```
SkillDefinition
SkillRegistry
SkillProperties
SkillRoute
agent.skills
```

### `Hook` 与事件
```
HookManager
HookProperties
HookRecord
EventStreamService
EventLogService
StreamEvent
EventType
SseStreamController
TimelineController
ReplayService
```

### 审批与流程控制
```
APPROVAL_REQUESTED
APPROVAL_DECISION
pause
resume
cancel
```

### 插件
```
plugin
plugins
```

## 证据补齐复核清单
| 序号 | 原结论 | 复核状态 | 证据与说明 |
| --- | --- | --- | --- |
| 1 | 模型请求仅包含提示词与场景，缺少函数调用式工具定义注入与工具选择约束。 | `Confirmed Missing` | 本项目仅见 `src/main/java/com/example/agent/model/ModelRequest.java` 的 `prompt/scene` 与 `src/main/java/com/example/agent/common/TaskRequest.java` 的 `query/context`，`src/main/java/com/example/agent/model/DefaultModelProvider.java` 仅提交 `messages`；检索关键词：`toolChoice`、`functionCall`、`toolDefinitions`，范围：`src/main/java`、`src/test/java` 未命中。 |
| 2 | 工具定义的元数据不包含危险级别、成本、超时、限流等字段，参数模式未用于校验与类型纠正。 | `Confirmed Missing` | `src/main/java/com/example/agent/tools/McpToolDefinition.java` 仅包含 `name/version/description/inputSchema/outputSchema/tags`；`inputSchema/outputSchema` 仅在定义处出现（检索关键词：`inputSchema`、`outputSchema`，范围：`src/main/java`、`src/test/java`），未见校验逻辑。 |
| 3 | 仅命中策略与定时任务相关字段，未发现协议要素实现。 | `Confirmed Missing` | 检索关键词：`jsonrpc`、`initialize`、`resources`、`stdio`，范围：`src/main/java`、`src/test/java` 未命中；`initialize` 仅出现在调度初始化逻辑中（`src/main/java/com/example/agent/scheduler/ScheduleEngine.java`）。 |
| 4 | 未实现标准协议消息格式、初始化握手、资源读取与订阅等能力。 | `Confirmed Missing` | 同上，未发现对应协议字段与消息流程实现；当前仅见 `tools/list` 与 `tools/call` 的简化实现。 |
| 5 | 仅出现定义与配置加载，未发现运行时使用。 | `Confirmed Missing` | `Skill` 相关类仅在 `src/main/java/com/example/agent/tools/skill/**` 中出现，检索 `SkillDefinition/SkillRegistry/agent.skills` 在 `src/main/java`、`src/test/java` 无运行时调用命中。 |
| 6 | 未见技能与提示词、工具白名单、参数上限或模型选择的运行时绑定。 | `Confirmed Missing` | `ModelRequest` 与运行时路径无 `Skill` 绑定字段；`Skill` 相关类未被 `runtime`、`planning`、`model` 模块引用。 |
| 7 | 审批相关仅出现事件枚举与定时任务接口，未发现任务执行流程控制实现。 | `Confirmed Missing` | `src/main/java/com/example/agent/domain/event/EventType.java` 存在 `APPROVAL_REQUESTED/APPROVAL_DECISION`；检索 `Approval/approve/审批` 未命中；`pause/resume/cancel` 仅出现在 `src/main/java/com/example/agent/gateway/controller/ScheduleController.java` 与 `src/main/java/com/example/agent/scheduler/ScheduleManager.java`。 |
| 8 | 缺少任务执行流程的暂停、恢复、取消与人工审批控制。 | `Confirmed Missing` | 任务运行链路未见 `pause/resume/cancel` 入口与状态机控制，检索关键词：`pause`、`resume`、`cancel` 在 `runtime/orchestrator` 范围内无命中。 |
| 9 | 未发现插件目录、插件注册或扩展分发机制。 | `Confirmed Missing` | 检索关键词：`plugin`、`plugins`，范围：`src/main/java`、`src/test/java` 未命中。 |

## 关联模块矩阵（证据补齐）
| 模块 | 本项目证据链接 | `Shannon` 证据链接 | 证据状态 |
| --- | --- | --- | --- |
| 工具调用基础 | `src/main/java/com/example/agent/agentcore/ToolExecutor.java#execute`、`src/main/java/com/example/agent/agentcore/ToolRegistry.java#listDefinitions`、`src/main/java/com/example/agent/agentcore/EnforcementGateway.java#execute` | `vendor/Shannon/python/llm-service/llm_service/api/tools.py`、`vendor/Shannon/go/orchestrator/internal/activities/stream_events.go` | `Found Evidence` |
| `MCP` 协议接口 | `src/main/java/com/example/agent/gateway/controller/McpController.java#listTools`、`src/main/java/com/example/agent/gateway/controller/McpController.java#callTool`、`src/main/java/com/example/agent/tools/McpToolClient.java#listTools`、`src/main/java/com/example/agent/tools/McpToolClient.java#callTool` | `vendor/Shannon/python/llm-service/llm_service/api/tools.py`、`vendor/Shannon/python/llm-service/llm_service/mcp_client.py` | `Found Evidence` |
| `Skill` 系统 | `src/main/java/com/example/agent/tools/skill/SkillRegistry.java#listDefinitions`、`src/main/java/com/example/agent/tools/skill/SkillDefinition.java` | `vendor/Shannon/go/orchestrator/internal/activities/p2p.go` | `Found Evidence` |
| `Hook` 与事件 | `src/main/java/com/example/agent/tools/hook/HookManager.java#preTool`、`src/main/java/com/example/agent/tools/hook/HookManager.java#postTool`、`src/main/java/com/example/agent/streaming/EventStreamService.java#stream` | `vendor/Shannon/docs/event-types.md`、`vendor/Shannon/go/orchestrator/internal/activities/stream_events.go` | `Found Evidence` |
| 审批与流程控制 | `src/main/java/com/example/agent/domain/event/EventType.java`、`src/main/java/com/example/agent/gateway/controller/ScheduleController.java#pause`、`src/main/java/com/example/agent/gateway/controller/ScheduleController.java#resume` | `vendor/Shannon/go/orchestrator/internal/activities/human_intervention.go`、`vendor/Shannon/go/orchestrator/internal/activities/stream_events.go` | `Confirmed Missing` |
| 插件与扩展分发 | `Confirmed Missing`（检索关键词：`plugin`、`plugins`；范围：`src/main/java`、`src/test/java`） | `vendor/Shannon/python/llm-service/llm_service/tools/plugin_loader.py` | `Confirmed Missing` |

## Evidence Index
| 模块 | 本项目类/方法 | 关联事件/错误码 | 关联 `Shannon` 位置 |
| --- | --- | --- | --- |
| 工具调用基础 | `src/main/java/com/example/agent/agentcore/ToolExecutor.java#execute`、`src/main/java/com/example/agent/agentcore/EnforcementGateway.java#execute` | `TOOL_INVOKED`、`TOOL_OBSERVATION`、`TOOL_ERROR`、`MCP_UNAVAILABLE`、`CIRCUIT_OPEN`、`RATE_LIMITED`、`SANDBOX_DENIED` | `vendor/Shannon/go/orchestrator/internal/activities/stream_events.go`、`vendor/Shannon/python/llm-service/llm_service/mcp_client.py` |
| `MCP` 协议接口 | `src/main/java/com/example/agent/gateway/controller/McpController.java#listTools`、`src/main/java/com/example/agent/gateway/controller/McpController.java#callTool`、`src/main/java/com/example/agent/tools/McpToolClient.java#listTools`、`src/main/java/com/example/agent/tools/McpToolClient.java#callTool` | `MCP_UNAVAILABLE`、`CIRCUIT_OPEN`、`HOOK_BLOCKED` | `vendor/Shannon/python/llm-service/llm_service/api/tools.py` |
| `Skill` 系统 | `src/main/java/com/example/agent/tools/skill/SkillRegistry.java#listDefinitions`、`src/main/java/com/example/agent/tools/skill/SkillDefinition.java` | 无 | `vendor/Shannon/go/orchestrator/internal/activities/p2p.go` |
| `Hook` 与事件 | `src/main/java/com/example/agent/tools/hook/HookManager.java#preTool`、`src/main/java/com/example/agent/tools/hook/HookManager.java#postTool`、`src/main/java/com/example/agent/tools/hook/HookManager.java#preStep`、`src/main/java/com/example/agent/tools/hook/HookManager.java#postStep` | `HOOK_PRE_TOOL`、`HOOK_POST_TOOL`、`HOOK_PRE_STEP`、`HOOK_POST_STEP`、`HOOK_BLOCKED` | `vendor/Shannon/docs/event-types.md` |
| 审批与流程控制 | `src/main/java/com/example/agent/domain/event/EventType.java`（仅枚举） | `APPROVAL_REQUESTED`、`APPROVAL_DECISION`（未落地） | `vendor/Shannon/go/orchestrator/internal/activities/human_intervention.go`、`vendor/Shannon/go/orchestrator/internal/activities/stream_events.go` |
| 插件与扩展分发 | `Confirmed Missing`（检索关键词：`plugin`、`plugins`；范围：`src/main/java`、`src/test/java`） | 无 | `vendor/Shannon/python/llm-service/llm_service/tools/plugin_loader.py` |

## 缺陷清单（重新评级）
### `P0`
- 无

### `P1`
#### `P1-01` 函数调用式工具定义与工具选择约束缺失
- 影响：模型无法基于工具定义与参数约束进行工具选择，只能依赖固定规则或上下文约定。
- 本项目证据：`src/main/java/com/example/agent/model/ModelRequest.java`、`src/main/java/com/example/agent/common/TaskRequest.java`、`src/main/java/com/example/agent/model/DefaultModelProvider.java`
- `Shannon` 证据：`vendor/Shannon/python/llm-service/llm_service/api/tools.py`
- 关联规范：`specs/001-agent-core-spec/spec.md`（`FR-020`，`Skill` 需包含 `schema/routes/constraints`）；`specs/001-agent-core-spec/contracts/openapi.yaml`（`McpToolDefinition` 含 `inputSchema/outputSchema`）

#### `P1-02` `MCP` 调用缺少域名白名单与响应大小限制
- 影响：外部调用缺少域名约束与响应大小防护，存在安全与稳定性风险。
- 本项目证据：`src/main/java/com/example/agent/tools/McpToolClient.java#post`、`src/main/java/com/example/agent/tools/McpServerProperties.java`
- `Shannon` 证据：`vendor/Shannon/python/llm-service/llm_service/mcp_client.py`
- 关联规范：`specs/001-agent-core-spec/spec.md`（`MCP` 调用必须支持域名白名单、超时、熔断与响应大小限制）

#### `P1-03` `Skill` 注册表未接入运行时
- 影响：`Skill` 无法参与模型选择或工具路由，`schema/constraints` 仅停留在配置层。
- 本项目证据：`src/main/java/com/example/agent/tools/skill/SkillRegistry.java#listDefinitions`、`src/main/java/com/example/agent/tools/skill/SkillDefinition.java`
- `Shannon` 证据：`vendor/Shannon/go/orchestrator/internal/activities/p2p.go`
- 关联规范：`specs/001-agent-core-spec/spec.md`（`Skill` 注册需包含 `name/version/schema/routes/constraints`，支持灰度与降级）

#### `P1-04` 任务级审批与流程控制缺失
- 影响：无法在任务执行链路中实现人工审批、暂停、恢复与取消控制。
- 本项目证据：`src/main/java/com/example/agent/domain/event/EventType.java`（仅枚举）、`src/main/java/com/example/agent/gateway/controller/ScheduleController.java#pause`
- `Shannon` 证据：`vendor/Shannon/go/orchestrator/internal/activities/human_intervention.go`、`vendor/Shannon/go/orchestrator/internal/activities/stream_events.go`
- 关联规范：`specs/001-agent-core-spec/spec.md`（事件类型 `APPROVAL_REQUESTED/APPROVAL_DECISION`）

### `P2`
#### `P2-01` 工具参数 `schema` 未用于校验与类型纠正
- 影响：工具调用缺少参数约束与类型纠正，可能放大运行时错误与数据污染风险。
- 本项目证据：`src/main/java/com/example/agent/tools/McpToolDefinition.java`（`inputSchema/outputSchema` 仅定义未使用）
- `Shannon` 证据：`vendor/Shannon/python/llm-service/llm_service/api/tools.py`（`parameters` 与 `ToolSchemaResponse`）
- 关联规范：`specs/001-agent-core-spec/contracts/openapi.yaml`（`McpToolDefinition` 定义 `inputSchema/outputSchema`）

#### `P2-02` `Hook` 执行缺少超时降级与顺序配置
- 影响：`Hook` 阻断与放行缺少超时与顺序控制，审计记录仅内存保存。
- 本项目证据：`src/main/java/com/example/agent/tools/hook/HookManager.java`、`src/main/java/com/example/agent/tools/hook/HookProperties.java`
- `Shannon` 证据：`vendor/Shannon/go/orchestrator/internal/activities/stream_events.go`
- 关联规范：`specs/001-agent-core-spec/spec.md`（`Hook` 支持阻断/放行策略与超时降级，执行顺序可配置并写入审计记录）

#### `P2-03` 插件化扩展与分发机制缺失
- 影响：无法按插件方式扩展工具或实现动态分发。
- 本项目证据：`Confirmed Missing`（检索关键词：`plugin`、`plugins`；范围：`src/main/java`、`src/test/java`）
- `Shannon` 证据：`vendor/Shannon/python/llm-service/llm_service/tools/plugin_loader.py`
- 关联规范：`specs/001-agent-core-spec/spec.md`（工具扩展模块约束，规范未显式定义插件形态）

## 结论
- 证据补齐后，工具调用链路与 `MCP` 基础接口实现具备明确代码证据，`Skill` 与 `Hook` 仅停留在配置与基础触发层。
- 关键缺口集中在工具选择与 `schema` 校验、`MCP` 安全控制、`Skill` 运行时绑定，以及任务级审批与流程控制能力。
