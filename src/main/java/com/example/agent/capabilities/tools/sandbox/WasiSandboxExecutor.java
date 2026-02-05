package com.example.agent.capabilities.tools.sandbox;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * WASI 沙箱执行器，提供基础资源限制校验。
 */
@Component
public class WasiSandboxExecutor {

    private static final Logger log = LoggerFactory.getLogger(WasiSandboxExecutor.class);

    private final SandboxProperties sandboxProperties;
    private final MetricsPublisher metricsPublisher;

    public WasiSandboxExecutor(SandboxProperties sandboxProperties, MetricsPublisher metricsPublisher) {
        this.sandboxProperties = sandboxProperties;
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 执行沙箱检查并返回执行结果。
     *
     * @param request 沙箱请求
     * @param tenantContext 租户上下文
     * @return 沙箱执行结果
     */
    public SandboxResult execute(SandboxRequest request, TenantContext tenantContext) {
        if (!sandboxProperties.isEnabled()) {
            return new SandboxResult("SKIPPED", Map.of("tool", request.getToolName()), null);
        }
        if (isBlocked(request.getToolName())) {
            metricsPublisher.increment("sandbox.violation.count");
            log.error("沙箱拒绝, tenantId={}, userId={}, traceId={}, requestId={}, toolName={}",
                    tenantContext.getTenantId(),
                    tenantContext.getUserId(),
                    tenantContext.getTraceId(),
                    tenantContext.getRequestId(),
                    request.getToolName());
            throw new ErrorCodeException(HttpStatus.FORBIDDEN, "SANDBOX_DENIED", "沙箱拒绝执行");
        }
        Map<String, Object> output = Map.of(
                "tool", request.getToolName(),
                "echo", request.getInput() == null ? Map.of() : request.getInput()
        );
        return new SandboxResult("OK", output, null);
    }

    private boolean isBlocked(String toolName) {
        if (toolName == null) {
            return false;
        }
        return sandboxProperties.getBlockedTools().stream()
                .anyMatch(blocked -> blocked.equalsIgnoreCase(toolName));
    }
}
