package com.example.agent.context;

import com.example.agent.auth.TenantContext;
import com.example.agent.budget.ContextBudgetAllocation;
import com.example.agent.budget.ContextBudgetAllocator;
import com.example.agent.budget.ContextBudgetProperties;
import com.example.agent.budget.ContextBudgetRequest;
import com.example.agent.budget.ContextPruneRequest;
import com.example.agent.budget.ContextPruner;
import com.example.agent.common.TaskRequest;
import com.example.agent.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextPolicyPropagationTest {

    @Test
    void buildPropagatesPolicyFromRuntimeContext() {
        ContextBudgetAllocator budgetAllocator = Mockito.mock(ContextBudgetAllocator.class);
        ContextPruner contextPruner = Mockito.mock(ContextPruner.class);
        ContextBudgetProperties budgetProperties = new ContextBudgetProperties();
        budgetProperties.setTotalBudgetTokens(200);

        DefaultContextBuilder builder = new DefaultContextBuilder(null, budgetAllocator, contextPruner,
                null, null, budgetProperties, null, new MetricsPublisher(new SimpleMeterRegistry()));
        ReflectionTestUtils.setField(builder, "defaultTokenBudget", 200);

        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setTotalTokens(200);
        when(budgetAllocator.allocate(any(ContextBudgetRequest.class))).thenReturn(allocation);

        ContextPolicy policy = new ContextPolicy();
        policy.setRetrievalPriority(List.of("RECENT"));
        policy.setPruneOrder(List.of("WORKING_MEMORY"));
        policy.setEnableSensitiveMask(Boolean.FALSE);

        TaskRequest request = new TaskRequest();
        request.setQuery("测试问题");
        request.setSessionId("s1");

        TenantContext tenantContext = new TenantContext("t1", "u1", List.of(), "req", "trace");

        ContextBuildRequest buildRequest = new ContextBuildRequest();
        buildRequest.setTaskRequest(request);
        buildRequest.setTenantContext(tenantContext);
        buildRequest.setWorkflowId("wf-1");
        buildRequest.setRuntimeContext(Map.of("contextPolicy", policy));

        builder.build(buildRequest);

        ArgumentCaptor<ContextPruneRequest> captor = ArgumentCaptor.forClass(ContextPruneRequest.class);
        verify(contextPruner).prune(captor.capture());
        ContextPolicy applied = captor.getValue().getPolicy();
        assertNotNull(applied);
        assertEquals(List.of("RECENT"), applied.getRetrievalPriority());
        assertEquals(List.of("WORKING_MEMORY"), applied.getPruneOrder());
        assertEquals(Boolean.FALSE, applied.getEnableSensitiveMask());
    }
}
