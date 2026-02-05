package com.example.agent.budget;

import com.example.agent.capabilities.context.Citation;
import com.example.agent.capabilities.context.ContextPolicy;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.DomainKnowledge;
import com.example.agent.capabilities.context.EvidenceItem;
import com.example.agent.capabilities.context.EvidencePack;
import com.example.agent.capabilities.context.LongTermMemory;
import com.example.agent.capabilities.context.MemoryRef;
import com.example.agent.capabilities.context.WorkingMemory;
import com.example.agent.capabilities.memory.TokenEstimator;
import com.example.agent.streaming.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.agent.budget.token.ContextBudgetAllocation;
import com.example.agent.budget.trim.ContextPruneRequest;
import com.example.agent.budget.trim.ContextPruneResult;
import com.example.agent.budget.trim.ContextSection;
import com.example.agent.budget.trim.DefaultContextPruner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultContextPrunerPolicyTest {

    @Test
    void pruneRespectsPolicyOrder() {
        DefaultContextPruner pruner = new DefaultContextPruner(new TokenEstimator(),
                new MetricsPublisher(new SimpleMeterRegistry()));

        ContextPolicy policy = new ContextPolicy();
        policy.setPruneOrder(List.of("DOMAIN_KNOWLEDGE", "LONG_TERM_MEMORY", "EVIDENCE_PACK", "WORKING_MEMORY"));
        policy.setMaxEvidenceCount(1);
        policy.setMaxMemoryCount(1);

        Citation citation1 = new Citation();
        citation1.setSource("c1");
        Citation citation2 = new Citation();
        citation2.setSource("c2");
        DomainKnowledge knowledge = new DomainKnowledge();
        knowledge.setCitations(List.of(citation1, citation2));

        MemoryRef ref1 = new MemoryRef();
        ref1.setMemoryId("m1");
        MemoryRef ref2 = new MemoryRef();
        ref2.setMemoryId("m2");
        LongTermMemory longTermMemory = new LongTermMemory();
        longTermMemory.setMemoryRefs(List.of(ref1, ref2));

        EvidenceItem item1 = new EvidenceItem();
        item1.setEvidenceId("e1");
        EvidenceItem item2 = new EvidenceItem();
        item2.setEvidenceId("e2");
        EvidencePack pack = new EvidencePack();
        pack.setEvidences(List.of(item1, item2));

        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.setEvidencePack(pack);
        workingMemory.setSummary("a".repeat(50));

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setDomainKnowledge(knowledge);
        snapshot.setLongTermMemory(longTermMemory);
        snapshot.setWorkingMemory(workingMemory);

        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        EnumMap<ContextSection, Integer> sectionTokens = new EnumMap<>(ContextSection.class);
        sectionTokens.put(ContextSection.WORKING_MEMORY, 1);
        allocation.setSectionTokens(sectionTokens);

        ContextPruneRequest request = new ContextPruneRequest(snapshot, allocation, policy);
        ContextPruneResult result = pruner.prune(request);

        assertNotNull(result.getRemovedItems());
        assertEquals("citation", result.getRemovedItems().get(0).getItemType());
        assertEquals("memory", result.getRemovedItems().get(1).getItemType());
        assertEquals("evidence", result.getRemovedItems().get(2).getItemType());
        assertEquals("working_summary", result.getRemovedItems().get(3).getItemType());
    }
}
