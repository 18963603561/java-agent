package com.example.agent.model;

import com.example.agent.common.TaskRequest;
import com.example.agent.context.ContextSnapshot;
import com.example.agent.context.RoleBoundary;
import com.example.agent.memory.TokenEstimator;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DefaultPromptAssemblerTest {

    @Test
    void buildAddsSystemDeveloperAndUserMessages() {
        DefaultPromptTemplate template = new DefaultPromptTemplate();
        ReflectionTestUtils.setField(template, "systemMessage", "系统策略");
        ReflectionTestUtils.setField(template, "developerMessage", "开发者策略");

        DefaultPromptAssembler assembler = new DefaultPromptAssembler(template, new TokenEstimator());

        ContextSnapshot snapshot = new ContextSnapshot();
        RoleBoundary boundary = new RoleBoundary();
        boundary.setRiskLevel("low");
        snapshot.setRoleBoundary(boundary);

        TaskRequest taskRequest = new TaskRequest();
        Map<String, Object> context = new HashMap<>();
        context.put("contextSnapshot", snapshot);
        taskRequest.setContext(context);

        PromptBundle bundle = assembler.build("用户问题", taskRequest, null);

        assertNotNull(bundle);
        assertEquals(3, bundle.getMessages().size());
        assertEquals(PromptRole.SYSTEM, bundle.getMessages().get(0).getRole());
        assertEquals(PromptRole.DEVELOPER, bundle.getMessages().get(1).getRole());
        assertEquals(PromptRole.USER, bundle.getMessages().get(2).getRole());
    }
}
