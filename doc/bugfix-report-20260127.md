# 缺陷修复记录
- 日期：2026-01-27

## G-007 鉴权与租户隔离缺失
- 现象：鉴权模块缺少 `JWT` 支持与租户范围校验，且 `AuthService` 无实现导致鉴权链路不完整。
- 复现步骤：
  1. 启动应用后调用任一受保护接口，未加载 `AuthService` 实现会导致启动失败或鉴权流程异常。
  2. 使用 `Authorization: Bearer <token>` 访问接口时无法解析或校验 `JWT`。
- 期望/实际：
  - 期望：支持 `API Key` 与 `JWT` 两种鉴权路径，具备租户范围校验并输出一致错误码。
  - 实际：无 `JWT` 鉴权实现，租户范围无法限制，部分鉴权链路缺失。
- 根因：`AuthService` 实现缺失，缺少 `JWT` 解析与签名校验逻辑。
- 修复：
  - 新增 `ApiKeyAuthenticator`，支持 `API Key`、`JWT` 与可信上游鉴权路径。
  - 增加租户范围校验，租户不匹配返回 `FORBIDDEN`。
  - 失败日志统一记录 `AUTH_FAILED` 与请求上下文字段。
- 影响范围：`gateway`、`streaming` 与所有依赖 `AuthService` 的控制器。
- 关联 Shannon 参考：`vendor/Shannon/docs/authentication-and-multitenancy.md`
- 关联规范与验收：
  - `specs/001-agent-core-spec/checklists/checklist.md`：`CHK046`、`CHK047`
- 回归测试：
  - `src/test/java/com/example/agent/gateway/controller/SecurityValidationTest.java#jwtAuthUsesClaims`
  - `src/test/java/com/example/agent/gateway/controller/SecurityValidationTest.java#jwtTenantScopeMismatchReturnsForbidden`

## T-001 记忆模块缺少嵌入服务导致测试上下文失败
- 现象：启动测试上下文时提示缺少 `EmbeddingService`，导致 `OpenApiContractDriftTest` 等测试无法运行。
- 复现步骤：运行 `mvn test`，观察应用上下文加载失败。
- 期望/实际：
  - 期望：无外部嵌入服务时应使用兜底逻辑继续启动。
  - 实际：`MemoryStore` 强依赖 `EmbeddingService` 导致上下文失败。
- 根因：`MemoryStore` 直接注入 `EmbeddingService`，缺少可选依赖处理。
- 修复：`MemoryStore` 改为注入 `ObjectProvider<EmbeddingService>` 并在缺失时记录告警与走文本检索兜底。
- 影响范围：`memory` 模块与测试上下文加载。
- 关联规范与验收：无
- 回归测试：`mvn test`

## T-002 SSE 超时关闭导致订阅阻塞
- 现象：`agent.sse.timeoutSeconds=0` 时订阅接口无法及时返回或首条事件为空。
- 复现步骤：运行 `McpToolEventTest` 或 `QuickstartFlowTest`。
- 期望/实际：
  - 期望：关闭超时后 SSE 应立即建立连接并继续等待事件。
  - 实际：连接阻塞或首条事件为空导致断言失败。
- 根因：无首条事件时响应未提交，测试阻塞等待。
- 修复：
  - 关闭超时时注入一次注释心跳以提交响应。
  - 测试中过滤无数据事件后再断言。
- 影响范围：`streaming` 模块与 SSE 集成测试。
- 关联规范与验收：`specs/001-agent-core-spec/quickstart.md`
- 回归测试：
  - `src/test/java/com/example/agent/gateway/controller/McpToolEventTest.java#mcpToolCallEmitsToolEvents`
  - `src/test/java/com/example/agent/gateway/controller/QuickstartFlowTest.java#quickstartFlowCoversCoreEndpoints`
