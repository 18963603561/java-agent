package com.example.agent.streaming;

import com.example.agent.auth.AuthService;
import com.example.agent.auth.TenantContext;
import com.example.agent.auth.UserContext;
import com.example.agent.domain.event.EventType;
import com.example.agent.domain.event.StreamEvent;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mockito;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SseStreamControllerTest {

    @Mock
    private EventStreamService eventStreamService;

    @Mock
    private AuthService authService;

    @Test
    void timeoutEmitsErrorEventWithSequence() {
        com.example.agent.observability.TracingPublisher tracingPublisher = Mockito.mock(
                com.example.agent.observability.TracingPublisher.class);
        SseStreamController controller = new SseStreamController(eventStreamService, authService, tracingPublisher);
        ReflectionTestUtils.setField(controller, "firstEventTimeout", Duration.ZERO);
        ReflectionTestUtils.setField(controller, "timeoutScheduler", Schedulers.immediate());

        when(authService.authenticate(anyString(), any(ServerWebExchange.class)))
                .thenReturn(new UserContext("user-1", List.of()));
        when(eventStreamService.stream(any(TaskStreamRequest.class), any(TenantContext.class)))
                .thenReturn(Flux.never());
        when(eventStreamService.nextSequence(anyString(), anyString())).thenReturn(1L);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/stream/sse").build());
        exchange.getAttributes().put(TenantContext.CONTEXT_KEY,
                new TenantContext("tenant-a", "user-1", List.of(), "req-1", "trace-1"));

        Flux<ServerSentEvent<StreamEvent>> flux = controller.stream(
                "workflow-1", null, null, null, "api-key", exchange);

        StepVerifier.create(flux)
                .expectSubscription()
                .expectNextMatches(event -> {
                    StreamEvent data = event.data();
                    return data != null
                            && EventType.ERROR_OCCURRED == data.getType()
                            && "workflow-1:1".equals(data.getEventId())
                            && data.getSeq() == 1
                            && "ERROR_OCCURRED".equals(event.event());
                })
                .verifyComplete();
    }
}
