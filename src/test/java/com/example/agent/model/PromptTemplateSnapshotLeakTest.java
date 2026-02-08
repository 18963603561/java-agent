package com.example.agent.model;

import com.example.agent.capabilities.context.AuditMetadata;
import com.example.agent.capabilities.context.BudgetState;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.RoleBoundary;
import com.example.agent.capabilities.context.RuntimeMeta;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.example.agent.capabilities.llm.prompt.PromptMessage;
import com.example.agent.capabilities.llm.prompt.DefaultPromptTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateSnapshotLeakTest {

    @Test
    void renderDoesNotLeakRuntimeFields() {
        DefaultPromptTemplate template = new DefaultPromptTemplate();
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snapshot-001");

        RuntimeMeta runtimeMeta = new RuntimeMeta();
        runtimeMeta.setTenantId("tenant-123");
        runtimeMeta.setWorkflowId("workflow-456");
        runtimeMeta.setTraceId("trace-789");
        snapshot.setRuntimeMeta(runtimeMeta);

        BudgetState budgetState = new BudgetState();
        budgetState.setAllocatedTokens(128);
        snapshot.setBudgetState(budgetState);

        AuditMetadata auditMetadata = new AuditMetadata();
        auditMetadata.setSource("audit-source");
        snapshot.setAuditMetadata(auditMetadata);

        RoleBoundary boundary = new RoleBoundary();
        boundary.setRiskLevel("high");
        snapshot.setRoleBoundary(boundary);

        List<PromptMessage> messages = template.render(snapshot);

        String combined = concatMessages(messages);
        assertNotNull(combined);
        assertFalse(combined.contains("tenant-123"));
        assertFalse(combined.contains("workflow-456"));
        assertFalse(combined.contains("trace-789"));
        assertFalse(combined.contains("snapshot-001"));
        assertTrue(combined.contains("风险等级:high"));
    }

    private String concatMessages(List<PromptMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (PromptMessage message : messages) {
            if (message == null || message.getContent() == null) {
                continue;
            }
            builder.append(message.getContent());
        }
        return builder.toString();
    }
}
