package com.example.agent.model;

import com.example.agent.agentcore.ToolRegistry;
import com.example.agent.common.TaskRequest;
import com.example.agent.tools.McpToolDefinition;
import com.example.agent.tools.skill.SkillDefinition;
import com.example.agent.tools.skill.SkillRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class ModelToolResolverTest {

    @Test
    void applyToolingInjectsAllToolsWhenNoSkill() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, new ObjectMapper());

        when(toolRegistry.listDefinitions()).thenReturn(List.of(
                new McpToolDefinition("tool_a", "v1", "a",
                        Map.of("type", "object"), Map.of("type", "object"), List.of()),
                new McpToolDefinition("tool_b", "v1", "b",
                        Map.of("type", "object"), Map.of("type", "object"), List.of())
        ));
        when(skillRegistry.listDefinitions()).thenReturn(List.of());

        ModelRequest request = new ModelRequest();
        resolver.applyTooling(request, new TaskRequest(), null);

        assertNotNull(request.getTools());
        assertEquals(2, request.getTools().size());
    }

    @Test
    void applyToolingFiltersToolsBySkillAllowList() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, new ObjectMapper());

        when(toolRegistry.listDefinitions()).thenReturn(List.of(
                new McpToolDefinition("tool_a", "v1", "a",
                        Map.of("type", "object"), Map.of("type", "object"), List.of()),
                new McpToolDefinition("tool_b", "v1", "b",
                        Map.of("type", "object"), Map.of("type", "object"), List.of())
        ));

        SkillDefinition skill = new SkillDefinition();
        skill.setName("skill-a");
        skill.setConstraints(Map.of("allowTools", List.of("tool_b")));
        when(skillRegistry.listDefinitions()).thenReturn(List.of(skill));

        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setSkillName("skill-a");

        ModelRequest request = new ModelRequest();
        resolver.applyTooling(request, taskRequest, null);

        assertNotNull(request.getTools());
        assertEquals(1, request.getTools().size());
        assertEquals("tool_b", request.getTools().get(0).getName());
    }

    @Test
    void applyToolingOverridesToolChoiceFromSkillConstraints() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, new ObjectMapper());

        when(toolRegistry.listDefinitions()).thenReturn(List.of(
                new McpToolDefinition("tool_a", "v1", "a",
                        Map.of("type", "object"), Map.of("type", "object"), List.of())
        ));

        SkillDefinition skill = new SkillDefinition();
        skill.setName("skill-b");
        skill.setConstraints(Map.of("toolChoice", "required"));
        when(skillRegistry.listDefinitions()).thenReturn(List.of(skill));

        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setSkillName("skill-b");

        ModelRequest request = new ModelRequest();
        resolver.applyTooling(request, taskRequest, null);

        assertNotNull(request.getToolChoice());
        assertEquals(ModelToolChoice.Mode.REQUIRED, request.getToolChoice().getMode());
    }
}
