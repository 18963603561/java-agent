package com.example.agent.governance;

import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.governance.policy.PolicyEngine;
import com.example.agent.governance.policy.domain.PolicyEvaluationCommand;
import com.example.agent.governance.policy.domain.PolicyEvaluationResult;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 策略引擎测试。
 */
class PolicyEngineTest {

    @Test
    void shouldDenyWhenRiskIsHigh() {
        PolicyEngine engine = new PolicyEngine(new MetricsPublisher(new SimpleMeterRegistry()));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "submit", "task", Map.of("risk", "high"));

        PolicyEvaluationResult result = engine.evaluate(command, tenantContext);

        assertEquals("DENY", result.getDecision());
        assertEquals("high_risk", result.getReason());
    }

    @Test
    void shouldRejectWhenTenantMissing() {
        PolicyEngine engine = new PolicyEngine(new MetricsPublisher(new SimpleMeterRegistry()));
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "submit", "task", Map.of());

        ErrorCodeException exception = assertThrows(ErrorCodeException.class, () -> engine.evaluate(command, null));

        assertEquals("POLICY_TENANT_MISSING", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldRejectWhenActionMissing() {
        PolicyEngine engine = new PolicyEngine(new MetricsPublisher(new SimpleMeterRegistry()));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "", "task", Map.of());

        ErrorCodeException exception = assertThrows(ErrorCodeException.class,
                () -> engine.evaluate(command, tenantContext));

        assertEquals("POLICY_ACTION_MISSING", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void shouldRejectWhenResourceMissing() {
        PolicyEngine engine = new PolicyEngine(new MetricsPublisher(new SimpleMeterRegistry()));
        TenantContext tenantContext = new TenantContext("tenant-a", "user-a", List.of(), "req", "trace");
        PolicyEvaluationCommand command = new PolicyEvaluationCommand("default", "submit", " ", Map.of());

        ErrorCodeException exception = assertThrows(ErrorCodeException.class,
                () -> engine.evaluate(command, tenantContext));

        assertEquals("POLICY_RESOURCE_MISSING", exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}

