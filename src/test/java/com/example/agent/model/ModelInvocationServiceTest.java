package com.example.agent.model;

import com.example.agent.capabilities.llm.LlmClient;
import com.example.agent.capabilities.llm.ModelInvocationService;
import com.example.agent.capabilities.llm.ModelRequest;
import com.example.agent.capabilities.llm.ModelResponse;
import com.example.agent.capabilities.llm.ModelRouter;
import com.example.agent.capabilities.llm.ModelScene;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.sse.EventStreamService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelInvocationServiceTest {

    @Test
    void invokeShouldPublishPromptAndParseEventsWhenEnabled() {
        LlmClient llmClient = Mockito.mock(LlmClient.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ObjectProvider<com.example.agent.runtime.raw.store.RawResultStore> rawStoreProvider = Mockito.mock(ObjectProvider.class);

        when(eventStreamService.nextSequence(anyString(), anyString())).thenReturn(1L, 2L, 3L);
        when(llmClient.generate(any())).thenReturn(new ModelResponse("model-x", "ok", 10, 5));
        when(rawStoreProvider.getIfAvailable()).thenReturn(null);

        ModelInvocationService service = new ModelInvocationService(
                llmClient, modelRouter, eventPublisher, eventStreamService, rawStoreProvider);
        ReflectionTestUtils.setField(service, "llmEventPublishEnabled", true);

        ModelRequest request = new ModelRequest("hello", ModelScene.CHEAP);
        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1");

        service.invoke(request,
                ModelScene.CHEAP,
                tenantContext,
                "wf-1",
                new AtomicLong(0),
                "plan",
                Map.of("k", "v"));

        verify(eventPublisher, times(2)).publishEvent((Object) any());
    }

    @Test
    void invokeShouldSkipEventPublishWhenDisabled() {
        LlmClient llmClient = Mockito.mock(LlmClient.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ObjectProvider<com.example.agent.runtime.raw.store.RawResultStore> rawStoreProvider = Mockito.mock(ObjectProvider.class);

        when(llmClient.generate(any())).thenReturn(new ModelResponse("model-x", "ok", 10, 5));
        when(rawStoreProvider.getIfAvailable()).thenReturn(null);

        ModelInvocationService service = new ModelInvocationService(
                llmClient, modelRouter, eventPublisher, eventStreamService, rawStoreProvider);
        ReflectionTestUtils.setField(service, "llmEventPublishEnabled", false);

        ModelRequest request = new ModelRequest("hello", ModelScene.CHEAP);
        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1");

        service.invoke(request,
                ModelScene.CHEAP,
                tenantContext,
                "wf-1",
                new AtomicLong(0),
                "plan",
                Map.of("k", "v"));

        verify(eventPublisher, never()).publishEvent((Object) any());
    }
}
