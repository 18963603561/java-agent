# 上下文工程组件接口与数据结构草案

## 输入依据
```
doc/context-engineering-evidence-review-20260128.md
doc/ai-agent-book-part1-coverage-20260127.md
```

## 目标
- 细化组件接口与数据结构
- 明确各组件职责与边界
- 为后续任务拆分提供结构化依据

## 约束
- 不破坏既有主流程
- 新能力以扩展方式引入
- 保持多租户隔离
- 保持日志与可观测性一致性

## 包结构建议
```
com.example.agent.context
com.example.agent.model
com.example.agent.tools
com.example.agent.budget
com.example.agent.streaming
```

## 组件接口草案

### 一、上下文构建与装配
组件职责
- 统一构建六层上下文模型
- 负责装配、裁剪与输出
- 提供可复用的装配模板

接口定义
```java
public interface ContextBuilder {
    ContextBuildResult build(ContextBuildRequest request);
}

public interface ContextAssembler {
    PromptBundle assemble(ContextSnapshot snapshot, PromptTemplate template, ContextBudgetAllocation allocation);
}
```

接口说明
- 负责生成上下文快照与提示词输出
- 负责记录装配过程指标

### 二、提示词模板与组装器
组件职责
- 固定策略与动态事实分离
- 支持多角色消息结构
- 提供模板化输出

接口定义
```java
public interface PromptTemplate {
    List<PromptMessage> render(ContextSnapshot snapshot);
    String getTemplateId();
}

public interface PromptAssembler {
    PromptBundle build(ContextSnapshot snapshot, PromptTemplate template, ContextBudgetAllocation allocation);
}
```

接口说明
- 模板输出需可裁剪
- 生成的消息需可追踪来源

### 三、工具目录与按需加载
组件职责
- 工具列表摘要化
- 工具定义按需加载
- 支持工具缓存与失效

接口定义
```java
public interface ToolCatalog {
    List<ToolSummary> listSummaries(ToolQuery query);
    ToolDefinition getDefinition(String toolName);
}
```

接口说明
- 列表接口仅返回摘要信息
- 定义接口返回可执行的完整规范

### 四、预算分配与裁剪
组件职责
- 分配上下文预算
- 执行裁剪与压缩
- 输出裁剪摘要

接口定义
```java
public interface ContextBudgetAllocator {
    ContextBudgetAllocation allocate(ContextBudgetRequest request);
}

public interface ContextPruner {
    ContextPruneResult prune(ContextPruneRequest request);
}
```

接口说明
- 预算分配需可配置
- 裁剪规则需可追踪

### 五、上下文快照与事件载荷
组件职责
- 统一上下文快照结构
- 记录上下文变化
- 输出可审计事件载荷

接口定义
```java
public interface ContextEventPublisher {
    void publish(ContextEventPayload payload);
}
```

接口说明
- 事件载荷应包含上下文快照标识
- 事件载荷应包含裁剪与预算摘要

## 数据结构草案

### 上下文构建请求与结果
结构定义
```java
public class ContextBuildRequest {
    private TaskRequest taskRequest;
    private RuntimeMeta runtimeMeta;
    private RoleBoundary roleBoundary;
    private ContextPolicy policy;
    private ToolQuery toolQuery;
    private ContextBudgetRequest budgetRequest;
}

public class ContextBuildResult {
    private ContextSnapshot snapshot;
    private PromptBundle promptBundle;
    private ContextBudgetAllocation budgetAllocation;
    private ContextPruneResult pruneResult;
    private BuildMetrics metrics;
}
```

字段说明
- 第一项为任务请求输入
- 第二项为运行时元信息
- 第三项为角色边界信息
- 第四项为上下文装配策略
- 第五项为工具筛选条件
- 第六项为预算分配请求

- 第一项为上下文快照
- 第二项为提示词输出
- 第三项为预算分配结果
- 第四项为裁剪结果
- 第五项为构建指标

