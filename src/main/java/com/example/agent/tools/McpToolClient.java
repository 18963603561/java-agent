package com.example.agent.tools;

import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.auth.TenantContext;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.governance.CircuitBreakerManager;
import com.example.agent.governance.RateLimitService;
import com.example.agent.runtime.RetryPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * MCP 工具客户端，负责工具列表与调用的统一入口。
 */
@Service
public class McpToolClient {

    private static final Logger log = LoggerFactory.getLogger(McpToolClient.class);

    private final McpServerProperties serverProperties;
    private final ToolRegistry toolRegistry;
    private final RateLimitService rateLimitService;
    private final CircuitBreakerManager circuitBreakerManager;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

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
        log.info("MCP tools/list start, tenantId={}, serverId={}", tenantContext.getTenantId(), serverId);

        RetryPolicy retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                McpToolListResponse response = isRemoteServer(server)
                        ? listToolsRemote(server, request)
                        : listToolsLocal(request);
                log.info("MCP tools/list end, tenantId={}, serverId={}, count={}",
                        tenantContext.getTenantId(), serverId, response.getTools().size());
                return response;
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
        if (!circuitBreakerManager.allow(rateKey)) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "CIRCUIT_OPEN", "熔断已开启");
        }

        log.info("MCP tools/call start, tenantId={}, serverId={}, toolName={}, callId={}",
                tenantContext.getTenantId(), serverId, toolName, request.getCallId());

        RetryPolicy retryPolicy = new RetryPolicy(baseDelayMs, maxDelayMs, jitterRatio);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                McpToolCallResponse response = isRemoteServer(server)
                        ? callToolRemote(server, request)
                        : callToolLocal(request);
                circuitBreakerManager.recordSuccess(rateKey);
                log.info("MCP tools/call success, tenantId={}, serverId={}, toolName={}, callId={}",
                        tenantContext.getTenantId(), serverId, toolName, request.getCallId());
                return response;
            } catch (ErrorCodeException ex) {
                circuitBreakerManager.recordFailure(rateKey);
                if (isRetryable(ex) && attempt < maxAttempts) {
                    log.warn("MCP tools/call retry, tenantId={}, serverId={}, toolName={}, attempt={}, code={}",
                            tenantContext.getTenantId(), serverId, toolName, attempt, ex.getErrorCode());
                    retryPolicy.sleepBeforeRetry(attempt);
                    continue;
                }
                throw ex;
            } catch (Exception ex) {
                circuitBreakerManager.recordFailure(rateKey);
                log.error("MCP tools/call failed, tenantId={}, serverId={}, toolName={}",
                        tenantContext.getTenantId(), serverId, toolName, ex);
                throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE",
                        "MCP 工具不可用");
            }
        }
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
        Map<String, Object> response = post(server.getBaseUrl(), "/tools/list", request, timeoutSeconds);
        return convertResponse(response, McpToolListResponse.class);
    }

    private McpToolCallResponse callToolRemote(McpServerProperties.McpServer server, McpToolCallRequest request) {
        long timeout = request.getTimeoutMs() != null && request.getTimeoutMs() > 0
                ? request.getTimeoutMs() / 1000
                : timeoutSeconds;
        Map<String, Object> response = post(server.getBaseUrl(), "/tools/call", request, timeout);
        return convertResponse(response, McpToolCallResponse.class);
    }

    private Map<String, Object> post(String baseUrl, String path, Object body, long timeoutSec) {
        WebClient client = webClientBuilder.baseUrl(baseUrl).build();
        return client.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        result -> result.bodyToMono(String.class)
                                .defaultIfEmpty("mcp_call_failed")
                                .flatMap(message -> Mono.error(new ErrorCodeException(
                                        HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", message))))
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                })
                .timeout(Duration.ofSeconds(timeoutSec))
                .block();
    }

    private <T> T convertResponse(Map<String, Object> response, Class<T> targetClass) {
        if (response == null) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 返回为空");
        }
        Object data = response.get("data");
        Object payload = data != null ? data : response;
        return objectMapper.convertValue(payload, targetClass);
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
