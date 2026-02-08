package com.example.agent.capabilities.tools.mcp.protocol;

import com.example.agent.common.error.ErrorCodeException;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.example.agent.capabilities.tools.mcp.McpServerProperties;
import com.example.agent.capabilities.tools.mcp.session.McpSseSessionManager;
import com.example.agent.capabilities.tools.mcp.transport.McpHttpTransport;

/**
 * MCP JSON-RPC 协议适配器。
 */
@Component
public class McpJsonRpcAdapter {

    private static final Logger log = LoggerFactory.getLogger(McpJsonRpcAdapter.class);
    private static final String JSONRPC_VERSION = "2.0";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String METHOD_INITIALIZE = "initialize";

    private final McpHttpTransport httpTransport;
    private final McpSseSessionManager sseSessionManager;
    /**
     * JSON-RPC 初始化状态缓存，避免重复握手。
     */
    private final ConcurrentMap<String, Boolean> initializedServers = new ConcurrentHashMap<>();
    /**
     * JSON-RPC 初始化锁，防止并发重复初始化。
     */
    private final ConcurrentMap<String, Object> initLocks = new ConcurrentHashMap<>();

    public McpJsonRpcAdapter(McpHttpTransport httpTransport,
                             McpSseSessionManager sseSessionManager) {
        this.httpTransport = httpTransport;
        this.sseSessionManager = sseSessionManager;
    }

    /**
     * 调用 JSON-RPC 列表接口。
     *
     * @param server 服务配置
     * @param params 调用参数
     * @param timeoutSeconds 超时秒数
     * @return 结果映射
     */
    public Map<String, Object> listTools(McpServerProperties.McpServer server,
                                         Map<String, Object> params,
                                         long timeoutSeconds) {
        ensureInitialized(server, timeoutSeconds);
        Map<String, Object> safeParams = params != null ? params : Map.of();
        Map<String, Object> response = postJsonRpc(server, METHOD_TOOLS_LIST, safeParams, null, timeoutSeconds);
        Map<String, Object> error = extractJsonRpcError(response);
        if (error != null && isJsonRpcInvalidParams(error) && !safeParams.isEmpty()) {
            log.warn("MCP tools/list params rejected, retry with empty params, serverId={}", server.getId());
            response = postJsonRpc(server, METHOD_TOOLS_LIST, Map.of(), null, timeoutSeconds);
            error = extractJsonRpcError(response);
        }
        Object result = resolveJsonRpcResult(response, error);
        return normalizeResultMap(result);
    }

    /**
     * 调用 JSON-RPC 工具执行接口。
     *
     * @param server 服务配置
     * @param params 调用参数
     * @param callId 调用标识
     * @param timeoutSeconds 超时秒数
     * @return 结果映射
     */
    public Map<String, Object> callTool(McpServerProperties.McpServer server,
                                        Map<String, Object> params,
                                        String callId,
                                        long timeoutSeconds) {
        ensureInitialized(server, timeoutSeconds);
        Map<String, Object> response = postJsonRpc(server, METHOD_TOOLS_CALL,
                params != null ? params : Map.of(), callId, timeoutSeconds);
        Map<String, Object> error = extractJsonRpcError(response);
        Object result = resolveJsonRpcResult(response, error);
        return normalizeResultMap(result);
    }

    /**
     * 解析 JSON-RPC 返回的调用标识。
     *
     * @param requestCallId 请求调用标识
     * @param response 响应映射
     * @return 调用标识
     */
    public String resolveCallId(String requestCallId, Map<String, Object> response) {
        if (StringUtils.hasText(requestCallId)) {
            return requestCallId;
        }
        if (response != null && response.get("id") != null) {
            return response.get("id").toString();
        }
        return null;
    }

    private void ensureInitialized(McpServerProperties.McpServer server, long timeoutSeconds) {
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
                Map<String, Object> response = postJsonRpc(server, METHOD_INITIALIZE, Map.of(), null, timeoutSeconds);
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
                                            long timeoutSeconds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("jsonrpc", JSONRPC_VERSION);
        payload.put("id", createJsonRpcId(method, callId));
        payload.put("method", method);
        payload.put("params", params != null ? params : Map.of());
        String url = resolveJsonRpcUrl(server, timeoutSeconds);
        long maxResponseBytes = httpTransport.resolveMaxResponseBytes(server);
        return httpTransport.post(url, payload, timeoutSeconds, maxResponseBytes);
    }

    private String resolveJsonRpcUrl(McpServerProperties.McpServer server, long timeoutSeconds) {
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
        String sessionId = sseSessionManager.ensureSession(server, timeoutSeconds);
        String paramName = StringUtils.hasText(server.getSessionParamName())
                ? server.getSessionParamName()
                : "sessionId";
        return sseSessionManager.appendQueryParam(baseUrl, paramName, sessionId);
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

    private boolean isJsonRpcMethodNotFound(Map<String, Object> error) {
        Integer code = resolveJsonRpcErrorCode(error);
        if (code != null && code == -32601) {
            return true;
        }
        String message = resolveJsonRpcErrorMessage(error, null);
        return message != null && message.toLowerCase(Locale.ROOT).contains("method not found");
    }

    private boolean isJsonRpcInvalidParams(Map<String, Object> error) {
        Integer code = resolveJsonRpcErrorCode(error);
        if (code != null && code == -32602) {
            return true;
        }
        String message = resolveJsonRpcErrorMessage(error, null);
        return message != null && message.toLowerCase(Locale.ROOT).contains("invalid params");
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

    /**
     * 校验并解析主机。
     *
     * @param baseUrl 地址
     * @return 主机
     */
    public String extractHost(String baseUrl) {
        URI uri = URI.create(baseUrl);
        return uri.getHost();
    }
}
