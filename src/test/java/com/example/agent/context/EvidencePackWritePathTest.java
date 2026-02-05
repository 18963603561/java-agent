package com.example.agent.context;

import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.tools.hook.EvidencePackHookHandler;
import com.example.agent.capabilities.tools.hook.HookContext;
import com.example.agent.capabilities.tools.hook.HookType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.agent.capabilities.context.EvidenceItem;
import com.example.agent.capabilities.context.EvidencePack;
import com.example.agent.capabilities.context.EvidencePackService;
import com.example.agent.capabilities.context.EvidenceType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EvidencePackWritePathTest {

    @Test
    void postToolHookAppendsToolEvidence() {
        EvidencePackService service = new EvidencePackService(new MetricsPublisher(new SimpleMeterRegistry()));
        EvidencePackHookHandler handler = new EvidencePackHookHandler(service);

        HookContext context = new HookContext(
                HookType.POST_TOOL,
                "demo_tool",
                "step-1",
                "tenant-1",
                Map.of(
                        "workflowId", "wf-1",
                        "snapshotId", "snap-1",
                        "toolName", "demo_tool",
                        "rawRef", "raw:1",
                        "resultDigest", "ok"
                )
        );

        handler.handle(context);

        EvidencePack pack = service.getPack("tenant-1", "wf-1");
        assertNotNull(pack);
        assertNotNull(pack.getEvidences());
        assertEquals(1, pack.getEvidences().size());
        EvidenceItem item = pack.getEvidences().get(0);
        assertEquals(EvidenceType.TOOL_RESULT, item.getType());
        assertEquals("demo_tool", item.getSource());
        assertEquals("raw:1", item.getRef());
        assertEquals(1, pack.getStats().getToolCount());
    }

    @Test
    void postRecallHookAppendsMemoryEvidence() {
        EvidencePackService service = new EvidencePackService(new MetricsPublisher(new SimpleMeterRegistry()));
        EvidencePackHookHandler handler = new EvidencePackHookHandler(service);

        HookContext context = new HookContext(
                HookType.POST_RECALL,
                null,
                "step-2",
                "tenant-1",
                Map.of(
                        "workflowId", "wf-1",
                        "records", List.of(
                                Map.of("memoryId", "m-1"),
                                Map.of("memoryId", "m-2")
                        )
                )
        );

        handler.handle(context);

        EvidencePack pack = service.getPack("tenant-1", "wf-1");
        assertNotNull(pack);
        assertNotNull(pack.getEvidences());
        assertEquals(2, pack.getEvidences().size());
        assertEquals(EvidenceType.MEMORY, pack.getEvidences().get(0).getType());
        assertEquals(EvidenceType.MEMORY, pack.getEvidences().get(1).getType());
        assertEquals(2, pack.getStats().getMemoryCount());
    }
}
