package com.example.agent.agentcore;

import com.example.agent.auth.TenantContext;
import com.example.agent.budget.TokenBudgetManager;
import com.example.agent.budget.TokenUsageInput;
import com.example.agent.budget.TokenUsageRecord;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.common.TaskRequest;
import com.example.agent.model.ModelDefinition;
import com.example.agent.model.ModelRouter;
import com.example.agent.model.ModelScene;
import com.example.agent.tools.McpToolCallRequest;
import com.example.agent.tools.McpToolCallResponse;
import com.example.agent.tools.McpToolClient;
import com.example.agent.tools.McpToolDefinition;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.observability.TracingPublisher;
import com.example.agent.runtime.RetryPolicy;
import com.example.agent.sandbox.SandboxResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 工具执行器，负责工具调用、缓存与重试控制。
 */
@Component
public class ToolExecutor {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final McpToolClient mcpToolClient;
    private final ToolCache toolCache;
    private final SandboxExecutor sandboxExecutor;
    private final TokenBudgetManager tokenBudgetManager;
    private final ModelRouter modelRouter;
    private final ObjectMapper objectMapper;
    private final MetricsPublisher metricsPublisher;
    private final TracingPublisher tracingPublisher;
    private final ToolArgumentValidator argumentValidator = new ToolArgumentValidator();

    @Value("${agent.tool.cache.enabled:true}")
    private boolean cacheEnabled;

    @Value("${agent.tool.cache.ttl-seconds:300}")
    private long cacheTtlSeconds;

    @Value("${agent.tool.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${agent.tool.retry.base-delay-ms:100}")
    private long baseDelayMs;

    @Value("${agent.tool.retry.max-delay-ms:1000}")
    private long maxDelayMs;

    @Value("${agent.tool.retry.jitter-ratio:0.2}")
    private double jitterRatio;

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
                        TracingPublisher tracingPublisher) {
        this.toolRegistry = toolRegistry;
        this.mcpToolClient = mcpToolClient;
        this.toolCache = toolCache;
        this.sandboxExecutor = sandboxExecutor;
        this.tokenBudgetManager = tokenBudgetManager;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
        this.metricsPublisher = metricsPublisher;
        this.tracingPublisher = tracingPublisher;
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
        String resolvedTool = toolRegistry.resolve(toolName);
        Map<String, Object> arguments = buildArguments(request);
        arguments = validateArguments(resolvedTool, arguments);
        String cacheKey = buildCacheKey(resolvedTool, arguments);
        Duration ttl = Duration.ofSeconds(Math.max(0, cacheTtlSeconds));

        if (cacheEnabled) {
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
                response.put("tokenUsage", usageRecord);
                response.put("cacheHit", true);
                return response;
            }
        }

        RetryPolicy retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            long startNs = System.nanoTime();
            try {
                log.info("工具执行开始, tenantId={}, tool={}, attempt={}, usageId={}, traceId={}",
                        tenantContext.getTenantId(), resolvedTool, attempt, usageId,
                        resolveTraceId(tenantContext));
                SandboxResult sandboxResult = sandboxExecutor.execute(resolvedTool, request, tenantContext, arguments);
                McpToolCallRequest callRequest = buildCallRequest(request, resolvedTool, arguments, usageId);
                McpToolCallResponse callResponse = mcpToolClient.callTool(callRequest, tenantContext);
                Map<String, Object> toolResult = callResponse != null ? callResponse.getResult() : null;
                Map<String, Object> merged = new HashMap<>();
                if (toolResult != null) {
                    merged.putAll(toolResult);
                }
                if (sandboxResult != null && sandboxResult.getOutput() != null) {
                    merged.put("sandbox", sandboxResult.getOutput());
                }
                if (sandboxResult != null && sandboxResult.getStatus() != null) {
                    merged.put("sandboxStatus", sandboxResult.getStatus());
                }

                TokenUsageRecord usageRecord = recordUsage(tenantContext, request, usageId,
                        resolvedTool, merged, taskId, false);
                Map<String, Object> response = new HashMap<>();
                response.put("tool", resolvedTool);
                response.put("result", merged);
                response.put("tokenUsage", usageRecord);
                response.put("cacheHit", false);

                metricsPublisher.increment("tool.call.count", resolveTraceId(tenantContext));
                metricsPublisher.recordTime("tool.call.latency.ms",
                        Duration.ofNanos(System.nanoTime() - startNs).toMillis(),
                        resolveTraceId(tenantContext));

                if (cacheEnabled) {
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

    private Map<String, Object> validateArguments(String toolName, Map<String, Object> arguments) {
        McpToolDefinition definition = resolveDefinition(toolName);
        if (definition == null || definition.getInputSchema() == null || definition.getInputSchema().isEmpty()) {
            return arguments;
        }
        return argumentValidator.validateAndNormalize(definition.getInputSchema(), arguments, toolName);
    }

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

    private String resolveServerId(TaskRequest request) {
        if (request != null && request.getContext() != null) {
            Object serverId = request.getContext().get("mcpServerId");
            if (serverId instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return defaultServerId;
    }

    Map<String, Object> buildArguments(TaskRequest request) {
        Map<String, Object> arguments = new HashMap<>();
        if (request != null) {
            arguments.put("query", request.getQuery());
            if (request.getContext() != null) {
                arguments.putAll(request.getContext());
            }
        }
        return arguments;
    }

    String buildCacheKey(String toolName, Map<String, Object> arguments) {
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

    private boolean isRetryable(ErrorCodeException ex) {
        String code = ex.getErrorCode();
        return "MCP_UNAVAILABLE".equals(code)
                || "CIRCUIT_OPEN".equals(code)
                || "RATE_LIMITED".equals(code);
    }

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

    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && tenantContext.getTraceId() != null
                && !tenantContext.getTraceId().isBlank()) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }
}
