package com.example.agent.capabilities.tools.execution;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.token.application.TokenBudgetManager;
import com.example.agent.budget.token.model.TokenUsageRecord;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.runtime.ContextRuntimeKeys;
import com.example.agent.capabilities.llm.provider.ModelRouter;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.store.RawResultStore;
import com.example.agent.capabilities.tools.mcp.McpToolCallRequest;
import com.example.agent.capabilities.tools.mcp.McpToolClient;
import com.example.agent.capabilities.tools.model.ToolDefinition;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.runtime.recovery.RetryPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import com.example.agent.capabilities.tools.registry.ToolCache;
import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.capabilities.tools.sandbox.SandboxExecutor;
import com.example.agent.capabilities.tools.execution.mapping.ToolExecutionMapper;
import com.example.agent.capabilities.tools.execution.model.ToolExecutionRequest;
import com.example.agent.capabilities.tools.execution.model.ToolExecutionResult;
import com.example.agent.capabilities.tools.execution.model.ToolInvocationArguments;
import com.example.agent.capabilities.tools.execution.service.ToolExecutionCacheService;
import com.example.agent.capabilities.tools.execution.service.ToolExecutionTracer;
import com.example.agent.capabilities.tools.execution.service.ToolInvocationService;
import com.example.agent.capabilities.tools.execution.service.ToolRawRefService;
import com.example.agent.capabilities.tools.execution.service.ToolResultAssembler;
import com.example.agent.capabilities.tools.execution.service.ToolUsageRecorder;
import com.example.agent.capabilities.tools.validation.ToolArgumentValidator;
import com.example.agent.capabilities.tools.validation.ToolRequestValidator;

