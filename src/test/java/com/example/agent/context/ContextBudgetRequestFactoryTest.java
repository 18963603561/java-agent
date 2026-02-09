package com.example.agent.context;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.budget.config.ContextBudgetProperties;
import com.example.agent.budget.token.application.ContextBudgetRequest;
import com.example.agent.capabilities.context.ContextBuildRequest;
import com.example.agent.capabilities.context.model.ContextPolicy;
import com.example.agent.capabilities.context.builder.budget.ContextBudgetRequestFactory;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContextBudgetRequestFactoryTest {

    private final ContextBudgetRequestFactory factory = new ContextBudgetRequestFactory();

    @Test
    void createUsesRequestBudgetFirst() {
        ContextBuildRequest request = baseRequest();
        ContextBudgetRequest provided = new ContextBudgetRequest();
        provided.setTotalTokens(1200);
        provided.setReservedTokens(120);
        request.setBudgetRequest(provided);

        ContextBudgetRequest resolved = factory.create(
                request,
                Map.of("tokenBudget", 800),
                new ContextPolicy(),
                budgetProperties(600),
                400);

        assertNotNull(resolved);
        assertEquals(1200, resolved.getTotalTokens());
        assertEquals(120, resolved.getReservedTokens());
    }

    @Test
    void createUsesRuntimeBudgetWhenRequestMissing() {
        ContextBuildRequest request = baseRequest();

        ContextBudgetRequest resolved = factory.create(
                request,
                Map.of("tokenBudget", "900"),
                new ContextPolicy(),
                budgetProperties(600),
                400);

        assertEquals(900, resolved.getTotalTokens());
    }

    @Test
    void createFallsBackToConfigAndDefault() {
        ContextBuildRequest request = baseRequest();

        ContextBudgetRequest fromConfig = factory.create(
                request,
                Map.of(),
                new ContextPolicy(),
                budgetProperties(700),
                400);
        assertEquals(700, fromConfig.getTotalTokens());

        ContextBudgetRequest fromDefault = factory.create(
                request,
                Map.of(),
                new ContextPolicy(),
                budgetProperties(0),
                500);
        assertEquals(500, fromDefault.getTotalTokens());
    }

    @Test
    void createReturnsDisabledRequestWhenBudgetDisabled() {
        ContextBuildRequest request = baseRequest();
        ContextBudgetProperties properties = budgetProperties(600);
        properties.setEnabled(false);

        ContextBudgetRequest resolved = factory.create(
                request,
                Map.of("tokenBudget", 900),
                new ContextPolicy(),
                properties,
                400);

        assertNotNull(resolved);
        assertFalse(resolved.isEnabled());
        assertEquals(900, resolved.getTotalTokens());
    }

    @Test
    void createThrowsWhenReservedTokensNegative() {
        ContextBuildRequest request = baseRequest();
        ContextBudgetRequest provided = new ContextBudgetRequest();
        provided.setReservedTokens(-1);
        request.setBudgetRequest(provided);

        assertThrows(IllegalArgumentException.class,
                () -> factory.create(request, Map.of(), new ContextPolicy(), budgetProperties(0), 300));
    }

    @Test
    void createFillsTraceAndPolicyFields() {
        ContextBuildRequest request = baseRequest();
        ContextPolicy policy = new ContextPolicy();
        policy.setRetrievalPriority(List.of("RECENT"));

        ContextBudgetRequest resolved = factory.create(
                request,
                Map.of("tokenBudget", 600),
                policy,
                budgetProperties(500),
                400);

        assertEquals("t1", resolved.getTenantId());
        assertEquals("wf-1", resolved.getWorkflowId());
        assertEquals("task-1", resolved.getTaskId());
        assertEquals(List.of("RECENT"), resolved.getPolicy().getRetrievalPriority());
        assertNotNull(resolved.getBudgetPolicy());
    }

    private ContextBuildRequest baseRequest() {
        ContextBuildRequest request = new ContextBuildRequest();
        request.setTenantContext(new TenantContext("t1", "u1", List.of(), "req", "trace"));
        request.setWorkflowId("wf-1");
        request.setTaskId("task-1");
        return request;
    }

    private ContextBudgetProperties budgetProperties(int total) {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        properties.setEnabled(true);
        properties.setTotalBudgetTokens(total);
        return properties;
    }
}



