package com.example.agent.capabilities.tools.execution;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.token.TokenBudgetManager;
import com.example.agent.budget.token.TokenUsageInput;
import com.example.agent.budget.token.TokenUsageRecord;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.provider.ModelDefinition;
import com.example.agent.capabilities.llm.provider.ModelRouter;
import com.example.agent.capabilities.llm.contract.ModelScene;
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
import com.example.agent.capabilities.tools.sandbox.SandboxResult;
import com.example.agent.capabilities.tools.validation.ToolArgumentValidator;
import com.example.agent.capabilities.tools.validation.ToolRequestValidator;

/**
 * 宸ュ叿鎵ц鍣紝璐熻矗宸ュ叿璋冪敤銆佺紦瀛樹笌閲嶈瘯鎺у埗銆?
 *
 * <p>鑱岃矗锛氱粺涓€灏佽宸ュ叿璋冪敤娴佺▼锛屼繚璇佽瘉鎹摼涓庣紦瀛樹竴鑷存€с€?/p>
 * <p>杈圭晫锛氬紓甯哥粺涓€鏄犲皠涓轰笟鍔￠敊璇爜骞惰繘琛屽繀瑕侀噸璇曘€?/p>
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
     * 宸ュ叿娉ㄥ唽琛ㄣ€?
     */
    private final ToolRegistry toolRegistry;
    /**
     * MCP 宸ュ叿瀹㈡埛绔€?
     */
    private final McpToolClient mcpToolClient;
    /**
     * 宸ュ叿缁撴灉缂撳瓨銆?
     */
    private final ToolCache toolCache;
    /**
     * 娌欑鎵ц鍣ㄣ€?
     */
    private final SandboxExecutor sandboxExecutor;
    /**
     * 璁￠噺绠＄悊鍣ㄣ€?
     */
    private final TokenBudgetManager tokenBudgetManager;
    /**
     * 妯″瀷璺敱鍣紝鐢ㄤ簬璁￠噺鍦烘櫙銆?
     */
    private final ModelRouter modelRouter;
    /**
     * JSON 搴忓垪鍖栫粍浠躲€?
     */
    private final ObjectMapper objectMapper;
    /**
     * 鎸囨爣鍙戝竷鍣ㄣ€?
     */
    private final MetricsPublisher metricsPublisher;
    /**
     * 閾捐矾杩借釜鍙戝竷鍣ㄣ€?
     */
    private final TracingPublisher tracingPublisher;
    /**
     * 鍘熷缁撴灉瀛樺偍鍣ㄣ€?
     */
    private final RawResultStore rawResultStore;
    private final ToolArgumentValidator argumentValidator = new ToolArgumentValidator();

    /**
     * 鏄惁鍚敤宸ュ叿缂撳瓨銆?
     */
    @Value("${agent.tool.cache.enabled:true}")
    private boolean cacheEnabled;

    /**
     * 宸ュ叿缂撳瓨 TTL锛堢锛夈€?
     */
    @Value("${agent.tool.cache.ttl-seconds:300}")
    private long cacheTtlSeconds;

    /**
     * 宸ュ叿璋冪敤鏈€澶ч噸璇曟鏁般€?
     */
    @Value("${agent.tool.retry.max-attempts:2}")
    private int maxAttempts;

    /**
     * 閲嶈瘯鍩虹寤惰繜锛堟绉掞級銆?
     */
    @Value("${agent.tool.retry.base-delay-ms:100}")
    private long baseDelayMs;

    /**
     * 閲嶈瘯鏈€澶у欢杩燂紙姣锛夈€?
     */
    @Value("${agent.tool.retry.max-delay-ms:1000}")
    private long maxDelayMs;

    /**
     * 閲嶈瘯鎶栧姩姣斾緥銆?
     */
    @Value("${agent.tool.retry.jitter-ratio:0.2}")
    private double jitterRatio;

    /**
     * 榛樿 MCP 鏈嶅姟绔爣璇嗐€?
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
            log.warn("鏈娴嬪埌 RawResultStore 瀹炵幇锛屽伐鍏锋墽琛岃緭鍑哄皢璺宠繃 rawRef 瀛樺偍");
        }
    }

    /**
     * 鎵ц宸ュ叿璋冪敤骞惰繑鍥炵粨鏋溿€?
     *
     * @param request 浠诲姟璇锋眰
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param usageId 璁￠噺骞傜瓑閿?
     * @param toolName 宸ュ叿鍚嶇О
     * @param taskId 浠诲姟鏍囪瘑
     * @return 鎵ц缁撴灉
     */
    public Map<String, Object> execute(TaskRequest request,
                                       TenantContext tenantContext,
                                       String usageId,
                                       String toolName,
                                       String taskId) {
        return executeInternal(request, tenantContext, usageId, toolName, taskId, null);
    }

    /**
     * 鎵ц宸ュ叿璋冪敤骞惰繑鍥炵粨鏋滐紙浣跨敤澶栭儴浼犲叆鐨勫弬鏁帮級銆?
     *
     * @param request 浠诲姟璇锋眰
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param usageId 璁￠噺骞傜瓑閿?
     * @param toolName 宸ュ叿鍚嶇О
     * @param taskId 浠诲姟鏍囪瘑
     * @param toolArguments 宸ュ叿璋冪敤鍙傛暟
     * @return 鎵ц缁撴灉
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
        ToolRequestValidator.requireNonNull(request, "request");
        TenantContext validatedTenant = ToolRequestValidator.requireTenantContext(tenantContext);
        String validatedToolName = ToolRequestValidator.requireNonBlank(toolName, "toolName");
        // 瑙ｆ瀽宸ュ叿鍚嶇О骞跺悎骞跺弬鏁?
        String resolvedTool = toolRegistry.resolve(validatedToolName);
        Map<String, Object> arguments = toolArguments == null
                ? buildArguments(request)
                : buildMergedArguments(request, toolArguments);
        arguments = validateArguments(resolvedTool, arguments);
        // 鏋勫缓缂撳瓨閿笌 TTL
        String cacheKey = buildCacheKey(resolvedTool, arguments);
        Duration ttl = Duration.ofSeconds(Math.max(0, cacheTtlSeconds));

        if (cacheEnabled) {
            // 缂撳瓨鍛戒腑鐩存帴杩斿洖缁撴灉
            Object cached = toolCache.getIfFresh(cacheKey, ttl);
            if (cached instanceof Map<?, ?> cachedMap) {
                log.info("宸ュ叿缂撳瓨鍛戒腑, tenantId={}, tool={}, usageId={}, traceId={}",
                        validatedTenant.getTenantId(), resolvedTool, usageId, resolveTraceId(validatedTenant));
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedOutput = (Map<String, Object>) cachedMap;
                TokenUsageRecord usageRecord = recordUsage(validatedTenant, request, usageId,
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

        // 杩涘叆鐪熷疄璋冪敤娴佺▼锛屼娇鐢ㄩ噸璇曠瓥鐣ヤ繚鎶ゅ閮ㄤ緷璧?
        RetryPolicy retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            long startNs = System.nanoTime();
            try {
                log.info("宸ュ叿鎵ц寮€濮? tenantId={}, tool={}, attempt={}, usageId={}, traceId={}",
                        validatedTenant.getTenantId(), resolvedTool, attempt, usageId,
                        resolveTraceId(validatedTenant));
                // 先执行沙箱校验，再执行 MCP 工具调用
                SandboxResult sandboxResult = sandboxExecutor.execute(resolvedTool, request, validatedTenant, arguments);
                McpToolCallRequest callRequest = buildCallRequest(request, resolvedTool, arguments, usageId);
                McpToolCallResponse callResponse = mcpToolClient.callTool(callRequest, validatedTenant);
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

                TokenUsageRecord usageRecord = recordUsage(validatedTenant, request, usageId,
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
                metricsPublisher.increment("tool.call.count", resolveTraceId(validatedTenant));
                metricsPublisher.recordTime("tool.call.latency.ms", durationMs,
                        resolveTraceId(validatedTenant));

                if (cacheEnabled) {
                    // 鎴愬姛缁撴灉鍐欏洖缂撳瓨
                    toolCache.put(cacheKey, merged, ttl);
                }

                log.info("宸ュ叿鎵ц瀹屾垚, tenantId={}, tool={}, usageId={}, traceId={}",
                        validatedTenant.getTenantId(), resolvedTool, usageId,
                        resolveTraceId(validatedTenant));
                return response;
            } catch (ErrorCodeException ex) {
                metricsPublisher.increment("tool.call.failure.count", resolveTraceId(validatedTenant));
                if (isRetryable(ex) && attempt < maxAttempts) {
                    log.warn("宸ュ叿鎵ц鍙噸璇? tenantId={}, tool={}, attempt={}, errorCode={}, traceId={}",
                            validatedTenant.getTenantId(), resolvedTool, attempt, ex.getErrorCode(),
                            resolveTraceId(validatedTenant));
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("宸ュ叿鎵ц澶辫触, tenantId={}, tool={}, attempt={}, errorCode={}, traceId={}",
                        validatedTenant.getTenantId(), resolvedTool, attempt, ex.getErrorCode(),
                        resolveTraceId(validatedTenant), ex);
                throw ex;
            } catch (Exception ex) {
                metricsPublisher.increment("tool.call.failure.count", resolveTraceId(validatedTenant));
                if (attempt < maxAttempts) {
                    log.warn("宸ュ叿鎵ц寮傚父鍙噸璇? tenantId={}, tool={}, attempt={}, traceId={}",
                            validatedTenant.getTenantId(), resolvedTool, attempt,
                            resolveTraceId(validatedTenant), ex);
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("宸ュ叿鎵ц寮傚父, tenantId={}, tool={}, attempt={}, traceId={}",
                        validatedTenant.getTenantId(), resolvedTool, attempt,
                        resolveTraceId(validatedTenant), ex);
                throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE",
                        "宸ュ叿鎵ц寮傚父");
            }
        }
    }

    /**
     * 灏嗚閲忚褰曡浆鎹负鍙簭鍒楀寲鐨勮緭鍑虹粨鏋勩€?
     *
     * <p>鐢ㄩ€旓細閬垮厤鐩存帴閫忎紶瀵硅薄瀵艰嚧 {@code toString()} 缁撴灉鍑虹幇鍦?raw 杈撳嚭涓紙渚嬪 {@code TokenUsageRecord@xxxx}锛夈€?/p>
     *
     * @param record 璁￠噺璁板綍
     * @return 鍙簭鍒楀寲鏄犲皠
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
     * 鏍￠獙骞惰鑼冨寲宸ュ叿鍙傛暟銆?
     *
     * @param toolName 宸ュ叿鍚嶇О
     * @param arguments 鍘熷鍙傛暟
     * @return 鏍￠獙鍚庣殑鍙傛暟
     */
    private Map<String, Object> validateArguments(String toolName, Map<String, Object> arguments) {
        McpToolDefinition definition = resolveDefinition(toolName);
        if (definition == null || definition.getInputSchema() == null || definition.getInputSchema().isEmpty()) {
            return arguments;
        }
        return argumentValidator.validateAndNormalize(definition.getInputSchema(), arguments, toolName);
    }

    /**
     * 鏍规嵁宸ュ叿鍚嶇О瑙ｆ瀽宸ュ叿瀹氫箟銆?
     *
     * @param toolName 宸ュ叿鍚嶇О
     * @return 宸ュ叿瀹氫箟
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
     * 鏋勫缓 MCP 宸ュ叿璋冪敤璇锋眰銆?
     *
     * @param request 浠诲姟璇锋眰
     * @param toolName 宸ュ叿鍚嶇О
     * @param arguments 宸ュ叿鍙傛暟
     * @param usageId 璁￠噺骞傜瓑閿?
     * @return 璋冪敤璇锋眰
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
     * 瑙ｆ瀽 MCP 鏈嶅姟绔爣璇嗐€?
     *
     * @param request 浠诲姟璇锋眰
     * @return 鏈嶅姟绔爣璇?
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
     * 鍩轰簬浠诲姟璇锋眰鏋勫缓宸ュ叿璋冪敤鍙傛暟銆?
     *
     * @param request 浠诲姟璇锋眰
     * @return 鍙傛暟鏄犲皠
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
     * 鍚堝苟浠诲姟涓婁笅鏂囦笌澶栭儴浼犲叆鍙傛暟銆?
     *
     * @param request 浠诲姟璇锋眰
     * @param toolArguments 澶栭儴鍙傛暟
     * @return 鍚堝苟鍚庣殑鍙傛暟
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
     * 鏋勫缓宸ュ叿璋冪敤缂撳瓨閿€?
     *
     * @param toolName 宸ュ叿鍚嶇О
     * @param arguments 宸ュ叿鍙傛暟
     * @return 缂撳瓨閿?
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
     * 淇濆瓨鍘熷缁撴灉骞惰繑鍥炲紩鐢ㄣ€?
     */
    private RawRef storeRawRef(String toolName, Map<String, Object> result) {
        if (rawResultStore == null || result == null || result.isEmpty()) {
            return null;
        }
        return rawResultStore.store(toolName, result, "application/json");
    }

    /**
     * 瑙ｆ瀽杈撳嚭浣跨敤鐨?rawRef 瀛楁鍊笺€?     *
     * <p>浼樺厛杩斿洖 refId锛屽吋瀹瑰洖閫€ key銆?     *
     * @param rawRef 鍘熷寮曠敤瀵硅薄
     * @return 杈撳嚭寮曠敤
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
     * 鐢熸垚宸ュ叿璋冪敤缁撴灉鎽樿锛岀敤浜庤瘉鎹褰曘€?
     *
     * @param value 鍘熷瀵硅薄
     * @return 鎽樿瀛楃涓?
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
     * 鐢熸垚 Map 鐨勯敭鎽樿銆?
     *
     * @param map 鐩爣 Map
     * @return 鎽樿瀛楃涓?
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
     * 鎴柇鏂囨湰闀垮害锛岄伩鍏嶈繃闀胯緭鍑恒€?
     *
     * @param value 鍘熷鏂囨湰
     * @param maxLength 鏈€澶ч暱搴?
     * @return 鎴柇鍚庣殑鏂囨湰
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
     * 鍒ゆ柇寮傚父鏄惁鍙噸璇曘€?
     *
     * @param ex 寮傚父
     * @return 鏄惁鍙噸璇?
     */
    private boolean isRetryable(ErrorCodeException ex) {
        String code = ex.getErrorCode();
        return "MCP_UNAVAILABLE".equals(code)
                || "CIRCUIT_OPEN".equals(code)
                || "RATE_LIMITED".equals(code);
    }

    /**
     * 璁板綍宸ュ叿璋冪敤鐨勮閲忎俊鎭€?
     *
     * @param tenantContext 绉熸埛涓婁笅鏂?
     * @param request 浠诲姟璇锋眰
     * @param usageId 璁￠噺骞傜瓑閿?
     * @param toolName 宸ュ叿鍚嶇О
     * @param output 宸ュ叿杈撳嚭
     * @param taskId 浠诲姟鏍囪瘑
     * @param cacheHit 鏄惁缂撳瓨鍛戒腑
     * @return 璁￠噺璁板綍
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
        log.info("棰勭畻璁￠噺瀹屾垚, tenantId={}, usageId={}, totalTokens={}, cacheHit={}",
                tenantContext.getTenantId(), usageId, record.getTotalTokens(), cacheHit);
        return record;
    }

    /**
     * 鑾峰彇閾捐矾杩借釜鏍囪瘑銆?
     *
     * @param tenantContext 绉熸埛涓婁笅鏂?
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

