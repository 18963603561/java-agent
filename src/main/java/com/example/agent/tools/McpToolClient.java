package com.example.agent.tools;

import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.governance.CircuitBreakerManager;
import com.example.agent.governance.RateLimitService;
import com.example.agent.runtime.RetryPolicy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * MCP 工具客户端，负责工具列表与调用的统一入口。
 */
@Service
public class McpToolClient {

    private static final Logger log = LoggerFactory.getLogger(McpToolClient.class);
    private static final String JSONRPC_VERSION = "2.0";
    private static final String MCP_PROTOCOL_JSONRPC = "jsonrpc";
    private static final long SSE_RECONNECT_DELAY_MS = 1000;
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
            "session[_-]?id\\\"?\\s*[:=]\\s*\\\"?([a-f0-9\\-]{36})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "([a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12})",
            Pattern.CASE_INSENSITIVE);

    private final McpServerProperties serverProperties;
    private final ToolRegistry toolRegistry;
    private final RateLimitService rateLimitService;
    private final CircuitBreakerManager circuitBreakerManager;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    /**
     * JSON-RPC 初始化状态缓存，避免重复握手。
     */
    private final ConcurrentMap<String, Boolean> initializedServers = new ConcurrentHashMap<>();
    /**
     * JSON-RPC 初始化锁，防止并发重复初始化。
     */
    private final ConcurrentMap<String, Object> initLocks = new ConcurrentHashMap<>();
    /**
     * SSE 会话缓存。
     */
    private final ConcurrentMap<String, SseSessionState> sseSessions = new ConcurrentHashMap<>();
    /**
     * SSE 会话初始化锁。
     */
    private final ConcurrentMap<String, Object> sseLocks = new ConcurrentHashMap<>();

    @Value("${agent.mcp.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${agent.mcp.retry.base-delay-ms:100}")
    private long baseDelayMs;

    @Value("${agent.mcp.retry.max-delay-ms:1000}")
    private long maxDelayMs;

    @Value("${agent.mcp.retry.jitter-ratio:0.2}")
    private double jitterRatio;

    @Value("${agent.mcp.http.timeout-seconds:10}")
    private long timeoutSeconds;

    @Value("${agent.mcp.remote-enabled:false}")
    private boolean remoteEnabled;

    /**
     * 是否允许远端失败后回退本地。
     */
    @Value("${agent.mcp.fallback-to-local:false}")
    private boolean fallbackToLocal;

    /**
     * 是否合并远端与本地工具列表。
     */
    @Value("${agent.mcp.merge-local-tools:false}")
    private boolean mergeLocalTools;

    /**
     * 工具调用策略。
     */
    @Value("${agent.mcp.call-strategy:remote-first}")
    private String callStrategy;

    public McpToolClient(McpServerProperties serverProperties,
                         ToolRegistry toolRegistry,
                         RateLimitService rateLimitService,
                         CircuitBreakerManager circuitBreakerManager,
                         WebClient.Builder webClientBuilder,
                         ObjectMapper objectMapper) {
        this.serverProperties = serverProperties;
        this.toolRegistry = toolRegistry;
        this.rateLimitService = rateLimitService;
        this.circuitBreakerManager = circuitBreakerManager;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取工具列表。
     *
     * @param request 列表请求
     * @param tenantContext 租户上下文
     * @return 工具列表响应
     */
    public McpToolListResponse listTools(McpToolListRequest request, TenantContext tenantContext) {
        String serverId = normalizeServerId(request.getServerId());
        McpServerProperties.McpServer server = resolveServer(serverId);
        validateServer(server);
        CallStrategy strategy = resolveCallStrategy();
        boolean remoteAvailable = isRemoteServer(server);
        boolean allowRemote = strategy != CallStrategy.LOCAL_ONLY && remoteAvailable;
        boolean allowLocal = strategy != CallStrategy.REMOTE_ONLY;
        log.info("MCP tools/list start, tenantId={}, serverId={}, strategy={}, mergeLocal={}",
                tenantContext.getTenantId(), serverId, strategy.name().toLowerCase(Locale.ROOT), mergeLocalTools);

        McpToolListResponse response;
        if (strategy == CallStrategy.LOCAL_ONLY || !allowRemote) {
            response = listToolsLocal(request);
        } else if (strategy == CallStrategy.LOCAL_FIRST) {
            response = listToolsLocal(request);
            if (mergeLocalTools && allowRemote) {
                try {
                    McpToolListResponse remote = listToolsRemoteWithRetry(server, request, tenantContext, serverId);
                    response = mergeToolLists(response, remote);
                } catch (ErrorCodeException ex) {
                    log.warn("MCP tools/list remote merge failed, fallback local, tenantId={}, serverId={}, code={}",
                            tenantContext.getTenantId(), serverId, ex.getErrorCode());
                }
            }
        } else {
            try {
                response = listToolsRemoteWithRetry(server, request, tenantContext, serverId);
                if (mergeLocalTools && allowLocal) {
                    McpToolListResponse local = listToolsLocal(request);
                    response = mergeToolLists(response, local);
                }
            } catch (ErrorCodeException ex) {
                if (allowLocal && fallbackToLocal && isFallbackTrigger(ex)) {
                    log.warn("MCP tools/list fallback to local, tenantId={}, serverId={}, code={}",
                            tenantContext.getTenantId(), serverId, ex.getErrorCode());
                    response = listToolsLocal(request);
                } else {
                    throw ex;
                }
            }
        }

        log.info("MCP tools/list end, tenantId={}, serverId={}, count={}",
                tenantContext.getTenantId(), serverId, response.getTools().size());
        return response;
    }

    /**
     * 调用工具。
     *
     * @param request 调用请求
     * @param tenantContext 租户上下文
     * @return 调用响应
     */
    public McpToolCallResponse callTool(McpToolCallRequest request, TenantContext tenantContext) {
        String serverId = normalizeServerId(request.getServerId());
        McpServerProperties.McpServer server = resolveServer(serverId);
        validateServer(server);
        String toolName = request.getToolName();
        if (!StringUtils.hasText(toolName)) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "toolName 不能为空");
        }
        String rateKey = tenantContext.getTenantId() + ":" + toolName;
        if (!rateLimitService.allow(rateKey)) {
            throw new ErrorCodeException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁");
        }
        CallStrategy strategy = resolveCallStrategy();
        boolean remoteAvailable = isRemoteServer(server);
        boolean localAvailable = toolRegistry.hasTool(toolName);
        boolean allowRemote = strategy != CallStrategy.LOCAL_ONLY && remoteAvailable;
        boolean allowLocal = strategy != CallStrategy.REMOTE_ONLY;

        log.info("MCP tools/call start, tenantId={}, serverId={}, toolName={}, callId={}, strategy={}, localAvailable={}",
                tenantContext.getTenantId(), serverId, toolName, request.getCallId(),
                strategy.name().toLowerCase(Locale.ROOT), localAvailable);

        if (strategy == CallStrategy.LOCAL_ONLY) {
            return callToolLocal(request);
        }

        if (strategy == CallStrategy.LOCAL_FIRST) {
            if (localAvailable) {
                try {
                    McpToolCallResponse localResponse = callToolLocal(request);
                    log.info("MCP tools/call local success, tenantId={}, toolName={}, callId={}",
                            tenantContext.getTenantId(), toolName, request.getCallId());
                    return localResponse;
                } catch (Exception ex) {
                    log.warn("MCP tools/call local failed, try remote, tenantId={}, toolName={}, callId={}",
                            tenantContext.getTenantId(), toolName, request.getCallId(), ex);
                }
            }
            if (!allowRemote) {
                if (!localAvailable) {
                    throw new ErrorCodeException(HttpStatus.NOT_FOUND, "NOT_FOUND", "工具不存在");
                }
                throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "远端 MCP 不可用");
            }
            return callToolRemoteWithRetry(server, request, tenantContext, serverId, rateKey);
        }

        if (!allowRemote) {
            if (allowLocal && localAvailable) {
                log.warn("MCP tools/call remote disabled, fallback local, tenantId={}, toolName={}, callId={}",
                        tenantContext.getTenantId(), toolName, request.getCallId());
                return callToolLocal(request);
            }
            if (!localAvailable) {
                throw new ErrorCodeException(HttpStatus.NOT_FOUND, "NOT_FOUND", "工具不存在");
            }
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "远端 MCP 不可用");
        }

        try {
            McpToolCallResponse response = callToolRemoteWithRetry(server, request, tenantContext, serverId, rateKey);
            log.info("MCP tools/call success, tenantId={}, serverId={}, toolName={}, callId={}",
                    tenantContext.getTenantId(), serverId, toolName, request.getCallId());
            return response;
        } catch (ErrorCodeException ex) {
            if (allowLocal && localAvailable && fallbackToLocal && isFallbackTrigger(ex)) {
                log.warn("MCP tools/call fallback local, tenantId={}, serverId={}, toolName={}, callId={}, code={}",
                        tenantContext.getTenantId(), serverId, toolName, request.getCallId(), ex.getErrorCode());
                return callToolLocal(request);
            }
            throw ex;
        }
    }

    /**
     * 远端工具列表调用，带重试。
     */
    private McpToolListResponse listToolsRemoteWithRetry(McpServerProperties.McpServer server,
                                                        McpToolListRequest request,
                                                        TenantContext tenantContext,
                                                        String serverId) {
        RetryPolicy retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                return listToolsRemote(server, request);
            } catch (ErrorCodeException ex) {
                if (isRetryable(ex) && attempt < maxAttempts) {
                    log.warn("MCP tools/list retry, tenantId={}, serverId={}, attempt={}, code={}",
                            tenantContext.getTenantId(), serverId, attempt, ex.getErrorCode());
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                throw ex;
            }
        }
    }

    /**
     * 远端工具调用，带熔断与重试。
     */
    private McpToolCallResponse callToolRemoteWithRetry(McpServerProperties.McpServer server,
                                                       McpToolCallRequest request,
                                                       TenantContext tenantContext,
                                                       String serverId,
                                                       String rateKey) {
        RetryPolicy retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                if (!circuitBreakerManager.allow(rateKey)) {
                    throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN", "熔断已开启");
                }
                McpToolCallResponse response = callToolRemote(server, request);
                circuitBreakerManager.recordSuccess(rateKey);
                return response;
            } catch (ErrorCodeException ex) {
                circuitBreakerManager.recordFailure(rateKey);
                if (isRetryable(ex) && attempt < maxAttempts) {
                    log.warn("MCP tools/call retry, tenantId={}, serverId={}, toolName={}, attempt={}, code={}",
                            tenantContext.getTenantId(), serverId, request.getToolName(), attempt, ex.getErrorCode());
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                throw ex;
            } catch (Exception ex) {
                circuitBreakerManager.recordFailure(rateKey);
                log.error("MCP tools/call failed, tenantId={}, serverId={}, toolName={}",
                        tenantContext.getTenantId(), serverId, request.getToolName(), ex);
                throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE",
                        "MCP 工具不可用");
            }
        }
    }

    /**
     * 合并工具列表，优先保留主列表中的工具定义。
     */
    private McpToolListResponse mergeToolLists(McpToolListResponse primary, McpToolListResponse secondary) {
        if (primary == null && secondary == null) {
            return new McpToolListResponse(List.of(), null, false);
        }
        List<McpToolDefinition> merged = new ArrayList<>();
        Map<String, McpToolDefinition> seen = new HashMap<>();
        if (primary != null && primary.getTools() != null) {
            for (McpToolDefinition tool : primary.getTools()) {
                if (tool == null) {
                    continue;
                }
                String name = tool.getName();
                if (StringUtils.hasText(name)) {
                    seen.put(name, tool);
                }
                merged.add(tool);
            }
        }
        if (secondary != null && secondary.getTools() != null) {
            for (McpToolDefinition tool : secondary.getTools()) {
                if (tool == null) {
                    continue;
                }
                String name = tool.getName();
                if (StringUtils.hasText(name) && seen.containsKey(name)) {
                    continue;
                }
                if (StringUtils.hasText(name)) {
                    seen.put(name, tool);
                }
                merged.add(tool);
            }
        }
        String nextCursor = primary != null ? primary.getNextCursor()
                : secondary != null ? secondary.getNextCursor() : null;
        boolean hasMore = primary != null ? primary.isHasMore()
                : secondary != null && secondary.isHasMore();
        return new McpToolListResponse(merged, nextCursor, hasMore);
    }

    private McpToolListResponse listToolsLocal(McpToolListRequest request) {
        List<McpToolDefinition> tools = new ArrayList<>(toolRegistry.listDefinitions());
        int startIndex = resolveStartIndex(request.getCursor(), tools.size());
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : tools.size();
        int endIndex = Math.min(startIndex + size, tools.size());
        List<McpToolDefinition> page = startIndex < endIndex
                ? tools.subList(startIndex, endIndex)
                : List.of();

        String nextCursor = endIndex < tools.size() ? String.valueOf(endIndex) : null;
        boolean hasMore = endIndex < tools.size();
        return new McpToolListResponse(page, nextCursor, hasMore);
    }

    private McpToolCallResponse callToolLocal(McpToolCallRequest request) {
        Map<String, Object> result = toolRegistry.execute(request.getToolName(), request.getArguments());
        return new McpToolCallResponse(request.getCallId(), "SUCCESS", result, null);
    }

    private McpToolListResponse listToolsRemote(McpServerProperties.McpServer server, McpToolListRequest request) {
        if (isJsonRpcServer(server)) {
            ensureInitialized(server);
            Map<String, Object> params = buildListParams(request);
            Map<String, Object> response = postJsonRpc(server, "tools/list", params, null, timeoutSeconds);
            Map<String, Object> error = extractJsonRpcError(response);
            if (error != null && isJsonRpcInvalidParams(error) && !params.isEmpty()) {
                log.warn("MCP tools/list params rejected, retry with empty params, serverId={}", server.getId());
                response = postJsonRpc(server, "tools/list", Map.of(), null, timeoutSeconds);
                error = extractJsonRpcError(response);
            }
            Object result = resolveJsonRpcResult(response, error);
            return objectMapper.convertValue(result, McpToolListResponse.class);
        }
        String url = buildRestUrl(server.getBaseUrl(), "tools/list");
        Map<String, Object> response = post(server, url, request, timeoutSeconds);
        return convertResponse(response, McpToolListResponse.class);
    }

    private McpToolCallResponse callToolRemote(McpServerProperties.McpServer server, McpToolCallRequest request) {
        long timeout = request.getTimeoutMs() != null && request.getTimeoutMs() > 0
                ? request.getTimeoutMs() / 1000
                : timeoutSeconds;
        if (isJsonRpcServer(server)) {
            ensureInitialized(server);
            Map<String, Object> params = buildCallParams(request);
            Map<String, Object> response = postJsonRpc(server, "tools/call", params, request.getCallId(), timeout);
            Map<String, Object> error = extractJsonRpcError(response);
            Object result = resolveJsonRpcResult(response, error);
            Map<String, Object> resultMap = normalizeResultMap(result);
            String callId = resolveJsonRpcCallId(request.getCallId(), response);
            return new McpToolCallResponse(callId, "SUCCESS", resultMap, null);
        }
        String url = buildRestUrl(server.getBaseUrl(), "tools/call");
        Map<String, Object> response = post(server, url, request, timeout);
        return convertResponse(response, McpToolCallResponse.class);
    }

    private Map<String, Object> buildListParams(McpToolListRequest request) {
        Map<String, Object> params = new HashMap<>();
        if (request == null) {
            return params;
        }
        if (StringUtils.hasText(request.getCursor())) {
            params.put("cursor", request.getCursor());
        }
        if (request.getSize() != null && request.getSize() > 0) {
            params.put("size", request.getSize());
        }
        return params;
    }

    private Map<String, Object> buildCallParams(McpToolCallRequest request) {
        Map<String, Object> params = new HashMap<>();
        if (request == null) {
            return params;
        }
        if (StringUtils.hasText(request.getToolName())) {
            params.put("name", request.getToolName());
        }
        Map<String, Object> arguments = request.getArguments() != null ? request.getArguments() : Map.of();
        params.put("arguments", arguments);
        return params;
    }

    /**
     * JSON-RPC 初始化握手，避免重复调用初始化接口。
     */
    private void ensureInitialized(McpServerProperties.McpServer server) {
        if (!isJsonRpcServer(server)) {
            return;
        }
        String initKey = resolveInitKey(server);
        if (initializedServers.containsKey(initKey)) {
            return;
        }
        Object lock = initLocks.computeIfAbsent(initKey, key -> new Object());
        synchronized (lock) {
            if (initializedServers.containsKey(initKey)) {
                return;
            }
            log.info("MCP initialize start, serverId={}, baseUrl={}", server.getId(), server.getBaseUrl());
            try {
                Map<String, Object> response = postJsonRpc(server, "initialize", Map.of(), null, timeoutSeconds);
                if (response == null) {
                    throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 返回为空");
                }
                Map<String, Object> error = extractJsonRpcError(response);
                if (error != null) {
                    if (isJsonRpcMethodNotFound(error)) {
                        log.warn("MCP initialize not supported, skip, serverId={}, baseUrl={}",
                                server.getId(), server.getBaseUrl());
                    } else {
                        throw buildJsonRpcException(error);
                    }
                }
                initializedServers.put(initKey, true);
                log.info("MCP initialize end, serverId={}, baseUrl={}", server.getId(), server.getBaseUrl());
            } catch (ErrorCodeException ex) {
                log.error("MCP initialize failed, serverId={}, baseUrl={}", server.getId(), server.getBaseUrl(), ex);
                throw ex;
            }
        }
    }

    private Map<String, Object> postJsonRpc(McpServerProperties.McpServer server,
                                            String method,
                                            Map<String, Object> params,
                                            String callId,
                                            long timeoutSec) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", JSONRPC_VERSION);
        payload.put("id", createJsonRpcId(method, callId));
        payload.put("method", method);
        payload.put("params", params != null ? params : Map.of());
        String url = resolveJsonRpcUrl(server);
        return post(server, url, payload, timeoutSec);
    }

    private String resolveJsonRpcUrl(McpServerProperties.McpServer server) {
        if (server == null) {
            return null;
        }
        String baseUrl = server.getBaseUrl();
        if (!StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        if (!StringUtils.hasText(server.getSseUrl())) {
            return baseUrl;
        }
        String sessionId = ensureSseSession(server);
        String paramName = StringUtils.hasText(server.getSessionParamName())
                ? server.getSessionParamName()
                : "sessionId";
        return appendQueryParam(baseUrl, paramName, sessionId);
    }

    private String ensureSseSession(McpServerProperties.McpServer server) {
        String sseUrl = server.getSseUrl();
        if (!StringUtils.hasText(sseUrl)) {
            return null;
        }
        validateAllowedHost(server, sseUrl);
        String sessionKey = resolveSseKey(server);
        SseSessionState state = sseSessions.computeIfAbsent(sessionKey, key -> new SseSessionState());
        Object lock = sseLocks.computeIfAbsent(sessionKey, key -> new Object());
        synchronized (lock) {
            long now = System.currentTimeMillis();
            boolean hasSession = StringUtils.hasText(state.sessionId);
            boolean refreshNeeded = hasSession && isRefreshNeeded(server, state, now);
            if (!isWorkerAlive(state)) {
                startSseWorker(server, state);
            }
            if (refreshNeeded) {
                log.info("MCP SSE refresh, serverId={}, url={}", server.getId(), sseUrl);
                clearSession(state);
                closeSseStream(state);
            }
        }
        String sessionId = waitForSessionId(state, timeoutSeconds);
        if (!StringUtils.hasText(sessionId)) {
            log.error("MCP SSE session unavailable, serverId={}, url={}", server.getId(), sseUrl);
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP SSE 会话不可用");
        }
        return sessionId;
    }

    private boolean isRefreshNeeded(McpServerProperties.McpServer server, SseSessionState state, long now) {
        long refreshSeconds = server.getSessionRefreshSeconds();
        if (refreshSeconds <= 0) {
            return false;
        }
        long lastRefresh = state.lastRefreshTimeMs;
        if (lastRefresh <= 0) {
            return false;
        }
        return now - lastRefresh >= refreshSeconds * 1000;
    }

    private void clearSession(SseSessionState state) {
        state.sessionId = null;
        state.lastRefreshTimeMs = 0;
    }

    private String waitForSessionId(SseSessionState state, long waitSeconds) {
        if (state == null) {
            return null;
        }
        if (StringUtils.hasText(state.sessionId)) {
            return state.sessionId;
        }
        long waitMs = Math.max(1000, waitSeconds * 1000);
        long deadline = System.currentTimeMillis() + waitMs;
        while (System.currentTimeMillis() < deadline) {
            if (StringUtils.hasText(state.sessionId)) {
                return state.sessionId;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return state.sessionId;
            }
        }
        return state.sessionId;
    }

    private Object resolveJsonRpcResult(Map<String, Object> response,
                                        Map<String, Object> error) {
        if (response == null) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 返回为空");
        }
        if (error != null) {
            throw buildJsonRpcException(error);
        }
        if (!response.containsKey("result")) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 返回为空");
        }
        return response.get("result");
    }

    private Map<String, Object> extractJsonRpcError(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object error = response.get("error");
        if (error instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        if (error != null) {
            return Map.of("message", error.toString());
        }
        return null;
    }

    private Map<String, Object> normalizeResultMap(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) map;
            return cast;
        }
        Map<String, Object> wrapper = new HashMap<>();
        wrapper.put("value", result);
        return wrapper;
    }

    private String resolveJsonRpcCallId(String requestCallId, Map<String, Object> response) {
        if (StringUtils.hasText(requestCallId)) {
            return requestCallId;
        }
        if (response != null && response.get("id") != null) {
            return response.get("id").toString();
        }
        return null;
    }

    private String createJsonRpcId(String method, String callId) {
        if (StringUtils.hasText(callId)) {
            return callId;
        }
        String prefix = StringUtils.hasText(method) ? method.replace('/', '-') : "mcp";
        return prefix + "-" + UUID.randomUUID();
    }

    private String resolveInitKey(McpServerProperties.McpServer server) {
        String serverId = server != null ? server.getId() : "unknown";
        String baseUrl = server != null && server.getBaseUrl() != null ? server.getBaseUrl() : "";
        return serverId + "|" + baseUrl;
    }

    private String resolveSseKey(McpServerProperties.McpServer server) {
        String serverId = server != null ? server.getId() : "unknown";
        String sseUrl = server != null && server.getSseUrl() != null ? server.getSseUrl() : "";
        return serverId + "|" + sseUrl;
    }

    private boolean isWorkerAlive(SseSessionState state) {
        Thread worker = state != null ? state.worker : null;
        return worker != null && worker.isAlive();
    }

    private void startSseWorker(McpServerProperties.McpServer server, SseSessionState state) {
        if (state == null || server == null) {
            return;
        }
        if (isWorkerAlive(state)) {
            return;
        }
        String threadName = "mcp-sse-" + (StringUtils.hasText(server.getId()) ? server.getId() : "default");
        Thread worker = new Thread(() -> runSseLoop(server, state), threadName);
        worker.setDaemon(true);
        state.worker = worker;
        worker.start();
    }

    private void runSseLoop(McpServerProperties.McpServer server, SseSessionState state) {
        String serverId = server.getId();
        String sseUrl = server.getSseUrl();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                log.info("MCP SSE connect start, serverId={}, url={}", serverId, sseUrl);
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                        .build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sseUrl))
                        .header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE)
                        .GET()
                        .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    log.warn("MCP SSE status error, serverId={}, url={}, status={}",
                            serverId, sseUrl, response.statusCode());
                    sleepBeforeReconnect();
                    continue;
                }
                try (InputStream stream = response.body();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    setSseStream(state, stream);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!StringUtils.hasText(line)) {
                            continue;
                        }
                        String sessionId = extractSessionId(line);
                        if (StringUtils.hasText(sessionId)) {
                            updateSession(state, serverId, sessionId);
                        }
                    }
                } finally {
                    closeSseStream(state);
                }
                clearSession(state);
                log.warn("MCP SSE disconnected, serverId={}, url={}", serverId, sseUrl);
            } catch (Exception ex) {
                log.warn("MCP SSE failed, serverId={}, url={}", serverId, sseUrl, ex);
            }
            sleepBeforeReconnect();
        }
    }

    private void updateSession(SseSessionState state, String serverId, String sessionId) {
        if (state == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        boolean changed = !sessionId.equals(state.sessionId);
        state.sessionId = sessionId;
        state.lastRefreshTimeMs = System.currentTimeMillis();
        if (changed) {
            log.info("MCP SSE session updated, serverId={}, sessionId={}", serverId, sessionId);
        }
    }

    private void setSseStream(SseSessionState state, InputStream stream) {
        if (state == null) {
            return;
        }
        synchronized (state.streamLock) {
            state.stream = stream;
        }
    }

    private void closeSseStream(SseSessionState state) {
        if (state == null) {
            return;
        }
        InputStream stream;
        synchronized (state.streamLock) {
            stream = state.stream;
            state.stream = null;
        }
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException ex) {
                log.warn("MCP SSE stream close failed", ex);
            }
        }
    }

    private String extractSessionId(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        String text = line.trim();
        if (text.startsWith("data:")) {
            text = text.substring("data:".length()).trim();
        }
        Matcher matcher = SESSION_ID_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = UUID_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String appendQueryParam(String baseUrl, String name, String value) {
        if (!StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
        String separator = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + separator + encodedName + "=" + encodedValue;
    }

    private void sleepBeforeReconnect() {
        try {
            Thread.sleep(SSE_RECONNECT_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isJsonRpcMethodNotFound(Map<String, Object> error) {
        Integer code = resolveJsonRpcErrorCode(error);
        if (code != null && code == -32601) {
            return true;
        }
        String message = resolveJsonRpcErrorMessage(error, null);
        return message != null && message.toLowerCase().contains("method not found");
    }

    private boolean isJsonRpcInvalidParams(Map<String, Object> error) {
        Integer code = resolveJsonRpcErrorCode(error);
        if (code != null && code == -32602) {
            return true;
        }
        String message = resolveJsonRpcErrorMessage(error, null);
        return message != null && message.toLowerCase().contains("invalid params");
    }

    private Integer resolveJsonRpcErrorCode(Map<String, Object> error) {
        if (error == null) {
            return null;
        }
        Object code = error.get("code");
        if (code instanceof Number number) {
            return number.intValue();
        }
        if (code instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private String resolveJsonRpcErrorMessage(Map<String, Object> error, String fallback) {
        if (error != null && error.get("message") != null) {
            return error.get("message").toString();
        }
        return fallback;
    }

    private ErrorCodeException buildJsonRpcException(Map<String, Object> error) {
        boolean invalidParams = isJsonRpcInvalidParams(error);
        HttpStatus status = invalidParams ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE;
        String errorCode = invalidParams ? "INVALID_REQUEST" : "MCP_UNAVAILABLE";
        String message = resolveJsonRpcErrorMessage(error, "MCP 调用失败");
        return new ErrorCodeException(status, errorCode, message);
    }

    private String buildRestUrl(String baseUrl, String path) {
        if (!StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        if (!StringUtils.hasText(path)) {
            return baseUrl;
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        return normalizedBase + "/" + normalizedPath;
    }

    private Map<String, Object> post(McpServerProperties.McpServer server, String url, Object body, long timeoutSec) {
        validateAllowedHost(server, url);
        long maxResponseBytes = resolveMaxResponseBytes(server);
        byte[] payload = serializeBody(body);
        if (Schedulers.isInNonBlockingThread()) {
            if (log.isDebugEnabled()) {
                log.debug("MCP use JDK client in non-blocking thread, url={}", url);
            }
            return postWithJdkHttpClient(url, payload, timeoutSec, maxResponseBytes);
        }
        WebClient client = webClientBuilder.build();
        try {
            return client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(payload.length)
                    .bodyValue(payload)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is4xxClientError() || response.statusCode().is5xxServerError()) {
                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("mcp_call_failed")
                                    .flatMap(message -> Mono.error(new ErrorCodeException(
                                            HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", message)));
                        }
                        return response.bodyToFlux(DataBuffer.class)
                                .reduceWith(ByteArrayOutputStream::new, (stream, buffer) -> {
                                    int readable = buffer.readableByteCount();
                                    if (readable > 0) {
                                        if (stream.size() + readable > maxResponseBytes) {
                                            DataBufferUtils.release(buffer);
                                            throw new ErrorCodeException(HttpStatus.PAYLOAD_TOO_LARGE,
                                                    "MCP_RESPONSE_TOO_LARGE", "mcp_response_too_large");
                                        }
                                        byte[] chunk = new byte[readable];
                                        buffer.read(chunk);
                                        stream.write(chunk, 0, readable);
                                    }
                                    DataBufferUtils.release(buffer);
                                    return stream;
                                })
                                .map(this::readResponseAsMap);
                    })
                    .timeout(Duration.ofSeconds(timeoutSec))
                    .toFuture()
                    .get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "mcp_call_interrupted");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (isConnectionIssue(cause)) {
                log.warn("MCP WebClient failed, fallback to JDK client, url={}", url, cause);
                return postWithJdkHttpClient(url, payload, timeoutSec, maxResponseBytes);
            }
            if (cause instanceof ErrorCodeException error
                    && isJsonRpcServer(server)
                    && "mcp_call_failed".equals(error.getReason())) {
                log.warn("MCP WebClient empty failure, fallback to JDK client, url={}", url);
                return postWithJdkHttpClient(url, payload, timeoutSec, maxResponseBytes);
            }
            if (cause instanceof ErrorCodeException error) {
                throw error;
            }
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "mcp_call_failed");
        } catch (RuntimeException ex) {
            if (isConnectionIssue(ex)) {
                log.warn("MCP WebClient failed, fallback to JDK client, url={}", url, ex);
                return postWithJdkHttpClient(url, payload, timeoutSec, maxResponseBytes);
            }
            throw ex;
        }
    }

    private byte[] serializeBody(Object body) {
        if (body == null) {
            return new byte[0];
        }
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (Exception ex) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "MCP 请求序列化失败");
        }
    }

    /**
     * 当 WebClient 连接异常时，使用 JDK HTTP 客户端作为兜底。
     */
    private Map<String, Object> postWithJdkHttpClient(String url,
                                                      byte[] payload,
                                                      long timeoutSec,
                                                      long maxResponseBytes) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSec))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body = readResponseBytes(response.body(), maxResponseBytes);
            if (response.statusCode() >= 400) {
                String message = body.length > 0 ? new String(body, StandardCharsets.UTF_8) : "mcp_call_failed";
                throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", message);
            }
            if (body.length == 0) {
                return null;
            }
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
            });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "mcp_call_interrupted");
        } catch (IOException ex) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "mcp_call_failed");
        }
    }

    private byte[] readResponseBytes(InputStream inputStream, long maxResponseBytes) throws IOException {
        if (inputStream == null) {
            return new byte[0];
        }
        try (InputStream input = inputStream; ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                if (stream.size() + read > maxResponseBytes) {
                    throw new ErrorCodeException(HttpStatus.PAYLOAD_TOO_LARGE,
                            "MCP_RESPONSE_TOO_LARGE", "mcp_response_too_large");
                }
                stream.write(buffer, 0, read);
            }
            return stream.toByteArray();
        }
    }

    private boolean isConnectionIssue(Throwable throwable) {
        if (throwable == null) {
            return false;
        }
        if (throwable instanceof java.net.SocketException || throwable instanceof IOException) {
            return true;
        }
        Throwable cause = throwable.getCause();
        if (cause != null && cause != throwable) {
            return isConnectionIssue(cause);
        }
        return false;
    }

    private Map<String, Object> readResponseAsMap(ByteArrayOutputStream stream) {
        if (stream == null || stream.size() == 0) {
            return null;
        }
        byte[] payload = stream.toByteArray();
        try {
            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
        } catch (IOException ex) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 响应解析失败");
        }
    }

    private <T> T convertResponse(Map<String, Object> response, Class<T> targetClass) {
        if (response == null) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 返回为空");
        }
        Object data = response.get("data");
        Object payload = data != null ? data : response;
        return objectMapper.convertValue(payload, targetClass);
    }

    /**
     * 解析调用策略。
     *
     * @return 调用策略
     */
    private CallStrategy resolveCallStrategy() {
        if (!StringUtils.hasText(callStrategy)) {
            return CallStrategy.REMOTE_FIRST;
        }
        String normalized = callStrategy.trim()
                .toLowerCase(Locale.ROOT)
                .replace('_', '-');
        return switch (normalized) {
            case "remote-only" -> CallStrategy.REMOTE_ONLY;
            case "local-only" -> CallStrategy.LOCAL_ONLY;
            case "local-first" -> CallStrategy.LOCAL_FIRST;
            case "remote-first" -> CallStrategy.REMOTE_FIRST;
            default -> CallStrategy.REMOTE_FIRST;
        };
    }

    /**
     * 判断是否需要触发本地回退。
     *
     * @param ex 异常信息
     * @return 是否触发回退
     */
    private boolean isFallbackTrigger(ErrorCodeException ex) {
        if (ex == null) {
            return false;
        }
        String code = ex.getErrorCode();
        if ("MCP_UNAVAILABLE".equals(code) || "CIRCUIT_OPEN".equals(code) || "NOT_FOUND".equals(code)) {
            return true;
        }
        return isToolNotFoundReason(ex.getReason());
    }

    /**
     * 判断错误原因是否为工具不存在。
     *
     * @param reason 错误原因
     * @return 是否命中工具不存在
     */
    private boolean isToolNotFoundReason(String reason) {
        if (!StringUtils.hasText(reason)) {
            return false;
        }
        String lower = reason.toLowerCase(Locale.ROOT);
        return lower.contains("tool not found")
                || lower.contains("not found")
                || reason.contains("工具不存在");
    }

    private boolean isRetryable(ErrorCodeException ex) {
        String code = ex.getErrorCode();
        return "MCP_UNAVAILABLE".equals(code) || "CIRCUIT_OPEN".equals(code);
    }

    private boolean isRemoteServer(McpServerProperties.McpServer server) {
        return remoteEnabled
                && server != null
                && StringUtils.hasText(server.getBaseUrl())
                && server.getBaseUrl().startsWith("http");
    }

    private boolean isJsonRpcServer(McpServerProperties.McpServer server) {
        if (server == null) {
            return false;
        }
        String protocol = server.getProtocol();
        return StringUtils.hasText(protocol) && MCP_PROTOCOL_JSONRPC.equalsIgnoreCase(protocol.trim());
    }

    private String normalizeServerId(String serverId) {
        if (StringUtils.hasText(serverId)) {
            return serverId.trim();
        }
        return "mcp-default";
    }

    private McpServerProperties.McpServer resolveServer(String serverId) {
        return serverProperties.getServers().stream()
                .filter(server -> serverId.equals(server.getId()))
                .findFirst()
                .orElse(null);
    }

    private void validateServer(McpServerProperties.McpServer server) {
        if (server == null || !server.isAvailable()) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE",
                    "MCP 服务不可用");
        }
    }

    private void validateAllowedHost(McpServerProperties.McpServer server, String baseUrl) {
        List<String> allowedHosts = server != null ? server.getAllowedHosts() : null;
        if (allowedHosts == null || allowedHosts.isEmpty()) {
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "MCP_FORBIDDEN_HOST",
                    "MCP 目标主机不在允许列表");
        }
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException ex) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "MCP 地址非法");
        }
        String host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "MCP 地址缺少主机");
        }
        boolean allowed = allowedHosts.stream()
                .filter(StringUtils::hasText)
                .anyMatch(allowedHost -> host.equalsIgnoreCase(allowedHost.trim()));
        if (!allowed) {
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "MCP_FORBIDDEN_HOST",
                    "MCP 目标主机不在允许列表");
        }
    }

    private long resolveMaxResponseBytes(McpServerProperties.McpServer server) {
        long configured = server != null ? server.getMaxResponseBytes() : 0;
        if (configured > 0) {
            return configured;
        }
        return 2 * 1024 * 1024L;
    }

    private int resolveStartIndex(String cursor, int totalSize) {
        if (!StringUtils.hasText(cursor)) {
            return 0;
        }
        try {
            int index = Integer.parseInt(cursor.trim());
            if (index < 0 || index >= totalSize) {
                return 0;
            }
            return index;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /**
     * 调用策略枚举。
     */
    private enum CallStrategy {
        REMOTE_ONLY,
        LOCAL_ONLY,
        REMOTE_FIRST,
        LOCAL_FIRST
    }

    /**
     * SSE 会话状态。
     */
    private static class SseSessionState {
        /**
         * 当前 sessionId。
         */
        private volatile String sessionId;
        /**
         * 最近刷新时间戳。
         */
        private volatile long lastRefreshTimeMs;
        /**
         * SSE 线程。
         */
        private volatile Thread worker;
        /**
         * SSE 流对象。
         */
        private volatile InputStream stream;
        /**
         * SSE 流锁。
         */
        private final Object streamLock = new Object();
    }
}
