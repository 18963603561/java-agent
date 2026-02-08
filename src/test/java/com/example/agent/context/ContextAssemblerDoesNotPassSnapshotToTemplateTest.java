package com.example.agent.context;

import com.example.agent.capabilities.llm.prompt.DefaultPromptTemplate;
import org.junit.jupiter.api.Test;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.DefaultContextAssembler;
import com.example.agent.capabilities.context.PromptAssemblyInput;
import com.example.agent.capabilities.context.RoleBoundary;
import com.example.agent.capabilities.context.RuntimeMeta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblerDoesNotPassSnapshotToTemplateTest {

    @Test
    void assembleShouldNotLeakRuntimeFields() {
        DefaultContextAssembler assembler = new DefaultContextAssembler(new DefaultPromptTemplate(), null);
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snapshot-002");

        RuntimeMeta runtimeMeta = new RuntimeMeta();
        runtimeMeta.setTenantId("tenant-456");
        runtimeMeta.setWorkflowId("workflow-789");
        runtimeMeta.setTraceId("trace-001");
        snapshot.setRuntimeMeta(runtimeMeta);

        RoleBoundary boundary = new RoleBoundary();
        boundary.setRiskLevel("medium");
        snapshot.setRoleBoundary(boundary);

        PromptAssemblyInput input = assembler.assemble(snapshot, null, null, null, null, null, null, null);

        String combined = (input.getSystemText() == null ? "" : input.getSystemText())
                + (input.getDeveloperText() == null ? "" : input.getDeveloperText());
        assertNotNull(combined);
        assertFalse(combined.contains("tenant-456"));
        assertFalse(combined.contains("workflow-789"));
        assertFalse(combined.contains("trace-001"));
        assertFalse(combined.contains("snapshot-002"));
        assertTrue(combined.contains("风险等级:medium"));
    }
}
