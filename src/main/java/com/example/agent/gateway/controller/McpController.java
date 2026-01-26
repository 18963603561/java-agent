package com.example.agent.gateway.controller;

import com.example.agent.auth.AuthService;
import com.example.agent.auth.TenantContext;
import com.example.agent.auth.UserContext;
import com.example.agent.common.ApiResponse;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.tools.McpToolCallRequest;
import com.example.agent.tools.McpToolCallResponse;
import com.example.agent.tools.McpToolClient;
import com.example.agent.tools.McpToolListRequest;
import com.example.agent.tools.McpToolListResponse;
import com.example.agent.tools.hook.HookManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * MCP 工具接口。
 */
@RestController
@Validated
public class McpController {

    private static final Logger log = LoggerFactory.getLogger(McpController.class);

    private final McpToolClient mcpToolClient;
    private final AuthService authService;
    private final HookManager hookManager;

    public McpController(McpToolClient mcpToolClient,
                         AuthService authService,
                         HookManager hookManager) {
        this.mcpToolClient = mcpToolClient;
        this.authService = authService;
        this.hookManager = hookManager;
    }

    /**
     * 获取工具列表。
     *
     * @param request 列表请求
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 工具列表
     */
    @PostMapping("/api/v1/mcp/tools/list")
    public ApiResponse<McpToolListResponse> listTools(@RequestBody McpToolListRequest request,
                                                      @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                      ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        try {
            McpToolListResponse response = mcpToolClient.listTools(request, tenantContext);
            log.info("工具列表返回, tenantId={}, count={}",
                    tenantContext.getTenantId(), response.getTools().size());
            return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
        } catch (ErrorCodeException ex) {
            logMcpErrorIfNeeded(ex, tenantContext, request.getServerId(), null);
            throw ex;
        }
    }

    /**
     * 调用 MCP 工具。
     *
     * @param request 调用请求
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 调用结果
     */
    @PostMapping("/api/v1/mcp/tools/call")
    public ApiResponse<McpToolCallResponse> callTool(@RequestBody McpToolCallRequest request,
                                                     @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                     ServerWebExchange exchange) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        hookManager.preTool(tenantContext, null, request.getToolName());
        try {
            McpToolCallResponse response = mcpToolClient.callTool(request, tenantContext);
            hookManager.postTool(tenantContext, null, request.getToolName(),
                    response.getResult() == null ? null : response.getResult());
            log.info("工具调用完成, tenantId={}, tool={}, callId={}",
                    tenantContext.getTenantId(), request.getToolName(), request.getCallId());
            return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
        } catch (ErrorCodeException ex) {
            logMcpErrorIfNeeded(ex, tenantContext, request.getServerId(), request.getToolName());
            throw ex;
        }
    }

    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }

    private void logMcpErrorIfNeeded(ErrorCodeException ex,
                                     TenantContext tenantContext,
                                     String serverId,
                                     String toolName) {
        String code = ex.getErrorCode();
        if (!"MCP_UNAVAILABLE".equals(code) && !"CIRCUIT_OPEN".equals(code)) {
            return;
        }
        log.error("MCP_UNAVAILABLE, tenantId={}, userId={}, traceId={}, requestId={}, serverId={}, toolName={}, code={}",
                tenantContext.getTenantId(),
                tenantContext.getUserId(),
                tenantContext.getTraceId(),
                tenantContext.getRequestId(),
                serverId,
                toolName,
                code);
    }
}
