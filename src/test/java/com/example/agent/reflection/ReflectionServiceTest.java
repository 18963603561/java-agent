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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper());

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
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper());

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
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper());

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
}
