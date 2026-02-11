package com.example.agent.api.http.controller;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.common.response.ApiResponse;
import com.example.agent.orchestration.multiagent.dag.actor.control.DagControlCommand;
import com.example.agent.orchestration.multiagent.dag.actor.control.DagControlResult;
import com.example.agent.orchestration.multiagent.dag.actor.control.DagControlService;
import com.example.agent.security.auth.AuthService;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.security.auth.UserContext;
import java.util.concurrent.atomic.AtomicLong;
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
 * DAG 控制接口控制器。
 * <p>用途：提供 pause、resume、rebalance、recover、replay_deadletter 控制命令入口。</p>
 */
@RestController
@Validated
public class DagControlController {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(DagControlController.class);

    /**
     * 鉴权服务。
     */
    private final AuthService authService;

    /**
     * DAG 控制服务。
     */
    private final DagControlService dagControlService;

    public DagControlController(AuthService authService,
                                DagControlService dagControlService) {
        this.authService = authService;
        this.dagControlService = dagControlService;
    }

    /**
     * 执行控制命令。
     */
    @PostMapping("/api/v1/workflows/dag/control")
    public ApiResponse<DagControlResult> control(@RequestBody DagControlCommand command,
                                                 @RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                                 ServerWebExchange exchange) {
        TenantContext tenantContext = authenticate(exchange, apiKey);
        DagControlResult result = dagControlService.execute(command, tenantContext, new AtomicLong(0L));
        log.info("DAG控制命令执行完成, tenantId={}, workflowId={}, dagRunId={}, command={}, status={}, deduplicated={}",
                tenantContext.getTenantId(),
                result.getWorkflowId(),
                result.getDagRunId(),
                result.getCommand(),
                result.getStatus(),
                result.isDeduplicated());
        return ApiResponse.success(result, tenantContext.getTraceId(), tenantContext.getRequestId());
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
        // 关键逻辑：未注入租户上下文时直接拒绝请求，防止越权访问。
        TenantContext context = exchange.getAttribute(TenantContext.CONTEXT_KEY);
        if (context == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST, "TENANT_MISSING", "租户标识缺失");
        }
        return context;
    }
}
