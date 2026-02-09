package com.example.agent.governance.policy;

import com.example.agent.governance.policy.domain.PolicyEvaluationCommand;
import com.example.agent.governance.policy.domain.PolicyEvaluationResult;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 策略评估引擎，提供基础的风险评估逻辑。
 */
@Service
public class PolicyEngine {

    private static final Logger log = LoggerFactory.getLogger(PolicyEngine.class);

    private final MetricsPublisher metricsPublisher;

    public PolicyEngine(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    /**
     * 评估策略请求。
     *
     * @param command 策略评估命令
     * @param tenantContext 租户上下文
     * @return 策略决策
     */
    public PolicyEvaluationResult evaluate(PolicyEvaluationCommand command, TenantContext tenantContext) {
        String tenantId = validateAndResolveTenantId(tenantContext);
        PolicyEvaluationCommand normalized = normalizeCommand(command);
        String evaluationId = UUID.randomUUID().toString();
        String risk = extractRisk(normalized.getInput());
        if ("high".equalsIgnoreCase(risk)) {
            metricsPublisher.increment("policy.deny.count");
            log.warn("策略拒绝, tenantId={}, policyId={}, action={}, resource={}",
                    tenantId,
                    normalized.getPolicyId(),
                    normalized.getAction(),
                    normalized.getResource());
            return new PolicyEvaluationResult(normalized.getPolicyId(),
                    "DENY", "high_risk",
                    evaluationId, List.of("risk_high"));
        }
        log.info("策略通过, tenantId={}, policyId={}, action={}, resource={}",
                tenantId,
                normalized.getPolicyId(),
                normalized.getAction(),
                normalized.getResource());
        return new PolicyEvaluationResult(normalized.getPolicyId(),
                "ALLOW", "ok",
                evaluationId, List.of("default_allow"));
    }

    /**
     * 校验并提取租户标识。
     *
     * @param tenantContext 租户上下文
     * @return 租户标识
     */
    private String validateAndResolveTenantId(TenantContext tenantContext) {
        if (tenantContext == null || !StringUtils.hasText(tenantContext.getTenantId())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "POLICY_TENANT_MISSING",
                    "策略评估缺少租户信息");
        }
        return tenantContext.getTenantId();
    }

    /**
     * 校验并标准化策略评估命令。
     *
     * @param command 原始命令
     * @return 标准化命令
     */
    private PolicyEvaluationCommand normalizeCommand(PolicyEvaluationCommand command) {
        if (command == null) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "POLICY_COMMAND_MISSING",
                    "策略评估请求不能为空");
        }
        if (!StringUtils.hasText(command.getAction())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "POLICY_ACTION_MISSING",
                    "策略评估缺少 action");
        }
        if (!StringUtils.hasText(command.getResource())) {
            throw new ErrorCodeException(HttpStatus.BAD_REQUEST,
                    "POLICY_RESOURCE_MISSING",
                    "策略评估缺少 resource");
        }
        command.setAction(command.getAction().trim());
        command.setResource(command.getResource().trim());
        if (StringUtils.hasText(command.getPolicyId())) {
            command.setPolicyId(command.getPolicyId().trim());
        }
        return command;
    }

    private String extractRisk(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object value = input.get("risk");
        return value != null ? value.toString() : null;
    }
}
