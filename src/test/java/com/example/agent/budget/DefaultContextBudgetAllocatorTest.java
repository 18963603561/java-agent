package com.example.agent.budget;

import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextBudgetAllocationState;
import com.example.agent.budget.core.ContextBudgetPolicy;
import com.example.agent.budget.config.ContextBudgetProperties;
import com.example.agent.budget.token.application.ContextBudgetRequest;
import com.example.agent.budget.token.application.DefaultContextBudgetAllocator;
import com.example.agent.budget.token.application.TokenBudgetManager;
import com.example.agent.budget.core.ContextSection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetAllocatorTest {

    @Test
    void allocateSplitsAvailableBudgetByRatios() {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        properties.setTotalBudgetTokens(1000);
        ContextBudgetProperties.Ratios ratios = new ContextBudgetProperties.Ratios();
        ratios.setSystemPolicy(0.1);
        ratios.setDeveloperPolicy(0.1);
        ratios.setTaskIntent(0.2);
        ratios.setWorkingMemory(0.2);
        ratios.setDomainKnowledge(0.1);
        ratios.setLongTermMemory(0.1);
        ratios.setToolSummaries(0.1);
        ratios.setToolSchema(0.05);
        ratios.setEvidencePack(0.05);
        ratios.setSlack(0.0);
        properties.setRatios(ratios);

        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        TokenBudgetManager tokenBudgetManager = Mockito.mock(TokenBudgetManager.class);
        DefaultContextBudgetAllocator allocator = new DefaultContextBudgetAllocator(
                properties, metricsPublisher, tokenBudgetManager);

        ContextBudgetRequest request = new ContextBudgetRequest();
        request.setTotalTokens(1000);
        request.setReservedTokens(100);

        ContextBudgetAllocation allocation = allocator.allocate(request);

        assertNotNull(allocation);
        assertTrue(allocation.isAllocationEnabled());
        assertEquals(ContextBudgetAllocationState.ENABLED, allocation.getAllocationState());
        assertEquals(1000, allocation.getTotalTokens());
        assertEquals(100, allocation.getReservedTokens());

        int sum = 0;
        for (Integer value : allocation.getSectionTokens().values()) {
            sum += value == null ? 0 : value;
        }
        assertEquals(900, sum);
    }

    @Test
    void allocateHandlesZeroTotalTokens() {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        properties.setTotalBudgetTokens(0);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        TokenBudgetManager tokenBudgetManager = Mockito.mock(TokenBudgetManager.class);
        DefaultContextBudgetAllocator allocator = new DefaultContextBudgetAllocator(
                properties, metricsPublisher, tokenBudgetManager);

        ContextBudgetRequest request = new ContextBudgetRequest();
        request.setTotalTokens(0);

        ContextBudgetAllocation allocation = allocator.allocate(request);

        assertNotNull(allocation);
        assertTrue(allocation.isAllocationEnabled());
        assertEquals(0, allocation.getTotalTokens());
        int sum = 0;
        for (Integer value : allocation.getSectionTokens().values()) {
            sum += value == null ? 0 : value;
        }
        assertEquals(0, sum);
    }

    @Test
    void allocateNormalizesRatiosWhenOverOne() {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        properties.setTotalBudgetTokens(100);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        TokenBudgetManager tokenBudgetManager = Mockito.mock(TokenBudgetManager.class);
        DefaultContextBudgetAllocator allocator = new DefaultContextBudgetAllocator(
                properties, metricsPublisher, tokenBudgetManager);

        Map<ContextSection, Double> ratios = new EnumMap<>(ContextSection.class);
        ratios.put(ContextSection.WORKING_MEMORY, 0.8);
        ratios.put(ContextSection.USER_INPUT, 0.6);
        ContextBudgetPolicy policy = new ContextBudgetPolicy();
        policy.setSectionRatios(ratios);

        ContextBudgetRequest request = new ContextBudgetRequest();
        request.setTotalTokens(100);
        request.setBudgetPolicy(policy);

        ContextBudgetAllocation allocation = allocator.allocate(request);

        int expectedWorking = (int) Math.floor(100 * (0.8 / 1.4));
        int expectedTask = (int) Math.floor(100 * (0.6 / 1.4));
        assertEquals(expectedWorking, allocation.getSectionTokens().get(ContextSection.WORKING_MEMORY));
        assertEquals(expectedTask, allocation.getSectionTokens().get(ContextSection.USER_INPUT));

        int sum = 0;
        for (Integer value : allocation.getSectionTokens().values()) {
            sum += value == null ? 0 : value;
        }
        assertEquals(100, sum);
    }

    @Test
    void allocateReturnsDisabledAllocationWhenRequestDisabled() {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        properties.setEnabled(true);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        TokenBudgetManager tokenBudgetManager = Mockito.mock(TokenBudgetManager.class);
        DefaultContextBudgetAllocator allocator = new DefaultContextBudgetAllocator(
                properties, metricsPublisher, tokenBudgetManager);

        ContextBudgetRequest request = new ContextBudgetRequest();
        request.setEnabled(false);

        ContextBudgetAllocation allocation = allocator.allocate(request);

        assertNotNull(allocation);
        assertFalse(allocation.isAllocationEnabled());
        assertEquals(ContextBudgetAllocationState.DISABLED_BY_REQUEST, allocation.getAllocationState());
        assertEquals("request_disabled", allocation.getAllocationReason());
        assertEquals(0, allocation.getTotalTokens());
    }

    @Test
    void allocateReturnsDisabledAllocationWhenConfigDisabled() {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        properties.setEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        TokenBudgetManager tokenBudgetManager = Mockito.mock(TokenBudgetManager.class);
        DefaultContextBudgetAllocator allocator = new DefaultContextBudgetAllocator(
                properties, metricsPublisher, tokenBudgetManager);

        ContextBudgetRequest request = new ContextBudgetRequest();
        request.setEnabled(true);
        request.setTotalTokens(256);

        ContextBudgetAllocation allocation = allocator.allocate(request);

        assertNotNull(allocation);
        assertFalse(allocation.isAllocationEnabled());
        assertEquals(ContextBudgetAllocationState.DISABLED_BY_CONFIG, allocation.getAllocationState());
        assertEquals("config_disabled", allocation.getAllocationReason());
        assertEquals(0, allocation.getTotalTokens());
    }
}


