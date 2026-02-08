package com.example.agent.research;

import com.example.agent.security.auth.TenantContext;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.prompt.PromptAssembler;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.capabilities.llm.repair.JsonOutputRepairService;
import com.example.agent.streaming.sse.EventStreamService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import com.example.agent.capabilities.context.research.ResearchCitation;
import com.example.agent.capabilities.context.research.ResearchPipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ResearchPipelineTest {

    @Test
    void researchRepairsOutputWithExtraText() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        ResearchPipeline pipeline = new ResearchPipeline(modelInvocationService, promptAssembler, new ObjectMapper(),
                eventPublisher, eventStreamService, repairService);

        String badContent = "璇存槑:{\"citations\":[{\"source\":\"s\",\"snippet\":\"x\"}]}鍚庣紑";
        String repaired = "{\"citations\":[{\"source\":\"s\",\"snippet\":\"x\"}]}";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.RESEARCH),
                any(), any(), any(), eq("research"), any()))
                .thenReturn(new ModelResponse("research", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", repaired, 10, 10));

        List<ResearchCitation> citations = pipeline.run("q", new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));
        assertFalse(citations.isEmpty());
        assertEquals("s", citations.get(0).getSource());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_success_total", "scene", "research");
    }

    @Test
    void researchFallsBackWhenRepairFails() {
        ModelInvocationService modelInvocationService = Mockito.mock(ModelInvocationService.class);
        PromptAssembler promptAssembler = Mockito.mock(PromptAssembler.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        MetricsPublisher metricsPublisher = Mockito.mock(MetricsPublisher.class);
        JsonOutputRepairService repairService = new JsonOutputRepairService(modelInvocationService, promptAssembler,
                metricsPublisher);
        ResearchPipeline pipeline = new ResearchPipeline(modelInvocationService, promptAssembler, new ObjectMapper(),
                eventPublisher, eventStreamService, repairService);

        String badContent = "鏃犳硶瑙ｆ瀽";
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.RESEARCH),
                any(), any(), any(), eq("research"), any()))
                .thenReturn(new ModelResponse("research", badContent, 10, 10));
        when(modelInvocationService.invoke(any(ModelRequest.class), eq(ModelScene.CHEAP),
                any(), any(), any(), eq("json_repair"), any()))
                .thenReturn(new ModelResponse("repair", "", 10, 10));

        List<ResearchCitation> citations = pipeline.run("q", new TenantContext("t-1", "u-1", List.of(), "req", "trace"),
                "wf-1", new AtomicLong(0));
        assertFalse(citations.isEmpty());
        assertEquals("local", citations.get(0).getSource());
        Mockito.verify(metricsPublisher).incrementWithTags("json_repair_failure_total", "scene", "research");
    }
}
