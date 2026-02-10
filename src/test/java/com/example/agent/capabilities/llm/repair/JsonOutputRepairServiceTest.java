package com.example.agent.capabilities.llm.repair;

import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.LlmTaskContext;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.capabilities.llm.prompt.PromptBundle;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.capabilities.llm.repair.JsonOutputSchema;
import com.example.agent.capabilities.llm.repair.JsonRepairRequest;
import com.example.agent.streaming.observability.MetricsPublisher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonOutputRepairServiceTest {

    @Test
    void repairShouldNormalizeMaxAttemptsWhenNonPositive() {
        ModelInvocationService invocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        when(invocationService.invoke(any(ModelRequest.class), eq(com.example.agent.capabilities.llm.contract.ModelScene.CHEAP),
                eq(null), eq(null), eq(null), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "{\"ok\":true}", 1, 1));

        JsonOutputRepairService service = new JsonOutputRepairService(invocationService, promptAssembler, metricsPublisher);
        String repaired = service.repair("scene-a", "bad-json", JsonOutputSchema.FINAL, null, 0);

        assertEquals("{\"ok\":true}", repaired);
        verify(metricsPublisher, times(1)).incrementWithTags("json_repair_attempt_total", "scene", "scene-a");
    }

    @Test
    void repairShouldRetryWhenModelReturnsBlank() {
        ModelInvocationService invocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        when(invocationService.invoke(any(ModelRequest.class), eq(com.example.agent.capabilities.llm.contract.ModelScene.CHEAP),
                eq(null), eq(null), eq(null), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "", 1, 1))
                .thenReturn(new ModelResponse("repair", "{\"answer\":\"ok\"}", 1, 1));

        JsonOutputRepairService service = new JsonOutputRepairService(invocationService, promptAssembler, metricsPublisher);
        String repaired = service.repair("scene-b", "bad-json", JsonOutputSchema.FINAL, null, 2);

        assertEquals("{\"answer\":\"ok\"}", repaired);
        verify(metricsPublisher, times(2)).incrementWithTags("json_repair_attempt_total", "scene", "scene-b");
        verify(metricsPublisher, times(1)).incrementWithTags("json_repair_success_total", "scene", "scene-b");
    }

    @Test
    void repairShouldReturnNullWhenAllAttemptsFail() {
        ModelInvocationService invocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        when(invocationService.invoke(any(ModelRequest.class), eq(com.example.agent.capabilities.llm.contract.ModelScene.CHEAP),
                eq(null), eq(null), eq(null), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "", 1, 1));

        JsonOutputRepairService service = new JsonOutputRepairService(invocationService, promptAssembler, metricsPublisher);
        String repaired = service.repair("scene-c", "bad-json", JsonOutputSchema.FINAL, null, 2);

        assertNull(repaired);
        verify(metricsPublisher, times(1)).incrementWithTags("json_repair_failure_total", "scene", "scene-c");
    }

    @Test
    void repairShouldReturnNullWhenSchemaOrRawTextInvalid() {
        ModelInvocationService invocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);

        JsonOutputRepairService service = new JsonOutputRepairService(invocationService, promptAssembler, metricsPublisher);

        assertNull(service.repair("scene", "", JsonOutputSchema.FINAL, null, 1));
        assertNull(service.repair("scene", "raw", null, null, 1));
        verify(invocationService, never()).invoke(any(ModelRequest.class), any(), any(), any(), any(), any(), any());
    }

    @Test
    void repairShouldWorkWhenPromptAssemblerMissing() {
        ModelInvocationService invocationService = Mockito.mock(ModelInvocationService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        when(invocationService.invoke(any(ModelRequest.class), eq(com.example.agent.capabilities.llm.contract.ModelScene.CHEAP),
                eq(null), eq(null), eq(null), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "{\"ok\":true}", 1, 1));

        JsonOutputRepairService service = new JsonOutputRepairService(invocationService, null, metricsPublisher);
        String repaired = service.repair("scene-d", "bad-json", JsonOutputSchema.FINAL, null, 1);

        assertEquals("{\"ok\":true}", repaired);
        ArgumentCaptor<ModelRequest> captor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(invocationService).invoke(captor.capture(), eq(com.example.agent.capabilities.llm.contract.ModelScene.CHEAP),
                eq(null), eq(null), eq(null), eq("json_repair"), any());
        assertNotNull(captor.getValue().getPrompt());
    }

    @Test
    void repairShouldUsePromptAssemblerWhenAvailable() {
        ModelInvocationService invocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        PromptBundle bundle = new PromptBundle();
        bundle.setMessages(java.util.List.of(new com.example.agent.capabilities.llm.prompt.PromptMessage(
                com.example.agent.capabilities.llm.prompt.PromptRole.USER, "assembled")));
        when(promptAssembler.build(any(), isA(LlmTaskContext.class), eq(null))).thenReturn(bundle);
        when(invocationService.invoke(any(ModelRequest.class), eq(com.example.agent.capabilities.llm.contract.ModelScene.CHEAP),
                eq(null), eq(null), eq(null), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "{\"ok\":true}", 1, 1));

        JsonOutputRepairService service = new JsonOutputRepairService(invocationService, promptAssembler, metricsPublisher);
        String repaired = service.repair(new JsonRepairRequest("scene-e", "bad-json", JsonOutputSchema.FINAL,
                Map.of("x", 1).toString(), 1));

        assertEquals("{\"ok\":true}", repaired);
        verify(promptAssembler, times(1)).build(any(), isA(LlmTaskContext.class), eq(null));
    }
}

