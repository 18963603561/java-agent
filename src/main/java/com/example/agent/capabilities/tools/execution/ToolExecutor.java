package com.example.agent.capabilities.tools.execution;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.token.TokenBudgetManager;
import com.example.agent.budget.token.TokenUsageInput;
import com.example.agent.budget.token.TokenUsageRecord;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.ModelDefinition;
import com.example.agent.capabilities.llm.ModelRouter;
import com.example.agent.capabilities.llm.ModelScene;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.store.RawResultStore;
import com.example.agent.capabilities.tools.mcp.McpToolCallRequest;
import com.example.agent.capabilities.tools.mcp.McpToolCallResponse;
import com.example.agent.capabilities.tools.mcp.McpToolClient;
import com.example.agent.capabilities.tools.mcp.McpToolDefinition;
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
import com.example.agent.capabilities.tools.validation.ToolArgumentValidator;

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
    private static final String CONTEXT_EVIDENCE_PACK = "evidencePack";
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
        return executeInternal(request, tenantContext, usageId, toolName, taskId, null);
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
        return executeInternal(request, tenantContext, usageId, toolName, taskId, toolArguments);
    }

    private Map<String, Object> executeInternal(TaskRequest request,
                                                TenantContext tenantContext,
                                                String usageId,
                                                String toolName,
                                                String taskId,
                                                Map<String, Object> toolArguments) {
        // 解析工具名称并合并参数
        String resolvedTool = toolRegistry.resolve(toolName);
        Map<String, Object> arguments = toolArguments == null
                ? buildArguments(request)
                : buildMergedArguments(request, toolArguments);
        arguments = validateArguments(resolvedTool, arguments);
        // 构建缓存键与 TTL
        String cacheKey = buildCacheKey(resolvedTool, arguments);
        Duration ttl = Duration.ofSeconds(Math.max(0, cacheTtlSeconds));

        if (cacheEnabled) {
            // 缓存命中直接返回结果
            Object cached = toolCache.getIfFresh(cacheKey, ttl);
            if (cached instanceof Map<?, ?> cachedMap) {
                log.info("工具缓存命中, tenantId={}, tool={}, usageId={}, traceId={}",
                        tenantContext.getTenantId(), resolvedTool, usageId, resolveTraceId(tenantContext));
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedOutput = (Map<String, Object>) cachedMap;
                TokenUsageRecord usageRecord = recordUsage(tenantContext, request, usageId,
                        resolvedTool, cachedOutput, taskId, true);
                Map<String, Object> response = new HashMap<>();
                response.put("tool", resolvedTool);
                response.put("result", cachedOutput);
                response.put("tokenUsage", toTokenUsagePayload(usageRecord));
                response.put("cacheHit", true);
                RawRef rawRef = storeRawRef(resolvedTool, cachedOutput);
                response.put("rawRef", resolveOutputRawRef(rawRef));
                response.put("resultDigest", buildDigest(cachedOutput));
                return response;
            }
        }

        // 进入真实调用流程，使用重试策略保护外部依赖
        RetryPolicy retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            long startNs = System.nanoTime();
            try {
                log.info("工具执行开始, tenantId={}, tool={}, attempt={}, usageId={}, traceId={}",
                        tenantContext.getTenantId(), resolvedTool, attempt, usageId,
                        resolveTraceId(tenantContext));
                // 先执行沙箱任务，再执行 MCP 工具调用
                //SandboxResult sandboxResult = sandboxExecutor.execute(resolvedTool, request, tenantContext, arguments);
                McpToolCallRequest callRequest = buildCallRequest(request, resolvedTool, arguments, usageId);
                McpToolCallResponse callResponse = mcpToolClient.callTool(callRequest, tenantContext);
                Map<String, Object> toolResult = callResponse != null ? callResponse.getResult() : null;
                Map<String, Object> merged = new HashMap<>();
                if (toolResult != null) {
                    merged.putAll(toolResult);
                }
/*                if (sandboxResult != null && sandboxResult.getOutput() != null) {
                    merged.put("sandbox", sandboxResult.getOutput());
                }
                if (sandboxResult != null && sandboxResult.getStatus() != null) {
                    merged.put("sandboxStatus", sandboxResult.getStatus());
                }*/

                TokenUsageRecord usageRecord = recordUsage(tenantContext, request, usageId,
                        resolvedTool, merged, taskId, false);
                Map<String, Object> response = new HashMap<>();
                response.put("tool", resolvedTool);
                response.put("result", merged);
                response.put("tokenUsage", toTokenUsagePayload(usageRecord));
                response.put("cacheHit", false);
                RawRef rawRef = storeRawRef(resolvedTool, merged);
                response.put("rawRef", resolveOutputRawRef(rawRef));
                response.put("resultDigest", buildDigest(merged));

                long durationMs = Duration.ofNanos(System.nanoTime() - startNs).toMillis();
                metricsPublisher.increment("tool.call.count", resolveTraceId(tenantContext));
                metricsPublisher.recordTime("tool.call.latency.ms", durationMs,
                        resolveTraceId(tenantContext));

                if (cacheEnabled) {
                    // 成功结果写回缓存
                    toolCache.put(cacheKey, merged, ttl);
                }

                log.info("工具执行完成, tenantId={}, tool={}, usageId={}, traceId={}",
                        tenantContext.getTenantId(), resolvedTool, usageId,
                        resolveTraceId(tenantContext));
                return response;
            } catch (ErrorCodeException ex) {
                metricsPublisher.increment("tool.call.failure.count", resolveTraceId(tenantContext));
                if (isRetryable(ex) && attempt < maxAttempts) {
                    log.warn("工具执行可重试, tenantId={}, tool={}, attempt={}, errorCode={}, traceId={}",
                            tenantContext.getTenantId(), resolvedTool, attempt, ex.getErrorCode(),
                            resolveTraceId(tenantContext));
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("工具执行失败, tenantId={}, tool={}, attempt={}, errorCode={}, traceId={}",
                        tenantContext.getTenantId(), resolvedTool, attempt, ex.getErrorCode(),
                        resolveTraceId(tenantContext), ex);
                throw ex;
            } catch (Exception ex) {
                metricsPublisher.increment("tool.call.failure.count", resolveTraceId(tenantContext));
                if (attempt < maxAttempts) {
                    log.warn("工具执行异常可重试, tenantId={}, tool={}, attempt={}, traceId={}",
                            tenantContext.getTenantId(), resolvedTool, attempt,
                            resolveTraceId(tenantContext), ex);
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("工具执行异常, tenantId={}, tool={}, attempt={}, traceId={}",
                        tenantContext.getTenantId(), resolvedTool, attempt,
                        resolveTraceId(tenantContext), ex);
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
        McpToolDefinition definition = resolveDefinition(toolName);
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
    private McpToolDefinition resolveDefinition(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return null;
        }
        List<McpToolDefinition> definitions = toolRegistry.listDefinitions();
        if (definitions == null || definitions.isEmpty()) {
            return null;
        }
        for (McpToolDefinition definition : definitions) {
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
        Map<String, Object> arguments = new HashMap<>();
        if (request != null) {
            arguments.put("query", request.getQuery());
            if (request.getContext() != null) {
                arguments.putAll(request.getContext());
            }
        }
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
        Map<String, Object> arguments = buildArguments(request);
        if (toolArguments != null && !toolArguments.isEmpty()) {
            arguments.putAll(toolArguments);
        }
        removeInternalArguments(arguments);
        return arguments;
    }

    private void removeInternalArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return;
        }
        arguments.remove(CONTEXT_EVIDENCE_PACK);
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
     * 保存原始结果并返回引用。
     */
    private RawRef storeRawRef(String toolName, Map<String, Object> result) {
        if (rawResultStore == null || result == null || result.isEmpty()) {
            return null;
        }
        return rawResultStore.store(toolName, result, "application/json");
    }

    /**
     * 解析输出使用的 rawRef 字段值。
     *
     * <p>优先返回 refId，兼容回退 key。
     *
     * @param rawRef 原始引用对象
     * @return 输出引用
     */
    private String resolveOutputRawRef(RawRef rawRef) {
        if (rawRef == null) {
            return null;
        }
        if (rawRef.getRefId() != null && !rawRef.getRefId().isBlank()) {
            return rawRef.getRefId();
        }
        if (rawRef.getKey() != null && !rawRef.getKey().isBlank()) {
            return rawRef.getKey();
        }
        return null;
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

    /**
     * 记录工具调用的计量信息。
     *
     * @param tenantContext 租户上下文
     * @param request 任务请求
     * @param usageId 计量幂等键
     * @param toolName 工具名称
     * @param output 工具输出
     * @param taskId 任务标识
     * @param cacheHit 是否缓存命中
     * @return 计量记录
     */
    private TokenUsageRecord recordUsage(TenantContext tenantContext,
                                         TaskRequest request,
                                         String usageId,
                                         String toolName,
                                         Map<String, Object> output,
                                         String taskId,
                                         boolean cacheHit) {
        ModelDefinition model = modelRouter.route(ModelScene.CHEAP);
        TokenUsageInput input = new TokenUsageInput();
        input.setUsageId(usageId);
        input.setTenantId(tenantContext.getTenantId());
        input.setTaskId(taskId);
        input.setAgentId(toolName);
        input.setModel(model != null ? model.getModelId() : "default");
        input.setProvider(model != null ? model.getProvider() : "local");
        int inputTokens = cacheHit ? 0 : (request != null && request.getQuery() != null ? request.getQuery().length() : 0);
        int outputTokens = cacheHit ? 0 : (output != null ? output.toString().length() : 0);
        input.setInputTokens(inputTokens);
        input.setOutputTokens(outputTokens);
        input.setTotalTokens(inputTokens + outputTokens);
        TokenUsageRecord record = tokenBudgetManager.recordUsage(input, tenantContext);
        log.info("预算计量完成, tenantId={}, usageId={}, totalTokens={}, cacheHit={}",
                tenantContext.getTenantId(), usageId, record.getTotalTokens(), cacheHit);
        return record;
    }

    /**
     * 获取链路追踪标识。
     *
     * @param tenantContext 租户上下文
     * @return traceId
     */
    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && tenantContext.getTraceId() != null
                && !tenantContext.getTraceId().isBlank()) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }
}
