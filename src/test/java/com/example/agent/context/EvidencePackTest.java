package com.example.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EvidencePackTest {

    @Test
    void recomputeStatsCounts() {
        ToolCallEvidence toolCallA = new ToolCallEvidence();
        toolCallA.setToolName("tool-a");
        toolCallA.setArgsDigest("args-a");
        toolCallA.setResultDigest("result-a");
        toolCallA.setStatus("SUCCESS");
        toolCallA.setDurationMs(120L);

        ToolCallEvidence toolCallB = new ToolCallEvidence();
        toolCallB.setToolName("tool-b");
        toolCallB.setArgsDigest("args-b");
        toolCallB.setResultDigest("result-b");
        toolCallB.setStatus("FAILED");
        toolCallB.setErrorCode("TOOL_ERROR");

        MemoryEvidence memoryEvidence = new MemoryEvidence();
        memoryEvidence.setMemoryId("memory-1");
        memoryEvidence.setScore(0.86);
        memoryEvidence.setSummaryVersion("v1");

        Citation citationA = new Citation();
        citationA.setType("MEMORY");
        citationA.setRefId("memory-1");
        citationA.setLabel("memory-ref");

        Citation citationB = new Citation();
        citationB.setType("URL");
        citationB.setRefId("https://example.com");
        citationB.setLabel("external-link");

        EvidencePack pack = new EvidencePack();
        pack.setToolCalls(List.of(toolCallA, toolCallB));
        pack.setMemoriesUsed(List.of(memoryEvidence));
        pack.setCitations(List.of(citationA, citationB));

        EvidenceStats stats = pack.recomputeStats();

        assertNotNull(stats);
        assertEquals(2, stats.getToolCallsCount());
        assertEquals(1, stats.getMemoriesCount());
        assertEquals(2, stats.getCitationsCount());
        assertNotNull(stats.getUpdatedAt());
    }

    @Test
    void jacksonRoundTripKeepsCoreFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        ToolCallEvidence toolCall = new ToolCallEvidence();
        toolCall.setToolName("tool-a");
        toolCall.setArgsDigest("args-a");
        toolCall.setResultDigest("result-a");
        toolCall.setStatus("SUCCESS");

        MemoryEvidence memoryEvidence = new MemoryEvidence();
        memoryEvidence.setMemoryId("memory-1");
        memoryEvidence.setScore(0.91);
        memoryEvidence.setSummaryVersion("v1");

        Citation citation = new Citation();
        citation.setType("MEMORY");
        citation.setRefId("memory-1");
        citation.setLabel("memory-ref");

        EvidencePack pack = new EvidencePack();
        pack.setVersion("v1");
        pack.setTenantId("tenant-1");
        pack.setWorkflowId("workflow-1");
        pack.setSnapshotId("snapshot-1");
        pack.setCreatedAt(Instant.parse("2026-01-28T00:00:00Z"));
        pack.setToolCalls(List.of(toolCall));
        pack.setMemoriesUsed(List.of(memoryEvidence));
        pack.setCitations(List.of(citation));
        pack.recomputeStats();

        String payload = mapper.writeValueAsString(pack);
        EvidencePack restored = mapper.readValue(payload, EvidencePack.class);

        assertEquals("v1", restored.getVersion());
        assertEquals("tenant-1", restored.getTenantId());
        assertEquals("workflow-1", restored.getWorkflowId());
        assertEquals("snapshot-1", restored.getSnapshotId());
        assertNotNull(restored.getToolCalls());
        assertEquals(1, restored.getToolCalls().size());
        assertNotNull(restored.getMemoriesUsed());
        assertEquals(1, restored.getMemoriesUsed().size());
        assertNotNull(restored.getCitations());
        assertEquals(1, restored.getCitations().size());
    }

    @Test
    void emptyPackDoesNotFail() {
        EvidencePack pack = new EvidencePack();
        EvidenceStats stats = pack.recomputeStats();

        assertNotNull(stats);
        assertEquals(0, stats.getToolCallsCount());
        assertEquals(0, stats.getMemoriesCount());
        assertEquals(0, stats.getCitationsCount());
    }
}
