package com.example.agent.policy;

import com.example.agent.auth.TenantContext;
import com.example.agent.observability.MetricsPublisher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
     * @param request 策略请求
     * @param tenantContext 租户上下文
     * @return 策略决策
     */
    public PolicyDecision evaluate(PolicyRequest request, TenantContext tenantContext) {
        String evaluationId = UUID.randomUUID().toString();
        String risk = extractRisk(request.getInput());
        if ("high".equalsIgnoreCase(risk)) {
            metricsPublisher.increment("policy.deny.count");
            log.warn("策略拒绝, tenantId={}, policyId={}, action={}, resource={}",
                    tenantContext.getTenantId(), request.getPolicyId(), request.getAction(), request.getResource());
            return new PolicyDecision(request.getPolicyId(), "DENY", "high_risk",
                    evaluationId, List.of("risk_high"));
        }
        log.info("策略通过, tenantId={}, policyId={}, action={}, resource={}",
                tenantContext.getTenantId(), request.getPolicyId(), request.getAction(), request.getResource());
        return new PolicyDecision(request.getPolicyId(), "ALLOW", "ok",
                evaluationId, List.of("default_allow"));
    }

    private String extractRisk(Map<String, Object> input) {
        if (input == null) {
            return null;
        }
        Object value = input.get("risk");
        return value != null ? value.toString() : null;
    }
}
