package com.example.agent.reflection.strategy;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptTrace;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.tooling.ModelToolResolver;
import com.example.agent.reflection.ReflectionDecision;
import com.example.agent.reflection.ReflectionDecisionStatus;
import com.example.agent.reflection.ReflectionExecutionContext;
import com.example.agent.reflection.ReflectionFailureReason;
import com.example.agent.reflection.ReflectionProperties;
import com.example.agent.reflection.model.ReflectionContext;
import com.example.agent.reflection.model.ReflectionOutputDigest;
import com.example.agent.reflection.model.ReflectionOutputSummary;
import com.example.agent.reflection.parser.ReflectionResponseParser;
import com.example.agent.reflection.prompt.ReflectionPromptProvider;
import com.example.agent.reflection.prompt.ReflectionPromptTemplateEngine;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.step.contract.StepExecutionOutput;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmReflectionStrategyReasonCodeTest {

    @Test
    void returnsLlmEmptyResponseWhenModelResponseEmpty() {
        LlmReflectionStrategy strategy = createStrategy(new ReflectionResponseParser(new ObjectMapper(), null));
        ModelInvocationService invocationService = getInvocationService(strategy);
        when(invocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT), any(), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("m1", "   ", 1, 1));

        ReflectionDecision decision = strategy.execute(buildContext());

        assertNotNull(decision);
        assertEquals(ReflectionDecisionStatus.FALLBACK_REQUIRED, decision.getStatus());
        assertEquals(ReflectionFailureReason.LLM_EMPTY_RESPONSE, decision.getReason());
        assertEquals("empty_output", decision.getParseErrorType());
    }

    @Test
    void returnsLlmParseErrorWhenJsonParseFailsAndRepairFails() {
        JsonOutputRepairService repairService = createRepairServiceReturning(null);
        ReflectionResponseParser parser = new ReflectionResponseParser(new ObjectMapper(), repairService);
        LlmReflectionStrategy strategy = createStrategy(parser);
        ModelInvocationService invocationService = getInvocationService(strategy);
        when(invocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT), any(), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("m1", "not_json", 1, 1));

        ReflectionDecision decision = strategy.execute(buildContext());

        assertNotNull(decision);
        assertEquals(ReflectionDecisionStatus.FALLBACK_REQUIRED, decision.getStatus());
        assertEquals(ReflectionFailureReason.LLM_PARSE_ERROR, decision.getReason());
        assertEquals("json_parse_error", decision.getParseErrorType());
        assertTrue(decision.isRepairAttempted());
    }

    @Test
    void returnsLlmRepairFailedWhenParseNullAndRepairFails() {
        JsonOutputRepairService repairService = createRepairServiceReturning(null);
        ReflectionResponseParser parser = new ReflectionResponseParser(new ObjectMapper(), repairService);
        LlmReflectionStrategy strategy = createStrategy(parser);
        ModelInvocationService invocationService = getInvocationService(strategy);
        when(invocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT), any(), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("m1", "{\"retry\":true,\"notes\":\"x\"}", 1, 1));

        ReflectionDecision decision = strategy.execute(buildContext());

        assertNotNull(decision);
        assertEquals(ReflectionDecisionStatus.FALLBACK_REQUIRED, decision.getStatus());
        assertEquals(ReflectionFailureReason.LLM_REPAIR_FAILED, decision.getReason());
        assertEquals("missing_field", decision.getParseErrorType());
        assertTrue(decision.isRepairAttempted());
    }

    @Test
    void returnsLlmRepairFailedWhenScoreOutOfRangeAndRepairFails() {
        JsonOutputRepairService repairService = createRepairServiceReturning(null);
        ReflectionResponseParser parser = new ReflectionResponseParser(new ObjectMapper(), repairService);
        LlmReflectionStrategy strategy = createStrategy(parser);
        ModelInvocationService invocationService = getInvocationService(strategy);
        when(invocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT), any(), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("m1", "{\"score\":1.5,\"retry\":false,\"notes\":\"x\"}", 1, 1));

        ReflectionDecision decision = strategy.execute(buildContext());

        assertNotNull(decision);
        assertEquals(ReflectionDecisionStatus.FALLBACK_REQUIRED, decision.getStatus());
        assertEquals(ReflectionFailureReason.LLM_REPAIR_FAILED, decision.getReason());
        assertEquals("score_out_of_range", decision.getParseErrorType());
        assertTrue(decision.isRepairAttempted());
    }

    @Test
    void returnsLlmInvocationErrorWhenInvokeThrows() {
        LlmReflectionStrategy strategy = createStrategy(new ReflectionResponseParser(new ObjectMapper(), null));
        ModelInvocationService invocationService = getInvocationService(strategy);
        when(invocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT), any(), any(), any(), eq("reflect"), any()))
                .thenThrow(new RuntimeException("timeout"));

        ReflectionDecision decision = strategy.execute(buildContext());

        assertNotNull(decision);
        assertEquals(ReflectionDecisionStatus.FALLBACK_REQUIRED, decision.getStatus());
        assertEquals(ReflectionFailureReason.LLM_INVOCATION_ERROR, decision.getReason());
        assertEquals("llm_invocation_error", decision.getParseErrorType());
    }

    @Test
    void recordsPromptTraceFieldsConsistentlyWhenRepairFailed() {
        JsonOutputRepairService repairService = createRepairServiceReturning(null);
        ReflectionResponseParser parser = new ReflectionResponseParser(new ObjectMapper(), repairService);
        LlmReflectionStrategy strategy = createStrategy(parser);
        ModelInvocationService invocationService = getInvocationService(strategy);
        when(invocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT), any(), any(), any(), eq("reflect"), any()))
                .thenReturn(new ModelResponse("m1", "{\"retry\":true}", 1, 1));

        ReflectionDecision decision = strategy.execute(buildContext());

        assertEquals(ReflectionFailureReason.LLM_REPAIR_FAILED, decision.getReason());
        ArgumentCaptor<PromptTrace> traceCaptor = ArgumentCaptor.forClass(PromptTrace.class);
        verify(invocationService).recordPromptTrace(traceCaptor.capture(), any(), any(), any(), eq("reflect"), eq("m1"));
        PromptTrace trace = traceCaptor.getValue();
        assertNotNull(trace);
        assertEquals(Boolean.FALSE, trace.getParseSuccess());
        assertEquals("missing_field", trace.getParseErrorType());
        assertEquals(Boolean.TRUE, trace.getRepairAttempted());
        assertEquals(Boolean.FALSE, trace.getRepairSuccess());
    }

    private LlmReflectionStrategy createStrategy(ReflectionResponseParser parser) {
        ReflectionProperties properties = new ReflectionProperties();
        properties.setEnabled(true);
        properties.setLlmEnabled(true);
        properties.setFallbackEnabled(true);
        properties.setMaxRetries(2);
        properties.setPromptStrict(true);
        properties.setPromptVersion("v1");

        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        ModelInvocationService invocationService = Mockito.mock(ModelInvocationService.class);
        ModelToolResolver modelToolResolver = Mockito.mock(ModelToolResolver.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        when(promptAssembler.build(any(), any(), any())).thenReturn(null);

        ReflectionPromptProvider promptProvider = new ReflectionPromptProvider(
                properties,
                new DefaultResourceLoader(),
                new ObjectMapper(),
                new ReflectionPromptTemplateEngine()
        );

        ReflectionLlmInvocationExecutor invocationExecutor = new ReflectionLlmInvocationExecutor(
                invocationService,
                modelToolResolver,
                promptAssembler,
                promptProvider
        );
        ReflectionLlmDecisionResolver decisionResolver = new ReflectionLlmDecisionResolver(
                parser,
                properties,
                metricsPublisher
        );
        ReflectionPromptTraceRecorder traceRecorder = new ReflectionPromptTraceRecorder(invocationService);

        return new LlmReflectionStrategy(
                metricsPublisher,
                properties,
                invocationService,
                invocationExecutor,
                decisionResolver,
                traceRecorder
        );
    }

    private ModelInvocationService getInvocationService(LlmReflectionStrategy strategy) {
        try {
            java.lang.reflect.Field field = LlmReflectionStrategy.class.getDeclaredField("modelInvocationService");
            field.setAccessible(true);
            return (ModelInvocationService) field.get(strategy);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private ReflectionExecutionContext buildContext() {
        ReflectionContext reflectionContext = ReflectionContext.builder()
                .stepType("TOOL")
                .attempt(1)
                .outputSummary(new ReflectionOutputSummary("ok"))
                .outputDigest(new ReflectionOutputDigest(1, List.of("answer"), 32, false))
                .build();

        return ReflectionExecutionContext.builder()
                .step(new StepSpec("TOOL", Map.of("tool", "search")))
                .output(StepExecutionOutput.fromPayload(Map.of("answer", "ok")))
                .tenantContext(new TenantContext("t1", "u1", List.of(), "req", "trace"))
                .attempt(1)
                .workflowId("wf-1")
                .seqCounter(new AtomicLong(0))
                .reflectionContext(reflectionContext)
                .build();
    }

    private JsonOutputRepairService createRepairServiceReturning(String repairedContent) {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP), any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(repairedContent == null ? null : new ModelResponse("repair", repairedContent, 1, 1));

        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        when(promptAssembler.build(any(), any(), any())).thenReturn(null);

        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        return new JsonOutputRepairService(modelInvocationService, promptAssembler, metricsPublisher);
    }
}
