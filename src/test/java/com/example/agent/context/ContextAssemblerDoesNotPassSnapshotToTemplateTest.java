package com.example.agent.context;

import com.example.agent.capabilities.llm.prompt.DefaultPromptTemplate;
import org.junit.jupiter.api.Test;
import com.example.agent.capabilities.context.assembly.ContextAssemblyCommand;
import com.example.agent.capabilities.context.model.ContextSnapshot;
import com.example.agent.capabilities.context.assembly.DefaultContextAssembler;
import com.example.agent.capabilities.context.assembly.PromptAssemblyInput;
import com.example.agent.capabilities.context.model.RoleBoundary;
import com.example.agent.capabilities.context.model.RuntimeMeta;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblerDoesNotPassSnapshotToTemplateTest {

    @Test
    void assembleShouldNotLeakRuntimeFields() {
        DefaultContextAssembler assembler = new DefaultContextAssembler(new DefaultPromptTemplate(), null, null);
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

        ContextAssemblyCommand command = ContextAssemblyCommand.builder()
                .snapshot(snapshot)
                .build();
        PromptAssemblyInput input = assembler.assemble(command);

        String combined = (input.getSystemText() == null ? "" : input.getSystemText())
                + (input.getDeveloperText() == null ? "" : input.getDeveloperText());
        assertNotNull(combined);
        assertFalse(combined.contains("tenant-456"));
        assertFalse(combined.contains("workflow-789"));
        assertFalse(combined.contains("trace-001"));
        assertFalse(combined.contains("snapshot-002"));
        assertTrue(combined.contains("风险等级:medium"));
        assertNotNull(input.getAssemblyMetadata());
        assertEquals(Boolean.FALSE, input.getAssemblyMetadata().get("trimmed"));
        assertEquals(Boolean.FALSE, input.getAssemblyMetadata().get("pruned"));
        assertEquals(Boolean.FALSE, input.getAssemblyMetadata().get("compressed"));
    }
}
