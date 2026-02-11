package com.example.agent.governance.policy.provider.local;

import com.example.agent.governance.policy.domain.PolicyEvaluationCommand;
import com.example.agent.governance.policy.domain.PolicyEvaluationResult;
import com.example.agent.governance.policy.spi.PolicyDecisionProvider;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 本地策略决策提供者。
 */
@Component
public class LocalPolicyDecisionProvider implements PolicyDecisionProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalPolicyDecisionProvider.class);

    private final MetricsPublisher metricsPublisher;

    public LocalPolicyDecisionProvider(MetricsPublisher metricsPublisher) {
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    public String providerId() {
        return "local";
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyEvaluationCommand command, TenantContext tenantContext) {
        String evaluationId = UUID.randomUUID().toString();
        String risk = extractRisk(command != null ? command.getInput() : null);
        if ("high".equalsIgnoreCase(risk)) {
            metricsPublisher.increment("policy.deny.count");
            log.warn("本地策略拒绝, tenantId={}, policyId={}, action={}, resource={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    command != null ? command.getPolicyId() : null,
                    command != null ? command.getAction() : null,
                    command != null ? command.getResource() : null);
            return new PolicyEvaluationResult(command != null ? command.getPolicyId() : null,
                    "DENY",
                    "high_risk",
                    evaluationId,
                    List.of("risk_high"));
        }
        log.info("本地策略通过, tenantId={}, policyId={}, action={}, resource={}",
                tenantContext != null ? tenantContext.getTenantId() : null,
                command != null ? command.getPolicyId() : null,
                command != null ? command.getAction() : null,
                command != null ? command.getResource() : null);
        return new PolicyEvaluationResult(command != null ? command.getPolicyId() : null,
                "ALLOW",
                "ok",
                evaluationId,
                List.of("default_allow"));
    }

    private String extractRisk(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object value = input.get("risk");
        return value != null ? value.toString() : null;
    }
}

