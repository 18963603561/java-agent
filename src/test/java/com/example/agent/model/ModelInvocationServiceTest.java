package com.example.agent.model;

import com.example.agent.capabilities.llm.client.LlmClient;
import com.example.agent.capabilities.llm.client.LlmEventPublisher;
import com.example.agent.capabilities.llm.client.LlmFailureRecorder;
import com.example.agent.capabilities.llm.client.ModelInvocationService;
import com.example.agent.capabilities.llm.client.RawRefAttachmentService;
import com.example.agent.capabilities.llm.contract.ModelRequest;
import com.example.agent.capabilities.llm.contract.ModelResponse;
import com.example.agent.capabilities.llm.provider.ModelRouter;
import com.example.agent.capabilities.llm.contract.ModelScene;
import com.example.agent.capabilities.llm.provider.ProviderErrorMapper;
import com.example.agent.capabilities.llm.prompt.PromptMessage;
import com.example.agent.capabilities.llm.prompt.PromptRole;
import com.example.agent.capabilities.llm.client.events.LlmEventPayloadMapper;
import com.example.agent.capabilities.llm.support.ValidationSupport;
import com.example.agent.runtime.raw.ref.RawRef;
import com.example.agent.runtime.raw.store.RawResultStore;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.MetricsPublisher;
import com.example.agent.streaming.sse.EventStreamService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        ObjectProvider<RawResultStore> rawStoreProvider = Mockito.mock(ObjectProvider.class);

        when(eventStreamService.nextSequence(anyString(), anyString())).thenReturn(1L, 2L, 3L);
        when(llmClient.generate(any())).thenReturn(new ModelResponse("model-x", "ok", 10, 5));
        when(rawStoreProvider.getIfAvailable()).thenReturn(null);

        ModelInvocationService service = createService(
                llmClient,
                modelRouter,
                eventPublisher,
                eventStreamService,
                rawStoreProvider);
        ReflectionTestUtils.setField(service, "llmEventPublishEnabled", true);

        ModelRequest request = new ModelRequest("hello", ModelScene.CHEAP);
        request.setMessages(List.of(
                new PromptMessage(PromptRole.SYSTEM, "sys"),
                new PromptMessage(PromptRole.USER, "user")
        ));
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
        ObjectProvider<RawResultStore> rawStoreProvider = Mockito.mock(ObjectProvider.class);

        when(llmClient.generate(any())).thenReturn(new ModelResponse("model-x", "ok", 10, 5));
        when(rawStoreProvider.getIfAvailable()).thenReturn(null);

        ModelInvocationService service = createService(
                llmClient,
                modelRouter,
                eventPublisher,
                eventStreamService,
                rawStoreProvider);
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

    @Test
    void invokeShouldSkipEventWhenTenantOrWorkflowMissing() {
        LlmClient llmClient = Mockito.mock(LlmClient.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ObjectProvider<RawResultStore> rawStoreProvider = Mockito.mock(ObjectProvider.class);

        when(llmClient.generate(any())).thenReturn(new ModelResponse("model-x", "ok", 10, 5));
        when(rawStoreProvider.getIfAvailable()).thenReturn(null);

        ModelInvocationService service = createService(
                llmClient,
                modelRouter,
                eventPublisher,
                eventStreamService,
                rawStoreProvider);
        ReflectionTestUtils.setField(service, "llmEventPublishEnabled", true);

        ModelRequest request = new ModelRequest("hello", ModelScene.CHEAP);

        service.invoke(request, ModelScene.CHEAP, null, "wf-1", null, "plan", Map.of());
        service.invoke(request, ModelScene.CHEAP,
                new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1"),
                null, null, "plan", Map.of());

        verify(eventPublisher, never()).publishEvent((Object) any());
    }

    @Test
    void invokeShouldNormalizeNullSceneAndPhase() {
        LlmClient llmClient = Mockito.mock(LlmClient.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ObjectProvider<RawResultStore> rawStoreProvider = Mockito.mock(ObjectProvider.class);

        when(llmClient.generate(any())).thenReturn(new ModelResponse("model-x", "ok", 10, 5));
        when(rawStoreProvider.getIfAvailable()).thenReturn(null);

        ModelInvocationService service = createService(
                llmClient,
                modelRouter,
                eventPublisher,
                eventStreamService,
                rawStoreProvider);
        ReflectionTestUtils.setField(service, "llmEventPublishEnabled", true);

        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1");
        ModelRequest request = new ModelRequest("hello", null);

        service.invoke(request, null, tenantContext, "wf-1", new AtomicLong(0), null, Map.of());

        ArgumentCaptor<ModelRequest> requestCaptor = ArgumentCaptor.forClass(ModelRequest.class);
        verify(llmClient).generate(requestCaptor.capture());
        assertEquals(ModelScene.CHEAP, requestCaptor.getValue().getScene());

        ArgumentCaptor<StreamEvent> eventCaptor = ArgumentCaptor.forClass(StreamEvent.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        List<StreamEvent> events = eventCaptor.getAllValues();
        assertEquals("unknown", events.get(0).getPayload().get("phase"));
        assertEquals("unknown", events.get(1).getPayload().get("phase"));
    }

    @Test
    void invokeShouldAttachRawRefWhenStoreReturnsRefId() {
        LlmClient llmClient = Mockito.mock(LlmClient.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ObjectProvider<RawResultStore> rawStoreProvider = Mockito.mock(ObjectProvider.class);
        RawResultStore rawResultStore = Mockito.mock(RawResultStore.class);

        ModelResponse response = new ModelResponse("model-x", "ok", 10, 5);
        when(llmClient.generate(any())).thenReturn(response);
        when(rawStoreProvider.getIfAvailable()).thenReturn(rawResultStore);

        RawRef ref = new RawRef();
        ref.setRefId("rawref:v1:file:1");
        when(rawResultStore.store(anyString(), any(), anyString())).thenReturn(ref);

        ModelInvocationService service = createService(
                llmClient,
                modelRouter,
                eventPublisher,
                eventStreamService,
                rawStoreProvider);
        ReflectionTestUtils.setField(service, "llmEventPublishEnabled", false);

        ModelResponse result = service.invoke(new ModelRequest("hello", ModelScene.CHEAP), ModelScene.CHEAP,
                new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1"),
                "wf-1", null, "plan", Map.of());

        assertNotNull(result);
        assertEquals("rawref:v1:file:1", result.getRawRef());
    }

    @Test
    void invokeShouldFallbackToRawKeyWhenRefIdMissing() {
        LlmClient llmClient = Mockito.mock(LlmClient.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ObjectProvider<RawResultStore> rawStoreProvider = Mockito.mock(ObjectProvider.class);
        RawResultStore rawResultStore = Mockito.mock(RawResultStore.class);

        ModelResponse response = new ModelResponse("model-x", "ok", 10, 5);
        when(llmClient.generate(any())).thenReturn(response);
        when(rawStoreProvider.getIfAvailable()).thenReturn(rawResultStore);

        RawRef ref = new RawRef();
        ref.setKey("legacy-key");
        when(rawResultStore.store(anyString(), any(), anyString())).thenReturn(ref);

        ModelInvocationService service = createService(
                llmClient,
                modelRouter,
                eventPublisher,
                eventStreamService,
                rawStoreProvider);
        ReflectionTestUtils.setField(service, "llmEventPublishEnabled", false);

        ModelResponse result = service.invoke(new ModelRequest("hello", ModelScene.CHEAP), ModelScene.CHEAP,
                new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1"),
                "wf-1", null, "plan", Map.of());

        assertNotNull(result);
        assertEquals("legacy-key", result.getRawRef());
    }

    @Test
    void invokeShouldIgnoreRawRefWhenContentBlank() {
        LlmClient llmClient = Mockito.mock(LlmClient.class);
        ModelRouter modelRouter = Mockito.mock(ModelRouter.class);
        ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
        EventStreamService eventStreamService = Mockito.mock(EventStreamService.class);
        ObjectProvider<RawResultStore> rawStoreProvider = Mockito.mock(ObjectProvider.class);
        RawResultStore rawResultStore = Mockito.mock(RawResultStore.class);

        ModelResponse response = new ModelResponse("model-x", "", 10, 5);
        when(llmClient.generate(any())).thenReturn(response);
        when(rawStoreProvider.getIfAvailable()).thenReturn(rawResultStore);

        ModelInvocationService service = createService(
                llmClient,
                modelRouter,
                eventPublisher,
                eventStreamService,
                rawStoreProvider);
        ReflectionTestUtils.setField(service, "llmEventPublishEnabled", false);

        ModelResponse result = service.invoke(new ModelRequest("hello", ModelScene.CHEAP), ModelScene.CHEAP,
                new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1"),
                "wf-1", null, "plan", Map.of());

        assertNotNull(result);
        assertNull(result.getRawRef());
        verify(rawResultStore, never()).store(anyString(), any(), anyString());
    }

    private ModelInvocationService createService(LlmClient llmClient,
                                                 ModelRouter modelRouter,
                                                 ApplicationEventPublisher eventPublisher,
                                                 EventStreamService eventStreamService,
                                                 ObjectProvider<RawResultStore> rawStoreProvider) {
        @SuppressWarnings("unchecked")
        ObjectProvider<MetricsPublisher> metricsPublisherProvider = Mockito.mock(ObjectProvider.class);
        when(metricsPublisherProvider.getIfAvailable()).thenReturn(null);
        LlmEventPublisher llmEventPublisher = new LlmEventPublisher(
                eventPublisher,
                eventStreamService,
                new LlmEventPayloadMapper());
        RawRefAttachmentService rawRefAttachmentService = new RawRefAttachmentService(rawStoreProvider);
        LlmFailureRecorder llmFailureRecorder = new LlmFailureRecorder(new ProviderErrorMapper(), metricsPublisherProvider);
        return new ModelInvocationService(
                llmClient,
                modelRouter,
                new ValidationSupport(),
                new ProviderErrorMapper(),
                rawRefAttachmentService,
                llmEventPublisher,
                llmFailureRecorder);
    }
}

