package com.example.agent.runtime;

import com.example.agent.planning.PlanResult;
import com.example.agent.runtime.engine.RuntimeEventDispatchService;
import com.example.agent.runtime.model.StepSpec;
import com.example.agent.security.auth.TenantContext;
import com.example.agent.streaming.domain.EventType;
import com.example.agent.streaming.domain.StreamEvent;
import com.example.agent.streaming.observability.TracingPublisher;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuntimeEventDispatchServiceTest {

    @Test
    void publishPlanEventBuildsExpectedPayload() {
        TestEventPublisher publisher = new TestEventPublisher();
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        Mockito.when(tracingPublisher.currentTraceId()).thenReturn("trace-from-tracing");
        RuntimeEventDispatchService service = new RuntimeEventDispatchService(publisher, tracingPublisher);

        PlanResult plan = new PlanResult(
                "plan-1",
                "summary",
                List.of(new StepSpec("TOOL", Map.of("tool", "search")))
        );
        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "trace-1");
        AtomicLong seqCounter = new AtomicLong(0);

        service.publishPlanEvent(tenantContext, "wf-1", seqCounter, plan, EventType.PLAN_GENERATED);

        StreamEvent event = publisher.lastEvent;
        assertNotNull(event);
        assertEquals(EventType.PLAN_GENERATED, event.getType());
        assertEquals("wf-1:1", event.getEventId());
        assertEquals("tenant-1", event.getTenantId());
        assertEquals("plan-1", event.getPayload().get("planId"));
        assertEquals("summary", event.getPayload().get("summary"));
        assertEquals(1, event.getPayload().get("steps"));
        assertEquals("trace-1", event.getPayload().get("traceId"));
        assertEquals("req-1", event.getPayload().get("requestId"));
    }

    @Test
    void publishUsesTracingTraceIdWhenContextTraceMissing() {
        TestEventPublisher publisher = new TestEventPublisher();
        TracingPublisher tracingPublisher = Mockito.mock(TracingPublisher.class);
        Mockito.when(tracingPublisher.currentTraceId()).thenReturn("trace-from-tracing");
        RuntimeEventDispatchService service = new RuntimeEventDispatchService(publisher, tracingPublisher);

        TenantContext tenantContext = new TenantContext("tenant-1", "user-1", List.of(), "req-1", "");
        AtomicLong seqCounter = new AtomicLong(0);

        service.publish(tenantContext, "wf-1", seqCounter, EventType.REFLECTION_STARTED, Map.of("k", "v"));

        StreamEvent event = publisher.lastEvent;
        assertNotNull(event);
        assertEquals("trace-from-tracing", event.getPayload().get("traceId"));
        assertEquals("req-1", event.getPayload().get("requestId"));
    }

    static class TestEventPublisher implements ApplicationEventPublisher {

        private StreamEvent lastEvent;

        @Override
        public void publishEvent(Object event) {
            if (event instanceof StreamEvent streamEvent) {
                this.lastEvent = streamEvent;
            }
        }

        @Override
        public void publishEvent(ApplicationEvent event) {
        }
    }
}
