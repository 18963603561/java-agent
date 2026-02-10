package com.example.agent.reflection;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.reflection.model.ReflectionContextMapper;
import com.example.agent.reflection.prompt.ReflectionPromptProvider;
import com.example.agent.reflection.prompt.ReflectionPromptTemplateEngine;
import com.example.agent.reflection.parser.ReflectionResponseParser;
import com.example.agent.reflection.strategy.HeuristicReflectionStrategy;
import com.example.agent.reflection.strategy.LlmReflectionStrategy;
import com.example.agent.reflection.strategy.ReflectionLlmDecisionResolver;
import com.example.agent.reflection.strategy.ReflectionLlmInvocationExecutor;
import com.example.agent.reflection.strategy.ReflectionPromptTraceRecorder;
import com.example.agent.reflection.strategy.ReflectionStrategySelector;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ReflectionServiceTest {

    private ReflectionService createService(ReflectionProperties properties,
                                            MetricsPublisher metricsPublisher,
                                            ModelInvocationService modelInvocationService,
                                            ModelToolResolver modelToolResolver,
                                            PromptAssembler promptAssembler,
                                            ObjectMapper objectMapper,
                                            JsonOutputRepairService jsonOutputRepairService) {
        ReflectionPromptTemplateEngine templateEngine = new ReflectionPromptTemplateEngine();
        ReflectionPromptProvider promptProvider = new ReflectionPromptProvider(
                properties,
                new org.springframework.core.io.DefaultResourceLoader(),
                objectMapper,
                templateEngine
        );
        ReflectionResponseParser parser = new ReflectionResponseParser(objectMapper, jsonOutputRepairService);
        ReflectionLlmInvocationExecutor invocationExecutor = new ReflectionLlmInvocationExecutor(
                modelInvocationService,
                modelToolResolver,
                promptAssembler,
                promptProvider
        );
        ReflectionLlmDecisionResolver decisionResolver = new ReflectionLlmDecisionResolver(
                parser,
                properties,
                metricsPublisher
        );
        ReflectionPromptTraceRecorder traceRecorder = new ReflectionPromptTraceRecorder(modelInvocationService);
        LlmReflectionStrategy llmStrategy = new LlmReflectionStrategy(
                metricsPublisher,
                properties,
                modelInvocationService,
                invocationExecutor,
                decisionResolver,
                traceRecorder
        );
        HeuristicReflectionStrategy heuristicStrategy = new HeuristicReflectionStrategy(properties, metricsPublisher);
        ReflectionStrategySelector selector = new ReflectionStrategySelector(
                properties,
                List.of(llmStrategy, heuristicStrategy)
        );
        return new ReflectionService(properties, new ReflectionContextMapper(), selector);
    }

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
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), Mockito.mock(JsonOutputRepairService.class));

        StepSpec step = new StepSpec("TOOL", Map.of("critical", true));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("error", "failed"));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1);
        assertTrue(result.retryRequested());
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
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), Mockito.mock(JsonOutputRepairService.class));

        StepSpec step = new StepSpec("TOOL", Map.of("critical", true));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("error", "failed"));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1);
        assertFalse(result.retryRequested());
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
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), Mockito.mock(JsonOutputRepairService.class));

        String content = "{\"score\":0.5,\"retry\":true,\"notes\":\"needs retry\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("reflect", content, 10, 5));

        StepSpec step = new StepSpec("TOOL", Map.of("critical", true));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("result", "ok"));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));
        assertTrue(result.retryRequested());
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
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), repairService);

        String badContent = "璇存槑:{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}鍚庣紑";
        String repaired = "{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), any(ModelScene.class),
                any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("reflect", badContent, 10, 20),
                        new ModelResponse("repair", repaired, 10, 20));

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("answer", "ok"));
        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), 1, "wf-1",
                new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);
        assertEquals(0.9, result.report().score());
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
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(), repairService);

        String badContent = "鏃犳硶瑙ｆ瀽";
        when(modelInvocationService.invoke(any(ModelRequest.class), any(ModelScene.class),
                any(), any(), any(), any(), any()))
                .thenReturn(new ModelResponse("reflect", badContent, 10, 20),
                        new ModelResponse("repair", "", 10, 20));

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of());
        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), 1, "wf-1",
                new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);
        assertNotNull(result.report());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "reflection");
    }

    @Test
    void reflectPromptUsesSummaryOnly() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(),
                Mockito.mock(JsonOutputRepairService.class));

        String content = "{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("reflect", content, 10, 5));

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of(
                "contextSnapshot", "big",
                "contextBudget", "big",
                "evidencePack", "big",
                "tokenUsage", "big"
        ));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        Mockito.verify(modelInvocationService).invoke(captor.capture(), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any());
        String prompt = captor.getValue().getPrompt();
        assertNotNull(prompt);
        assertFalse(prompt.contains("contextSnapshot"));
        assertFalse(prompt.contains("contextBudget"));
        assertFalse(prompt.contains("evidencePack"));
        assertFalse(prompt.contains("tokenUsage"));
        assertFalse(prompt.contains("\"output\""));
        assertTrue(prompt.contains("(summary disabled)"));
    }

    @Test
    void reflectPromptFillsDigestSummaryWhenMissing() throws Exception {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(),
                Mockito.mock(JsonOutputRepairService.class));

        String content = "{\"score\":0.9,\"retry\":false,\"notes\":\"ok\"}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("reflect", content, 10, 5));

        Map<String, Object> summaryView = new java.util.HashMap<>();
        summaryView.put("outputSummary", new java.util.HashMap<>());
        summaryView.put("outputDigest", Map.of(
                "keyCount", 12,
                "keys", List.of("a", "b", "c"),
                "charCount", 2048,
                "truncated", true
        ));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of()).withSummary(summaryView);

        StepSpec step = new StepSpec("TOOL", Map.of());
        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        Mockito.verify(modelInvocationService).invoke(captor.capture(), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any());
        String prompt = captor.getValue().getPrompt();
        assertNotNull(prompt);
        assertFalse(prompt.contains("contextSnapshot"));
        assertFalse(prompt.contains("contextBudget"));
        assertFalse(prompt.contains("evidencePack"));
        assertFalse(prompt.contains("tokenUsage"));

        String marker = "REFLECTION_CONTEXT_JSON:";
        int index = prompt.indexOf(marker);
        assertTrue(index > -1);
        String contextJson = prompt.substring(index + marker.length()).trim();
        Map<String, Object> context = new ObjectMapper().readValue(contextJson, new TypeReference<Map<String, Object>>() {
        });
        Object summaryObj = ((Map<?, ?>) context.get("outputSummary")).get("summary");
        assertNotNull(summaryObj);
        String summary = summaryObj.toString();
        assertTrue(summary.contains("keyCount=12"));
        assertTrue(summary.contains("keys="));
        assertTrue(summary.contains("truncated=true"));
    }

    @Test
    void reflectFallsBackToHeuristicWhenLlmResponseEmpty() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        properties.setMaxRetries(2);
        properties.setConfidenceThreshold(0.8);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(),
                Mockito.mock(JsonOutputRepairService.class));

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(null);

        StepSpec step = new StepSpec("TOOL", Map.of("critical", true));
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("error", "failed"));

        ReflectionResult result = service.reflect(step, output,
                new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                "wf-1", new java.util.concurrent.atomic.AtomicLong(0));

        assertNotNull(result);
        assertTrue(result.retryRequested());
    }

    @Test
    void reflectThrowsWhenFallbackDisabledAndLlmResponseEmpty() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(),
                Mockito.mock(JsonOutputRepairService.class));

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenReturn(null);

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("result", "ok"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.reflect(step, output,
                        new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                        "wf-1", new java.util.concurrent.atomic.AtomicLong(0)));
        assertEquals("reflection_fallback_disabled:llm_empty_response", exception.getMessage());
    }

    @Test
    void reflectThrowsWhenFallbackDisabledAndLlmInvocationError() {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(false);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ReflectionService service = createService(properties, metricsPublisher,
                modelInvocationService, modelToolResolver, promptAssembler, new ObjectMapper(),
                Mockito.mock(JsonOutputRepairService.class));

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(TenantContext.class), any(), any(), eq("reflect"), any()))
                .thenThrow(new RuntimeException("timeout"));

        StepSpec step = new StepSpec("TOOL", Map.of());
        StepExecutionOutput output = StepExecutionOutput.fromPayload(Map.of("result", "ok"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.reflect(step, output,
                        new TenantContext("t1", "u1", List.of(), "req", "trace"), 1,
                        "wf-1", new java.util.concurrent.atomic.AtomicLong(0)));
        assertEquals("reflection_fallback_disabled:llm_invocation_error", exception.getMessage());
    }
}
