package com.example.agent.runtime;

import com.example.agent.auth.TenantContext;
import com.example.agent.common.TaskRequest;
import com.example.agent.model.ModelInvocationService;
import com.example.agent.model.ModelRequest;
import com.example.agent.model.ModelResponse;
import com.example.agent.model.ModelScene;
import com.example.agent.model.PromptAssembler;
import com.example.agent.observability.MetricsPublisher;
import com.example.agent.repair.JsonOutputRepairService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class FinalOutputServiceTest {

    @Test
    void finalizeOutputRepairsOutputWithExtraText() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        FinalOutputProperties properties = new FinalOutputProperties();
        FinalOutputService service = new FinalOutputService(modelInvocationService, promptAssembler, new ObjectMapper(),
                repairService, properties);

        String badContent = "说明:{\"answer\":\"ok\",\"highlights\":\"\",\"confidence\":0.8}后缀";
        String repaired = "{\"answer\":\"ok\",\"highlights\":\"\",\"confidence\":0.8}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("finalize"), any()))
                .thenReturn(new ModelResponse("final", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", repaired, 10, 10));

        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        Map<String, Object> result = service.finalizeOutput(request, "q", "summary", List.of(),
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));

        assertNotNull(result);
        assertEquals("ok", result.get("answer"));
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_success_total", "scene", "final");
    }

    @Test
    void finalizeOutputFallsBackWhenRepairFails() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        FinalOutputProperties properties = new FinalOutputProperties();
        FinalOutputService service = new FinalOutputService(modelInvocationService, promptAssembler, new ObjectMapper(),
                repairService, properties);

        String badContent = "无法解析";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("finalize"), any()))
                .thenReturn(new ModelResponse("final", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "", 10, 10));

        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        Map<String, Object> result = service.finalizeOutput(request, "q", "summary", List.of(),
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));

        assertNotNull(result);
        assertEquals(badContent, result.get("answer"));
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "final");
    }

    @Test
    void finalizeOutputUsesStepSummaryInPrompt() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        FinalOutputProperties properties = new FinalOutputProperties();
        FinalOutputService service = new FinalOutputService(modelInvocationService, promptAssembler, new ObjectMapper(),
                repairService, properties);

        Map<String, Object> output = Map.of(
                "contextSnapshot", "big",
                "evidencePack", "big",
                "tokenUsage", "big"
        );
        Map<String, Object> stepSummary = new java.util.HashMap<>();
        stepSummary.put("summary", "tool summary");
        stepSummary.put("status", "COMPLETED");
        Map<String, Object> summary = new java.util.HashMap<>();
        summary.put("stepSummary", stepSummary);
        Map<String, Object> step = new java.util.HashMap<>();
        step.put("stepId", "s-1");
        step.put("type", "TOOL");
        step.put("output", output);
        step.put("summary", summary);

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("finalize"), any()))
                .thenReturn(new ModelResponse("final", "{\"answer\":\"ok\",\"highlights\":\"\",\"confidence\":1}", 10,
                        10));

        Map<String, Object> result = service.finalizeOutput("q", "summary", List.of(step),
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));

        assertNotNull(result);
        assertEquals("ok", result.get("answer"));

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        Mockito.verify(modelInvocationService).invoke(captor.capture(), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("finalize"), any());
        String prompt = captor.getValue().getPrompt();
        assertNotNull(prompt);
        assertFalse(prompt.contains("contextSnapshot"));
        assertFalse(prompt.contains("evidencePack"));
        assertFalse(prompt.contains("tokenUsage"));
        assertFalse(prompt.contains("\"output\""));
        assertTrue(prompt.contains("tool summary"));
    }

    @Test
    void finalizeOutputTruncatesSummaryInPrompt() throws Exception {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        FinalOutputProperties properties = new FinalOutputProperties();
        properties.setPromptSummaryMaxChars(60);
        FinalOutputService service = new FinalOutputService(modelInvocationService, promptAssembler, new ObjectMapper(),
                repairService, properties);

        String longSummary = "a".repeat(120);
        Map<String, Object> stepSummary = new java.util.HashMap<>();
        stepSummary.put("summary", longSummary);
        stepSummary.put("status", "COMPLETED");
        Map<String, Object> summaryPayload = new java.util.HashMap<>();
        summaryPayload.put("stepSummary", stepSummary);
        Map<String, Object> step = new java.util.HashMap<>();
        step.put("stepId", "s-1");
        step.put("type", "TOOL");
        step.put("summary", summaryPayload);

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("finalize"), any()))
                .thenReturn(new ModelResponse("final", "{\"answer\":\"ok\",\"highlights\":\"\",\"confidence\":1}", 10,
                        10));

        Map<String, Object> result = service.finalizeOutput("q", "summary", List.of(step),
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));

        assertNotNull(result);
        assertEquals("ok", result.get("answer"));

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        Mockito.verify(modelInvocationService).invoke(captor.capture(), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("finalize"), any());
        String prompt = captor.getValue().getPrompt();
        assertNotNull(prompt);
        String marker = "FINAL_CONTEXT_JSON:";
        int index = prompt.indexOf(marker);
        assertTrue(index > -1);
        String contextJson = prompt.substring(index + marker.length()).trim();
        Map<String, Object> context = new ObjectMapper().readValue(contextJson, Map.class);
        List<?> steps = (List<?>) context.get("steps");
        Map<?, ?> summaryItem = (Map<?, ?>) steps.get(0);
        String summaryText = String.valueOf(summaryItem.get("summary"));
        assertTrue(summaryText.length() <= properties.getPromptSummaryMaxChars());
        assertTrue(summaryText.endsWith("...(truncated)"));
    }

    @Test
    void finalizeOutputKeepsShortSummaryInPrompt() throws Exception {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        FinalOutputProperties properties = new FinalOutputProperties();
        properties.setPromptSummaryMaxChars(200);
        FinalOutputService service = new FinalOutputService(modelInvocationService, promptAssembler, new ObjectMapper(),
                repairService, properties);

        String shortSummary = "short summary";
        Map<String, Object> stepSummary = new java.util.HashMap<>();
        stepSummary.put("summary", shortSummary);
        stepSummary.put("status", "COMPLETED");
        Map<String, Object> summaryPayload = new java.util.HashMap<>();
        summaryPayload.put("stepSummary", stepSummary);
        Map<String, Object> step = new java.util.HashMap<>();
        step.put("stepId", "s-1");
        step.put("type", "TOOL");
        step.put("summary", summaryPayload);

        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("finalize"), any()))
                .thenReturn(new ModelResponse("final", "{\"answer\":\"ok\",\"highlights\":\"\",\"confidence\":1}", 10,
                        10));

        Map<String, Object> result = service.finalizeOutput("q", "summary", List.of(step),
                new TenantContext("t-1", "u-1", List.of(), "req", "trace"), "wf-1", new AtomicLong(0));

        assertNotNull(result);
        assertEquals("ok", result.get("answer"));

        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        Mockito.verify(modelInvocationService).invoke(captor.capture(), eq(ModelScene.REFLECT),
                any(), any(), any(), eq("finalize"), any());
        String prompt = captor.getValue().getPrompt();
        assertNotNull(prompt);
        String marker = "FINAL_CONTEXT_JSON:";
        int index = prompt.indexOf(marker);
        assertTrue(index > -1);
        String contextJson = prompt.substring(index + marker.length()).trim();
        Map<String, Object> context = new ObjectMapper().readValue(contextJson, Map.class);
        List<?> steps = (List<?>) context.get("steps");
        Map<?, ?> summaryItem = (Map<?, ?>) steps.get(0);
        String summaryText = String.valueOf(summaryItem.get("summary"));
        assertEquals(shortSummary, summaryText);
        assertFalse(summaryText.endsWith("...(truncated)"));
    }
}
