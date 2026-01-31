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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        FinalOutputService service = new FinalOutputService(modelInvocationService, promptAssembler, new ObjectMapper(),
                repairService);

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
        FinalOutputService service = new FinalOutputService(modelInvocationService, promptAssembler, new ObjectMapper(),
                repairService);

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
}