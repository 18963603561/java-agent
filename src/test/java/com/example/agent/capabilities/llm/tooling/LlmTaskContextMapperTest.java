package com.example.agent.capabilities.llm.tooling;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.llm.contract.LlmTaskContextMapper;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmTaskContextMapperTest {

    @Test
    void fromTaskRequestShouldMapFields() {
        TaskRequest request = new TaskRequest();
        request.setSkillName("skill-a");
        request.setToolChoice(ModelToolChoice.required());
        request.setContext(Map.of("tenantId", "tenant-a", "k", "v"));

        LlmTaskContext context = LlmTaskContextMapper.fromTaskRequest(request);

        assertNotNull(context);
        assertEquals("tenant-a", context.getTenantId());
        assertEquals("skill-a", context.getSkillName());
        assertEquals(ModelToolChoice.Mode.REQUIRED, context.getToolChoice().getMode());
        assertEquals("v", context.getContext().get("k"));
    }

    @Test
    void fromTaskRequestShouldReturnEmptyWhenNull() {
        LlmTaskContext context = LlmTaskContextMapper.fromTaskRequest(null);

        assertNotNull(context);
        assertNull(context.getTenantId());
        assertNull(context.getSkillName());
        assertNull(context.getToolChoice());
        assertEquals(Map.of(), context.getContext());
    }
}
