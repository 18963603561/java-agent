package com.example.agent.api.http.controller;

import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.TracingPublisher;
import com.example.agent.runtime.engine.ApprovalDecisionRequest;
import com.example.agent.runtime.control.ExecutionControlService;
import com.example.agent.runtime.control.ExecutionControlState;
import com.example.agent.runtime.control.ExecutionControlStateResponse;
import com.example.agent.streaming.sse.EventStreamService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 审批决策控制器。
 */
@RestController
@Validated
public class ApprovalController {

    private static final Logger log = LoggerFactory.getLogger(ApprovalController.class);

    private final ExecutionControlService executionControlService;
    private final AuthService authService;
    private final ApplicationEventPublisher eventPublisher;
    private final EventStreamService eventStreamService;
    private final TracingPublisher tracingPublisher;

    public ApprovalController(ExecutionControlService executionControlService,
                              AuthService authService,
                              ApplicationEventPublisher eventPublisher,
                              EventStreamService eventStreamService,
                              TracingPublisher tracingPublisher) {
        this.executionControlService = executionControlService;
        this.authService = authService;
        this.eventPublisher = eventPublisher;
        this.eventStreamService = eventStreamService;
        this.tracingPublisher = tracingPublisher;
    }

    /**
     * 写入审批决策并发布事件。
     *
     * @param workflowId 工作流标识
     * @param decisionRequest 决策请求
     * @param apiKey API Key
     * @param exchange 请求上下文
     * @return 决策结果
     */
    @PostMapping("/api/v1/workflows/{workflowId}/approval/decision")
    public ApiResponse<ExecutionControlStateResponse> decide(@PathVariable("workflowId") String workflowId,
                                                             @RequestBody ApprovalDecisionRequest decisionRequest,
                                                             @RequestHeader(value = "X-API-Key", required = false)
                                                             String apiKey,
                                                             ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        if (decisionRequest == null || !StringUtils.hasText(decisionRequest.getDecision())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "decision 不能为空");
        }
        ExecutionControlState state = executionControlService.decideApproval(workflowId,
                decisionRequest.getDecision());
        publishDecisionEvent(tenantContext, workflowId, decisionRequest);
        ExecutionControlStateResponse response = new ExecutionControlStateResponse(
                workflowId, state, decisionRequest.getDecision());
        log.info("审批决策完成, tenantId={}, workflowId={}, decision={}, state={}",
                tenantContext.getTenantId(), workflowId, decisionRequest.getDecision(), state);
        return ApiResponse.success(response, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    private void publishDecisionEvent(TenantContext tenantContext,
                                      String workflowId,
                                      ApprovalDecisionRequest decisionRequest) {
        long seq = eventStreamService.nextSequence(tenantContext.getTenantId(), workflowId);
        StreamEvent event = new StreamEvent();
        event.setEventId(workflowId + ":" + seq);
        event.setSchemaVersion("v1");
        event.setWorkflowId(workflowId);
        event.setType(EventType.APPROVAL_DECISION);
        event.setTimestamp(Instant.now());
        event.setSeq(seq);
        event.setStreamId(workflowId);
        event.setTenantId(tenantContext.getTenantId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("decision", decisionRequest.getDecision());
        if (StringUtils.hasText(decisionRequest.getReason())) {
            payload.put("reason", decisionRequest.getReason());
        }
        attachTraceContext(payload, tenantContext);
        event.setPayload(payload);
        eventPublisher.publishEvent(event);
    }

    private void attachTraceContext(Map<String, Object> payload, TenantContext tenantContext) {
        if (payload == null || tenantContext == null) {
            return;
        }
        payload.putIfAbsent("traceId", resolveTraceId(tenantContext));
        payload.putIfAbsent("requestId", tenantContext.getRequestId());
    }

    private String resolveTraceId(TenantContext tenantContext) {
        if (tenantContext != null && StringUtils.hasText(tenantContext.getTraceId())) {
            return tenantContext.getTraceId();
        }
        return tracingPublisher.currentTraceId();
    }

    private TenantContext authenticate(ServerWebExchange exchange, String apiKey) {
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        return tenantContext;
    }

    private TenantContext getTenantContext(ServerWebExchange exchange) {
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }
}
