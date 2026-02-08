package com.example.agent.capabilities.llm.tooling;

import com.example.agent.capabilities.tools.registry.ToolRegistry;
import com.example.agent.common.error.ErrorCodeException;
import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.tools.mcp.McpToolDefinition;
import com.example.agent.capabilities.tools.ToolCatalog;
import com.example.agent.capabilities.tools.ToolCatalogService;
import com.example.agent.capabilities.tools.ToolSummary;
import com.example.agent.capabilities.tools.skill.SkillDefinition;
import com.example.agent.capabilities.tools.skill.SkillRegistry;
import com.example.agent.capabilities.llm.tooling.ToolingContextMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelToolResolverTest {

    @Test
    void applyToolingInjectsSummariesWhenNoSkill() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ToolCatalog toolCatalog = Mockito.mock(ToolCatalog.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, toolCatalog,
                new ObjectMapper(), metricsPublisher, new ToolingContextMapper());

        ToolSummary summaryA = new ToolSummary();
        summaryA.setToolName("tool_a");
        summaryA.setDescription("a");
        ToolSummary summaryB = new ToolSummary();
        summaryB.setToolName("tool_b");
        summaryB.setDescription("b");
        when(toolCatalog.listSummaries(any())).thenReturn(List.of(summaryA, summaryB));
        when(skillRegistry.listDefinitions()).thenReturn(List.of());

        ModelRequest request = new ModelRequest();
        resolver.applyTooling(request, new TaskRequest(), null);

        assertNotNull(request.getTools());
        assertEquals(2, request.getTools().size());
        assertNull(request.getTools().get(0).getParameters());
        assertNull(request.getTools().get(1).getParameters());
        verify(metricsPublisher, times(2)).increment("model_tool_injected_summaries_total");
    }

    @Test
    void applyToolingSkipsWhenToolChoiceNone() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ToolCatalog toolCatalog = Mockito.mock(ToolCatalog.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, toolCatalog,
                new ObjectMapper(), metricsPublisher, new ToolingContextMapper());

        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setToolChoice(ModelToolChoice.none());

        ModelRequest request = new ModelRequest();
        resolver.applyTooling(request, taskRequest, null);

        assertNotNull(request.getToolChoice());
        assertEquals(ModelToolChoice.Mode.NONE, request.getToolChoice().getMode());
        assertNotNull(request.getTools());
        assertEquals(0, request.getTools().size());
    }

    @Test
    void applyToolingSkipsWhenDisableToolsFlagSet() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ToolCatalog toolCatalog = Mockito.mock(ToolCatalog.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, toolCatalog,
                new ObjectMapper(), metricsPublisher, new ToolingContextMapper());

        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setContext(Map.of("disableTools", true));

        ModelRequest request = new ModelRequest();
        resolver.applyTooling(request, taskRequest, null);

        assertNotNull(request.getToolChoice());
        assertEquals(ModelToolChoice.Mode.NONE, request.getToolChoice().getMode());
        assertNotNull(request.getTools());
        assertEquals(0, request.getTools().size());
    }

    @Test
    void applyToolingFiltersToolsBySkillAllowList() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ToolCatalog toolCatalog = Mockito.mock(ToolCatalog.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, toolCatalog,
                new ObjectMapper(), metricsPublisher, new ToolingContextMapper());

        ToolSummary summaryA = new ToolSummary();
        summaryA.setToolName("tool_a");
        summaryA.setDescription("a");
        ToolSummary summaryB = new ToolSummary();
        summaryB.setToolName("tool_b");
        summaryB.setDescription("b");
        when(toolCatalog.listSummaries(any())).thenReturn(List.of(summaryA, summaryB));

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
        assertNull(request.getTools().get(0).getParameters());
        verify(metricsPublisher, times(1)).increment("model_tool_injected_summaries_total");
    }

    @Test
    void applyToolingOverridesToolChoiceFromSkillConstraints() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ToolCatalog toolCatalog = Mockito.mock(ToolCatalog.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, toolCatalog,
                new ObjectMapper(), metricsPublisher, new ToolingContextMapper());

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

    @Test
    void applyToolingLoadsSpecifiedToolOnly() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ToolCatalog toolCatalog = Mockito.mock(ToolCatalog.class, Mockito.withSettings().extraInterfaces(ToolCatalogService.class));
        ToolCatalogService toolCatalogService = (ToolCatalogService) toolCatalog;
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, toolCatalog,
                new ObjectMapper(), metricsPublisher, new ToolingContextMapper());

        McpToolDefinition toolA = new McpToolDefinition("tool_a", "v1", "a",
                Map.of("type", "object"), Map.of("type", "object"), List.of());
        McpToolDefinition toolB = new McpToolDefinition("tool_b", "v1", "b",
                Map.of("type", "object"), Map.of("type", "object"), List.of());

        when(toolRegistry.listDefinitions()).thenReturn(List.of(toolA, toolB));
        when(toolCatalog.getDefinition("tool_b")).thenReturn(toolB);
        when(toolCatalogService.getToolSchema("tool_b")).thenReturn(Map.of("type", "object"));

        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setToolChoice(ModelToolChoice.specified("tool_b"));

        ModelRequest request = new ModelRequest();
        resolver.applyTooling(request, taskRequest, null);

        assertNotNull(request.getTools());
        assertEquals(1, request.getTools().size());
        assertEquals("tool_b", request.getTools().get(0).getName());
        assertNotNull(request.getTools().get(0).getParameters());
        verify(metricsPublisher, times(1)).increment("model_tool_on_demand_schema_total");
    }

    @Test
    void applyToolingUsesFullModeWhenConfigured() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ToolCatalog toolCatalog = Mockito.mock(ToolCatalog.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, toolCatalog,
                new ObjectMapper(), metricsPublisher, new ToolingContextMapper());
        ReflectionTestUtils.setField(resolver, "toolInjectMode", "full");

        when(toolRegistry.listDefinitions()).thenReturn(List.of(
                new McpToolDefinition("tool_a", "v1", "a",
                        Map.of("type", "object"), Map.of("type", "object"), List.of())
        ));
        when(skillRegistry.listDefinitions()).thenReturn(List.of());

        ModelRequest request = new ModelRequest();
        resolver.applyTooling(request, new TaskRequest(), null);

        assertNotNull(request.getTools());
        assertNotNull(request.getTools().get(0).getParameters());
        verify(metricsPublisher, never()).increment("model_tool_injected_summaries_total");
    }

    @Test
    void applyToolingFailsWhenSchemaNotFound() {
        ToolRegistry toolRegistry = Mockito.mock(ToolRegistry.class);
        SkillRegistry skillRegistry = Mockito.mock(SkillRegistry.class);
        ToolCatalog toolCatalog = Mockito.mock(ToolCatalog.class, Mockito.withSettings().extraInterfaces(ToolCatalogService.class));
        ToolCatalogService toolCatalogService = (ToolCatalogService) toolCatalog;
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelToolResolver resolver = new ModelToolResolver(toolRegistry, skillRegistry, toolCatalog,
                new ObjectMapper(), metricsPublisher, new ToolingContextMapper());

        when(toolCatalogService.getToolSchema("tool_missing")).thenReturn(null);

        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setToolChoice(ModelToolChoice.specified("tool_missing"));

        assertThrows(ErrorCodeException.class, () -> resolver.applyTooling(new ModelRequest(), taskRequest, null));
        verify(metricsPublisher, times(1)).increment("model_tool_on_demand_schema_not_found_total");
    }
}

