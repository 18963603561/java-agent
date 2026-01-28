package com.example.agent.budget;

import com.example.agent.context.ContextSnapshot;
import com.example.agent.context.EvidencePack;
import com.example.agent.context.LongTermMemory;
import com.example.agent.context.MemoryRef;
import com.example.agent.context.ToolCallEvidence;
import com.example.agent.context.WorkingMemory;
import com.example.agent.memory.TokenEstimator;
import com.example.agent.observability.MetricsPublisher;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultContextTrimmerTest {

    @Test
    void trimRecalledMemoriesKeepsHighScore() {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        DefaultContextTrimmer trimmer = buildTrimmer(properties);

        MemoryRef low = new MemoryRef();
        low.setMemoryId("m1");
        low.setScore(0.1);
        low.setSnippet("a".repeat(80));
        low.setSource("recall");

        MemoryRef high = new MemoryRef();
        high.setMemoryId("m2");
        high.setScore(0.9);
        high.setSnippet("a".repeat(80));
        high.setSource("recall");

        LongTermMemory longTermMemory = new LongTermMemory();
        longTermMemory.setMemoryRefs(List.of(low, high));

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setLongTermMemory(longTermMemory);

        TokenEstimator estimator = new TokenEstimator();
        int highTokens = estimateMemoryRefTokens(estimator, high);
        EnumMap<ContextSection, Integer> sectionTokens = defaultSectionTokens();
        sectionTokens.put(ContextSection.LONG_TERM_MEMORY, highTokens);

        ContextBudgetAllocation allocation = buildAllocation(highTokens, sectionTokens);

        ContextTrimResult result = trimmer.trim(new ContextTrimRequest(snapshot, allocation, new ContextBudgetPolicy()));

        List<MemoryRef> remaining = result.getTrimmedSnapshot().getLongTermMemory().getMemoryRefs();
        assertEquals(1, remaining.size());
        assertEquals("m2", remaining.get(0).getMemoryId());
    }

    @Test
    void trimWorkingMemoryRemovesOldestItems() {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        DefaultContextTrimmer trimmer = buildTrimmer(properties);

        String item1 = "a".repeat(40);
        String item2 = "b".repeat(40);
        String item3 = "c".repeat(40);

        WorkingMemory memory = new WorkingMemory();
        memory.setKeyFacts(List.of(item1, item2, item3));

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(memory);

        TokenEstimator estimator = new TokenEstimator();
        int itemTokens = estimator.estimateTokens(item3);
        EnumMap<ContextSection, Integer> sectionTokens = defaultSectionTokens();
        sectionTokens.put(ContextSection.WORKING_MEMORY, itemTokens);

        ContextBudgetAllocation allocation = buildAllocation(itemTokens, sectionTokens);

        ContextTrimResult result = trimmer.trim(new ContextTrimRequest(snapshot, allocation, new ContextBudgetPolicy()));

        List<String> remaining = result.getTrimmedSnapshot().getWorkingMemory().getKeyFacts();
        assertNotNull(remaining);
        assertEquals(1, remaining.size());
        assertEquals(item3, remaining.get(0));
    }

    @Test
    void trimByTotalBudgetFollowsOrder() {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        DefaultContextTrimmer trimmer = buildTrimmer(properties);

        ToolCallEvidence call = new ToolCallEvidence();
        call.setToolName("tool-a");
        call.setResultDigest("a".repeat(400));

        EvidencePack pack = new EvidencePack();
        pack.setToolCalls(List.of(call));

        WorkingMemory workingMemory = new WorkingMemory();
        workingMemory.setSummary("b".repeat(80));
        workingMemory.setEvidencePack(pack);

        MemoryRef ref = new MemoryRef();
        ref.setMemoryId("m1");
        ref.setSnippet("c".repeat(80));
        ref.setScore(0.8);

        LongTermMemory longTermMemory = new LongTermMemory();
        longTermMemory.setMemoryRefs(List.of(ref));

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(workingMemory);
        snapshot.setLongTermMemory(longTermMemory);

        TokenEstimator estimator = new TokenEstimator();
        int evidenceTokens = estimateToolCallTokens(estimator, call);
        int workingTokens = estimator.estimateTokens(workingMemory.getSummary());
        int memoryTokens = estimateMemoryRefTokens(estimator, ref);
        int totalBefore = evidenceTokens + workingTokens + memoryTokens;
        int totalBudget = totalBefore - 10;

        EnumMap<ContextSection, Integer> sectionTokens = defaultSectionTokens();
        sectionTokens.put(ContextSection.EVIDENCE_PACK, 1000);
        sectionTokens.put(ContextSection.WORKING_MEMORY, 1000);
        sectionTokens.put(ContextSection.LONG_TERM_MEMORY, 1000);

        ContextBudgetAllocation allocation = buildAllocation(totalBudget, sectionTokens);

        String originalSummary = workingMemory.getSummary();
        int originalDigestLength = call.getResultDigest().length();

        ContextTrimResult result = trimmer.trim(new ContextTrimRequest(snapshot, allocation, new ContextBudgetPolicy()));

        assertEquals(originalSummary, result.getTrimmedSnapshot().getWorkingMemory().getSummary());
        assertEquals(1, result.getTrimmedSnapshot().getLongTermMemory().getMemoryRefs().size());

        EvidencePack trimmedPack = result.getTrimmedSnapshot().getWorkingMemory().getEvidencePack();
        List<ToolCallEvidence> calls = trimmedPack != null ? trimmedPack.getToolCalls() : null;
        assertTrue(calls == null || calls.isEmpty() || calls.get(0).getResultDigest().length() < originalDigestLength);
    }

    @Test
    void trimSkippedWhenBudgetDisabled() {
        ContextBudgetProperties properties = new ContextBudgetProperties();
        properties.setEnabled(false);
        DefaultContextTrimmer trimmer = buildTrimmer(properties);

        WorkingMemory memory = new WorkingMemory();
        memory.setSummary("a".repeat(200));

        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setWorkingMemory(memory);

        ContextBudgetAllocation allocation = buildAllocation(50, defaultSectionTokens());

        ContextTrimResult result = trimmer.trim(new ContextTrimRequest(snapshot, allocation, new ContextBudgetPolicy()));

        assertNull(result.getReport());
    }

    private DefaultContextTrimmer buildTrimmer(ContextBudgetProperties properties) {
        return new DefaultContextTrimmer(new TokenEstimator(), new MetricsPublisher(new SimpleMeterRegistry()),
                properties);
    }

    private ContextBudgetAllocation buildAllocation(int totalTokens, EnumMap<ContextSection, Integer> sectionTokens) {
        ContextBudgetAllocation allocation = new ContextBudgetAllocation();
        allocation.setTotalTokens(totalTokens);
        allocation.setSectionTokens(sectionTokens);
        return allocation;
    }

    private EnumMap<ContextSection, Integer> defaultSectionTokens() {
        EnumMap<ContextSection, Integer> sectionTokens = new EnumMap<>(ContextSection.class);
        for (ContextSection section : ContextSection.values()) {
            sectionTokens.put(section, 0);
        }
        return sectionTokens;
    }

    private int estimateMemoryRefTokens(TokenEstimator estimator, MemoryRef ref) {
        int total = 0;
        total += estimator.estimateTokens(ref.getMemoryId());
        total += estimator.estimateTokens(ref.getMemoryType());
        total += estimator.estimateTokens(ref.getSnippet());
        total += estimator.estimateTokens(ref.getSource());
        return total;
    }

    private int estimateToolCallTokens(TokenEstimator estimator, ToolCallEvidence evidence) {
        int total = 0;
        total += estimator.estimateTokens(evidence.getToolName());
        total += estimator.estimateTokens(evidence.getArgsDigest());
        total += estimator.estimateTokens(evidence.getResultDigest());
        total += estimator.estimateTokens(evidence.getStatus());
        total += estimator.estimateTokens(evidence.getErrorCode());
        total += estimator.estimateTokens(evidence.getToolCallId());
        return total;
    }
}
