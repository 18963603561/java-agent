package com.example.agent.budget;

import com.example.agent.context.ContextSnapshot;
import com.example.agent.context.WorkingMemory;
import com.example.agent.memory.TokenEstimator;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultContextPrunerTest {

    @Test
    void pruneTruncatesWorkingSummaryWhenOverBudget() {
        DefaultContextPruner pruner = new DefaultContextPruner(new TokenEstimator());

        WorkingMemory memory = new WorkingMemory();
        memory.setSummary("这是一个很长的摘要，需要被裁剪以适配预算限制。重复重复重复重复重复重复。");

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(memory);

        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        EnumMap<ContextSection, Integer> sectionTokens = new EnumMap<>(ContextSection.class);
        sectionTokens.put(ContextSection.WORKING_MEMORY, 1);
        allocation.setSectionTokens(sectionTokens);

        ContextPruneRequest request = new ContextPruneRequest(snapshot, allocation, null);
        ContextPruneResult result = pruner.prune(request);

        assertNotNull(result.getPrunedSnapshot());
        assertTrue(result.getRemovedItems().size() >= 1);
        assertTrue(result.getPrunedSnapshot().getWorkingMemory().getSummary().length() <= 4);
    }
}