### 上下文快照与六层模型
结构定义
```java
public class ContextSnapshot {
    private RuntimeMeta runtimeMeta;
    private RoleBoundary roleBoundary;
    private TaskIntent taskIntent;
    private WorkingMemory workingMemory;
    private DomainKnowledge domainKnowledge;
    private LongTermMemory longTermMemory;
    private ToolState toolState;
    private BudgetState budgetState;
    private AuditMetadata auditMetadata;
}

public class RuntimeMeta {
    private String tenantId;
    private String userId;
    private String sessionId;
    private String workflowId;
    private String requestId;
    private String traceId;
    private String locale;
    private String outputFormat;
    private Integer tokenBudget;
    private List<String> allowedTools;
    private Instant requestTime;
}

public class RoleBoundary {
    private String systemPolicyId;
    private String developerPolicyId;
    private List<String> forbiddenActions;
    private List<String> dataScopes;
    private String riskLevel;
    private Boolean approvalRequired;
}

public class TaskIntent {
    private String taskId;
    private String inputText;
    private String successCriteria;
    private String failurePolicy;
    private String requiredOutput;
    private List<String> constraints;
}

public class WorkingMemory {
    private String summary;
    private List<String> keyFacts;
    private List<String> planSteps;
    private String nextStep;
    private List<ToolCallState> recentToolCalls;
    private EvidencePack evidencePack;
}

public class DomainKnowledge {
    private List<Citation> citations;
}

public class LongTermMemory {
    private List<MemoryRef> memoryRefs;
}
```

字段说明
- 结构包含运行时元信息、角色边界、任务意图、工作记忆、领域知识、长期记忆、工具状态、预算状态与审计元数据
- 运行时元信息包含租户、用户、会话、请求与追踪标识、语言偏好、输出格式、令牌预算、可用工具范围与请求时间
- 角色边界包含系统策略标识、开发者策略标识、禁用动作、数据范围、风险等级与审批要求
- 任务意图包含任务标识、输入文本、成功标准、失败处理、输出要求与约束条件
- 工作记忆包含摘要、关键事实、计划步骤、下一步、近期工具调用与证据包
- 领域知识以检索引用为主
- 长期记忆以引用为主

### 记忆引用与证据包
结构定义
```java
public class MemoryRef {
    private String memoryId;
    private String memoryType;
    private Double score;
    private String snippet;
    private Instant expiresAt;
    private String source;
}

public class EvidencePack {
    private List<EvidenceItem> items;
}

public class EvidenceItem {
    private String sourceType;
    private String sourceId;
    private String uri;
    private String title;
    private String snippet;
    private String hash;
    private Instant retrievedAt;
}
```

字段说明
- 记忆引用包含记忆标识、记忆类型、匹配分值、摘要片段、过期时间与来源
- 证据包用于记录检索证据
- 证据项包含来源类型、来源标识、位置、标题、片段、摘要校验与检索时间

### 工具状态与调用记录
结构定义
```java
public class ToolState {
    private List<ToolSummary> availableTools;
    private List<String> selectedTools;
    private ToolCallState lastCall;
    private String lastError;
    private Integer retryCount;
}

public class ToolCallState {
    private String toolName;
    private String requestId;
    private Instant startTime;
    private Instant endTime;
    private Boolean success;
    private String errorCode;
}
```

字段说明
- 工具状态包含可用摘要列表、已选工具、最近调用、最近错误与重试次数
- 调用记录包含工具名称、请求标识、起止时间、成功标识与错误码

### 提示词与消息结构
结构定义
```java
public class PromptBundle {
    private List<PromptMessage> messages;
    private String templateId;
    private Integer estimatedTokens;
    private List<String> truncatedSections;
}

public class PromptMessage {
    private PromptRole role;
    private String content;
}

public enum PromptRole {
    SYSTEM,
    DEVELOPER,
    USER
}
```

