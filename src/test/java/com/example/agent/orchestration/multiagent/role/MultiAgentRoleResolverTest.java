package com.example.agent.orchestration.multiagent.role;

import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.orchestration.multiagent.AgentProfile;
import com.example.agent.orchestration.multiagent.AgentProfileProperties;
import com.example.agent.orchestration.multiagent.MultiAgentRoleResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class MultiAgentRoleResolverTest {

    @Test
    void shouldResolveRolesWithoutRepairWhenJsonValid() {
        MultiAgentRoleResolver resolver = buildResolver(Mockito.mock(JsonOutputRepairService.class), new AgentProfileProperties());
        String raw = "{\"team\":[{\"roleId\":\"planner\",\"name\":\"Planner\",\"modelId\":\"m1\",\"description\":\"plan\"}]}";

        MultiAgentRoleResolver.RoleResolveResult result = resolver.resolve(raw,
                Map.of("query", "x"),
                "wf-1",
                "MULTI_AGENT");

        assertEquals(1, result.roles().size());
        assertFalse(result.repairAttempted());
        assertFalse(result.repairSuccess());
        assertEquals("planner", result.roles().get(0).getRoleId());
    }

    @Test
    void shouldRepairAndResolveWhenRawJsonInvalid() {
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        when(repairService.repair(eq("multiagent"), anyString(), eq(com.example.agent.capabilities.llm.repair.JsonOutputSchema.MULTIAGENT), anyString(), anyInt()))
                .thenReturn("{\"team\":[{\"roleId\":\"writer\",\"name\":\"Writer\",\"modelId\":\"m2\",\"description\":\"write\"}]}");
        MultiAgentRoleResolver resolver = buildResolver(repairService, new AgentProfileProperties());

        MultiAgentRoleResolver.RoleResolveResult result = resolver.resolve("{invalid",
                Map.of("query", "x"),
                "wf-2",
                "MULTI_AGENT");

        assertTrue(result.repairAttempted());
        assertTrue(result.repairSuccess());
        assertEquals("json_parse_error", result.parseErrorType());
        assertEquals(1, result.roles().size());
        assertEquals("writer", result.roles().get(0).getRoleId());
    }

    @Test
    void shouldReturnEmptyWhenRepairFails() {
        JsonOutputRepairService repairService = Mockito.mock(JsonOutputRepairService.class);
        when(repairService.repair(eq("multiagent"), anyString(), eq(com.example.agent.capabilities.llm.repair.JsonOutputSchema.MULTIAGENT), anyString(), anyInt()))
                .thenReturn(null);
        MultiAgentRoleResolver resolver = buildResolver(repairService, new AgentProfileProperties());

        MultiAgentRoleResolver.RoleResolveResult result = resolver.resolve("{broken",
                Map.of(),
                "wf-3",
                "MULTI_AGENT");

        assertTrue(result.repairAttempted());
        assertFalse(result.repairSuccess());
        assertEquals("json_parse_error", result.parseErrorType());
        assertTrue(result.roles().isEmpty());
    }

    @Test
    void shouldBuildFallbackRolesFromProfiles() {
        AgentProfile profile = new AgentProfile();
        profile.setAgentId("planner");
        profile.setModelId("m1");
        profile.setPrompt("plan");
        AgentProfileProperties properties = new AgentProfileProperties();
        properties.setItems(List.of(profile));
        MultiAgentRoleResolver resolver = buildResolver(Mockito.mock(JsonOutputRepairService.class), properties);

        assertEquals(1, resolver.buildFallbackRoles().size());
        assertEquals("planner", resolver.buildFallbackRoles().get(0).getRoleId());
    }

    @Test
    void shouldBuildDefaultFallbackWhenNoProfiles() {
        AgentProfileProperties properties = new AgentProfileProperties();
        properties.setItems(List.of());
        MultiAgentRoleResolver resolver = buildResolver(Mockito.mock(JsonOutputRepairService.class), properties);

        assertEquals(1, resolver.buildFallbackRoles().size());
        assertEquals("default", resolver.buildFallbackRoles().get(0).getRoleId());
    }

    private MultiAgentRoleResolver buildResolver(JsonOutputRepairService repairService,
                                                 AgentProfileProperties properties) {
        ObjectMapper objectMapper = new ObjectMapper();
        RolePlanParser rolePlanParser = new RolePlanParser(objectMapper);
        RolePlanRepairService rolePlanRepairService = new RolePlanRepairService(objectMapper, repairService);
        RoleFallbackFactory roleFallbackFactory = new RoleFallbackFactory(properties);
        RoleResolveDiagnostics roleResolveDiagnostics = new RoleResolveDiagnostics();
        return new MultiAgentRoleResolver(rolePlanParser,
                rolePlanRepairService,
                roleFallbackFactory,
                roleResolveDiagnostics);
    }
}

