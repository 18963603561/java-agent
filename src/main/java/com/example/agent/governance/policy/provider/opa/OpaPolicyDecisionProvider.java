package com.example.agent.governance.policy.provider.opa;

import com.example.agent.governance.policy.config.PolicyProviderProperties;
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
import org.springframework.web.client.RestClientException;

/**
 * OPA 策略决策提供者。
 */
@Component
public class OpaPolicyDecisionProvider implements PolicyDecisionProvider {

    private static final Logger log = LoggerFactory.getLogger(OpaPolicyDecisionProvider.class);

    private final OpaClient opaClient;
    private final PolicyProviderProperties properties;
    private final MetricsPublisher metricsPublisher;

    public OpaPolicyDecisionProvider(OpaClient opaClient,
                                     PolicyProviderProperties properties,
                                     MetricsPublisher metricsPublisher) {
        this.opaClient = opaClient;
        this.properties = properties;
        this.metricsPublisher = metricsPublisher;
    }

    @Override
    public String providerId() {
        return "opa";
    }

    @Override
    public PolicyEvaluationResult evaluate(PolicyEvaluationCommand command, TenantContext tenantContext) {
        String evaluationId = UUID.randomUUID().toString();
        try {
            Map<String, Object> raw = opaClient.evaluate(command, tenantContext);
            OpaClient.OpaDecision decision = opaClient.parseDecision(raw);
            if (decision == null) {
                log.error("OPA决策解析为空，按拒绝处理, tenantId={}, policyId={}, action={}, resource={}",
                        tenantContext != null ? tenantContext.getTenantId() : null,
                        command != null ? command.getPolicyId() : null,
                        command != null ? command.getAction() : null,
                        command != null ? command.getResource() : null);
                decision = new OpaClient.OpaDecision(false,
                        "opa_decision_missing",
                        List.of("opa_decision_missing"));
            }
            if (!decision.allow()) {
                metricsPublisher.increment("policy.deny.count");
                log.warn("OPA策略拒绝, tenantId={}, policyId={}, action={}, resource={}, reason={}",
                        tenantContext != null ? tenantContext.getTenantId() : null,
                        command != null ? command.getPolicyId() : null,
                        command != null ? command.getAction() : null,
                        command != null ? command.getResource() : null,
                        decision.reason());
                return new PolicyEvaluationResult(
                        command != null ? command.getPolicyId() : null,
                        "DENY",
                        decision.reason() == null ? "opa_denied" : decision.reason(),
                        evaluationId,
                        decision.matchedRules() == null ? List.of("opa_denied") : decision.matchedRules()
                );
            }
            return new PolicyEvaluationResult(
                    command != null ? command.getPolicyId() : null,
                    "ALLOW",
                    decision.reason() == null ? "opa_allow" : decision.reason(),
                    evaluationId,
                    decision.matchedRules() == null ? List.of("opa_allow") : decision.matchedRules()
            );
        } catch (RestClientException ex) {
            boolean failClosed = properties != null && properties.getOpa() != null && properties.getOpa().isFailClosed();
            if (failClosed) {
                metricsPublisher.increment("policy.deny.count");
                log.error("OPA调用异常且failClosed生效, tenantId={}, action={}, resource={}",
                        tenantContext != null ? tenantContext.getTenantId() : null,
                        command != null ? command.getAction() : null,
                        command != null ? command.getResource() : null,
                        ex);
                return new PolicyEvaluationResult(
                        command != null ? command.getPolicyId() : null,
                        "DENY",
                        "opa_unavailable_fail_closed",
                        evaluationId,
                        List.of("opa_unavailable")
                );
            }
            log.warn("OPA调用异常且failOpen生效, tenantId={}, action={}, resource={}",
                    tenantContext != null ? tenantContext.getTenantId() : null,
                    command != null ? command.getAction() : null,
                    command != null ? command.getResource() : null,
                    ex);
            return new PolicyEvaluationResult(
                    command != null ? command.getPolicyId() : null,
                    "ALLOW",
                    "opa_unavailable_fail_open",
                    evaluationId,
                    List.of("opa_unavailable")
            );
        }
    }
}
