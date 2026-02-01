package com.example.agent.gateway.controller;

import com.example.agent.auth.AuthService;
import com.example.agent.auth.TenantContext;
import com.example.agent.auth.UserContext;
import com.example.agent.common.ApiResponse;
import com.example.agent.common.ErrorCodeException;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import com.example.agent.runtime.StepRecord;
import com.example.agent.runtime.StepState;
import com.example.agent.streaming.EventStreamService;
import com.example.agent.tools.McpToolCallRequest;
import com.example.agent.tools.McpToolCallResponse;
import com.example.agent.tools.McpToolClient;
import com.example.agent.tools.McpToolListRequest;
import com.example.agent.tools.McpToolListResponse;
import com.example.agent.tools.hook.HookManager;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;

    public McpController(McpToolClient mcpToolClient,
                         AuthService authService,
                         HookManager hookManager,
                         ApplicationEventPublisher eventPublisher,
                         EventStreamService eventStreamService) {
        this.mcpToolClient = mcpToolClient;
        this.authService = authService;
        this.hookManager = hookManager;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
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
            // 外部接口调用：请求工具服务获取工具列表
            McpToolListResponse response = mcpToolClient.listTools(request, tenantContext);
            log.info("工具列表返回, tenantId={}, count={}",
                    tenantContext.getTenantId(), response.getTools().size());
            return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
        // 异常捕获：记录上下文并按当前策略处理
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

        String workflowId = resolveWorkflowId(request);
        StepRecord stepRecord = buildStepRecord(tenantContext, workflowId, request.getCallId());
        hookManager.preTool(tenantContext, stepRecord, request.getToolName());
        Map<String, Object> invokedPayload = new java.util.HashMap<>();
        invokedPayload.put("tool", request.getToolName());
        if (request.getCallId() != null) {
            invokedPayload.put("callId", request.getCallId());
        }
        if (request.getServerId() != null) {
            invokedPayload.put("serverId", request.getServerId());
        }
        if (request.getArguments() != null) {
            invokedPayload.put("arguments", request.getArguments());
        }
        publishToolEvent(tenantContext, workflowId, EventType.TOOL_INVOKED, invokedPayload);
        try {
            // 外部接口调用：请求工具服务执行工具
            McpToolCallResponse response = mcpToolClient.callTool(request, tenantContext);
            Map<String, Object> observationPayload = new java.util.HashMap<>();
            observationPayload.put("tool", request.getToolName());
            if (request.getCallId() != null) {
                observationPayload.put("callId", request.getCallId());
            }
            if (request.getServerId() != null) {
                observationPayload.put("serverId", request.getServerId());
            }
            if (response.getResult() != null) {
                observationPayload.put("result", response.getResult());
            }
            publishToolEvent(tenantContext, workflowId, EventType.TOOL_OBSERVATION, observationPayload);
            hookManager.postTool(tenantContext, stepRecord, request.getToolName(),
                    response.getResult() == null ? null : response.getResult());
            log.info("工具调用完成, tenantId={}, tool={}, callId={}",
                    tenantContext.getTenantId(), request.getToolName(), request.getCallId());
            return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
        // 异常捕获：记录上下文并按当前策略处理
        } catch (ErrorCodeException ex) {
            Map<String, Object> errorPayload = new java.util.HashMap<>();
            errorPayload.put("tool", request.getToolName());
            if (request.getCallId() != null) {
                errorPayload.put("callId", request.getCallId());
            }
            if (request.getServerId() != null) {
                errorPayload.put("serverId", request.getServerId());
            }
            if (ex.getErrorCode() != null) {
                errorPayload.put("errorCode", ex.getErrorCode());
            }
            if (ex.getReason() != null) {
                errorPayload.put("error", ex.getReason());
            }
            publishToolEvent(tenantContext, workflowId, EventType.TOOL_ERROR, errorPayload);
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

    private void publishToolEvent(TenantContext tenantContext,
                                  String workflowId,
                                  EventType type,
                                  Map<String, Object> payload) {
        if (workflowId == null) {
            return;
        }
        if (payload != null) {
            payload.putIfAbsent("traceId", tenantContext.getTraceId());
            payload.putIfAbsent("requestId", tenantContext.getRequestId());
        }
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(type);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }

    private String resolveWorkflowId(McpToolCallRequest request) {
        if (request != null && request.getArguments() != null) {
            Object value = request.getArguments().get("workflowId");
            if (value instanceof String workflowId && !workflowId.isBlank()) {
                return workflowId;
            }
        }
        if (request != null && request.getCallId() != null && !request.getCallId().isBlank()) {
            return "mcp-" + request.getCallId();
        }
        return "mcp-unknown";
    }

    private StepRecord buildStepRecord(TenantContext tenantContext, String workflowId, String callId) {
        StepRecord record = new StepRecord();
        record.setStepId(callId != null ? callId : "mcp-step");
        record.setWorkflowId(workflowId);
        record.setStatus(StepState.STARTED);
        record.setAttempt(1);
        record.setTenantId(tenantContext.getTenantId());
        record.setStartedAt(Instant.now());
        return record;
    }
}
