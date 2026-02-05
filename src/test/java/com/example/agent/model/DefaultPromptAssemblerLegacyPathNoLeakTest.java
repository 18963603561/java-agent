package com.example.agent.model;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.context.ContextSnapshot;
import com.example.agent.capabilities.context.RoleBoundary;
import com.example.agent.capabilities.context.RuntimeMeta;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.example.agent.capabilities.llm.PromptMessage;
import com.example.agent.capabilities.llm.DefaultPromptAssembler;
import com.example.agent.capabilities.llm.DefaultPromptTemplate;
import com.example.agent.capabilities.llm.PromptBundle;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPromptAssemblerLegacyPathNoLeakTest {

    @Test
    void buildLegacyShouldNotLeakRuntimeFields() {
        DefaultPromptAssembler assembler = new DefaultPromptAssembler(new DefaultPromptTemplate(), null, null);
        ContextSnapshot snapshot = new ContextSnapshot();
        snapshot.setSnapshotId("snapshot-003");

        RuntimeMeta runtimeMeta = new RuntimeMeta();
        runtimeMeta.setTenantId("tenant-999");
        runtimeMeta.setWorkflowId("workflow-888");
        runtimeMeta.setTraceId("trace-777");
        snapshot.setRuntimeMeta(runtimeMeta);

        RoleBoundary boundary = new RoleBoundary();
        boundary.setRiskLevel("low");
        snapshot.setRoleBoundary(boundary);

        Map<String, Object> context = new HashMap<>();
        context.put("contextSnapshot", snapshot);
        TaskRequest request = new TaskRequest();
        request.setQuery("测试");
        request.setContext(context);

        PromptBundle bundle = assembler.build("用户提示", request, null);
        assertNotNull(bundle);
        List<PromptMessage> messages = bundle.getMessages();

        String combined = concatMessages(messages);
        assertFalse(combined.contains("tenant-999"));
        assertFalse(combined.contains("workflow-888"));
        assertFalse(combined.contains("trace-777"));
        assertFalse(combined.contains("snapshot-003"));
        assertTrue(combined.contains("风险等级:low"));
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
