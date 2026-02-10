# `security` 包系统设计说明

## 1. 包定位与核心功能
- 包定位：`security` 是系统安全边界，覆盖认证、租户作用域校验与脱敏治理。
- 核心功能：
  - `ApiKeyAuthenticator` 支持 trusted-upstream/JWT/API-Key 三类路径。
  - `TenantResolver` 解析 `TenantContext`。
  - `AuthService` 提供认证抽象。
  - `RedactionService` 统一敏感信息识别与掩码处理。
  - `RedactionStage` 区分 WRITE 与 RECALL 阶段策略。

## 2. 分层线框图
```mermaid
graph TB
    A[请求头] --> B[TenantResolver]
    A --> C[ApiKeyAuthenticator]
    C --> D[UserContext]
    B --> E[TenantContext]
    D --> F[业务上下文]
    E --> F
    G[文本输入] --> H[RedactionService]
    H --> I[RedactionResult]
```

## 3. 关键流程图
```mermaid
graph TB
    A[请求进入] --> B[解析租户]
    B --> C[trusted upstream 校验]
    C -->|未命中| D[JWT 校验]
    D -->|未命中| E[API Key 校验]
    E --> F{认证通过?}
    F -->|否| X[UNAUTHORIZED]
    F -->|是| G[tenant scope 校验]
    G -->|失败| Y[FORBIDDEN]
    G -->|通过| H[写入用户/租户上下文]
```

## 4. 状态流转图
```mermaid
graph TB
    S0[AUTH_INIT] --> S1[TRUSTED_UPSTREAM_CHECK]
    S1 -->|命中| S4[AUTH_SUCCESS]
    S1 -->|未命中| S2[JWT_CHECK]
    S2 -->|成功| S4
    S2 -->|失败| S3[APIKEY_CHECK]
    S3 -->|成功| S4
    S3 -->|失败| S5[AUTH_FAILED]
    S4 --> S6[TENANT_SCOPE_VALIDATING]
    S6 -->|通过| S7[AUTHORIZED]
    S6 -->|拒绝| S8[FORBIDDEN]
    R0[WRITE_STAGE] --> R1[MASKED_OR_REJECTED]
    R2[RECALL_STAGE] --> R3[MASKED_OR_PASS]
```

## 5. 类职责与设计原因
- `ApiKeyAuthenticator`：统一认证行为，避免多入口不一致。
- `TenantResolver`：提前建立租户边界。
- `AuthService`：抽象认证接口，便于扩展。
- `RedactionService`：统一敏感数据治理规则。
- `RedactionProperties`：策略配置化，支持分环境调优。

## 6. 关键类直接关系图
```mermaid
graph TB
    A[AuthService] --> B[ApiKeyAuthenticator]
    B --> C[ApiKeyProperties]
    B --> D[JwtProperties]
    E[TenantResolver] --> F[TenantContext]
    G[RedactionService] --> H[RedactionProperties]
    G --> I[RedactionStage]
    G --> J[RedactionResult]
```

## 7. 重点说明
- 认证优先级链路明确，兼容网关与直连场景。
- 作用域校验 + 脱敏形成双重安全防线。
- 所有图统一使用 `graph TB`。

