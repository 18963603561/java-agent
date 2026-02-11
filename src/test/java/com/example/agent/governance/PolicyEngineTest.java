package com.example.agent.governance;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.governance.policy.config.PolicyProviderProperties;
import com.example.agent.governance.policy.provider.local.LocalPolicyDecisionProvider;
import com.example.agent.governance.policy.provider.opa.OpaClient;
import com.example.agent.governance.policy.provider.opa.OpaPolicyDecisionProvider;
import com.example.agent.governance.policy.PolicyEngine;
import com.example.agent.governance.policy.domain.PolicyEvaluationCommand;
import com.example.agent.governance.policy.domain.PolicyEvaluationResult;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 策略引擎测试。
 */
class PolicyEngineTest {

    @Test
    void shouldDenyWhenRiskIsHigh() {
        PolicyEngine engine = buildLocalEngine();
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "submit", "task", Map.of("risk", "high"));

        PolicyEvaluationResult result = engine.evaluate(command, tenantContext);

        assertEquals("DENY", result.getDecision());
        assertEquals("high_risk", result.getReason());
    }

    @Test
    void shouldRejectWhenTenantMissing() {
        PolicyEngine engine = buildLocalEngine();
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "submit", "task", Map.of());

        ErrorCodeException exception = assertThrows(ErrorCodeException.class, () -> engine.evaluate(command, null));

        assertEquals("POLICY_TENANT_MISSING", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldRejectWhenActionMissing() {
        PolicyEngine engine = buildLocalEngine();
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "", "task", Map.of());

        ErrorCodeException exception = assertThrows(ErrorCodeException.class,
                () -> engine.evaluate(command, tenantContext));

        assertEquals("POLICY_ACTION_MISSING", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldRejectWhenResourceMissing() {
        PolicyEngine engine = buildLocalEngine();
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "submit", " ", Map.of());

        ErrorCodeException exception = assertThrows(ErrorCodeException.class,
                () -> engine.evaluate(command, tenantContext));

        assertEquals("POLICY_RESOURCE_MISSING", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldUseOpaProviderWhenConfigured() {
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        PolicyProviderProperties properties = new PolicyProviderProperties();
        properties.setProvider("opa");
        OpaClient opaClient = Mockito.mock(OpaClient.class);
        Mockito.when(opaClient.evaluate(Mockito.any(), Mockito.any()))
                .thenReturn(Map.of("result", Map.of(
                        "allow", false,
                        "reason", "opa_risk",
                        "matchedRules", List.of("rule-a")
                )));
        Mockito.when(opaClient.parseDecision(Mockito.any()))
                .thenCallRealMethod();
        OpaPolicyDecisionProvider opaProvider = new OpaPolicyDecisionProvider(opaClient, properties, metricsPublisher);
        LocalPolicyDecisionProvider localProvider = new LocalPolicyDecisionProvider(metricsPublisher);
        PolicyEngine engine = new PolicyEngine(List.of(localProvider, opaProvider), properties);

        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "submit", "task", Map.of("risk", "low"));
        PolicyEvaluationResult result = engine.evaluate(command, tenantContext);

        assertEquals("DENY", result.getDecision());
        assertEquals("opa_risk", result.getReason());
    }

    @Test
    void opaFailOpenShouldAllow() {
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        PolicyProviderProperties properties = new PolicyProviderProperties();
        properties.setProvider("opa");
        properties.getOpa().setFailClosed(false);
        OpaClient opaClient = Mockito.mock(OpaClient.class);
        Mockito.when(opaClient.evaluate(Mockito.any(), Mockito.any()))
                .thenThrow(new RestClientException("opa_down"));
        Mockito.when(opaClient.parseDecision(Mockito.any()))
                .thenCallRealMethod();
        OpaPolicyDecisionProvider opaProvider = new OpaPolicyDecisionProvider(opaClient, properties, metricsPublisher);
        LocalPolicyDecisionProvider localProvider = new LocalPolicyDecisionProvider(metricsPublisher);
        PolicyEngine engine = new PolicyEngine(List.of(localProvider, opaProvider), properties);

        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "submit", "task", Map.of("risk", "low"));
        PolicyEvaluationResult result = engine.evaluate(command, tenantContext);

        assertEquals("ALLOW", result.getDecision());
        assertEquals("opa_unavailable_fail_open", result.getReason());
    }

    @Test
    void opaFailClosedShouldDeny() {
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        PolicyProviderProperties properties = new PolicyProviderProperties();
        properties.setProvider("opa");
        properties.getOpa().setFailClosed(true);
        OpaClient opaClient = Mockito.mock(OpaClient.class);
        Mockito.when(opaClient.evaluate(Mockito.any(), Mockito.any()))
                .thenThrow(new RestClientException("opa_down"));
        Mockito.when(opaClient.parseDecision(Mockito.any()))
                .thenCallRealMethod();
        OpaPolicyDecisionProvider opaProvider = new OpaPolicyDecisionProvider(opaClient, properties, metricsPublisher);
        LocalPolicyDecisionProvider localProvider = new LocalPolicyDecisionProvider(metricsPublisher);
        PolicyEngine engine = new PolicyEngine(List.of(localProvider, opaProvider), properties);

        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "submit", "task", Map.of("risk", "low"));
        PolicyEvaluationResult result = engine.evaluate(command, tenantContext);

        assertEquals("DENY", result.getDecision());
        assertEquals("opa_unavailable_fail_closed", result.getReason());
    }

    private PolicyEngine buildLocalEngine() {
        MetricsPublisher metricsPublisher = new MetricsPublisher(new SimpleMeterRegistry());
        PolicyProviderProperties properties = new PolicyProviderProperties();
        properties.setProvider("local");
        LocalPolicyDecisionProvider localProvider = new LocalPolicyDecisionProvider(metricsPublisher);
        return new PolicyEngine(List.of(localProvider), properties);
    }
}
