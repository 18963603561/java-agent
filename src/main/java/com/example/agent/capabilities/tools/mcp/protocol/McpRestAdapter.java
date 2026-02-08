package com.example.agent.capabilities.tools.mcp.protocol;

import com.example.agent.common.error.ErrorCodeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.example.agent.capabilities.tools.mcp.McpServerProperties;
import com.example.agent.capabilities.tools.mcp.session.McpSseSessionManager;
import com.example.agent.capabilities.tools.mcp.transport.McpHttpTransport;

/**
 * MCP REST 协议适配器。
 */
@Component
public class McpRestAdapter {

    private final McpHttpTransport httpTransport;
    private final McpSseSessionManager sseSessionManager;
    private final ObjectMapper objectMapper;

    public McpRestAdapter(McpHttpTransport httpTransport,
                          McpSseSessionManager sseSessionManager,
                          ObjectMapper objectMapper) {
        this.httpTransport = httpTransport;
        this.sseSessionManager = sseSessionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用远端列表接口。
     *
     * @param server 服务配置
     * @param requestBody 请求体
     * @param timeoutSeconds 超时秒数
     * @return 响应映射
     */
    public Map<String, Object> listTools(McpServerProperties.McpServer server,
                                         Object requestBody,
                                         long timeoutSeconds) {
        String url = buildRestUrl(server, "tools/list", timeoutSeconds);
        long maxResponseBytes = httpTransport.resolveMaxResponseBytes(server);
        return httpTransport.post(url, requestBody, timeoutSeconds, maxResponseBytes);
    }

    /**
     * 调用远端工具执行接口。
     *
     * @param server 服务配置
     * @param requestBody 请求体
     * @param timeoutSeconds 超时秒数
     * @return 响应映射
     */
    public Map<String, Object> callTool(McpServerProperties.McpServer server,
                                        Object requestBody,
                                        long timeoutSeconds) {
        String url = buildRestUrl(server, "tools/call", timeoutSeconds);
        long maxResponseBytes = httpTransport.resolveMaxResponseBytes(server);
        return httpTransport.post(url, requestBody, timeoutSeconds, maxResponseBytes);
    }

    /**
     * 响应转换。
     *
     * @param response 响应映射
     * @param targetClass 目标类型
     * @param <T> 类型参数
     * @return 目标对象
     */
    public <T> T convertResponse(Map<String, Object> response, Class<T> targetClass) {
        if (response == null) {
            throw new ErrorCodeException(HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 返回为空");
        }
        Object data = response.get("data");
        Object payload = data != null ? data : response;
        return objectMapper.convertValue(payload, targetClass);
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

    private String buildRestUrl(McpServerProperties.McpServer server,
                                String path,
                                long timeoutSeconds) {
        String baseUrl = server != null ? server.getBaseUrl() : null;
        if (!StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path.substring(1) : path;
        String url = normalizedBase + "/" + normalizedPath;
        if (server != null && StringUtils.hasText(server.getSseUrl())) {
            String sessionId = sseSessionManager.ensureSession(server, timeoutSeconds);
            String paramName = StringUtils.hasText(server.getSessionParamName())
                    ? server.getSessionParamName()
                    : "sessionId";
            url = sseSessionManager.appendQueryParam(url, paramName, sessionId);
        }
        return url;
    }
}

