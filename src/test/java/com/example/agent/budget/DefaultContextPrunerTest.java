package com.example.agent.budget;

import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.model.WorkingMemory;
import com.example.agent.capabilities.memory.policy.TokenEstimator;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import com.example.agent.budget.core.ContextBudgetAllocation;
import com.example.agent.budget.core.ContextBudgetAllocationState;
import com.example.agent.budget.trim.model.ContextPruneRequest;
import com.example.agent.budget.trim.model.ContextPruneResult;
import com.example.agent.budget.core.ContextSection;
import com.example.agent.budget.trim.application.DefaultContextPruner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultContextPrunerTest {

    @Test
    void pruneTruncatesWorkingSummaryWhenOverBudget() {
        DefaultContextPruner pruner = new DefaultContextPruner(new TokenEstimator(),
                new MetricsPublisher(new SimpleMeterRegistry()));

        WorkingMemory memory = new WorkingMemory();
        memory.setSummary("这是一个很长的摘要，需要被裁剪以适配预算限制。重复重复重复重复重复重复。");

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(memory);

        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setAllocationState(ContextBudgetAllocationState.ENABLED);
        EnumMap<ContextSection, Integer> sectionTokens = new EnumMap<>(ContextSection.class);
        sectionTokens.put(ContextSection.WORKING_MEMORY, 1);
        allocation.setSectionTokens(sectionTokens);

        ContextPruneRequest request = new ContextPruneRequest(snapshot, allocation, null);
        ContextPruneResult result = pruner.prune(request);

        assertNotNull(result.getPrunedSnapshot());
        assertTrue(result.getRemovedItems().size() >= 1);
        assertTrue(result.getPrunedSnapshot().getWorkingMemory().getSummary().length() <= 4);
    }

    @Test
    void pruneShouldSkipWhenAllocationDisabled() {
        DefaultContextPruner pruner = new DefaultContextPruner(new TokenEstimator(),
                new MetricsPublisher(new SimpleMeterRegistry()));

        WorkingMemory memory = new WorkingMemory();
        String summary = "这是一个很长的摘要，需要被裁剪以适配预算限制。重复重复重复重复重复重复。";
        memory.setSummary(summary);

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(memory);

        ContextBudgetAllocation allocation = ContextBudgetAllocation.disabled(
                ContextBudgetAllocationState.DISABLED_BY_CONFIG,
                "config_disabled");

        ContextPruneRequest request = new ContextPruneRequest(snapshot, allocation, null);
        ContextPruneResult result = pruner.prune(request);

        assertNotNull(result.getPrunedSnapshot());
        assertTrue(result.getRemovedItems() == null || result.getRemovedItems().isEmpty());
        assertFalse(result.getPrunedSnapshot().getWorkingMemory().getSummary().length() <= 4);
    }
}




