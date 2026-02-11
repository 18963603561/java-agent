package com.example.agent.runtime;

import com.example.agent.api.http.dto.TaskRequest;
import com.example.agent.capabilities.memory.write.MemoryWriteService;
import com.example.agent.planning.PlanResult;
import com.example.agent.runtime.control.RuntimeControlEventPublisher;
import com.example.agent.runtime.finalize.RuntimeFinalizationService;
import com.example.agent.runtime.model.RuntimeResult;
import com.example.agent.runtime.model.StepResult;
import com.example.agent.runtime.model.StepResultMeta;
import com.example.agent.runtime.model.StepResultRaw;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.runtime.output.FinalOutputService;
import com.example.agent.runtime.step.StepState;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuntimeFinalizationServiceTest {

    @Test
    void finalizeRunShouldPublishLlmOutputEventWhenFinalOutputGeneratedByService() {
        FinalOutputService finalOutputService = Mockito.mock(FinalOutputService.class);
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        RuntimeControlEventPublisher eventPublisher = Mockito.mock(RuntimeControlEventPublisher.class);
        RuntimeFinalizationService service = new RuntimeFinalizationService(
                finalOutputService,
                memoryWriteService,
                eventPublisher
        );

        Map<String, Object> generated = Map.of(
                "answer", "done",
                "confidence", 0.9,
                "modelId", "gpt-4o",
                "rawRef", "raw://1"
        );
        when(finalOutputService.finalizeOutput(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(generated);

        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        PlanResult plan = new PlanResult("plan-1", "summary", List.of(new StepSpec("TOOL", Map.of("tool", "search"))));
        List<StepResult> stepOutputs = List.of();
        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1");
        AtomicLong seqCounter = new AtomicLong(0);

        RuntimeResult result = service.finalizeRun(
                request,
                tenantContext,
                "wf-1",
                "task-1",
                seqCounter,
                plan,
                stepOutputs
        );

        assertNotNull(result);
        assertEquals(generated, result.getFinalOutput());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publish(eq(tenantContext), eq("wf-1"), eq(seqCounter), eq(EventType.LLM_OUTPUT),
                payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("done", payload.get("response"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) payload.get("metadata");
        assertNotNull(metadata);
        assertEquals("plan-1", metadata.get("planId"));
        assertEquals("summary", metadata.get("planSummary"));
        assertEquals(0, metadata.get("stepCount"));
        assertEquals("gpt-4o", metadata.get("modelId"));
        assertEquals(0.9, metadata.get("confidence"));
        assertEquals("raw://1", metadata.get("rawRef"));

        verify(memoryWriteService).saveTaskMemory(eq(request), any(RuntimeResult.class), eq(tenantContext), eq("task-1"));
    }

    @Test
    void finalizeRunShouldPublishLlmOutputEventWhenFinalOutputExtractedFromLastLlmStep() {
        FinalOutputService finalOutputService = Mockito.mock(FinalOutputService.class);
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        RuntimeControlEventPublisher eventPublisher = Mockito.mock(RuntimeControlEventPublisher.class);
        RuntimeFinalizationService service = new RuntimeFinalizationService(
                finalOutputService,
                memoryWriteService,
                eventPublisher
        );

        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        PlanResult plan = new PlanResult("plan-2", "summary-2", List.of(new StepSpec("LLM", Map.of())));
        StepResult stepResult = buildStepResult("step-1", "LLM", Map.of("answer", "from-step", "highlights", "h"));
        List<StepResult> stepOutputs = List.of(stepResult);
        TenantContext tenantContext = new TenantContext("tenant-2", "user-2", List.of(), "req-2", "trace-2");
        AtomicLong seqCounter = new AtomicLong(10);

        RuntimeResult result = service.finalizeRun(
                request,
                tenantContext,
                "wf-2",
                "task-2",
                seqCounter,
                plan,
                stepOutputs
        );

        assertNotNull(result);
        assertEquals("from-step", result.getFinalOutput().get("answer"));

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(eventPublisher).publish(eq(tenantContext), eq("wf-2"), eq(seqCounter), eq(EventType.LLM_OUTPUT),
                payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertEquals("from-step", payload.get("response"));

        verify(finalOutputService, never()).finalizeOutput(any(), any(), any(), any(), any(), any(), any());
        verify(memoryWriteService).saveTaskMemory(eq(request), any(RuntimeResult.class), eq(tenantContext), eq("task-2"));
    }

    @Test
    void finalizeRunShouldSkipLlmOutputEventWhenFinalOutputEmpty() {
        FinalOutputService finalOutputService = Mockito.mock(FinalOutputService.class);
        MemoryWriteService memoryWriteService = Mockito.mock(MemoryWriteService.class);
        RuntimeControlEventPublisher eventPublisher = Mockito.mock(RuntimeControlEventPublisher.class);
        RuntimeFinalizationService service = new RuntimeFinalizationService(
                finalOutputService,
                memoryWriteService,
                eventPublisher
        );

        when(finalOutputService.finalizeOutput(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of());

        TaskRequest request = new TaskRequest();
        request.setQuery("q");
        PlanResult plan = new PlanResult("plan-3", "summary-3", List.of(new StepSpec("TOOL", Map.of("tool", "search"))));
        TenantContext tenantContext = new TenantContext("tenant-3", "user-3", List.of(), "req-3", "trace-3");

        RuntimeResult result = service.finalizeRun(
                request,
                tenantContext,
                "wf-3",
                "task-3",
                new AtomicLong(0),
                plan,
                List.of()
        );

        assertNotNull(result);
        assertEquals(Map.of(), result.getFinalOutput());
        verify(eventPublisher, never()).publish(any(), any(), any(), eq(EventType.LLM_OUTPUT), any());
        verify(memoryWriteService).saveTaskMemory(eq(request), any(RuntimeResult.class), eq(tenantContext), eq("task-3"));
    }

    private StepResult buildStepResult(String stepId, String type, Map<String, Object> rawData) {
        StepResult stepResult = new StepResult();
        StepResultMeta meta = new StepResultMeta();
        meta.setStepId(stepId);
        meta.setType(type);
        meta.setStatus(StepState.COMPLETED);
        stepResult.setMeta(meta);
        StepResultRaw raw = new StepResultRaw();
        raw.setData(rawData);
        raw.setTruncated(false);
        stepResult.setRaw(raw);
        return stepResult;
    }
}