字段说明
- 提示词输出包含消息列表、模板标识、预估令牌与裁剪段落
- 消息结构包含角色与内容
- 角色取值包含系统、开发者与用户

### 工具目录结构
结构定义
```java
public class ToolQuery {
    private List<String> requiredTags;
    private List<String> allowedScopes;
    private String locale;
}

public class ToolSummary {
    private String toolName;
    private String description;
    private List<String> tags;
    private String costLevel;
    private String latencyLevel;
    private String authScope;
}

public class ToolDefinition {
    private String toolName;
    private String description;
    private String inputSchema;
    private String outputSchema;
    private String authScope;
}
```

字段说明
- 工具查询包含标签条件、授权范围与语言偏好
- 工具摘要包含名称、描述、标签、成本等级、时延等级与授权范围
- 工具定义包含名称、描述、输入规范、输出规范与授权范围

### 预算分配与裁剪结构
结构定义
```java
public class ContextBudgetRequest {
    private Integer totalTokens;
    private Integer reservedTokens;
    private ContextPolicy policy;
}

public class ContextBudgetAllocation {
    private Integer totalTokens;
    private Integer reservedTokens;
    private Map<ContextSection, Integer> sectionTokens;
}

public enum ContextSection {
    SYSTEM_POLICY,
    DEVELOPER_POLICY,
    USER_INPUT,
    WORKING_MEMORY,
    DOMAIN_KNOWLEDGE,
    LONG_TERM_MEMORY,
    TOOL_SUMMARY,
    TOOL_SCHEMA,
    EVIDENCE_PACK
}

public class ContextPruneRequest {
    private ContextSnapshot snapshot;
    private ContextBudgetAllocation allocation;
    private ContextPolicy policy;
}

public class ContextPruneResult {
    private ContextSnapshot prunedSnapshot;
    private List<PrunedItem> removedItems;
    private String summary;
}

public class PrunedItem {
    private String itemType;
    private String itemId;
    private String reason;
}
```

字段说明
- 预算请求包含总令牌、预留令牌与策略
- 预算分配包含总令牌、预留令牌与分段额度
- 分段类型用于标识上下文各部分
- 裁剪请求包含快照、预算与策略
- 裁剪结果包含裁剪后的快照、移除项与摘要
- 移除项包含类型、标识与原因

### 策略与审计元数据
结构定义
```java
public class ContextPolicy {
    private String policyId;
    private List<String> retrievalPriority;
    private List<String> pruneOrder;
    private Integer maxEvidenceCount;
    private Integer maxMemoryCount;
    private Boolean enableSensitiveMask;
}

public class BudgetState {
    private Integer allocatedTokens;
    private Integer usedTokens;
    private Integer remainingTokens;
}

public class AuditMetadata {
    private String version;
    private String source;
    private Instant createdAt;
}

public class BuildMetrics {
    private Long buildMillis;
    private Integer retrievalCount;
    private Integer toolCount;
}
```

字段说明
- 策略包含策略标识、检索优先级、裁剪顺序、证据上限、记忆上限与脱敏开关
- 预算状态包含已分配、已使用与剩余令牌
- 审计元数据包含版本、来源与时间
- 构建指标包含耗时、检索次数与工具数量

### 事件载荷结构
结构定义
```java
public class ContextEventPayload {
    private String eventId;
    private String eventType;
    private String snapshotId;
    private ContextDelta delta;
    private BudgetState budgetState;
    private ToolState toolState;
    private Instant eventTime;
}

public class ContextDelta {
    private List<String> changedSections;
    private String summary;
}
```

字段说明
- 事件载荷包含事件标识、类型、快照标识、变更摘要、预算与工具状态、事件时间
- 变更摘要包含变更段落标识与摘要说明

## 落地校验要点
- 六层上下文结构可被统一构建与裁剪
- 提示词消息支持角色分离
- 工具列表不注入完整定义
- 裁剪结果可追踪与可审计
- 事件载荷包含上下文变化摘要