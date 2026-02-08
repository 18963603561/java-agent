package com.example.agent.capabilities.tools.mcp;

import com.example.agent.capabilities.tools.mcp.protocol.McpJsonRpcAdapter;
import com.example.agent.capabilities.tools.mcp.protocol.McpRestAdapter;
import com.example.agent.capabilities.tools.mcp.strategy.McpCallStrategy;
import com.example.agent.capabilities.tools.mcp.strategy.McpCallStrategyResolver;
import com.example.agent.capabilities.tools.model.ToolDefinition;
import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.governance.circuitbreaker.CircuitBreakerManager;
import com.example.agent.governance.ratelimit.RateLimitService;
import com.example.agent.runtime.recovery.RetryPolicy;
import com.example.agent.capabilities.tools.validation.ToolRequestValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * MCP 工具客户端，负责工具列表与调用的统一入口。
 */
@Service
public class McpToolClient {

    private static final Logger log = LoggerFactory.getLogger(McpToolClient.class);
    private static final String MCP_PROTOCOL_JSONRPC = "jsonrpc";

    private final McpServerProperties serverProperties;
    private final ToolRegistry toolRegistry;
    private final RateLimitService rateLimitService;
    private final CircuitBreakerManager circuitBreakerManager;
    private final ObjectMapper objectMapper;
    private final McpCallStrategyResolver strategyResolver;
    private final McpJsonRpcAdapter jsonRpcAdapter;
    private final McpRestAdapter restAdapter;

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
                         ObjectMapper objectMapper,
                         McpCallStrategyResolver strategyResolver,
                         McpJsonRpcAdapter jsonRpcAdapter,
                         McpRestAdapter restAdapter) {
        this.serverProperties = serverProperties;
        this.toolRegistry = toolRegistry;
        this.rateLimitService = rateLimitService;
        this.circuitBreakerManager = circuitBreakerManager;
        this.objectMapper = objectMapper;
        this.strategyResolver = strategyResolver;
        this.jsonRpcAdapter = jsonRpcAdapter;
        this.restAdapter = restAdapter;
    }

    /**
     * 获取工具列表。
     *
     * @param request 列表请求
     * @param tenantContext 租户上下文
     * @return 工具列表响应
     */
    public McpToolListResponse listTools(McpToolListRequest request, TenantContext tenantContext) {
        McpToolListRequest validatedRequest = ToolRequestValidator.requireNonNull(request, "request");
        TenantContext validatedTenant = ToolRequestValidator.requireTenantContext(tenantContext);
        String serverId = normalizeServerId(validatedRequest.getServerId());
        McpServerProperties.McpServer server = resolveServer(serverId);
        validateServer(server);
        McpCallStrategy strategy = strategyResolver.resolve(callStrategy);
        boolean remoteAvailable = isRemoteServer(server);
        boolean allowRemote = strategy != McpCallStrategy.LOCAL_ONLY && remoteAvailable;
        boolean allowLocal = strategy != McpCallStrategy.REMOTE_ONLY;
        log.info("MCP tools/list start, tenantId={}, serverId={}, strategy={}, mergeLocal={}",
                validatedTenant.getTenantId(), serverId, strategy.name().toLowerCase(Locale.ROOT), mergeLocalTools);
        McpToolListResponse response = executeListByStrategy(
                strategy, allowRemote, allowLocal, server,
                validatedRequest, validatedTenant, serverId);

        log.info("MCP tools/list end, tenantId={}, serverId={}, count={}",
                validatedTenant.getTenantId(), serverId, response.getTools().size());
        return response;
    }

    /**
     * 仅调用远程 MCP 获取工具列表，不走本地合并与回退。
     *
     * @param server MCP 服务器
     * @param request 列表请求
     * @param tenantContext 租户上下文（为空时使用系统上下文）
     * @return 远程工具列表
     *
     * <p>注意：server 为空会抛出 MCP_UNAVAILABLE。</p>
     */
    public McpToolListResponse listToolsRemoteOnly(McpServerProperties.McpServer server,
                                                   McpToolListRequest request,
                                                   TenantContext tenantContext) {
        if (server == null) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 服务不可用");
        }
        if (!isRemoteServer(server)) {
            return new McpToolListResponse(List.of(), null, false);
        }
        validateServer(server);
        TenantContext effectiveContext = tenantContext != null
                ? tenantContext
                : new TenantContext("system", "system", List.of("system"), "mcp-sync", null);
        McpToolListRequest safeRequest = request != null ? request : new McpToolListRequest();
        return listToolsRemoteWithRetry(server, safeRequest, effectiveContext, server.getId());
    }

    /**
     * 调用工具。
     *
     * @param request 调用请求
     * @param tenantContext 租户上下文
     * @return 调用响应
     */
    public McpToolCallResponse callTool(McpToolCallRequest request, TenantContext tenantContext) {
        McpToolCallRequest validatedRequest = ToolRequestValidator.requireNonNull(request, "request");
        TenantContext validatedTenant = ToolRequestValidator.requireTenantContext(tenantContext);
        String serverId = normalizeServerId(validatedRequest.getServerId());
        McpServerProperties.McpServer server = resolveServer(serverId);
        validateServer(server);
        String toolName = ToolRequestValidator.requireNonBlank(validatedRequest.getToolName(), "request.toolName");
        validatedRequest.setToolName(toolName);
        String rateKey = validatedTenant.getTenantId() + ":" + toolName;
        if (!rateLimitService.allow(rateKey)) {
            throw new ErrorCodeException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "请求过于频繁");
        }
        McpCallStrategy strategy = strategyResolver.resolve(callStrategy);
        boolean remoteAvailable = isRemoteServer(server);
        boolean localAvailable = toolRegistry.hasTool(toolName);
        boolean allowRemote = strategy != McpCallStrategy.LOCAL_ONLY && remoteAvailable;
        boolean allowLocal = strategy != McpCallStrategy.REMOTE_ONLY;

        log.info("MCP tools/call start, tenantId={}, serverId={}, toolName={}, callId={}, strategy={}, localAvailable={}",
                validatedTenant.getTenantId(), serverId, toolName, validatedRequest.getCallId(),
                strategy.name().toLowerCase(Locale.ROOT), localAvailable);
        return executeCallByStrategy(strategy, allowRemote, allowLocal, localAvailable,
                server, validatedRequest, validatedTenant, serverId, rateKey);
    }

    /**
     * 按策略执行工具列表查询。
     */
    private McpToolListResponse executeListByStrategy(McpCallStrategy strategy,
                                                      boolean allowRemote,
                                                      boolean allowLocal,
                                                      McpServerProperties.McpServer server,
                                                      McpToolListRequest validatedRequest,
                                                      TenantContext validatedTenant,
                                                      String serverId) {
        if (strategy == McpCallStrategy.LOCAL_ONLY || !allowRemote) {
            return listToolsLocal(validatedRequest);
        }
        if (strategy == McpCallStrategy.LOCAL_FIRST) {
            return executeListLocalFirst(allowRemote, server, validatedRequest, validatedTenant, serverId);
        }
        return executeListRemotePreferred(allowLocal, server, validatedRequest, validatedTenant, serverId);
    }

    /**
     * 本地优先策略执行列表查询。
     */
    private McpToolListResponse executeListLocalFirst(boolean allowRemote,
                                                      McpServerProperties.McpServer server,
                                                      McpToolListRequest validatedRequest,
                                                      TenantContext validatedTenant,
                                                      String serverId) {
        McpToolListResponse local = listToolsLocal(validatedRequest);
        if (!mergeLocalTools || !allowRemote) {
            return local;
        }
        try {
            McpToolListResponse remote = listToolsRemoteWithRetry(server, validatedRequest, validatedTenant, serverId);
            return mergeToolLists(local, remote);
        } catch (ErrorCodeException ex) {
            log.warn("MCP tools/list remote merge failed, fallback local, tenantId={}, serverId={}, code={}",
                    validatedTenant.getTenantId(), serverId, ex.getErrorCode());
            return local;
        }
    }

    /**
     * 远端优先策略执行列表查询。
     */
    private McpToolListResponse executeListRemotePreferred(boolean allowLocal,
                                                           McpServerProperties.McpServer server,
                                                           McpToolListRequest validatedRequest,
                                                           TenantContext validatedTenant,
                                                           String serverId) {
        try {
            McpToolListResponse remote = listToolsRemoteWithRetry(server, validatedRequest, validatedTenant, serverId);
            if (mergeLocalTools && allowLocal) {
                McpToolListResponse local = listToolsLocal(validatedRequest);
                return mergeToolLists(remote, local);
            }
            return remote;
        } catch (ErrorCodeException ex) {
            if (allowLocal && fallbackToLocal && strategyResolver.isFallbackTrigger(ex)) {
                log.warn("MCP tools/list fallback to local, tenantId={}, serverId={}, code={}",
                        validatedTenant.getTenantId(), serverId, ex.getErrorCode());
                return listToolsLocal(validatedRequest);
            }
            throw ex;
        }
    }

    /**
     * 按策略执行工具调用。
     */
    private McpToolCallResponse executeCallByStrategy(McpCallStrategy strategy,
                                                      boolean allowRemote,
                                                      boolean allowLocal,
                                                      boolean localAvailable,
                                                      McpServerProperties.McpServer server,
                                                      McpToolCallRequest validatedRequest,
                                                      TenantContext validatedTenant,
                                                      String serverId,
                                                      String rateKey) {
        if (strategy == McpCallStrategy.LOCAL_ONLY) {
            return callToolLocal(validatedRequest);
        }
        if (strategy == McpCallStrategy.LOCAL_FIRST) {
            return executeCallLocalFirst(allowRemote, localAvailable, server,
                    validatedRequest, validatedTenant, serverId, rateKey);
        }
        return executeCallRemotePreferred(allowRemote, allowLocal, localAvailable, server,
                validatedRequest, validatedTenant, serverId, rateKey);
    }

    /**
     * 本地优先策略执行工具调用。
     */
    private McpToolCallResponse executeCallLocalFirst(boolean allowRemote,
                                                      boolean localAvailable,
                                                      McpServerProperties.McpServer server,
                                                      McpToolCallRequest validatedRequest,
                                                      TenantContext validatedTenant,
                                                      String serverId,
                                                      String rateKey) {
        if (localAvailable) {
            try {
                McpToolCallResponse localResponse = callToolLocal(validatedRequest);
                log.info("MCP tools/call local success, tenantId={}, toolName={}, callId={}",
                        validatedTenant.getTenantId(), validatedRequest.getToolName(), validatedRequest.getCallId());
                return localResponse;
            } catch (Exception ex) {
                log.warn("MCP tools/call local failed, try remote, tenantId={}, toolName={}, callId={}",
                        validatedTenant.getTenantId(), validatedRequest.getToolName(), validatedRequest.getCallId(), ex);
            }
        }
        if (!allowRemote) {
            throw unavailableForCall(localAvailable);
        }
        return callToolRemoteWithRetry(server, validatedRequest, validatedTenant, serverId, rateKey);
    }

    /**
     * 远端优先策略执行工具调用。
     */
    private McpToolCallResponse executeCallRemotePreferred(boolean allowRemote,
                                                           boolean allowLocal,
                                                           boolean localAvailable,
                                                           McpServerProperties.McpServer server,
                                                           McpToolCallRequest validatedRequest,
                                                           TenantContext validatedTenant,
                                                           String serverId,
                                                           String rateKey) {
        if (!allowRemote) {
            if (allowLocal && localAvailable) {
                log.warn("MCP tools/call remote disabled, fallback local, tenantId={}, toolName={}, callId={}",
                        validatedTenant.getTenantId(), validatedRequest.getToolName(), validatedRequest.getCallId());
                return callToolLocal(validatedRequest);
            }
            throw unavailableForCall(localAvailable);
        }
        try {
            McpToolCallResponse response = callToolRemoteWithRetry(server, validatedRequest, validatedTenant, serverId, rateKey);
            log.info("MCP tools/call success, tenantId={}, serverId={}, toolName={}, callId={}",
                    validatedTenant.getTenantId(), serverId, validatedRequest.getToolName(), validatedRequest.getCallId());
            return response;
        } catch (ErrorCodeException ex) {
            if (allowLocal && localAvailable && fallbackToLocal && strategyResolver.isFallbackTrigger(ex)) {
                log.warn("MCP tools/call fallback local, tenantId={}, serverId={}, toolName={}, callId={}, code={}",
                        validatedTenant.getTenantId(), serverId, validatedRequest.getToolName(),
                        validatedRequest.getCallId(), ex.getErrorCode());
                return callToolLocal(validatedRequest);
            }
            throw ex;
        }
    }

    private ErrorCodeException unavailableForCall(boolean localAvailable) {
        if (!localAvailable) {
            return new ErrorCodeException(HttpStatus.NOT_FOUND, "NOT_FOUND", "工具不存在");
        }
        return new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "远端 MCP 不可用");
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
                if (strategyResolver.isRetryable(ex) && attempt < maxAttempts) {
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
                if (strategyResolver.isRetryable(ex) && attempt < maxAttempts) {
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
        List<ToolDefinition> merged = new ArrayList<>();
        Map<String, ToolDefinition> seen = new HashMap<>();
        if (primary != null && primary.getTools() != null) {
            for (ToolDefinition tool : primary.getTools()) {
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
            for (ToolDefinition tool : secondary.getTools()) {
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
        List<ToolDefinition> tools = new ArrayList<>(toolRegistry.listDefinitions());
        int startIndex = resolveStartIndex(request.getCursor(), tools.size());
        int size = request.getSize() != null && request.getSize() > 0 ? request.getSize() : tools.size();
        int endIndex = Math.min(startIndex + size, tools.size());
        List<ToolDefinition> page = startIndex < endIndex
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
        validateRemoteAccess(server);
        if (isJsonRpcServer(server)) {
            Map<String, Object> params = buildListParams(request);
            Map<String, Object> resultMap = jsonRpcAdapter.listTools(server, params, timeoutSeconds);
            return objectMapper.convertValue(resultMap, McpToolListResponse.class);
        }
        Map<String, Object> response = restAdapter.listTools(server, request, timeoutSeconds);
        return restAdapter.convertResponse(response, McpToolListResponse.class);
    }

    private McpToolCallResponse callToolRemote(McpServerProperties.McpServer server, McpToolCallRequest request) {
        validateRemoteAccess(server);
        long timeout = request.getTimeoutMs() != null && request.getTimeoutMs() > 0
                ? request.getTimeoutMs() / 1000
                : timeoutSeconds;
        if (isJsonRpcServer(server)) {
            Map<String, Object> params = buildCallParams(request);
            Map<String, Object> resultMap = jsonRpcAdapter.callTool(server, params, request.getCallId(), timeout);
            return new McpToolCallResponse(request.getCallId(), "SUCCESS", resultMap, null);
        }
        Map<String, Object> response = restAdapter.callTool(server, request, timeout);
        return restAdapter.convertResponse(response, McpToolCallResponse.class);
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
        if (serverProperties == null || serverProperties.getServers() == null) {
            return null;
        }
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

    private void validateRemoteAccess(McpServerProperties.McpServer server) {
        if (server == null) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE",
                    "MCP 服务不可用");
        }
        validateAllowedHost(server, server.getBaseUrl());
        if (StringUtils.hasText(server.getSseUrl())) {
            validateAllowedHost(server, server.getSseUrl());
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
}
