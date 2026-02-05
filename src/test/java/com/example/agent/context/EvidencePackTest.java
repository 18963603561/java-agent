package com.example.agent.context;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.agent.capabilities.context.EvidenceItem;
import com.example.agent.capabilities.context.EvidencePack;
import com.example.agent.capabilities.context.EvidenceStats;
import com.example.agent.capabilities.context.EvidenceType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EvidencePackTest {

    @Test
    void recomputeStatsCounts() {
        EvidenceItem tool = new EvidenceItem();
        tool.setEvidenceId("ev-tool-1");
        tool.setType(EvidenceType.TOOL_RESULT);
        tool.setSource("weather_tool");
        tool.setDigest("ok");

        EvidenceItem memory = new EvidenceItem();
        memory.setEvidenceId("ev-memory-1");
        memory.setType(EvidenceType.MEMORY);
        memory.setSource("memory");
        memory.setDigest("memory hit");

        EvidenceItem research = new EvidenceItem();
        research.setEvidenceId("ev-research-1");
        research.setType(EvidenceType.RESEARCH);
        research.setSource("example.com");
        research.setDigest("citation");

        EvidenceItem truncation = new EvidenceItem();
        truncation.setEvidenceId("ev-trim-1");
        truncation.setType(EvidenceType.CONTEXT_TRUNCATION);
        truncation.setSource("context_trim");
        truncation.setDigest("trimmed");

        EvidencePack pack = new EvidencePack();
        pack.setEvidences(List.of(tool, memory, research, truncation));

        EvidenceStats stats = pack.recomputeStats();

        assertNotNull(stats);
        assertEquals(1, stats.getToolCount());
        assertEquals(1, stats.getMemoryCount());
        assertEquals(1, stats.getResearchCount());
        assertEquals(1, stats.getTruncationCount());
        assertEquals(4, stats.getTotalCount());
        assertNotNull(stats.getUpdatedAt());
    }

    @Test
    void jacksonRoundTripKeepsCoreFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        EvidenceItem item = new EvidenceItem();
        item.setEvidenceId("ev-1");
        item.setType(EvidenceType.TOOL_RESULT);
        item.setStepId("step-1");
        item.setSource("weather_tool");
        item.setRef("raw:1");
        item.setDigest("ok");
        item.setCreatedAt(Instant.parse("2026-01-28T00:00:00Z"));

        EvidencePack pack = new EvidencePack();
        pack.setVersion("v1");
        pack.setPackId("ep-1");
        pack.setTenantId("tenant-1");
        pack.setWorkflowId("workflow-1");
        pack.setSnapshotId("snapshot-1");
        pack.setCreatedAt(Instant.parse("2026-01-28T00:00:00Z"));
        pack.setEvidences(List.of(item));
        pack.recomputeStats();

        String payload = mapper.writeValueAsString(pack);
        EvidencePack restored = mapper.readValue(payload, EvidencePack.class);

        assertEquals("v1", restored.getVersion());
        assertEquals("ep-1", restored.getPackId());
        assertEquals("tenant-1", restored.getTenantId());
        assertEquals("workflow-1", restored.getWorkflowId());
        assertEquals("snapshot-1", restored.getSnapshotId());
        assertNotNull(restored.getEvidences());
        assertEquals(1, restored.getEvidences().size());
    }

    @Test
    void emptyPackDoesNotFail() {
        EvidencePack pack = new EvidencePack();
        EvidenceStats stats = pack.recomputeStats();

        assertNotNull(stats);
        assertEquals(0, stats.getToolCount());
        assertEquals(0, stats.getMemoryCount());
        assertEquals(0, stats.getResearchCount());
        assertEquals(0, stats.getTruncationCount());
        assertEquals(0, stats.getTotalCount());
    }
}
