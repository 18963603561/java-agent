package com.example.agent.api.http.controller;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.orchestration.multiagent.dag.actor.control.DagControlService;
import com.example.agent.orchestration.multiagent.dag.actor.recovery.DagDeadLetterMessage;
import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * DAG 分布式运维接口控制器。
 * <p>用途：提供 mailbox、shards、leases、deadletters 运维查询能力。</p>
 */
@RestController
@Validated
public class DagDistributedOpsController {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DagDistributedOpsController.class);

    /**
     * 控制服务。
     */
    private final DagControlService dagControlService;

    /**
     * 鉴权服务。
     */
    private final AuthService authService;

    public DagDistributedOpsController(DagControlService dagControlService,
                                       AuthService authService) {
        this.dagControlService = dagControlService;
        this.authService = authService;
    }

    /**
     * 查询 mailbox 运维信息。
     */
    @GetMapping("/api/v1/workflows/dag/ops/mailbox")
    public ApiResponse<Map<String, Object>> mailbox(@RequestParam("workflowId") String workflowId,
                                                    @RequestParam("dagRunId") String dagRunId,
                                                    @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                    ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        Map<String, Object> data = new HashMap<>();
        data.put("workflowId", workflowId);
        data.put("dagRunId", dagRunId);
        data.put("runState", dagControlService.queryRunState(workflowId, dagRunId));
        data.put("pendingCount", dagControlService.queryPendingCount(dagRunId));
        data.put("handlerSummary", dagControlService.queryHandlerSummary());
        data.put("activeInstances", dagControlService.queryActiveInstances());
        log.info("DAG运维mailbox查询完成, tenantId={}, workflowId={}, dagRunId={}, pendingCount={}",
                tenantContext.getTenantId(),
                workflowId,
                dagRunId,
                data.get("pendingCount"));
        return ApiResponse.success(data, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询分片归属。
     */
    @GetMapping("/api/v1/workflows/dag/ops/shards")
    public ApiResponse<Map<String, Object>> shards(@RequestParam("workflowId") String workflowId,
                                                   @RequestParam("dagRunId") String dagRunId,
                                                   @RequestParam("nodeId") String nodeId,
                                                   @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        String owner = dagControlService.queryShardOwner(tenantContext.getTenantId(), workflowId, nodeId);
        Map<String, Object> data = new HashMap<>();
        data.put("workflowId", workflowId);
        data.put("dagRunId", dagRunId);
        data.put("nodeId", nodeId);
        data.put("ownerInstanceId", owner);
        data.put("activeInstances", dagControlService.queryActiveInstances());
        log.info("DAG运维shard查询完成, tenantId={}, workflowId={}, dagRunId={}, nodeId={}, ownerInstanceId={}",
                tenantContext.getTenantId(),
                workflowId,
                dagRunId,
                nodeId,
                owner);
        return ApiResponse.success(data, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询租约状态。
     */
    @GetMapping("/api/v1/workflows/dag/ops/leases")
    public ApiResponse<Map<String, Object>> leases(@RequestParam("workflowId") String workflowId,
                                                   @RequestParam("dagRunId") String dagRunId,
                                                   @RequestParam("nodeId") String nodeId,
                                                   @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                   ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        boolean owner = dagControlService.isLeaseOwner(dagRunId, nodeId);
        Map<String, Object> data = new HashMap<>();
        data.put("workflowId", workflowId);
        data.put("dagRunId", dagRunId);
        data.put("nodeId", nodeId);
        data.put("leaseOwnedByCurrentInstance", owner);
        log.info("DAG运维lease查询完成, tenantId={}, workflowId={}, dagRunId={}, nodeId={}, owner={}",
                tenantContext.getTenantId(),
                workflowId,
                dagRunId,
                nodeId,
                owner);
        return ApiResponse.success(data, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 查询死信列表。
     */
    @GetMapping("/api/v1/workflows/dag/ops/deadletters")
    public ApiResponse<Map<String, Object>> deadletters(@RequestParam("workflowId") String workflowId,
                                                        @RequestParam("dagRunId") String dagRunId,
                                                        @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                        ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        List<DagDeadLetterMessage> messages = dagControlService.queryDeadLetters(dagRunId);
        Map<String, Object> data = new HashMap<>();
        data.put("workflowId", workflowId);
        data.put("dagRunId", dagRunId);
        data.put("count", messages.size());
        data.put("messages", messages);
        log.info("DAG运维deadletters查询完成, tenantId={}, workflowId={}, dagRunId={}, deadLetterCount={}",
                tenantContext.getTenantId(),
                workflowId,
                dagRunId,
                messages.size());
        return ApiResponse.success(data, tenantContext.getTraceId(), tenantContext.getRequestId());
    }

    /**
     * 统一鉴权并注入租户上下文。
     */
    private TenantContext authenticate(ServerWebExchange exchange, String apiKey) {
        // 关键逻辑：先鉴权获取用户身份，再补齐租户上下文中的用户信息。
        UserContext userContext = authService.authenticate(apiKey, exchange);
        TenantContext tenantContext = getTenantContext(exchange);
        tenantContext.applyUserContext(userContext);
        return tenantContext;
    }

    /**
     * 获取租户上下文。
     */
    private TenantContext getTenantContext(ServerWebExchange exchange) {
        // 关键逻辑：未注入租户上下文时直接拒绝请求，防止越权查询。
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }
}
