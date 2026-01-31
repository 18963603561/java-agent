package com.example.agent.reflection;

import com.example.agent.auth.TenantContext;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.ModelToolResolver;
import com.example.agent.model.PromptAssembler;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.runtime.StepRequest;
import com.example.agent.repair.JsonOutputRepairService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ReflectionServiceTest {

    @Test
    void reflectRequestsRetryWhenScoreLow() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setMaxRetries(2);
        properties.setConfidenceThreshold(0.8);
        properties.setMinOutputChars(30);
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = new ReflectionService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), Mockito.mock(JsonOutputRepairService.class));

        StepRequest step = new StepRequest("TOOL", Map.of("critical", true));
        Map<String, Object> output = Map.of("error", "failed");

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1);
        assertTrue(result.isRetryRequested());
    }

    @Test
    void reflectStopsRetryWhenAttemptsExhausted() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setMaxRetries(1);
        properties.setConfidenceThreshold(0.8);
        properties.setMinOutputChars(30);
        properties.setLlmEnabled(false);
        properties.setFallbackEnabled(true);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = new ReflectionService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), Mockito.mock(JsonOutputRepairService.class));

        StepRequest step = new StepRequest("TOOL", Map.of("critical", true));
        Map<String, Object> output = Map.of("error", "failed");

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1);
        assertFalse(result.isRetryRequested());
    }

    @Test
    void reflectUsesLlmWhenEnabled() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setMaxRetries(2);
        properties.setConfidenceThreshold(0.6);
        properties.setMinOutputChars(10);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = new ReflectionService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), Mockito.mock(JsonOutputRepairService.class));

        String content = "{\"score\":0.5,\"retry\":true,\"notes\":\"needs retry\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("reflect", content, 10, 5));

        StepRequest step = new StepRequest("TOOL", Map.of("critical", true));
        Map<String, Object> output = Map.of("result", "ok");

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));
        assertTrue(result.isRetryRequested());
    }

    @Test
    void reflectRepairsOutputWithExtraText() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        ReflectionService service = new ReflectionService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), repairService);

        String badContent = "说明:{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}后缀";
        String repaired = "{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), any(ModelScene.class),
                any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("reflect", badContent, 10, 20),
                        new ModelResponse("repair", repaired, 10, 20));

        StepRequest step = new StepRequest("TOOL", Map.of());
        ReflectionResult result = service.reflect(step, Map.of("answer", "ok"),
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), 1, "wf-1",
                new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);
        assertEquals(0.9, result.getReport().getScore());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_success_total", "scene", "reflection");
    }

    @Test
    void reflectFallsBackWhenRepairFails() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        ReflectionService service = new ReflectionService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), repairService);

        String badContent = "无法解析";
        when(modelInvocationService.invoke(any(ModelRequest.class), any(ModelScene.class),
                any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("reflect", badContent, 10, 20),
                        new ModelResponse("repair", "", 10, 20));

        StepRequest step = new StepRequest("TOOL", Map.of());
        ReflectionResult result = service.reflect(step, Map.of(),
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), 1, "wf-1",
                new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);
        assertNotNull(result.getReport());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "reflection");
    }
}
