package com.example.agent.model;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.llm.contract.ModelToolChoice;
import com.example.agent.capabilities.llm.tooling.ModelToolingContext;
import com.example.agent.capabilities.llm.tooling.SkillToolPolicy;
import com.example.agent.capabilities.llm.tooling.ToolingConstraints;
import com.example.agent.capabilities.llm.tooling.ToolingContextMapper;
import com.example.agent.capabilities.tools.skill.SkillDefinition;
import com.example.agent.capabilities.tools.skill.SkillRoute;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolingContextMapperTest {

    private final ToolingContextMapper mapper = new ToolingContextMapper();

    @Test
    void toToolingContextResolvesStepPriorityAndDisableFlag() {
        TaskRequest taskRequest = new TaskRequest();
        taskRequest.setSkillName("task-skill");
        taskRequest.setToolChoice(ModelToolChoice.required());
        taskRequest.setContext(Map.of(
                "tenantId", "tenant-from-task",
                "disableTools", false,
                "toolChoice", "auto"
        ));

        Map<String, Object> stepInput = Map.of(
                "tenantId", "tenant-from-step",
                "skill", "step-skill",
                "toolChoice", "none",
                "disableTools", false
        );

        ModelToolingContext context = mapper.toToolingContext(taskRequest, stepInput);

        assertEquals("tenant-from-step", context.getTenantId());
        assertEquals("step-skill", context.getSkillName());
        assertNotNull(context.getExplicitToolChoice());
        assertEquals(ModelToolChoice.Mode.NONE, context.getExplicitToolChoice().getMode());
        assertTrue(context.isDisableTools());
    }

    @Test
    void toToolingContextResolvesDisableToolsFromNestedContext() {
        Map<String, Object> stepInput = Map.of(
                "context", Map.of("disableTools", "true")
        );

        ModelToolingContext context = mapper.toToolingContext(new TaskRequest(), stepInput);

        assertTrue(context.isDisableTools());
    }

    @Test
    void toConstraintsReadsAllowToolsAndToolChoiceFromConstraints() {
        SkillDefinition definition = new SkillDefinition();
        definition.setConstraints(Map.of(
                "allowTools", List.of("search", "fetch"),
                "toolChoice", "required"
        ));

        ToolingConstraints constraints = mapper.toConstraints(definition);
        SkillToolPolicy policy = constraints.getSkillToolPolicy();

        assertEquals(List.of("search", "fetch"), policy.getAllowTools());
        assertNotNull(policy.getToolChoice());
        assertEquals(ModelToolChoice.Mode.REQUIRED, policy.getToolChoice().getMode());
    }

    @Test
    void toConstraintsFallsBackToRoutesWhenAllowToolsMissing() {
        SkillRoute routeA = new SkillRoute();
        routeA.setToolName("tool_a");
        SkillRoute routeB = new SkillRoute();
        routeB.setToolName("tool_b");
        SkillDefinition definition = new SkillDefinition();
        definition.setRoutes(List.of(routeA, routeB));

        ToolingConstraints constraints = mapper.toConstraints(definition);
        SkillToolPolicy policy = constraints.getSkillToolPolicy();

        assertEquals(List.of("tool_a", "tool_b"), policy.getAllowTools());
        assertNull(policy.getToolChoice());
        assertTrue(policy.hasAllowTools());
    }

    @Test
    void toConstraintsReturnsEmptyPolicyWhenDefinitionMissing() {
        ToolingConstraints constraints = mapper.toConstraints(null);
        SkillToolPolicy policy = constraints.getSkillToolPolicy();

        assertNotNull(policy);
        assertFalse(policy.hasAllowTools());
        assertNull(policy.getToolChoice());
    }
}