/**
 * 工具执行器，负责工具调用、缓存与重试控制。
 *
 * <p>职责：统一封装工具调用流程，保证证据链与缓存一致性。</p>
 * <p>边界：异常统一映射为业务错误码并进行必要重试。</p>
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private static final int MAX_DIGEST_CHARS = 800;
    private static final int MAX_DIGEST_KEYS = 20;
    private static final String INTERNAL_EVIDENCE_PACK = "EvidencePack";
    private static final String INTERNAL_EVIDENCE_PACK_ALIAS = "_internalEvidencePack";

    /**
     * 工具注册表。
     */
    private final ToolRegistry toolRegistry;
    /**
     * MCP 工具客户端。
     */
    private final McpToolClient mcpToolClient;
    /**
     * 工具结果缓存。
     */
    private final ToolCache toolCache;
    /**
     * 沙箱执行器。
     */
    private final SandboxExecutor sandboxExecutor;
    /**
     * 计量管理器。
     */
    private final TokenBudgetManager tokenBudgetManager;
    /**
     * 模型路由器，用于计量场景。
     */
    private final ModelRouter modelRouter;
    /**
     * JSON 序列化组件。
     */
    private final ObjectMapper objectMapper;
    /**
     * 指标发布器。
     */
    private final MetricsPublisher metricsPublisher;
    /**
     * 链路追踪发布器。
     */
    private final TracingPublisher tracingPublisher;
    /**
     * 原始结果存储器。
     */
    private final RawResultStore rawResultStore;
    /**
     * 执行域对象映射器。
     */
    private final ToolExecutionMapper executionMapper;
    /**
     * 执行缓存服务。
     */
    private final ToolExecutionCacheService cacheService;
    /**
     * 执行调用服务。
     */
    private final ToolInvocationService invocationService;
    /**
     * 执行追踪服务。
     */
    private final ToolExecutionTracer executionTracer;
    /**
     * 原始引用服务。
     */
    private final ToolRawRefService rawRefService;
    /**
     * 结果组装服务。
     */
    private final ToolResultAssembler resultAssembler;
    /**
     * 计量记录服务。
     */
    private final ToolUsageRecorder usageRecorder;
    private final ToolArgumentValidator argumentValidator = new ToolArgumentValidator();

    /**
     * 是否启用工具缓存。
     */
    @Value("${agent.tool.cache.enabled:true}")
    private boolean cacheEnabled;

    /**
     * 工具缓存 TTL（秒）。
     */
    @Value("${agent.tool.cache.ttl-seconds:300}")
    private long cacheTtlSeconds;

    /**
     * 工具调用最大重试次数。
     */
    @Value("${agent.tool.retry.max-attempts:2}")
    private int maxAttempts;

    /**
     * 重试基础延迟（毫秒）。
     */
    @Value("${agent.tool.retry.base-delay-ms:100}")
    private long baseDelayMs;

    /**
     * 重试最大延迟（毫秒）。
     */
    @Value("${agent.tool.retry.max-delay-ms:1000}")
    private long maxDelayMs;

    /**
     * 重试抖动比例。
     */
    @Value("${agent.tool.retry.jitter-ratio:0.2}")
    private double jitterRatio;

    /**
     * 默认 MCP 服务端标识。
     */
    @Value("${agent.mcp.default-server-id:mcp-default}")
    private String defaultServerId;

    public ToolExecutor(ToolRegistry toolRegistry,
                        McpToolClient mcpToolClient,
                        ToolCache toolCache,
                        SandboxExecutor sandboxExecutor,
                        TokenBudgetManager tokenBudgetManager,
                        ModelRouter modelRouter,
                        ObjectMapper objectMapper,
                        MetricsPublisher metricsPublisher,
                        TracingPublisher tracingPublisher,
                        ToolExecutionMapper executionMapper,
                        ToolExecutionCacheService cacheService,
                        ToolInvocationService invocationService,
                        ToolExecutionTracer executionTracer,
                        ToolRawRefService rawRefService,
                        ToolResultAssembler resultAssembler,
                        ToolUsageRecorder usageRecorder,
                        ObjectProvider<RawResultStore> rawResultStoreProvider) {
        this.toolRegistry = toolRegistry;
        this.mcpToolClient = mcpToolClient;
        this.toolCache = toolCache;
        this.sandboxExecutor = sandboxExecutor;
        this.tokenBudgetManager = tokenBudgetManager;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
        this.metricsPublisher = metricsPublisher;
        this.tracingPublisher = tracingPublisher;
        this.executionMapper = executionMapper;
        this.cacheService = cacheService;
        this.invocationService = invocationService;
        this.executionTracer = executionTracer;
        this.rawRefService = rawRefService;
        this.resultAssembler = resultAssembler;
        this.usageRecorder = usageRecorder;
        this.rawResultStore = rawResultStoreProvider.getIfAvailable();
        if (this.rawResultStore == null) {
            log.warn("未检测到 RawResultStore 实现，工具执行输出将跳过 rawRef 存储");
        }
    }

    /**
     * 执行工具调用并返回结果。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param usageId 计量幂等键
     * @param toolName 工具名称
     * @param taskId 任务标识
     * @return 执行结果
     */
    public Map<String, Object> execute(TaskRequest request,
                                       TenantContext tenantContext,
                                       String usageId,
                                       String toolName,
                                       String taskId) {
        return executeWithArguments(request, tenantContext, usageId, toolName, taskId, null);
    }

    /**
     * 执行工具调用并返回结果（使用外部传入的参数）。
     *
     * @param request 任务请求
     * @param tenantContext 租户上下文
     * @param usageId 计量幂等键
     * @param toolName 工具名称
     * @param taskId 任务标识
     * @param toolArguments 工具调用参数
     * @return 执行结果
     */
    public Map<String, Object> executeWithArguments(TaskRequest request,
                                                    TenantContext tenantContext,
                                                    String usageId,
                                                    String toolName,
                                                    String taskId,
                                                    Map<String, Object> toolArguments) {
        ToolExecutionRequest executionRequest = executionMapper.toExecutionRequest(
                request, tenantContext, usageId, toolName, taskId);
        ToolInvocationArguments invocationArguments = executionMapper.toInvocationArguments(request, toolArguments);
        ToolExecutionResult executionResult = executeInternal(executionRequest, invocationArguments);
        return executionMapper.toResultMap(executionResult);
    }

    private ToolExecutionResult executeInternal(ToolExecutionRequest executionRequest,
                                                ToolInvocationArguments invocationArguments) {
        ToolExecutionRequest validatedRequest = ToolRequestValidator.requireNonNull(executionRequest, "executionRequest");
        TaskRequest taskRequest = ToolRequestValidator.requireNonNull(validatedRequest.getTaskRequest(), "request");
        TenantContext validatedTenant = ToolRequestValidator.requireTenantContext(validatedRequest.getTenantContext());
        String validatedToolName = ToolRequestValidator.requireNonBlank(validatedRequest.getToolName(), "toolName");
        ToolInvocationArguments safeArguments = invocationArguments == null ? new ToolInvocationArguments() : invocationArguments;
        String usageId = validatedRequest.getUsageId();
        String taskId = validatedRequest.getTaskId();
        // 解析工具名称并合并参数
        String resolvedTool = toolRegistry.resolve(validatedToolName);
        Map<String, Object> arguments = new HashMap<>(safeArguments.getValues());
        removeInternalArguments(arguments);
        arguments = validateArguments(resolvedTool, arguments);
        // 构建缓存键与 TTL
        String cacheKey = buildCacheKey(resolvedTool, arguments);
        Duration ttl = Duration.ofSeconds(Math.max(0, cacheTtlSeconds));
        String traceId = executionTracer.resolveTraceId(validatedTenant, tracingPublisher);

        if (cacheEnabled) {
            Map<String, Object> cachedOutput = cacheService.getCachedResult(toolCache, cacheKey, ttl);
            if (cachedOutput != null) {
                executionTracer.logCacheHit(log, validatedTenant.getTenantId(), resolvedTool, usageId, traceId);
                TokenUsageRecord usageRecord = usageRecorder.record(tokenBudgetManager, modelRouter, log,
                        validatedTenant, taskRequest, usageId, resolvedTool, cachedOutput, taskId, true);
                RawRef rawRef = rawRefService.store(rawResultStore, resolvedTool, cachedOutput);
                return resultAssembler.assemble(
                        resolvedTool,
                        cachedOutput,
                        toTokenUsagePayload(usageRecord),
                        true,
                        rawRefService.resolveOutputRawRef(rawRef),
                        buildDigest(cachedOutput));
            }
        }

        // 进入真实调用流程，使用重试策略保护外部依赖
        RetryPolicy retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            long startNs = System.nanoTime();
            try {
                executionTracer.logExecutionStart(log, validatedTenant.getTenantId(), resolvedTool, attempt,
                        usageId, traceId);
                McpToolCallRequest callRequest = buildCallRequest(taskRequest, resolvedTool, arguments, usageId);
                Map<String, Object> merged = invocationService.invoke(
                        sandboxExecutor,
                        mcpToolClient,
                        taskRequest,
                        validatedTenant,
                        resolvedTool,
                        callRequest,
                        arguments);

                TokenUsageRecord usageRecord = usageRecorder.record(tokenBudgetManager, modelRouter, log,
                        validatedTenant, taskRequest, usageId,
                        resolvedTool, merged, taskId, false);
                RawRef rawRef = rawRefService.store(rawResultStore, resolvedTool, merged);
                ToolExecutionResult executionResult = resultAssembler.assemble(
                        resolvedTool,
                        merged,
                        toTokenUsagePayload(usageRecord),
                        false,
                        rawRefService.resolveOutputRawRef(rawRef),
                        buildDigest(merged));

                long durationMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
                executionTracer.recordCallSuccess(metricsPublisher, traceId, durationMs);

                if (cacheEnabled) {
                    cacheService.putCachedResult(toolCache, cacheKey, merged, ttl);
                }

                executionTracer.logExecutionSuccess(log, validatedTenant.getTenantId(), resolvedTool,
                        usageId, traceId);
                return executionResult;
            } catch (ErrorCodeException ex) {
                executionTracer.recordCallFailure(metricsPublisher, traceId);
                if (isRetryable(ex) && attempt < maxAttempts) {
                    executionTracer.logRetryableError(log, validatedTenant.getTenantId(), resolvedTool,
                            attempt, ex.getErrorCode(), traceId);
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                executionTracer.logBusinessFailure(log, validatedTenant.getTenantId(), resolvedTool,
                        attempt, ex.getErrorCode(), traceId, ex);
                throw ex;
            } catch (Exception ex) {
                executionTracer.recordCallFailure(metricsPublisher, traceId);
                if (attempt < maxAttempts) {
                    executionTracer.logSystemRetry(log, validatedTenant.getTenantId(), resolvedTool,
                            attempt, traceId, ex);
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                executionTracer.logSystemFailure(log, validatedTenant.getTenantId(), resolvedTool,
                        attempt, traceId, ex);
                throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE",
                        "工具执行异常");
            }
        }
    }

    /**
     * 将计量记录转换为可序列化的输出结构。
     *
     * <p>用途：避免直接透传对象导致 {@code toString()} 结果出现在 raw 输出中（例如 {@code TokenUsageRecord@xxxx}）。</p>
     *
     * @param record 计量记录
     * @return 可序列化映射
     */
    private Map<String, Object> toTokenUsagePayload(TokenUsageRecord record) {
        if (record == null) {
            return Map.of();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("recordId", record.getRecordId());
        payload.put("usageId", record.getUsageId());
        payload.put("taskId", record.getTaskId());
        payload.put("agentId", record.getAgentId());
        payload.put("model", record.getModel());
        payload.put("provider", record.getProvider());
        payload.put("inputTokens", record.getInputTokens());
        payload.put("outputTokens", record.getOutputTokens());
        payload.put("totalTokens", record.getTotalTokens());
        payload.put("costUsd", record.getCostUsd());
        payload.put("createdAt", record.getCreatedAt() != null ? record.getCreatedAt().toString() : null);
        payload.put("tenantId", record.getTenantId());
        return payload;
    }

    /**
     * 校验并规范化工具参数。
     *
     * @param toolName 工具名称
     * @param arguments 原始参数
     * @return 校验后的参数
     */
    private Map<String, Object> validateArguments(String toolName, Map<String, Object> arguments) {
        ToolDefinition definition = resolveDefinition(toolName);
        if (definition == null || definition.getInputSchema() == null || definition.getInputSchema().isEmpty()) {
            return arguments;
        }
        return argumentValidator.validateAndNormalize(definition.getInputSchema(), arguments, toolName);
    }

    /**
     * 根据工具名称解析工具定义。
     *
     * @param toolName 工具名称
     * @return 工具定义
     */
    private ToolDefinition resolveDefinition(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        List<ToolDefinition> definitions = toolRegistry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        for (ToolDefinition definition : definitions) {
            if (definition != null && toolName.equals(definition.getName())) {
                return definition;
            }
        }
        return null;
    }

    /**
     * 构建 MCP 工具调用请求。
     *
     * @param request 任务请求
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @param usageId 计量幂等键
     * @return 调用请求
     */
    private McpToolCallRequest buildCallRequest(TaskRequest request,
                                                String toolName,
                                                Map<String, Object> arguments,
                                                String usageId) {
        McpToolCallRequest callRequest = new McpToolCallRequest();
        callRequest.setCallId(usageId);
        callRequest.setToolName(toolName);
        callRequest.setArguments(arguments);
        callRequest.setServerId(resolveServerId(request));
        return callRequest;
    }

    /**
     * 解析 MCP 服务端标识。
     *
     * @param request 任务请求
     * @return 服务端标识
     */
    private String resolveServerId(TaskRequest request) {
        if (request != null && request.getContext() != null) {
            Object serverId = request.getContext().get("mcpServerId");
            if (serverId instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return defaultServerId;
    }

    /**
     * 基于任务请求构建工具调用参数。
     *
     * @param request 任务请求
     * @return 参数映射
     */
    public Map<String, Object> buildArguments(TaskRequest request) {
        Map<String, Object> arguments = executionMapper.toInvocationArguments(request, null).getValues();
        removeInternalArguments(arguments);
        return arguments;
    }

    /**
     * 合并任务上下文与外部传入参数。
     *
     * @param request 任务请求
     * @param toolArguments 外部参数
     * @return 合并后的参数
     */
    public Map<String, Object> buildMergedArguments(TaskRequest request, Map<String, Object> toolArguments) {
        Map<String, Object> arguments = executionMapper.toInvocationArguments(request, toolArguments).getValues();
        removeInternalArguments(arguments);
        return arguments;
    }

    private void removeInternalArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        arguments.remove(ContextRuntimeKeys.EVIDENCE_PACK);
        arguments.remove(INTERNAL_EVIDENCE_PACK);
        arguments.remove(INTERNAL_EVIDENCE_PACK_ALIAS);
    }

    /**
     * 构建工具调用缓存键。
     *
     * @param toolName 工具名称
     * @param arguments 工具参数
     * @return 缓存键
     */
    public String buildCacheKey(String toolName, Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        ObjectMapper mapper = objectMapper.copy();
        mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        try {
            return toolName + ":" + mapper.writeValueAsString(safeArguments);
        } catch (JsonProcessingException ex) {
            return toolName + ":" + safeArguments.toString();
        }
    }

    /**
     * 生成工具调用结果摘要，用于证据记录。
     *
     * @param value 原始对象
     * @return 摘要字符串
     */
    private String buildDigest(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return buildMapDigest(map);
        }
        if (value instanceof List<?> list) {
            return "list(size=" + list.size() + ")";
        }
        if (value instanceof String text) {
            return truncate(text, MAX_DIGEST_CHARS);
        }
        return truncate(value.toString(), MAX_DIGEST_CHARS);
    }

    /**
     * 生成 Map 的键摘要。
     *
     * @param map 目标 Map
     * @return 摘要字符串
     */
    private String buildMapDigest(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        List<String> keys = new ArrayList<>();
        for (Object key : map.keySet()) {
            if (key == null) {
                continue;
            }
            keys.add(key.toString());
            if (keys.size() >= MAX_DIGEST_KEYS) {
                break;
            }
        }
        StringBuilder builder = new StringBuilder("keys=").append(keys);
        if (map.size() > keys.size()) {
            builder.append("...");
        }
        builder.append(",size=").append(map.size());
        return truncate(builder.toString(), MAX_DIGEST_CHARS);
    }

    /**
     * 截断文本长度，避免过长输出。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 截断后的文本
     */
    private String truncate(String value, int maxLength) {
        if (value == null || maxLength <= 0) {
            return value;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 判断异常是否可重试。
     *
     * @param ex 异常
     * @return 是否可重试
     */
    private boolean isRetryable(ErrorCodeException ex) {
        String code = ex.getErrorCode();
        return "MCP_UNAVAILABLE".equals(code)
                || "CIRCUIT_OPEN".equals(code)
                || "RATE_LIMITED".equals(code);
    }

}



